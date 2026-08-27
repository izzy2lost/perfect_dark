package com.perfectdark.port;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.SparseArray;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;

import org.libsdl.app.SDLActivity;

import java.util.HashMap;
import java.util.Map;

/**
 * The on-screen controller.
 *
 * This sits as a transparent, full-screen sibling above SDL's surface. It only consumes a
 * pointer when that pointer lands on one of its controls; anything else is returned to the
 * view below, so SDL still sees taps as mouse input for menus.
 *
 * Control state is pushed straight to the native virtual pad in port/src/touch.c, which
 * inputReadController() folds into player 1's pad each frame.
 */
public class TouchControls extends View {

    private static final float DEADZONE = 0.14f;
    /** fraction of a stick's radius that counts as full deflection */
    private static final float STICK_TRAVEL = 0.80f;
    /** how far the drawn knob moves, as a fraction of the radius */
    private static final float KNOB_THROW = 0.55f;

    private static final long EDIT_LONG_PRESS_MS = 650;

    /** Matches colorPrimary / colorOnPrimary in themes.xml, so the pad reads as part of the app. */
    private static final int ACCENT = 0xFF67D8F5;
    private static final int ON_ACCENT = 0xFF00344A;

    /** how far a pill's half-width and half-height sit from its radius */
    private static final float PILL_W = 1.45f;
    private static final float PILL_H = 0.86f;

    private final TouchLayout layout;
    private final float density;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint editHint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** pointer id -> the control it is currently driving */
    private final SparseArray<TouchLayout.Control> owned = new SparseArray<>();
    /** pointer id -> current position in pixels, for stick controls */
    private final SparseArray<float[]> stickPos = new SparseArray<>();

    private boolean editMode;
    private TouchLayout.Control editTarget;
    private float editGrabDx, editGrabDy;
    private float pinchStartDist, pinchStartRadius;
    private int pinchPointerA = -1, pinchPointerB = -1;

    private int lastMask;
    private boolean gamepadPresent;
    private boolean longPressFired;
    private boolean animating;

    /** control id -> 0..1 press animation, so buttons swell and settle instead of snapping */
    private final Map<String, Float> pressAnim = new HashMap<>();
    private final RectF rect = new RectF();

    private final Runnable enterEdit = () -> {
        longPressFired = true;
        setEditMode(true);
    };

