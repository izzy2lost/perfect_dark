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
 * Positions are normalised: cx/cy are fractions of the view's width/height, and radius is a
 * fraction of the view's height. That keeps a layout usable across the very wide range of
 * phone and tablet aspect ratios without storing anything device specific.
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

    /** Port-specific extra bits; see the control table in README.md. */
    public static final int CONT_CROUCH_CYCLE = 0x80000000;

    public enum Kind {
        BUTTON,      // holds a CONT_ bit while pressed
        STICK_MOVE,  // left analog stick
        STICK_LOOK,  // look stick: C buttons or right stick, depending on the port's config
        KEY,         // injects an Android keycode into SDL (used for the console)
        TOGGLE       // hides/shows the overlay so taps reach the game surface directly
    }

    public static final class Control {
        public final String id;
        public final String label;
        public final Kind kind;
        public final int mask;
        public final int keyCode;

        public float cx, cy, radius;
        public boolean enabled;

        final float defCx, defCy, defRadius;
        final boolean defEnabled;

        Control(String id, String label, Kind kind, int mask, int keyCode,
                float cx, float cy, float radius, boolean enabled) {
            this.id = id;
            this.label = label;
            this.kind = kind;
            this.mask = mask;
            this.keyCode = keyCode;
            this.cx = this.defCx = cx;
            this.cy = this.defCy = cy;
            this.radius = this.defRadius = radius;
            this.enabled = this.defEnabled = enabled;
        }

        void reset() {
            cx = defCx;
            cy = defCy;
            radius = defRadius;
            enabled = defEnabled;
        }
    }

    private static final String PREFS = "touch_controls";
    private static final String KEY_LAYOUT = "layout";
    private static final String KEY_OPACITY = "opacity";
    private static final String KEY_LOOK_SENS = "look_sensitivity";
    private static final String KEY_ENABLED = "enabled";

    private final List<Control> controls = new ArrayList<>();
    private final SharedPreferences prefs;

    public float opacity = 0.45f;
    public float lookSensitivity = 1.0f;
    public boolean enabled = true;

    public TouchLayout(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        buildDefaults();
        load();
    }

    private void buildDefaults() {
        controls.clear();

        // left thumb: movement
        controls.add(new Control("move", "", Kind.STICK_MOVE, 0, 0, 0.14f, 0.70f, 0.34f, true));

        // right thumb: look. Buttons are hit-tested first, so this only claims empty space.
        controls.add(new Control("look", "", Kind.STICK_LOOK, 0, 0, 0.70f, 0.55f, 0.75f, true));

        // primary actions, sized for the thumb that rests over them
        controls.add(new Control("fire",   "Z",  Kind.BUTTON, CONT_Z, 0, 0.90f, 0.80f, 0.14f, true));
        controls.add(new Control("aim",    "R",  Kind.BUTTON, CONT_R, 0, 0.72f, 0.87f, 0.105f, true));
        controls.add(new Control("use",    "A",  Kind.BUTTON, CONT_A, 0, 0.955f, 0.545f, 0.085f, true));
        controls.add(new Control("cancel", "B",  Kind.BUTTON, CONT_B, 0, 0.815f, 0.545f, 0.085f, true));

        // secondary actions
        controls.add(new Control("reload", "X",  Kind.BUTTON, CONT_X, 0, 0.90f, 0.335f, 0.082f, true));
        controls.add(new Control("next",   "Y",  Kind.BUTTON, CONT_Y, 0, 0.765f, 0.335f, 0.082f, true));
        controls.add(new Control("prev",   "<",  Kind.BUTTON, CONT_LEFT, 0, 0.645f, 0.335f, 0.072f, true));
        controls.add(new Control("altfire","L",  Kind.BUTTON, CONT_L, 0, 0.645f, 0.16f, 0.072f, true));
        controls.add(new Control("radial", "Q",  Kind.BUTTON, CONT_DOWN, 0, 0.515f, 0.16f, 0.072f, true));

        // left-hand extras
        controls.add(new Control("crouch", "CR", Kind.BUTTON, CONT_CROUCH_CYCLE, 0, 0.315f, 0.855f, 0.085f, true));

        // system row along the top
        controls.add(new Control("start",  "ST", Kind.BUTTON, CONT_START, 0, 0.955f, 0.09f, 0.062f, true));
        controls.add(new Control("console","~",  Kind.KEY, 0, android.view.KeyEvent.KEYCODE_GRAVE,
                0.865f, 0.09f, 0.062f, true));
        controls.add(new Control("hide",   "\u25CE", Kind.TOGGLE, 0, 0, 0.045f, 0.09f, 0.055f, true));
    }

    public List<Control> getControls() {
        return Collections.unmodifiableList(controls);
    }

    public Control findById(String id) {
        for (Control c : controls) {
            if (c.id.equals(id)) {
                return c;
            }
        }
        return null;
    }

    public void resetToDefaults() {
        for (Control c : controls) {
            c.reset();
        }
        opacity = 0.45f;
        lookSensitivity = 1.0f;
        enabled = true;
        save();
    }

    private void load() {
        opacity = prefs.getFloat(KEY_OPACITY, opacity);
        lookSensitivity = prefs.getFloat(KEY_LOOK_SENS, lookSensitivity);
        enabled = prefs.getBoolean(KEY_ENABLED, enabled);

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
                c.cx = (float) o.optDouble("cx", c.cx);
                c.cy = (float) o.optDouble("cy", c.cy);
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
                o.put("cx", c.cx);
                o.put("cy", c.cy);
                o.put("r", c.radius);
                o.put("on", c.enabled);
                root.put(c.id, o);
            }
        } catch (JSONException e) {
            android.util.Log.e("PerfectDark", "could not serialise touch layout", e);
            return;
        }

        prefs.edit()
                .putString(KEY_LAYOUT, root.toString())
                .putFloat(KEY_OPACITY, opacity)
                .putFloat(KEY_LOOK_SENS, lookSensitivity)
                .putBoolean(KEY_ENABLED, enabled)
                .apply();
    }
}
