package com.perfectdark.port;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Draws the virtual sticks + buttons on top of the SDL surface and pumps
 * their state into native code (touch.c) via nativeSetState().
 *
 * In edit mode, touches drag elements around or (with the mode toggle)
 * resize them; no native state is published.
 */
public class TouchOverlayView extends View {

    public interface EditListener {
        void onLayoutChanged();
    }

    // Element id -> pointer id currently pressing it (-1 if not pressed).
    private final SparseArray<String> pointerToElement = new SparseArray<>();
    private TouchLayout layout;
    private boolean editMode = false;
    private boolean resizeMode = false;
    private @Nullable EditListener editListener;

    // Per-stick state: which pointer owns it and last normalized axis value.
    private int leftPointer = -1, rightPointer = -1;
    private float leftStickX, leftStickY;
    private float rightStickX, rightStickY;

    // Per-button press state, indexed by mask.
    private int pressedButtons = 0;

    // Edit-mode drag tracking.
    private String draggedId = null;
    private float dragOffsetX, dragOffsetY;

    private final Paint paintBase = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintKnob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintBtnIdle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintBtnActive = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintEditBorder = new Paint(Paint.ANTI_ALIAS_FLAG);

    public TouchOverlayView(Context c) {
        super(c);
        init(c);
    }

    public TouchOverlayView(Context c, @Nullable AttributeSet a) {
        super(c, a);
        init(c);
    }

    private void init(Context c) {
        layout = TouchLayout.load(c);
        setWillNotDraw(false);

        paintBase.setColor(Color.argb(70, 255, 255, 255));
        paintBase.setStyle(Paint.Style.STROKE);
        paintBase.setStrokeWidth(4);

        paintKnob.setColor(Color.argb(120, 255, 255, 255));
        paintKnob.setStyle(Paint.Style.FILL);

        paintBtnIdle.setColor(Color.argb(90, 200, 220, 255));
        paintBtnIdle.setStyle(Paint.Style.FILL);

        paintBtnActive.setColor(Color.argb(180, 255, 240, 150));
        paintBtnActive.setStyle(Paint.Style.FILL);

        paintLabel.setColor(Color.argb(220, 255, 255, 255));
        paintLabel.setTextAlign(Paint.Align.CENTER);

        paintEditBorder.setColor(Color.argb(220, 255, 160, 0));
        paintEditBorder.setStyle(Paint.Style.STROKE);
        paintEditBorder.setStrokeWidth(6);
    }

    public void setEditMode(boolean enabled) {
        this.editMode = enabled;
        resetTouchState();
        invalidate();
    }

    public void setResizeMode(boolean enabled) {
        this.resizeMode = enabled;
    }

    public void setEditListener(@Nullable EditListener l) {
        this.editListener = l;
    }

    public TouchLayout getLayout() {
        return layout;
    }

    public void reloadLayout() {
        layout = TouchLayout.load(getContext());
        resetTouchState();
        invalidate();
    }

    private void resetTouchState() {
        pointerToElement.clear();
        leftPointer = rightPointer = -1;
        leftStickX = leftStickY = rightStickX = rightStickY = 0f;
        pressedButtons = 0;
        publish(false);
    }

    // ---------------- Layout helpers ----------------

    private float px(float norm, int size) { return norm * size; }

    /** Min of width/height used for radius sizing so buttons stay circular. */
    private int minExtent() { return Math.min(getWidth(), getHeight()); }

    @Nullable
    private TouchLayout.Element hitTest(float x, float y) {
        int w = getWidth(), h = getHeight(), m = minExtent();
        // Reverse iterate so visually-top-most wins (buttons drawn last).
        for (int i = layout.elements.size() - 1; i >= 0; --i) {
            TouchLayout.Element el = layout.elements.get(i);
            float dx = x - px(el.cx, w);
            float dy = y - px(el.cy, h);
            float r = el.radius * m;
            // Give sticks a bigger hit area so the thumb doesn't miss.
            if (el.kind != TouchLayout.Kind.BUTTON) r *= 1.4f;
            if (dx * dx + dy * dy <= r * r) return el;
        }
        return null;
    }

    // ---------------- Touch handling ----------------

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (editMode) return onEditTouch(ev);

