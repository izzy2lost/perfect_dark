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

    /** Port-specific extra bits; see the control table in README.md. */
    public static final int CONT_CROUCH_CYCLE = 0x80000000;

    public enum Kind {
        BUTTON,      // holds a CONT_ bit while pressed
        STICK_MOVE,  // left analog stick
        STICK_LOOK,  // look stick: C buttons or right stick, depending on the port's config
        KEY,         // injects an Android keycode into SDL (used for the console)
        TOGGLE       // hides/shows the overlay so taps reach the game surface directly
    }

    public enum Anchor {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    public static final class Control {
        public final String id;
        /** what the control does, in words */
        public final String label;
        /** which N64 button that is, shown small underneath */
        public final String subLabel;
        public final Kind kind;
        public final Anchor anchor;
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
            this.id = id;
            this.label = label;
            this.subLabel = subLabel;
            this.kind = kind;
            this.anchor = anchor;
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
            return kind == Kind.STICK_MOVE || kind == Kind.STICK_LOOK;
        }
    }

    private static final String PREFS = "touch_controls";
    // bumped when the default layout changes shape, so an old save cannot resurrect a bad one
    private static final int LAYOUT_VERSION = 3;
    private static final String KEY_VERSION = "layout_version";
    private static final String KEY_LAYOUT = "layout";
    private static final String KEY_OPACITY = "opacity";
    private static final String KEY_LOOK_SENS = "look_sensitivity";
    private static final String KEY_ENABLED = "enabled";

    private final List<Control> controls = new ArrayList<>();
    private final SharedPreferences prefs;

    public float opacity = 0.5f;
    public float lookSensitivity = 1.0f;
    public boolean enabled = true;

    public TouchLayout(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        buildDefaults();
        load();
    }

    private void buildDefaults() {
        controls.clear();

        // Twin sticks in the bottom corners, where the thumbs already rest.
        controls.add(new Control("move", "", "", Kind.STICK_MOVE, Anchor.BOTTOM_LEFT,
                0, 0, 118, 118, 78, true));
        controls.add(new Control("look", "", "", Kind.STICK_LOOK, Anchor.BOTTOM_RIGHT,
                0, 0, 118, 118, 78, true));

        // Fire and aim go along the top edge under the index fingers, since both thumbs are
        // occupied by the sticks.
        controls.add(new Control("aim", "AIM", "R", Kind.BUTTON, Anchor.TOP_LEFT,
                CONT_R, 0, 150, 56, 44, true));
        controls.add(new Control("fire", "FIRE", "Z", Kind.BUTTON, Anchor.TOP_RIGHT,
                CONT_Z, 0, 150, 56, 48, true));

        // Face buttons, in a block inboard of the look stick where the right thumb reaches.
        // Nothing goes past ~380dp from either edge, so the middle of the screen stays clear.
        controls.add(new Control("use", "USE", "A", Kind.BUTTON, Anchor.BOTTOM_RIGHT,
                CONT_A, 0, 250, 100, 36, true));
        controls.add(new Control("back", "BACK", "B", Kind.BUTTON, Anchor.BOTTOM_RIGHT,
                CONT_B, 0, 250, 200, 33, true));
        controls.add(new Control("reload", "RELOAD", "X", Kind.BUTTON, Anchor.BOTTOM_RIGHT,
                CONT_X, 0, 344, 86, 33, true));
        controls.add(new Control("next", "NEXT", "Y", Kind.BUTTON, Anchor.BOTTOM_RIGHT,
                CONT_Y, 0, 344, 186, 33, true));

        // Left-hand extras, stacked beside the move stick.
        controls.add(new Control("crouch", "CROUCH", "", Kind.BUTTON, Anchor.BOTTOM_LEFT,
                CONT_CROUCH_CYCLE, 0, 250, 96, 34, true));
        controls.add(new Control("radial", "WEAPON", "D-D", Kind.BUTTON, Anchor.BOTTOM_LEFT,
                CONT_DOWN, 0, 250, 196, 33, true));

        // Occasional actions live on the top edge rather than over the play area.
        controls.add(new Control("altfire", "ALT", "L", Kind.BUTTON, Anchor.TOP_LEFT,
                CONT_L, 0, 258, 56, 34, true));
        controls.add(new Control("prev", "PREV", "D-L", Kind.BUTTON, Anchor.TOP_RIGHT,
                CONT_LEFT, 0, 340, 52, 32, true));

        // System row.
        controls.add(new Control("hide", "◎", "", Kind.TOGGLE, Anchor.TOP_LEFT,
                0, 0, 44, 44, 26, true));
        controls.add(new Control("start", "START", "", Kind.BUTTON, Anchor.TOP_RIGHT,
                CONT_START, 0, 46, 46, 30, true));
        controls.add(new Control("console", "~", "", Kind.KEY, Anchor.TOP_RIGHT,
                0, android.view.KeyEvent.KEYCODE_GRAVE, 250, 46, 26, true));
    }

    public List<Control> getControls() {
        return Collections.unmodifiableList(controls);
    }

    public void resetToDefaults() {
        for (Control c : controls) {
            c.reset();
        }
        opacity = 0.5f;
        lookSensitivity = 1.0f;
        enabled = true;
        save();
    }

    private void load() {
        opacity = prefs.getFloat(KEY_OPACITY, opacity);
        lookSensitivity = prefs.getFloat(KEY_LOOK_SENS, lookSensitivity);
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
                .putFloat(KEY_LOOK_SENS, lookSensitivity)
                .putBoolean(KEY_ENABLED, enabled)
                .apply();
    }
}
