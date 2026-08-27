package com.perfectdark.port;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The set of on-screen controls, their placement, and its persistence.
 *
 * Everything is measured in dp from whichever screen corner the control is anchored to, so a
 * control keeps the same physical size and the same distance from the player's thumb on every
 * device. Fractions of the viewport do not work here: phones in landscape run from about 16:9
 * to over 21:9, and a fraction-based layout flings the buttons apart horizontally while
 * shrinking them vertically on exactly the wide, short screens that are most common.
 */
public final class TouchLayout {

    /** N64 pad bits, matching CONT_* in include/PR/os_cont.h. */
    public static final int CONT_A       = 0x00008000;
    public static final int CONT_B       = 0x00004000;
    public static final int CONT_Z       = 0x00002000; // CONT_G
    public static final int CONT_START   = 0x00001000;
    public static final int CONT_UP      = 0x00000800;
    public static final int CONT_DOWN    = 0x00000400;
    public static final int CONT_LEFT    = 0x00000200;
    public static final int CONT_RIGHT   = 0x00000100;
    public static final int CONT_Y       = 0x00000080; // CONT_EXTRA1
    public static final int CONT_X       = 0x00000040; // CONT_EXTRA0
    public static final int CONT_L       = 0x00000020;
    public static final int CONT_R       = 0x00000010;
    public static final int CONT_C_UP    = 0x00000008; // CONT_E
    public static final int CONT_C_DOWN  = 0x00000004; // CONT_D
    public static final int CONT_C_LEFT  = 0x00000002; // CONT_C
    public static final int CONT_C_RIGHT = 0x00000001; // CONT_F

    /** Port-specific extra bits; see the control table in README.md. */
    public static final int CONT_CROUCH_CYCLE = 0x80000000;

    public enum Kind {
        BUTTON,      // holds a CONT_ bit while pressed
        STICK_MOVE,  // the analog stick
        KEY,         // injects an Android keycode into SDL (used for the console)
        TOGGLE       // hides/shows the overlay so taps reach the game surface directly
    }

    public enum Anchor {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    /** Shape variety is the point of an expressive layout: triggers read differently to buttons. */
    public enum Shape {
        CIRCLE, PILL
    }

    public static final class Control {
        public final String id;
        /** what the control does, in words */
        public final String label;
        /** which N64 button that is, shown small underneath */
        public final String subLabel;
        public final Kind kind;
        public final Anchor anchor;
        public final Shape shape;
        public final int mask;
        public final int keyCode;

        /** dp from the anchored corner to the control's centre */
        public float dx, dy;
        /** hit and draw radius, in dp */
        public float radius;
        public boolean enabled;

        final float defDx, defDy, defRadius;
        final boolean defEnabled;

        Control(String id, String label, String subLabel, Kind kind, Anchor anchor,
                int mask, int keyCode, float dx, float dy, float radius, boolean enabled) {
            this(id, label, subLabel, kind, anchor, Shape.CIRCLE, mask, keyCode, dx, dy, radius, enabled);
        }

        Control(String id, String label, String subLabel, Kind kind, Anchor anchor, Shape shape,
                int mask, int keyCode, float dx, float dy, float radius, boolean enabled) {
            this.id = id;
            this.label = label;
            this.subLabel = subLabel;
            this.kind = kind;
            this.anchor = anchor;
            this.shape = shape;
            this.mask = mask;
            this.keyCode = keyCode;
            this.dx = this.defDx = dx;
            this.dy = this.defDy = dy;
            this.radius = this.defRadius = radius;
            this.enabled = this.defEnabled = enabled;
        }

        void reset() {
            dx = defDx;
            dy = defDy;
            radius = defRadius;
            enabled = defEnabled;
        }

        public boolean isStick() {
            return kind == Kind.STICK_MOVE;
        }
    }

    private static final String PREFS = "touch_controls";
    // bumped when the default layout changes shape, so an old save cannot resurrect a bad one
    private static final int LAYOUT_VERSION = 6;
    private static final String KEY_VERSION = "layout_version";
    private static final String KEY_LAYOUT = "layout";
    private static final String KEY_OPACITY = "opacity";
    private static final String KEY_ENABLED = "enabled";

    private final List<Control> controls = new ArrayList<>();
    private final SharedPreferences prefs;

    public float opacity = 0.5f;
    public boolean enabled = true;

    public TouchLayout(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        buildDefaults();
        load();
    }