        int action = ev.getActionMasked();
        int idx = ev.getActionIndex();
        int pid = ev.getPointerId(idx);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                handleDown(pid, ev.getX(idx), ev.getY(idx));
                break;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < ev.getPointerCount(); ++i) {
                    handleMove(ev.getPointerId(i), ev.getX(i), ev.getY(i));
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL:
                handleUp(pid);
                break;
        }

        publish(pointerToElement.size() > 0);
        invalidate();
        return true;
    }

    private void handleDown(int pid, float x, float y) {
        TouchLayout.Element el = hitTest(x, y);
        if (el == null) return;

        switch (el.kind) {
            case LEFT_STICK:
                if (leftPointer == -1) {
                    leftPointer = pid;
                    pointerToElement.put(pid, el.id);
                    updateStick(el, x, y, true);
                }
                break;
            case RIGHT_STICK:
                if (rightPointer == -1) {
                    rightPointer = pid;
                    pointerToElement.put(pid, el.id);
                    updateStick(el, x, y, false);
                }
                break;
            case BUTTON:
                pointerToElement.put(pid, el.id);
                pressedButtons |= el.mask;
                break;
        }
    }

    private void handleMove(int pid, float x, float y) {
        String id = pointerToElement.get(pid);
        if (id == null) {
            // A pointer that started outside any element can still slide
            // onto a button (common for quick fire taps).
            TouchLayout.Element el = hitTest(x, y);
            if (el != null && el.kind == TouchLayout.Kind.BUTTON) {
                pointerToElement.put(pid, el.id);
                pressedButtons |= el.mask;
            }
            return;
        }
        TouchLayout.Element el = findById(id);
        if (el == null) return;
        if (el.kind == TouchLayout.Kind.LEFT_STICK) updateStick(el, x, y, true);
        else if (el.kind == TouchLayout.Kind.RIGHT_STICK) updateStick(el, x, y, false);
        // Buttons only react on down/up, sliding around doesn't retrigger.
    }

    private void handleUp(int pid) {
        String id = pointerToElement.get(pid);
        if (id == null) return;
        pointerToElement.remove(pid);

        TouchLayout.Element el = findById(id);
        if (el == null) return;

        switch (el.kind) {
            case LEFT_STICK:
                if (leftPointer == pid) {
                    leftPointer = -1;
                    leftStickX = leftStickY = 0f;
                }
                break;
            case RIGHT_STICK:
                if (rightPointer == pid) {
                    rightPointer = -1;
                    rightStickX = rightStickY = 0f;
                }
                break;
            case BUTTON:
                // Only clear the bit if no other active pointer is holding
                // the same button id (rare but possible when rebound).
                boolean stillHeld = false;
                for (int i = 0; i < pointerToElement.size(); ++i) {
                    if (id.equals(pointerToElement.valueAt(i))) { stillHeld = true; break; }
                }
                if (!stillHeld) pressedButtons &= ~el.mask;
                break;
        }
    }

    @Nullable
    private TouchLayout.Element findById(String id) {
        for (TouchLayout.Element el : layout.elements) if (el.id.equals(id)) return el;
        return null;
    }

    private void updateStick(TouchLayout.Element el, float x, float y, boolean left) {
        int w = getWidth(), h = getHeight(), m = minExtent();
        float cx = px(el.cx, w);
        float cy = px(el.cy, h);
        float r = el.radius * m;
        float dx = (x - cx) / r;
        float dy = (y - cy) / r;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len > 1f) { dx /= len; dy /= len; }
        if (left) { leftStickX = dx; leftStickY = dy; }
        else      { rightStickX = dx; rightStickY = dy; }
    }

    private void publish(boolean anyDown) {
        nativeSetState(leftStickX, leftStickY, rightStickX, rightStickY,
                pressedButtons, anyDown);
    }

    // ---------------- Edit mode ----------------

    private boolean onEditTouch(MotionEvent ev) {
        int action = ev.getActionMasked();
        int idx = ev.getActionIndex();
        int w = getWidth(), h = getHeight(), m = minExtent();
        float x = ev.getX(idx), y = ev.getY(idx);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                TouchLayout.Element el = hitTest(x, y);
                if (el == null) return true;
                draggedId = el.id;
                dragOffsetX = x - px(el.cx, w);
                dragOffsetY = y - px(el.cy, h);
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (draggedId == null) return true;
                TouchLayout.Element el = findById(draggedId);
                if (el == null) return true;
                if (resizeMode) {
                    // Distance from element center -> new radius.
                    float dx = x - px(el.cx, w);
                    float dy = y - px(el.cy, h);
                    float newR = (float) Math.sqrt(dx * dx + dy * dy) / m;
                    el.radius = Math.max(0.03f, Math.min(0.25f, newR));
                } else {
                    el.cx = Math.max(0.02f, Math.min(0.98f, (x - dragOffsetX) / w));
                    el.cy = Math.max(0.02f, Math.min(0.98f, (y - dragOffsetY) / h));
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL:
                draggedId = null;
                if (editListener != null) editListener.onLayoutChanged();
                break;
        }
        invalidate();
        return true;
    }

    // ---------------- Rendering ----------------

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight(), m = minExtent();
        paintLabel.setTextSize(Math.max(16f, m * 0.03f));

        for (TouchLayout.Element el : layout.elements) {
            float cx = px(el.cx, w);
            float cy = px(el.cy, h);
            float r = el.radius * m;
            switch (el.kind) {
                case LEFT_STICK:
                case RIGHT_STICK: {
                    canvas.drawCircle(cx, cy, r, paintBase);
                    float sx = (el.kind == TouchLayout.Kind.LEFT_STICK) ? leftStickX : rightStickX;
                    float sy = (el.kind == TouchLayout.Kind.LEFT_STICK) ? leftStickY : rightStickY;
                    canvas.drawCircle(cx + sx * r * 0.5f, cy + sy * r * 0.5f, r * 0.45f, paintKnob);
                    break;
                }
                case BUTTON: {
                    Paint p = (pressedButtons & el.mask) != 0 ? paintBtnActive : paintBtnIdle;
                    canvas.drawCircle(cx, cy, r, p);
                    if (el.label != null && !el.label.isEmpty()) {
                        canvas.drawText(el.label, cx,
                                cy + paintLabel.getTextSize() * 0.35f, paintLabel);
                    }
                    break;
                }
            }
            if (editMode) {
                canvas.drawCircle(cx, cy, r, paintEditBorder);
            }
        }
    }

    // Published to native (see touch.c).
    private native void nativeSetState(float lx, float ly, float rx, float ry,
                                       int buttons, boolean anyDown);
}