    public TouchControls(Context context, TouchLayout layout) {
        super(context);
        this.layout = layout;
        this.density = context.getResources().getDisplayMetrics().density;

        setFocusable(false);
        setFocusableInTouchMode(false);
        setClickable(false);
        setLayerType(LAYER_TYPE_HARDWARE, null);

        stroke.setStyle(Paint.Style.STROKE);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));

        editHint.setTextAlign(Paint.Align.CENTER);
        editHint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        editHint.setColor(Color.WHITE);

        refreshGamepadState();
    }

    // ---------------------------------------------------------------- geometry

    private float px(TouchLayout.Control c) {
        final float d = c.dx * density;
        switch (c.anchor) {
            case TOP_LEFT:
            case BOTTOM_LEFT:
                return d;
            default:
                return getWidth() - d;
        }
    }

    private float py(TouchLayout.Control c) {
        final float d = c.dy * density;
        switch (c.anchor) {
            case TOP_LEFT:
            case TOP_RIGHT:
                return d;
            default:
                return getHeight() - d;
        }
    }

    private float pr(TouchLayout.Control c) {
        return c.radius * density;
    }

    /** Writes a pixel centre back to the control as dp from its own anchor, kept on screen. */
    private void setCentrePx(TouchLayout.Control c, float cx, float cy) {
        final float r = pr(c);
        cx = clamp(cx, r, getWidth() - r);
        cy = clamp(cy, r, getHeight() - r);

        final boolean left = c.anchor == TouchLayout.Anchor.TOP_LEFT
                || c.anchor == TouchLayout.Anchor.BOTTOM_LEFT;
        final boolean top = c.anchor == TouchLayout.Anchor.TOP_LEFT
                || c.anchor == TouchLayout.Anchor.TOP_RIGHT;

        c.dx = (left ? cx : getWidth() - cx) / density;
        c.dy = (top ? cy : getHeight() - cy) / density;
    }

    private TouchLayout.Control hitTest(float x, float y) {
        // Buttons win over the sticks, so a press that clips a button never also steers.
        TouchLayout.Control stick = null;
        for (TouchLayout.Control c : layout.getControls()) {
            if (!c.enabled) {
                continue;
            }
            if (!layout.enabled && !editMode && c.kind != TouchLayout.Kind.TOGGLE) {
                continue;
            }
            final float dx = x - px(c);
            final float dy = y - py(c);
            final float r = pr(c);
            if (c.shape == TouchLayout.Shape.PILL) {
                if (Math.abs(dx) > r * PILL_W || Math.abs(dy) > r * PILL_H) {
                    continue;
                }
            } else if (dx * dx + dy * dy > r * r) {
                continue;
            }
            if (c.isStick()) {
                stick = c;
            } else {
                return c;
            }
        }
        return stick;
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (getWidth() == 0 || getHeight() == 0) {
            return false;
        }

        // Nothing is drawn while a physical pad is attached, so nothing may be pressed either.
        if (gamepadPresent && !editMode) {
            return false;
        }

        final int action = event.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                final int index = event.getActionIndex();
                final int id = event.getPointerId(index);
                final float x = event.getX(index);
                final float y = event.getY(index);

                if (editMode) {
                    return onEditDown(id, x, y);
                }

                TouchLayout.Control c = hitTest(x, y);
                if (c == null) {
                    return false; // let it fall through to SDL
                }

                owned.put(id, c);
                if (c.isStick()) {
                    stickPos.put(id, new float[]{x, y});
                    updateStick(c, id, x, y);
                } else if (c.kind == TouchLayout.Kind.KEY) {
                    SDLActivity.onNativeKeyDown(c.keyCode);
                } else if (c.kind == TouchLayout.Kind.TOGGLE) {
                    // tap hides/shows the overlay, hold opens the layout editor
                    longPressFired = false;
                    postDelayed(enterEdit, EDIT_LONG_PRESS_MS);
                }
                pushState();
                invalidate();
                return true;
            }

            case MotionEvent.ACTION_MOVE: {
                if (editMode) {
                    return onEditMove(event);
                }
                boolean any = false;
                for (int i = 0; i < event.getPointerCount(); ++i) {
                    final int id = event.getPointerId(i);
                    final TouchLayout.Control c = owned.get(id);
                    if (c == null) {
                        continue;
                    }
                    any = true;
                    if (c.isStick()) {
                        updateStick(c, id, event.getX(i), event.getY(i));
                    }
                }
                if (any) {
                    pushState();
                    invalidate();
                }
                return any;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                final int id = event.getPointerId(event.getActionIndex());
                if (editMode) {
                    return onEditUp(id);
                }
                final TouchLayout.Control c = owned.get(id);
                if (c == null) {
                    return false;
                }
                releasePointer(id, c);
                pushState();
                invalidate();
                return true;
            }

            case MotionEvent.ACTION_CANCEL: {
                if (editMode) {
                    editTarget = null;
                    pinchPointerA = pinchPointerB = -1;
                    return true;
                }
                releaseAll();
                pushState();
                invalidate();
                return true;
            }
        }

        return false;
    }

    /**
     * The stick is fixed rather than floating: deflection is measured from the control's own
     * centre, so what the player sees under their thumb is what the game is being told.
     */
    private void updateStick(TouchLayout.Control c, int id, float x, float y) {
        final float[] pos = stickPos.get(id);
        if (pos != null) {
            pos[0] = x;
            pos[1] = y;
        }

        final float travel = pr(c) * STICK_TRAVEL;
        float dx = (x - px(c)) / travel;
        float dy = (y - py(c)) / travel;

        final float mag = (float) Math.hypot(dx, dy);
        if (mag < DEADZONE) {
            dx = dy = 0f;
        } else {
            // rescale so the stick starts from zero at the edge of the deadzone
            final float scaled = Math.min((mag - DEADZONE) / (1f - DEADZONE), 1f);
            dx = dx / mag * scaled;
            dy = dy / mag * scaled;
        }

        // Android's y grows downwards; the N64 stick's does not.
        nativeSetStick(dx, -dy);
    }

    private void releasePointer(int id, TouchLayout.Control c) {
        owned.remove(id);
        stickPos.remove(id);
        if (c.isStick()) {
            nativeSetStick(0f, 0f);
        } else if (c.kind == TouchLayout.Kind.KEY) {
            SDLActivity.onNativeKeyUp(c.keyCode);
        } else if (c.kind == TouchLayout.Kind.TOGGLE) {
            removeCallbacks(enterEdit);
            if (!longPressFired) {
                layout.enabled = !layout.enabled;
                layout.save();
                syncActive();
            }
            longPressFired = false;
        }
    }

    private void releaseAll() {
        for (int i = owned.size() - 1; i >= 0; --i) {
            releasePointer(owned.keyAt(i), owned.valueAt(i));
        }
        owned.clear();
        stickPos.clear();
        nativeSetStick(0f, 0f);
    }

    private void pushState() {
        int mask = 0;
        for (int i = 0; i < owned.size(); ++i) {
            final TouchLayout.Control c = owned.valueAt(i);
            if (c.kind == TouchLayout.Kind.BUTTON) {
                mask |= c.mask;
            }
        }
        if (mask != lastMask) {
            lastMask = mask;
            nativeSetButtons(mask);
        }
    }

    // ---------------------------------------------------------------- edit mode

    private boolean onEditDown(int id, float x, float y) {
        TouchLayout.Control c = hitTest(x, y);
        if (c == null) {
            return false;
        }
        if (editTarget == c && pinchPointerA >= 0 && pinchPointerB < 0) {
            // second finger on the same control: start a pinch resize
            pinchPointerB = id;
            return true;
        }
        editTarget = c;
        pinchPointerA = id;
        pinchPointerB = -1;
        editGrabDx = px(c) - x;
        editGrabDy = py(c) - y;
        invalidate();
        return true;
    }

    private boolean onEditMove(MotionEvent event) {
        if (editTarget == null) {
            return false;
        }

        if (pinchPointerA >= 0 && pinchPointerB >= 0) {
            final int ia = event.findPointerIndex(pinchPointerA);
            final int ib = event.findPointerIndex(pinchPointerB);
            if (ia < 0 || ib < 0) {
                return true;
            }
            final float dist = (float) Math.hypot(
                    event.getX(ia) - event.getX(ib), event.getY(ia) - event.getY(ib));
            if (pinchStartDist <= 0f) {
                pinchStartDist = dist;
                pinchStartRadius = editTarget.radius;
            } else if (pinchStartDist > 1f) {
                editTarget.radius = clamp(pinchStartRadius * (dist / pinchStartDist), 18f, 140f);
                invalidate();
            }
            return true;
        }

        final int index = event.findPointerIndex(pinchPointerA);
        if (index < 0) {
            return true;
        }
        setCentrePx(editTarget, event.getX(index) + editGrabDx, event.getY(index) + editGrabDy);
        invalidate();
        return true;
    }

    private boolean onEditUp(int id) {
        if (id == pinchPointerB) {
            pinchPointerB = -1;
            pinchStartDist = 0f;
        } else if (id == pinchPointerA) {
            pinchPointerA = pinchPointerB;
            pinchPointerB = -1;
            pinchStartDist = 0f;
            if (pinchPointerA < 0) {
                layout.save();
            }
        }
        return true;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public void setEditMode(boolean edit) {
        if (editMode == edit) {
            return;
        }
        removeCallbacks(enterEdit);
        editMode = edit;
        releaseAll();
        pushState();
        if (!edit) {
            layout.save();
        }
        editTarget = null;
        pinchPointerA = pinchPointerB = -1;
        pinchStartDist = 0f;
        syncActive();
        invalidate();
    }

    public boolean isEditMode() {
        return editMode;
    }

    public void resetLayout() {
        layout.resetToDefaults();
        editTarget = null;
        syncActive();
        invalidate();
    }

    public TouchLayout getLayout() {
        return layout;
    }

    // ---------------------------------------------------------------- lifecycle

    /** Hide the overlay while a physical controller is attached; it just gets in the way. */
    public void refreshGamepadState() {
        boolean found = false;
        for (int id : InputDevice.getDeviceIds()) {
            final InputDevice dev = InputDevice.getDevice(id);
            if (dev == null) {
                continue;
            }
            final int sources = dev.getSources();
            if ((sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                    || (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
                found = true;
                break;
            }
        }
        if (found != gamepadPresent) {
            gamepadPresent = found;
            releaseAll();
            pushState();
            syncActive();
            invalidate();
        }
    }

    private boolean isDriving() {
        return layout.enabled && !gamepadPresent && !editMode;
    }

    private void syncActive() {
        nativeSetActive(isDriving());
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        syncActive();
    }

    @Override
    protected void onDetachedFromWindow() {
        releaseAll();
        pushState();
        nativeSetActive(false);
        super.onDetachedFromWindow();
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void onDraw(Canvas canvas) {
        if (gamepadPresent && !editMode) {
            return;
        }

        final int alpha = (int) (clamp(layout.opacity, 0.05f, 1f) * 255);
        animating = false;

        for (TouchLayout.Control c : layout.getControls()) {
            if (!c.enabled) {
                continue;
            }
            // the editor always shows everything, even if the overlay is currently hidden
            if (!layout.enabled && !editMode && c.kind != TouchLayout.Kind.TOGGLE) {
                continue;
            }
            if (c.isStick()) {
                drawStick(canvas, c, alpha);
            } else {
                drawButton(canvas, c, alpha);
            }
        }

        if (editMode) {
            editHint.setTextSize(20f * density);
            editHint.setAlpha(230);
            canvas.drawText("Drag to move • pinch to resize • Back when done",
                    getWidth() * 0.5f, 34f * density, editHint);
        }
    }

    private void drawButton(Canvas canvas, TouchLayout.Control c, int alpha) {
        final float x = px(c);
        final float y = py(c);
        final boolean selected = editMode && c == editTarget;
        final float t = pressAnim(c);
        // a pressed control swells slightly; the eased value keeps it from snapping
        final float r = pr(c) * (1f + 0.09f * t);

        final int baseAlpha = (int) (alpha * 0.20f);
        final int litAlpha = (int) (Math.min(255, alpha + 60) * 0.92f);

        fill.setColor(blend(Color.WHITE, ACCENT, t));
        fill.setAlpha((int) (baseAlpha + (litAlpha - baseAlpha) * t));

        stroke.setColor(selected ? Color.YELLOW : blend(Color.WHITE, ACCENT, 0.45f + 0.55f * t));
        stroke.setAlpha(selected ? 255 : (int) (alpha * (0.75f + 0.25f * t)));
        stroke.setStrokeWidth(Math.max(2f, r * 0.05f));

        if (c.shape == TouchLayout.Shape.PILL) {
            rect.set(x - r * PILL_W, y - r * PILL_H, x + r * PILL_W, y + r * PILL_H);
            final float rad = r * PILL_H;
            canvas.drawRoundRect(rect, rad, rad, fill);
            canvas.drawRoundRect(rect, rad, rad, stroke);
        } else {
            canvas.drawCircle(x, y, r, fill);
            canvas.drawCircle(x, y, r, stroke);
        }

        if (c.label.isEmpty()) {
            return;
        }

        // label flips to the dark on-accent colour once the fill lights up
        final int labelColour = blend(Color.WHITE, ON_ACCENT, Math.max(0f, (t - 0.45f) / 0.55f));
        text.setColor(labelColour);
        text.setAlpha(Math.min(255, alpha + 95));

        final float maxW = (c.shape == TouchLayout.Shape.PILL ? r * PILL_W : r) * 1.62f;
        final boolean twoLine = !c.subLabel.isEmpty();
        final float size = fitTextSize(c.label, maxW, r * (twoLine ? 0.68f : 0.92f));
        text.setTextSize(size);

        if (twoLine) {
            canvas.drawText(c.label, x, y - r * 0.03f, text);
            text.setTextSize(size * 0.70f);
            text.setAlpha(Math.min(255, alpha + 40));
            canvas.drawText(c.subLabel, x, y + r * 0.55f, text);
        } else {
            canvas.drawText(c.label, x, y + size * 0.35f, text);
        }
    }

    /** Eases the control's press value toward its target and asks for another frame if moving. */
    private float pressAnim(TouchLayout.Control c) {
        final float target = isPressed(c) ? 1f : 0f;
        Float cur = pressAnim.get(c.id);
        float v = cur == null ? target : cur;
        if (Math.abs(target - v) < 0.02f) {
            v = target;
        } else {
            v += (target - v) * 0.35f;
            animating = true;
        }
        pressAnim.put(c.id, v);
        return v;
    }

    private static int blend(int from, int to, float t) {
        t = clamp(t, 0f, 1f);
        final int r = Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * t);
        final int g = Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * t);
        final int b = Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t);
        return Color.rgb(r, g, b);
    }

    /** Largest text size at which {@code s} fits inside {@code maxWidth}, capped at {@code cap}. */
    private float fitTextSize(String s, float maxWidth, float cap) {
        text.setTextSize(cap);
        final float w = text.measureText(s);
        if (w <= maxWidth || w <= 0f) {
            return cap;
        }
        return cap * (maxWidth / w);
    }

    private void drawStick(Canvas canvas, TouchLayout.Control c, int alpha) {
        final float x = px(c);
        final float y = py(c);
        final float r = pr(c);
        final boolean selected = editMode && c == editTarget;
        final float[] pos = currentStickPos(c);
        final boolean active = pos != null;

        // base
        fill.setColor(Color.WHITE);
        fill.setAlpha((int) (alpha * 0.11f));
        canvas.drawCircle(x, y, r, fill);

        stroke.setColor(selected ? Color.YELLOW : blend(Color.WHITE, ACCENT, active ? 0.9f : 0.4f));
        stroke.setAlpha(selected ? 255 : (int) (alpha * (active ? 0.95f : 0.7f)));
        stroke.setStrokeWidth(Math.max(2f, r * 0.042f));
        canvas.drawCircle(x, y, r, stroke);

        // a faint inner ring marks the deadzone edge, so the throw is readable at a glance
        stroke.setAlpha((int) (alpha * 0.25f));
        stroke.setStrokeWidth(Math.max(1f, r * 0.02f));
        canvas.drawCircle(x, y, r * 0.34f, stroke);

        // knob, offset by however far the thumb has pushed it
        float kx = x;
        float ky = y;
        if (active) {
            final float travel = r * STICK_TRAVEL;
            float dx = (pos[0] - x) / travel;
            float dy = (pos[1] - y) / travel;
            final float mag = (float) Math.hypot(dx, dy);
            if (mag > 1f) {
                dx /= mag;
                dy /= mag;
            }
            kx = x + dx * r * KNOB_THROW;
            ky = y + dy * r * KNOB_THROW;
        }

        final float kr = r * (active ? 0.40f : 0.36f);
        fill.setColor(active ? ACCENT : Color.WHITE);
        fill.setAlpha((int) (alpha * (active ? 0.85f : 0.38f)));
        canvas.drawCircle(kx, ky, kr, fill);

        stroke.setColor(active ? ACCENT : Color.WHITE);
        stroke.setAlpha((int) (alpha * 0.9f));
        stroke.setStrokeWidth(Math.max(2f, r * 0.034f));
        canvas.drawCircle(kx, ky, kr, stroke);
    }

    private float[] currentStickPos(TouchLayout.Control c) {
        for (int i = 0; i < owned.size(); ++i) {
            if (owned.valueAt(i) == c) {
                return stickPos.get(owned.keyAt(i));
            }
        }
        return null;
    }

    private boolean isPressed(TouchLayout.Control c) {
        for (int i = 0; i < owned.size(); ++i) {
            if (owned.valueAt(i) == c) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- native

    private static native void nativeSetButtons(int contMask);
    private static native void nativeSetStick(float x, float y);
    private static native void nativeSetActive(boolean active);
}
