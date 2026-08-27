package com.perfectdark.port;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;

/**
 * Front end for the port: makes sure the ROM is in place, lets the player pick a mod and
 * tune the on-screen controls, then hands off to {@link MainActivity}.
 */
public class LauncherActivity extends AppCompatActivity {

    private static final String ROM_FILE_NAME = "pd.ntsc-final.z64";
    // NTSC-U Rev 1 (v1.1) .z64 -- what the port targets
    private static final String MD5_NTSC_V11 = "e03b088b6ac9e0080440efed07c1e40f";
    // NTSC-U v1.0 .z64 -- allowed, but not recommended
    private static final String MD5_NTSC_V10 = "7f4171b0c8d17815be37913f535e4e93";

    private static final float OPACITY_MIN = 0.05f;

    private View missingRomView;
    private TextView infoText;
    private Button playButton;
    private RadioGroup modGroup;
    private TextView opacityLabel;

    private TouchLayout touchLayout;
    private boolean importing;

    private final ActivityResultLauncher<String[]> romPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onRomPicked);

    private final ActivityResultLauncher<String[]> modPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onModPicked);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_launcher);
        applyEdgeToEdgeInsets();

        missingRomView = findViewById(R.id.missingRomContainer);
        infoText = findViewById(R.id.infoText);
        playButton = findViewById(R.id.playButton);
        modGroup = findViewById(R.id.modGroup);
        opacityLabel = findViewById(R.id.opacityLabel);

        touchLayout = new TouchLayout(this);

        findViewById(R.id.pickRomButton).setOnClickListener(v -> openRomPicker());
        findViewById(R.id.importModButton).setOnClickListener(v -> openModPicker());
        findViewById(R.id.deleteModButton).setOnClickListener(v -> confirmDeleteMod());
        findViewById(R.id.resetLayoutButton).setOnClickListener(v -> {
            touchLayout.resetToDefaults();
            syncSliders();
            Toast.makeText(this, "Control layout reset", Toast.LENGTH_SHORT).show();
        });
        playButton.setOnClickListener(v -> onPlayPressed());

        setUpSliders();
        ensureDataDir();
    }

    /**
     * The window runs edge to edge, so the content has to keep itself clear of the status bar,
     * the navigation bar and any camera cutout. In landscape those land on the short edges,
     * which is exactly where the buttons are.
     */
    private void applyEdgeToEdgeInsets() {
        final View content = findViewById(R.id.launcherContent);
        final int base = Math.round(24 * getResources().getDisplayMetrics().density);

        ViewCompat.setOnApplyWindowInsetsListener(content, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(base + bars.left, base + bars.top, base + bars.right, base + bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(content);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshRomState();
        refreshModList();
    }

    // ---------------------------------------------------------------- ROM

    private File romFile() {
        return new File(new File(getExternalFilesDir(null), "data"), ROM_FILE_NAME);
    }

    private void ensureDataDir() {
        File dataDir = new File(getExternalFilesDir(null), "data");
        if (!dataDir.exists() && !dataDir.mkdirs()) {
            Toast.makeText(this, "Could not create the data folder", Toast.LENGTH_LONG).show();
        }
    }

    private boolean romExists() {
        File f = romFile();
        return f.exists() && f.length() > 0;
    }

    private void refreshRomState() {
        if (romExists()) {
            missingRomView.setVisibility(View.GONE);
            playButton.setEnabled(true);
        } else {
            missingRomView.setVisibility(View.VISIBLE);
            infoText.setText(getString(R.string.rom_missing));
            playButton.setEnabled(false);
        }
    }

    private void onPlayPressed() {
        if (!romExists()) {
            Toast.makeText(this, "Select a ROM first", Toast.LENGTH_SHORT).show();
            return;
        }
        int status = checkRomHash(romFile());
        if (status == 0) {
            startGame();
        } else if (status == 1) {
            showV10WarningDialog(romFile());
        } else {
            showHashMismatchDialog(romFile());
        }
    }

    private void openRomPicker() {
        romPicker.launch(new String[]{"application/octet-stream", "*/*"});
    }

    private void onRomPicked(@Nullable Uri uri) {
        if (uri == null) {
            return;
        }
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
            // some providers do not support persistable permissions; we only need it for the copy
        }

        try {
            copyToFile(uri, romFile());
        } catch (IOException e) {
            Toast.makeText(this, "Failed to copy ROM: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        refreshRomState();
        if (romExists()) {
            Toast.makeText(this, "ROM copied", Toast.LENGTH_SHORT).show();
        }
    }

    private void copyToFile(Uri sourceUri, File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create " + parent.getAbsolutePath());
        }
        try (InputStream in = getContentResolver().openInputStream(sourceUri);
             FileOutputStream out = new FileOutputStream(target)) {
            if (in == null) {
                throw new IOException("Unable to open the selected file");
            }
            byte[] buf = new byte[64 * 1024];
            int read;
            while ((read = in.read(buf)) != -1) {
                out.write(buf, 0, read);
            }
            out.flush();
        }
    }

    private void startGame() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    /** 0 = v1.1 (recommended), 1 = v1.0 (allowed), -1 = unknown. */
    private int checkRomHash(File file) {
        try {
            String md5 = computeMd5(file);
            if (MD5_NTSC_V11.equalsIgnoreCase(md5)) return 0;
            if (MD5_NTSC_V10.equalsIgnoreCase(md5)) return 1;
            return -1;
        } catch (Exception e) {
            Toast.makeText(this, "Hash check failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return -1;
        }
    }

    private void showHashMismatchDialog(File target) {
        String computed;
        try {
            computed = computeMd5(target);
        } catch (Exception e) {
            computed = "<error>";
        }

        new AlertDialog.Builder(this)
                .setTitle("Wrong ROM version")
                .setMessage("Expected NTSC-U v1.1 (md5 " + MD5_NTSC_V11 + ")\n"
                        + "Also allowed: v1.0 (md5 " + MD5_NTSC_V10 + ")\n\nGot: " + computed)
                .setPositiveButton("Pick another", (d, w) -> {
                    //noinspection ResultOfMethodCallIgnored
                    target.delete();
                    refreshRomState();
                    openRomPicker();
                })
                .setNegativeButton("Play anyway", (d, w) -> startGame())
                .show();
    }

    private void showV10WarningDialog(File target) {
        new AlertDialog.Builder(this)
                .setTitle("NTSC v1.0 detected")
                .setMessage("The port targets NTSC-U v1.1. v1.0 mostly works, but some content may not.")
                .setPositiveButton("Play", (d, w) -> startGame())
                .setNegativeButton("Pick another", (d, w) -> {
                    //noinspection ResultOfMethodCallIgnored
                    target.delete();
                    refreshRomState();
                    openRomPicker();
                })
                .show();
    }

    private String computeMd5(File file) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] buffer = new byte[64 * 1024];
        try (InputStream in = new java.io.FileInputStream(file);
             DigestInputStream din = new DigestInputStream(in, md)) {
            //noinspection StatementWithEmptyBody
            while (din.read(buffer) != -1) {
                // DigestInputStream updates the digest as we read
            }
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) {
            sb.append(String.format(Locale.US, "%02x", b));
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- mods

    private void refreshModList() {
        List<ModManager.ModInfo> mods = ModManager.installed(this);
        String active = ModManager.getActive(this);

        modGroup.setOnCheckedChangeListener(null);
        modGroup.removeAllViews();

        RadioButton none = new RadioButton(this);
        none.setId(View.generateViewId());
        none.setText(getString(R.string.no_mod));
        none.setTextColor(0xFFD8D8E2);
        none.setTag("");
        modGroup.addView(none);

        for (ModManager.ModInfo m : mods) {
            RadioButton rb = new RadioButton(this);
            rb.setId(View.generateViewId());
            rb.setText(m.name + "  (" + ModManager.humanSize(m.sizeBytes)
                    + (m.hasConfig ? ", modconfig.txt" : "") + ")");
            rb.setTextColor(0xFFD8D8E2);
            rb.setTag(m.name);
            modGroup.addView(rb);
        }

        // restore the selection
        int checkId = none.getId();
        for (int i = 0; i < modGroup.getChildCount(); ++i) {
            View child = modGroup.getChildAt(i);
            if (active.equals(child.getTag())) {
                checkId = child.getId();
                break;
            }
        }
        modGroup.check(checkId);

        modGroup.setOnCheckedChangeListener((group, id) -> {
            View sel = group.findViewById(id);
            if (sel != null) {
                ModManager.setActive(this, (String) sel.getTag());
            }
        });
    }

    private void openModPicker() {
        if (importing) {
            return;
        }
        modPicker.launch(new String[]{"application/zip", "application/x-zip-compressed", "*/*"});
    }

    private void onModPicked(@Nullable Uri uri) {
        if (uri == null || importing) {
            return;
        }

        final String displayName = queryDisplayName(uri);
        importing = true;
        Toast.makeText(this, "Importing " + displayName + "…", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            String result;
            String error = null;
            try {
                result = ModManager.installFromZip(this, uri, displayName);
            } catch (IOException e) {
                result = null;
                error = e.getMessage();
            }

            final String installed = result;
            final String failure = error;
            runOnUiThread(() -> {
                importing = false;
                if (installed != null) {
                    ModManager.setActive(this, installed);
                    refreshModList();
                    Toast.makeText(this, "Installed and selected: " + installed, Toast.LENGTH_LONG).show();
                } else {
                    new AlertDialog.Builder(this)
                            .setTitle("Could not import mod")
                            .setMessage(failure == null ? "Unknown error" : failure)
                            .setPositiveButton("OK", null)
                            .show();
                }
            });
        }, "mod-import").start();
    }

    private void confirmDeleteMod() {
        final String active = ModManager.getActive(this);
        if (active.isEmpty()) {
            Toast.makeText(this, "Select a mod to delete", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Delete " + active + "?")
                .setMessage("This removes the mod's files from this device.")
                .setPositiveButton("Delete", (d, w) -> {
                    if (ModManager.delete(this, active)) {
                        Toast.makeText(this, "Deleted " + active, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Could not delete " + active, Toast.LENGTH_LONG).show();
                    }
                    refreshModList();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String name = c.getString(idx);
                    if (name != null && !name.isEmpty()) {
                        return name;
                    }
                }
            }
        } catch (Exception ignored) {
            // fall through to the generic name
        }
        return "mod.zip";
    }

    // ---------------------------------------------------------------- touch settings

    private void setUpSliders() {
        SeekBar opacity = findViewById(R.id.opacitySeek);

        opacity.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                touchLayout.opacity = OPACITY_MIN + (1f - OPACITY_MIN) * (progress / 100f);
                updateSliderLabels();
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                touchLayout.save();
            }
        });

        syncSliders();
    }

    private void syncSliders() {
        SeekBar opacity = findViewById(R.id.opacitySeek);
        opacity.setProgress(Math.round((touchLayout.opacity - OPACITY_MIN) / (1f - OPACITY_MIN) * 100f));
        updateSliderLabels();
    }

    private void updateSliderLabels() {
        opacityLabel.setText(String.format(Locale.US, "%s \u2014 %d%%",
                getString(R.string.opacity), Math.round(touchLayout.opacity * 100)));
    }

    private abstract static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar bar) { }
        @Override public void onStopTrackingTouch(SeekBar bar) { }
    }
}