    private void buildDefaults() {
        controls.clear();

        // One analog stick, exactly like the N64 pad: it drives all movement, and how it
        // splits between walking and turning is the game's own Control Style setting.
        controls.add(new Control("move", "", "", Kind.STICK_MOVE, Anchor.BOTTOM_LEFT,
                0, 0, 105, 105, 68, true));

        // The C buttons, in their N64 diamond. In the default control style these look up and
        // down and sidestep left and right -- the job a second stick would otherwise do.
        controls.add(new Control("cup", "C\u2191", "", Kind.BUTTON, Anchor.BOTTOM_RIGHT,
                CONT_C_UP, 0, 108, 166, 26, true));
        controls.add(new Control("cdown", "C\u2193", "", Kind.BUTTON, Anchor.BOTTOM_RIGHT,
                CONT_C_DOWN, 0, 108, 50, 26, true));
        controls.add(new Control("cleft", "C\u2190", "", Kind.BUTTON, Anchor.BOTTOM_RIGHT,
                CONT_C_LEFT, 0, 166, 108, 26, true));
        controls.add(new Control("cright", "C\u2192", "", Kind.BUTTON, Anchor.BOTTOM_RIGHT,
                CONT_C_RIGHT, 0, 50, 108, 26, true));

        // Fire and aim go along the top edge under the index fingers.
        controls.add(new Control("aim", "AIM", "R", Kind.BUTTON, Anchor.TOP_LEFT, Shape.PILL,
                CONT_R, 0, 122, 44, 33, true));
        controls.add(new Control("fire", "FIRE", "Z", Kind.BUTTON, Anchor.TOP_RIGHT, Shape.PILL,
                CONT_Z, 0, 126, 44, 36, true));

        // Face buttons, inboard of the C diamond.
        controls.add(new Control("use", "USE", "A", Kind.BUTTON, Anchor.BOTTOM_RIGHT,
                CONT_A, 0, 232, 88, 27, true));
        controls.add(new Control("back", "BACK", "B", Kind.BUTTON, Anchor.BOTTOM_RIGHT,
                CONT_B, 0, 232, 162, 25, true));
        controls.add(new Control("reload", "RELOAD", "X", Kind.BUTTON, Anchor.BOTTOM_RIGHT,
                CONT_X, 0, 302, 78, 25, true));
        controls.add(new Control("next", "NEXT", "Y", Kind.BUTTON, Anchor.BOTTOM_RIGHT,
                CONT_Y, 0, 302, 152, 25, true));

        // Left-hand extras, stacked beside the stick.
        controls.add(new Control("crouch", "CROUCH", "", Kind.BUTTON, Anchor.BOTTOM_LEFT,
                CONT_CROUCH_CYCLE, 0, 212, 84, 26, true));
        controls.add(new Control("radial", "WEAPON", "D-D", Kind.BUTTON, Anchor.BOTTOM_LEFT,
                CONT_DOWN, 0, 212, 158, 25, true));

        // Occasional actions live on the top edge rather than over the play area.
        controls.add(new Control("altfire", "ALT", "L", Kind.BUTTON, Anchor.TOP_LEFT,
                CONT_L, 0, 214, 44, 26, true));
        controls.add(new Control("prev", "PREV", "D-L", Kind.BUTTON, Anchor.TOP_RIGHT,
                CONT_LEFT, 0, 272, 42, 24, true));

        // System row.
        controls.add(new Control("hide", "\u25CE", "", Kind.TOGGLE, Anchor.TOP_LEFT,
                0, 0, 36, 36, 20, true));
        controls.add(new Control("start", "START", "", Kind.BUTTON, Anchor.TOP_RIGHT,
                CONT_START, 0, 38, 38, 23, true));
        controls.add(new Control("console", "~", "", Kind.KEY, Anchor.TOP_RIGHT,
                0, android.view.KeyEvent.KEYCODE_GRAVE, 208, 40, 20, true));
    }

    public List<Control> getControls() {
        return Collections.unmodifiableList(controls);
    }

    public void resetToDefaults() {
        for (Control c : controls) {
            c.reset();
        }
        opacity = 0.5f;
        enabled = true;
        save();
    }

    private void load() {
        opacity = prefs.getFloat(KEY_OPACITY, opacity);
        enabled = prefs.getBoolean(KEY_ENABLED, enabled);

        if (prefs.getInt(KEY_VERSION, 0) != LAYOUT_VERSION) {
            // saved by an older build whose coordinates meant something else; keep the defaults
            return;
        }

        String json = prefs.getString(KEY_LAYOUT, null);
        if (json == null) {
            return;
        }

        try {
            JSONObject root = new JSONObject(json);
            for (Control c : controls) {
                JSONObject o = root.optJSONObject(c.id);
                if (o == null) {
                    // a control added in a later version: leave it at its default
                    continue;
                }
                c.dx = (float) o.optDouble("dx", c.dx);
                c.dy = (float) o.optDouble("dy", c.dy);
                c.radius = (float) o.optDouble("r", c.radius);
                c.enabled = o.optBoolean("on", c.enabled);
            }
        } catch (JSONException e) {
            // corrupt layout: fall back to defaults rather than leaving the player without controls
            android.util.Log.w("PerfectDark", "touch layout unreadable, using defaults", e);
            for (Control c : controls) {
                c.reset();
            }
        }
    }

    public void save() {
        JSONObject root = new JSONObject();
        try {
            for (Control c : controls) {
                JSONObject o = new JSONObject();
                o.put("dx", c.dx);
                o.put("dy", c.dy);
                o.put("r", c.radius);
                o.put("on", c.enabled);
                root.put(c.id, o);
            }
        } catch (JSONException e) {
            android.util.Log.e("PerfectDark", "could not serialise touch layout", e);
            return;
        }

        prefs.edit()
                .putInt(KEY_VERSION, LAYOUT_VERSION)
                .putString(KEY_LAYOUT, root.toString())
                .putFloat(KEY_OPACITY, opacity)
                .putBoolean(KEY_ENABLED, enabled)
                .apply();
    }
}
