package com.perfectdark.port;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes the on-screen touch controls: analog sticks, a drag-to-look pad
 * (CoD/Fortnite style) and buttons. Positions/sizes are stored in normalized
 * screen coordinates (0..1) so layouts survive rotation and different screens.
 *
 * Persisted in SharedPreferences under "touch_layout".
 */
public class TouchLayout {
    public static final String PREFS = "touch_layout";

    // N64 controller button bits (mirror CONT_* / os_cont.h).
    public static final int BTN_A       = 0x00008000;
    public static final int BTN_B       = 0x00004000;
    public static final int BTN_Z       = 0x00002000;
    public static final int BTN_START   = 0x00001000;
    public static final int BTN_DU      = 0x00000800;
    public static final int BTN_DD      = 0x00000400;
    public static final int BTN_DL      = 0x00000200;
    public static final int BTN_DR      = 0x00000100;
    public static final int BTN_X       = 0x00000040;
    public static final int BTN_Y       = 0x00000080;
    public static final int BTN_L       = 0x00000020;
    public static final int BTN_R       = 0x00000010;
    public static final int BTN_CROUCH  = 0x80000000;

    public enum Kind { LEFT_STICK, RIGHT_STICK, BUTTON, LOOK_PAD, MOVE_PAD }

    public static class Element {
        public final String id;
        public final Kind kind;
        public final int mask;
        public final String label;
        public float cx, cy;       // normalized center
        public float radius;       // for BUTTON / sticks (circular)
        public float hw, hh;       // half-width / half-height for LOOK_PAD

        public Element(String id, Kind kind, int mask, String label,
                       float cx, float cy, float radius) {
            this.id = id;
            this.kind = kind;
            this.mask = mask;
            this.label = label;
            this.cx = cx;
            this.cy = cy;
            this.radius = radius;
            this.hw = radius;
            this.hh = radius;
        }

        public static Element rect(String id, Kind kind, String label,
                                    float cx, float cy, float hw, float hh) {
            Element e = new Element(id, kind, 0, label, cx, cy, 0f);
            e.hw = hw;
            e.hh = hh;
            return e;
        }
    }

    public final List<Element> elements = new ArrayList<>();

    /** Pixel-to-mouse-delta multipliers for the look pad. Persistent, per-axis. */
    public float lookSensX = 0.4f;
    public float lookSensY = 0.4f;

    public static TouchLayout defaults() {
        TouchLayout l = new TouchLayout();

        // Left half is the floating-stick move pad (CoD Mobile style) and
        // covers the full left half of the screen. Buttons placed on top of
        // it win the hit test, so this is safe.
        Element movepad = Element.rect("movepad", Kind.MOVE_PAD, "MOVE",
                0.25f, 0.50f, 0.25f, 0.50f);
        movepad.radius = 0.09f;
        l.elements.add(movepad);

        // Right half is a drag-to-look pad, full right half of the screen.
        l.elements.add(Element.rect("lookpad", Kind.LOOK_PAD, "LOOK",
                0.75f, 0.50f, 0.25f, 0.50f));

        // Shooting cluster over the right-side look pad (right thumb / index).
        l.elements.add(new Element("fire",   Kind.BUTTON, BTN_Z,      "FIRE", 0.92f, 0.78f, 0.070f));
        l.elements.add(new Element("aim",    Kind.BUTTON, BTN_R,      "AIM",  0.92f, 0.45f, 0.055f));
        l.elements.add(new Element("use",    Kind.BUTTON, BTN_A,      "USE",  0.82f, 0.55f, 0.050f));
        l.elements.add(new Element("reload", Kind.BUTTON, BTN_X,      "RLD",  0.82f, 0.22f, 0.050f));
        l.elements.add(new Element("altfire",Kind.BUTTON, BTN_L,      "ALT",  0.93f, 0.22f, 0.045f));

        // Weapon + movement helpers over the left-side move pad.
        l.elements.add(new Element("wprev",  Kind.BUTTON, BTN_DL,     "<",    0.06f, 0.30f, 0.045f));
        l.elements.add(new Element("wnext",  Kind.BUTTON, BTN_Y,      ">",    0.17f, 0.30f, 0.045f));
        l.elements.add(new Element("radial", Kind.BUTTON, BTN_DD,     "WPN",  0.08f, 0.18f, 0.045f));
        l.elements.add(new Element("crouch", Kind.BUTTON, BTN_CROUCH, "CRC",  0.07f, 0.85f, 0.045f));

        // Menu + cancel at the top.
        l.elements.add(new Element("start",  Kind.BUTTON, BTN_START,  "STRT", 0.48f, 0.06f, 0.045f));
        l.elements.add(new Element("cancel", Kind.BUTTON, BTN_B,      "BACK", 0.55f, 0.06f, 0.045f));
        return l;
    }

    public void save(Context ctx) {
        SharedPreferences.Editor e = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        for (Element el : elements) {
            e.putFloat(el.id + ".cx", el.cx);
            e.putFloat(el.id + ".cy", el.cy);
            e.putFloat(el.id + ".r",  el.radius);
            e.putFloat(el.id + ".hw", el.hw);
            e.putFloat(el.id + ".hh", el.hh);
        }
        e.putFloat("_lookSensX", lookSensX);
        e.putFloat("_lookSensY", lookSensY);
        e.apply();
    }

    public static TouchLayout load(Context ctx) {
        TouchLayout l = defaults();
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        for (Element el : l.elements) {
            el.cx     = p.getFloat(el.id + ".cx", el.cx);
            el.cy     = p.getFloat(el.id + ".cy", el.cy);
            el.radius = p.getFloat(el.id + ".r",  el.radius);
            el.hw     = p.getFloat(el.id + ".hw", el.hw);
            el.hh     = p.getFloat(el.id + ".hh", el.hh);
        }
        // Legacy single-axis key is used as the fallback for both axes if
        // present, so existing installs keep their previous sensitivity.
        float legacy = p.getFloat("_lookSens", l.lookSensX);
        l.lookSensX = p.getFloat("_lookSensX", legacy);
        l.lookSensY = p.getFloat("_lookSensY", legacy);
        return l;
    }

    public static void resetDefaults(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }

    /** Deep copy, used to back up before entering live-edit mode. */
    public TouchLayout copy() {
        TouchLayout out = new TouchLayout();
        out.lookSensX = lookSensX;
        out.lookSensY = lookSensY;
        for (Element e : elements) {
            Element c = (e.kind == Kind.LOOK_PAD || e.kind == Kind.MOVE_PAD)
                    ? Element.rect(e.id, e.kind, e.label, e.cx, e.cy, e.hw, e.hh)
                    : new Element(e.id, e.kind, e.mask, e.label, e.cx, e.cy, e.radius);
            c.hw = e.hw;
            c.hh = e.hh;
            c.radius = e.radius;
            out.elements.add(c);
        }
        return out;
    }

    /** Copies positions/sizes (and sensitivity) from `src` into this layout in place. */
    public void assignFrom(TouchLayout src) {
        lookSensX = src.lookSensX;
        lookSensY = src.lookSensY;
        for (int i = 0; i < elements.size() && i < src.elements.size(); ++i) {
            Element dst = elements.get(i);
            Element s = src.elements.get(i);
            dst.cx = s.cx;
            dst.cy = s.cy;
            dst.radius = s.radius;
            dst.hw = s.hw;
            dst.hh = s.hh;
        }
    }
}
