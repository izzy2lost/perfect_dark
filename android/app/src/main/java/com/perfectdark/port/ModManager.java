package com.perfectdark.port;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Installed mods live in {@code <externalFilesDir>/mods/<name>/}, which is passed to the
 * game as {@code --moddir}. The port overlays that directory on top of {@code data/}, so a
 * mod is just a tree of replacement files plus an optional {@code modconfig.txt}.
 */
public final class ModManager {

    private static final String PREFS = "mods";
    private static final String KEY_ACTIVE = "active";
    private static final String MODS_DIR = "mods";
    private static final String CONFIG_NAME = "modconfig.txt";

    /** Refuse absurd archives rather than filling the user's storage. */
    private static final long MAX_TOTAL_BYTES = 2L * 1024 * 1024 * 1024;

    public static final class ModInfo {
        public final String name;
        public final File dir;
        public final boolean hasConfig;
        public final long sizeBytes;

        ModInfo(String name, File dir, boolean hasConfig, long sizeBytes) {
            this.name = name;
            this.dir = dir;
            this.hasConfig = hasConfig;
            this.sizeBytes = sizeBytes;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private ModManager() {
    }

    public static File modsRoot(Context ctx) {
        File root = new File(ctx.getExternalFilesDir(null), MODS_DIR);
        if (!root.exists()) {
            //noinspection ResultOfMethodCallIgnored
            root.mkdirs();
        }
        return root;
    }

    public static List<ModInfo> installed(Context ctx) {
        List<ModInfo> out = new ArrayList<>();
        File[] dirs = modsRoot(ctx).listFiles(File::isDirectory);
        if (dirs == null) {
            return out;
        }
        for (File d : dirs) {
            out.add(new ModInfo(d.getName(), d, new File(d, CONFIG_NAME).exists(), dirSize(d)));
        }
        Collections.sort(out, (a, b) -> a.name.compareToIgnoreCase(b.name));
        return out;
    }

    public static String getActive(Context ctx) {
        String name = prefs(ctx).getString(KEY_ACTIVE, "");
        if (name.isEmpty()) {
            return "";
        }
        // a mod deleted outside the app should not leave a dangling --moddir
        if (!new File(modsRoot(ctx), name).isDirectory()) {
            setActive(ctx, "");
            return "";
        }
        return name;
    }

    public static void setActive(Context ctx, String name) {
        prefs(ctx).edit().putString(KEY_ACTIVE, name == null ? "" : name).apply();
    }

    /** Absolute path to pass as {@code --moddir}, or null when no mod is selected. */
    public static String activeModPath(Context ctx) {
        String name = getActive(ctx);
        if (name.isEmpty()) {
            return null;
        }
        return new File(modsRoot(ctx), name).getAbsolutePath();
    }

    public static boolean delete(Context ctx, String name) {
        File dir = new File(modsRoot(ctx), name);
        if (!dir.isDirectory()) {
            return false;
        }
        boolean ok = deleteTree(dir);
        if (name.equals(getActive(ctx))) {
            setActive(ctx, "");
        }
        return ok;
    }

    /**
     * Extracts a mod archive into its own directory.
     *
     * @param suggestedName name to use when the archive has no single root folder
     * @return the installed mod's name
     */
    public static String installFromZip(Context ctx, Uri uri, String suggestedName) throws IOException {
        String base = sanitise(stripExtension(suggestedName));
        if (base.isEmpty()) {
            base = "mod";
        }

        File dest = new File(modsRoot(ctx), base);
        for (int i = 2; dest.exists(); ++i) {
            dest = new File(modsRoot(ctx), base + "_" + i);
        }
        if (!dest.mkdirs()) {
            throw new IOException("Could not create " + dest.getAbsolutePath());
        }

        try {
            extract(ctx, uri, dest);
        } catch (IOException e) {
            deleteTree(dest);
            throw e;
        }

        flattenSingleRoot(dest);

        if (!looksLikeMod(dest)) {
            deleteTree(dest);
            throw new IOException("That archive does not look like a mod: no modconfig.txt and no "
                    + "textures/animations/sequences folder inside it.");
        }

        return dest.getName();
    }

    private static void extract(Context ctx, Uri uri, File dest) throws IOException {
        final String destPath = dest.getCanonicalPath() + File.separator;
        long total = 0;

        try (InputStream raw = ctx.getContentResolver().openInputStream(uri)) {
            if (raw == null) {
                throw new IOException("Could not open the selected file");
            }
            try (ZipInputStream zin = new ZipInputStream(raw)) {
                ZipEntry entry;
                boolean sawEntry = false;
                while ((entry = zin.getNextEntry()) != null) {
                    sawEntry = true;
                    File out = new File(dest, entry.getName());

                    // Zip Slip: an entry named ../../something must never escape the mod dir.
                    if (!out.getCanonicalPath().startsWith(destPath)) {
                        throw new IOException("Archive contains an unsafe path: " + entry.getName());
                    }

                    if (entry.isDirectory()) {
                        //noinspection ResultOfMethodCallIgnored
                        out.mkdirs();
                        continue;
                    }

                    File parent = out.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        throw new IOException("Could not create " + parent.getAbsolutePath());
                    }

                    byte[] buf = new byte[64 * 1024];
                    try (OutputStream os = new FileOutputStream(out)) {
                        int n;
                        while ((n = zin.read(buf)) > 0) {
                            total += n;
                            if (total > MAX_TOTAL_BYTES) {
                                throw new IOException("Archive is unreasonably large (over 2 GB unpacked)");
                            }
                            os.write(buf, 0, n);
                        }
                    }
                }
                if (!sawEntry) {
                    throw new IOException("That file is not a zip archive");
                }
            }
        }
    }

    /** Archives usually wrap everything in one folder; hoist its contents up a level. */
    private static void flattenSingleRoot(File dest) {
        File[] kids = dest.listFiles();
        if (kids == null || kids.length != 1 || !kids[0].isDirectory()) {
            return;
        }
        File inner = kids[0];
        File[] innerKids = inner.listFiles();
        if (innerKids == null) {
            return;
        }
        for (File f : innerKids) {
            //noinspection ResultOfMethodCallIgnored
            f.renameTo(new File(dest, f.getName()));
        }
        //noinspection ResultOfMethodCallIgnored
        inner.delete();
    }

    private static boolean looksLikeMod(File dir) {
        if (new File(dir, CONFIG_NAME).exists()) {
            return true;
        }
        for (String known : new String[]{"textures", "animations", "sequences"}) {
            if (new File(dir, known).isDirectory()) {
                return true;
            }
        }
        // a bare tree of replacement ROM files is also legitimate
        File[] kids = dir.listFiles();
        return kids != null && kids.length > 0;
    }

    private static boolean deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) {
                deleteTree(k);
            }
        }
        return f.delete();
    }

    private static long dirSize(File f) {
        if (f.isFile()) {
            return f.length();
        }
        long total = 0;
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) {
                total += dirSize(k);
            }
        }
        return total;
    }

    public static String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.US, "%.0f KB", bytes / 1024.0);
        }
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private static String stripExtension(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String sanitise(String name) {
        StringBuilder sb = new StringBuilder();
        for (char c : name.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == ' ' || c == '.') {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
