package com.perfectdark.port;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.SparseArray;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;

import org.libsdl.app.SDLActivity;

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

    private static final float DEADZONE = 0.16f;

    private final TouchLayout layout;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint editHint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** pointer id -> the control it is currently driving */
    private final SparseArray<TouchLayout.Control> owned = new SparseArray<>();
    /** pointer id -> {anchorX, anchorY} in pixels, for stick controls */
    private final SparseArray<float[]> anchors = new SparseArray<>();

    private boolean editMode;
    private TouchLayout.Control editTarget;
    private float editGrabDx, editGrabDy;
    private float pinchStartDist, pinchStartRadius;
    private int pinchPointerA = -1, pinchPointerB = -1;

    private int lastMask;
    private boolean gamepadPresent;

    private static final long EDIT_LONG_PRESS_MS = 650;
    private final Runnable enterEdit = () -> {
        longPressFired = true;
        setEditMode(true);
    };
    private boolean longPressFired;

    public TouchControls(Context context, TouchLayout layout) {
        super(context);
        this.layout = layout;

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
        return c.cx * getWidth();
    }

    private float py(TouchLayout.Control c) {
        return c.cy * getHeight();
    }

    private float pr(TouchLayout.Control c) {
        return c.radius * getHeight() * 0.5f;
    }

    private TouchLayout.Control hitTest(float x, float y) {
        // Buttons win over the look area, so a stray press near a button never spins the camera.
        TouchLayout.Control stick = null;
        for (TouchLayout.Control c : layout.getControls()) {
            if (!c.enabled) {
                continue;
            }
            // the editor always shows everything, even if the overlay is currently hidden
            if (!layout.enabled && !editMode && c.kind != TouchLayout.Kind.TOGGLE) {
                continue;
            }
            final float dx = x - px(c);
            final float dy = y - py(c);
            final float r = pr(c);
            if (dx * dx + dy * dy > r * r) {
                continue;
            }
            if (c.kind == TouchLayout.Kind.STICK_MOVE || c.kind == TouchLayout.Kind.STICK_LOOK) {
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
                if (c.kind == TouchLayout.Kind.STICK_MOVE || c.kind == TouchLayout.Kind.STICK_LOOK) {
                    anchors.put(id, new float[]{x, y});
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
                    if (c.kind == TouchLayout.Kind.STICK_MOVE || c.kind == TouchLayout.Kind.STICK_LOOK) {
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

    private void updateStick(TouchLayout.Control c, int id, float x, float y) {
        final float[] anchor = anchors.get(id);
        if (anchor == null) {
            return;
        }
        final float r = pr(c);
        float dx = (x - anchor[0]) / r;
        float dy = (y - anchor[1]) / r;

        if (c.kind == TouchLayout.Kind.STICK_LOOK) {
            dx *= layout.lookSensitivity;
            dy *= layout.lookSensitivity;
        }

        final float mag = (float) Math.hypot(dx, dy);
        if (mag < DEADZONE) {
            dx = dy = 0f;
        } else {
            // rescale so the stick starts moving from zero at the edge of the deadzone
            final float scaled = Math.min((mag - DEADZONE) / (1f - DEADZONE), 1f);
            dx = dx / mag * scaled;
            dy = dy / mag * scaled;
        }

        // Android's y grows downwards; the N64 stick's does not.
        nativeSetStick(c.kind == TouchLayout.Kind.STICK_MOVE ? 0 : 1, dx, -dy);
    }

    private void releasePointer(int id, TouchLayout.Control c) {
        owned.remove(id);
        anchors.remove(id);
        if (c.kind == TouchLayout.Kind.STICK_MOVE) {
            nativeSetStick(0, 0f, 0f);
        } else if (c.kind == TouchLayout.Kind.STICK_LOOK) {
            nativeSetStick(1, 0f, 0f);
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
        anchors.clear();
        nativeSetStick(0, 0f, 0f);
        nativeSetStick(1, 0f, 0f);
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
                editTarget.radius = clamp(pinchStartRadius * (dist / pinchStartDist), 0.04f, 0.6f);
                invalidate();
            }
            return true;
        }

        final int index = event.findPointerIndex(pinchPointerA);
        if (index < 0) {
            return true;
        }
        final float r = pr(editTarget);
        editTarget.cx = clamp((event.getX(index) + editGrabDx) / getWidth(),
                r / getWidth(), 1f - r / getWidth());
        editTarget.cy = clamp((event.getY(index) + editGrabDy) / getHeight(),
                r / getHeight(), 1f - r / getHeight());
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

    public void onSettingsChanged() {
        layout.save();
        syncActive();
        invalidate();
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
        final float scale = getHeight() / 1080f;

        for (TouchLayout.Control c : layout.getControls()) {
            if (!c.enabled) {
                continue;
            }
            // the editor always shows everything, even if the overlay is currently hidden
            if (!layout.enabled && !editMode && c.kind != TouchLayout.Kind.TOGGLE) {
                continue;
            }

            final float x = px(c);
            final float y = py(c);
            final float r = pr(c);
            final boolean pressed = isPressed(c);
            final boolean selected = editMode && c == editTarget;

            if (c.kind == TouchLayout.Kind.STICK_LOOK && !editMode) {
                // The look area is invisible in play; showing it would just clutter the screen.
                drawStickKnob(canvas, c, alpha);
                continue;
            }

            fill.setColor(Color.WHITE);
            fill.setAlpha(pressed ? (int) (alpha * 0.75f) : (int) (alpha * 0.22f));
            canvas.drawCircle(x, y, r, fill);

            stroke.setColor(selected ? Color.YELLOW : Color.WHITE);
            stroke.setAlpha(selected ? 255 : alpha);
            stroke.setStrokeWidth(Math.max(2f, 3.5f * scale));
            canvas.drawCircle(x, y, r, stroke);

            if (!c.label.isEmpty()) {
                text.setColor(Color.WHITE);
                text.setAlpha(Math.min(255, alpha + 70));
                text.setTextSize(r * 0.85f);
                canvas.drawText(c.label, x, y + r * 0.30f, text);
            }

            if (c.kind == TouchLayout.Kind.STICK_MOVE) {
                drawStickKnob(canvas, c, alpha);
            }
        }

        if (editMode) {
            editHint.setTextSize(Math.max(22f, 34f * scale));
            editHint.setAlpha(220);
            canvas.drawText("Drag to move \u2022 pinch to resize \u2022 Back when done",
                    getWidth() * 0.5f, Math.max(40f, 62f * scale), editHint);
        }
    }

    private void drawStickKnob(Canvas canvas, TouchLayout.Control c, int alpha) {
        int id = -1;
        for (int i = 0; i < owned.size(); ++i) {
            if (owned.valueAt(i) == c) {
                id = owned.keyAt(i);
                break;
            }
        }
        if (id < 0) {
            return;
        }
        final float[] anchor = anchors.get(id);
        if (anchor == null) {
            return;
        }

        final float r = pr(c);
        stroke.setColor(Color.WHITE);
        stroke.setAlpha((int) (alpha * 0.7f));
        stroke.setStrokeWidth(Math.max(2f, r * 0.05f));
        canvas.drawCircle(anchor[0], anchor[1], r * 0.55f, stroke);

        fill.setColor(Color.WHITE);
        fill.setAlpha((int) (alpha * 0.55f));
        canvas.drawCircle(anchor[0], anchor[1], r * 0.22f, fill);
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
    private static native void nativeSetStick(int stick, float x, float y);
    private static native void nativeSetActive(boolean active);
}
