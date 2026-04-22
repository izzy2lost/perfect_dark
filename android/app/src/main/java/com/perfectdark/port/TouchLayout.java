package com.perfectdark.port;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes the on-screen touch controls: two analog sticks and a set of
 * buttons. Positions/radii are stored in normalized screen coordinates
 * (0..1) so layouts survive rotation and different screen sizes.
 *
 * Persisted in SharedPreferences under "touch_layout".
 */
public class TouchLayout {
    public static final String PREFS = "touch_layout";

    // N64 controller button bits (mirror CONT_* / os_cont.h).
    public static final int BTN_A       = 0x00008000; // Use / Accept
    public static final int BTN_B       = 0x00004000; // Cancel
    public static final int BTN_Z       = 0x00002000; // Fire
    public static final int BTN_START   = 0x00001000;
    public static final int BTN_DU      = 0x00000800;
    public static final int BTN_DD      = 0x00000400; // Radial menu
    public static final int BTN_DL      = 0x00000200; // Previous weapon
    public static final int BTN_DR      = 0x00000100;
    public static final int BTN_X       = 0x00000040; // Reload
    public static final int BTN_Y       = 0x00000080; // Next weapon
    public static final int BTN_L       = 0x00000020; // Alt fire
    public static final int BTN_R       = 0x00000010; // Aim
    // Perfect Dark uses an unused bit for the "cycle crouch" action.
    public static final int BTN_CROUCH  = 0x80000000;

    public enum Kind { LEFT_STICK, RIGHT_STICK, BUTTON }

    public static class Element {
        public final String id;
        public final Kind kind;
        public final int mask;     // CONT_* mask for BUTTON, unused for sticks
        public final String label; // drawn on button
        public float cx, cy;       // normalized center
        public float radius;       // normalized radius (relative to min(w,h))

        public Element(String id, Kind kind, int mask, String label,
                       float cx, float cy, float radius) {
            this.id = id;
            this.kind = kind;
            this.mask = mask;
            this.label = label;
            this.cx = cx;
            this.cy = cy;
            this.radius = radius;
        }
    }

    public final List<Element> elements = new ArrayList<>();

    public static TouchLayout defaults() {
        TouchLayout l = new TouchLayout();
        // Left thumb: analog move stick, bottom-left corner.
        l.elements.add(new Element("lstick", Kind.LEFT_STICK,  0, "",       0.14f, 0.70f, 0.11f));
        // Right thumb: analog look stick, bottom-right corner.
        l.elements.add(new Element("rstick", Kind.RIGHT_STICK, 0, "",       0.86f, 0.70f, 0.11f));

        // Primary action cluster on the right side, above the look stick.
        l.elements.add(new Element("fire",   Kind.BUTTON, BTN_Z,      "FIRE", 0.90f, 0.35f, 0.075f));
        l.elements.add(new Element("aim",    Kind.BUTTON, BTN_R,      "AIM",  0.76f, 0.30f, 0.060f));
        l.elements.add(new Element("use",    Kind.BUTTON, BTN_A,      "USE",  0.72f, 0.55f, 0.055f));
        l.elements.add(new Element("reload", Kind.BUTTON, BTN_X,      "RLD",  0.88f, 0.15f, 0.055f));
        l.elements.add(new Element("altfire",Kind.BUTTON, BTN_L,      "ALT",  0.70f, 0.15f, 0.050f));

        // Weapon + radial cluster on the left side, above the move stick.
        l.elements.add(new Element("wprev",  Kind.BUTTON, BTN_DL,     "<",    0.06f, 0.40f, 0.050f));
        l.elements.add(new Element("wnext",  Kind.BUTTON, BTN_Y,      ">",    0.24f, 0.40f, 0.050f));
        l.elements.add(new Element("radial", Kind.BUTTON, BTN_DD,     "WPN",  0.14f, 0.30f, 0.055f));
        l.elements.add(new Element("crouch", Kind.BUTTON, BTN_CROUCH, "CRC",  0.30f, 0.55f, 0.055f));

        // Menu + cancel at the top.
        l.elements.add(new Element("start",  Kind.BUTTON, BTN_START,  "STRT", 0.50f, 0.06f, 0.045f));
        l.elements.add(new Element("cancel", Kind.BUTTON, BTN_B,      "BACK", 0.92f, 0.06f, 0.045f));
        return l;
    }

    public void save(Context ctx) {
        SharedPreferences.Editor e = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        for (Element el : elements) {
            e.putFloat(el.id + ".cx", el.cx);
            e.putFloat(el.id + ".cy", el.cy);
            e.putFloat(el.id + ".r",  el.radius);
        }
        e.apply();
    }

    public static TouchLayout load(Context ctx) {
        TouchLayout l = defaults();
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        for (Element el : l.elements) {
            el.cx     = p.getFloat(el.id + ".cx", el.cx);
            el.cy     = p.getFloat(el.id + ".cy", el.cy);
            el.radius = p.getFloat(el.id + ".r",  el.radius);
        }
        return l;
    }

    public static void resetDefaults(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }
}
