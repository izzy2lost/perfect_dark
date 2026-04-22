package com.perfectdark.port;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Draws the virtual sticks / buttons / look-pad on top of the SDL surface and
 * pumps their state into native code (touch.c) via nativeSetState().
 *
 * Features:
 *  - LEFT_STICK / RIGHT_STICK: classic virtual analog sticks.
 *  - LOOK_PAD: rectangular zone where any drag maps to right-stick velocity
 *    (CoD Mobile / Fortnite-style camera control).
 *  - BUTTON: circular on/off buttons that OR into the controller button mask.
 *  - Edit mode: tap-drag an element to move, toggle RESIZE to stretch it
 *    (LOOK_PAD stretches width & height independently, others change radius).
 *  - Live-edit HUD: a small floating EDIT pill enters edit mode without
 *    leaving the game; SAVE persists, CANCEL restores the previous layout.
 */
public class TouchOverlayView extends View {

    private final SparseArray<String> pointerToElement = new SparseArray<>();
    private TouchLayout layout;
    private boolean editMode = false;
    private boolean internalHudVisible = true;
    // When true, the edit HUD shrinks to a single pill in the top-right so
    // the user can place / drag buttons that live under the full bar.
    private boolean hudCollapsed = false;
    // Currently selected button in edit mode. Drives the per-button resize
    // bar. Null means no button is selected.
    private @Nullable String selectedButtonId = null;
    private static final float BUTTON_SIZE_MIN = 0.03f;
    private static final float BUTTON_SIZE_MAX = 0.15f;
    private static final float BUTTON_SIZE_STEP = 0.005f;

    // Snapshot taken when entering edit mode; restored if the user cancels.
    private @Nullable TouchLayout editSnapshot;

    private int leftPointer = -1, rightPointer = -1;
    private float leftStickX, leftStickY;
    private float rightStickX, rightStickY;
    private int pressedButtons = 0;

    // ---- Floating-stick move pad (CoD-style: stick center = touch point).
    private int movePointer = -1;
    private float moveAnchorX, moveAnchorY;
    private float moveMaxRadiusPx;

    // ---- Look pad (CoD / Quake-style: finger delta == mouse delta).
    private int lookPointer = -1;
    private float lookLastX, lookLastY;
    // How many "mouse pixels" each phone pixel produces when dragging the
    // look pad, per axis. Lives on TouchLayout so it is persisted with the
    // rest of the config and tunable via the sens pills in the edit HUD.
    private static final float LOOK_SENS_MIN = 0.05f;
    private static final float LOOK_SENS_MAX = 5.0f;
    private static final float LOOK_SENS_STEP = 0.1f;

    // ---- Edit drag state.
    private String draggedId = null;
    private float dragOffsetX, dragOffsetY;

    // ---- HUD action rectangles (recomputed each draw).
    private final RectF editPillRect = new RectF();
    private final RectF btnSaveRect = new RectF();
    private final RectF btnResetRect = new RectF();
    private final RectF btnResizeRect = new RectF();
    private final RectF btnCancelRect = new RectF();
    private final RectF btnSensXDownRect = new RectF();
    private final RectF btnSensXUpRect = new RectF();
    private final RectF sensXLabelRect = new RectF();
    private final RectF btnSensYDownRect = new RectF();
    private final RectF btnSensYUpRect = new RectF();
    private final RectF sensYLabelRect = new RectF();
    private final RectF btnCollapseRect = new RectF();
    // Per-selected-button resize bar rects.
    private final RectF btnSizeDownRect = new RectF();
    private final RectF btnSizeLabelRect = new RectF();
    private final RectF btnSizeUpRect = new RectF();

    // ---- Paints.
    private final Paint paintBase = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintKnob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintBtnIdle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintBtnActive = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintEditBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintLookBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintLookFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintEditPillFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintEditPillText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintHudBar = new Paint();

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

        paintLookBorder.setColor(Color.argb(110, 120, 200, 255));
        paintLookBorder.setStyle(Paint.Style.STROKE);
        paintLookBorder.setStrokeWidth(3);
        paintLookFill.setColor(Color.argb(20, 120, 200, 255));
        paintLookFill.setStyle(Paint.Style.FILL);

        paintEditPillFill.setColor(Color.argb(180, 40, 40, 48));
        paintEditPillFill.setStyle(Paint.Style.FILL);
        paintEditPillText.setColor(Color.argb(255, 255, 200, 100));
        paintEditPillText.setTextAlign(Paint.Align.CENTER);

        paintHudBar.setColor(Color.argb(200, 20, 20, 28));
    }

    public TouchLayout getLayout() { return layout; }

    public void reloadLayout() {
        layout = TouchLayout.load(getContext());
        resetInputState();
        invalidate();
    }

    /** Call to force edit mode externally (used by LayoutEditorActivity). */
    public void setEditMode(boolean enabled) {
        if (enabled == editMode) return;
        editMode = enabled;
        if (enabled) {
            editSnapshot = layout.copy();
            hudCollapsed = false;
        } else {
            editSnapshot = null;
        }
        selectedButtonId = null;
        resetInputState();
        invalidate();
    }

    public boolean isEditMode() { return editMode; }

    /**
     * Disable the overlay's own HUD (EDIT pill + SAVE/RESET/RESIZE/CANCEL bar).
     * The standalone LayoutEditorActivity supplies its own buttons as
     * regular Android widgets, so we don't want double UI.
     */
    public void setInternalHudVisible(boolean visible) {
        internalHudVisible = visible;
        invalidate();
    }

    private void resetInputState() {
        pointerToElement.clear();
        leftPointer = rightPointer = lookPointer = movePointer = -1;
        leftStickX = leftStickY = rightStickX = rightStickY = 0f;
        pressedButtons = 0;
        publish(false);
    }

    // ---------------- Layout helpers ----------------

    private float px(float norm, int size) { return norm * size; }
    private int minExtent() { return Math.min(getWidth(), getHeight()); }

    @Nullable
    private TouchLayout.Element hitTest(float x, float y) {
        int w = getWidth(), h = getHeight(), m = minExtent();
        // Reverse iterate so visually top-most (buttons) wins over the lookpad.
        for (int i = layout.elements.size() - 1; i >= 0; --i) {
            TouchLayout.Element el = layout.elements.get(i);
            float cx = px(el.cx, w), cy = px(el.cy, h);
            if (el.kind == TouchLayout.Kind.LOOK_PAD || el.kind == TouchLayout.Kind.MOVE_PAD) {
                float rx = el.hw * w, ry = el.hh * h;
                if (x >= cx - rx && x <= cx + rx && y >= cy - ry && y <= cy + ry) {
                    return el;
                }
            } else {
                float dx = x - cx, dy = y - cy;
                float r = el.radius * m;
                if (el.kind != TouchLayout.Kind.BUTTON) r *= 1.4f;
                if (dx * dx + dy * dy <= r * r) return el;
            }
        }
        return null;
    }

    @Nullable
    private TouchLayout.Element findById(String id) {
        for (TouchLayout.Element el : layout.elements) if (el.id.equals(id)) return el;
        return null;
    }

    // ---------------- Touch handling ----------------

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        int idx = ev.getActionIndex();
        float x = ev.getX(idx), y = ev.getY(idx);

        // --- HUD (always tested first, in both gameplay and edit mode) ---
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            if (handleHudDown(x, y)) return true;
        }

        if (editMode) return onEditTouch(ev);

        int pid = ev.getPointerId(idx);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                handleDown(pid, x, y);
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

        publish(pointerToElement.size() > 0 || lookPointer != -1 || movePointer != -1);
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
            case LOOK_PAD:
                if (lookPointer == -1) {
                    lookPointer = pid;
                    lookLastX = x; lookLastY = y;
                    pointerToElement.put(pid, el.id);
                }
                break;
            case MOVE_PAD:
                if (movePointer == -1) {
                    movePointer = pid;
                    moveAnchorX = x;
                    moveAnchorY = y;
                    float r = el.radius > 0f ? el.radius : 0.09f;
                    moveMaxRadiusPx = r * minExtent();
                    pointerToElement.put(pid, el.id);
                }
                break;
            case BUTTON:
                pointerToElement.put(pid, el.id);
                pressedButtons |= el.mask;
                break;
        }
    }

    private void handleMove(int pid, float x, float y) {
        if (pid == lookPointer) {
            float dx = (x - lookLastX) * layout.lookSensX;
            float dy = (y - lookLastY) * layout.lookSensY;
            lookLastX = x; lookLastY = y;
            int idx = Math.round(dx), idy = Math.round(dy);
            if (idx != 0 || idy != 0) {
                nativeAddLookDelta(idx, idy);
            }
            return;
        }
        if (pid == movePointer) {
            float dx = (x - moveAnchorX) / moveMaxRadiusPx;
            float dy = (y - moveAnchorY) / moveMaxRadiusPx;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len > 1f) { dx /= len; dy /= len; }
            // In PD's CONTROLMODE_PC (the port default), stick1 is camera and
            // stick2 is walk/strafe, so the move pad must feed the right stick.
            rightStickX = dx;
            rightStickY = dy;
            return;
        }
        String id = pointerToElement.get(pid);
        if (id == null) return;
        TouchLayout.Element el = findById(id);
        if (el == null) return;
        if (el.kind == TouchLayout.Kind.LEFT_STICK) updateStick(el, x, y, true);
        else if (el.kind == TouchLayout.Kind.RIGHT_STICK) updateStick(el, x, y, false);
    }

    private void handleUp(int pid) {
        if (pid == lookPointer) {
            lookPointer = -1;
            pointerToElement.remove(pid);
            return;
        }
        if (pid == movePointer) {
            movePointer = -1;
            rightStickX = 0;
            rightStickY = 0;
            pointerToElement.remove(pid);
            return;
        }

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
                boolean stillHeld = false;
                for (int i = 0; i < pointerToElement.size(); ++i) {
                    if (id.equals(pointerToElement.valueAt(i))) { stillHeld = true; break; }
                }
                if (!stillHeld) pressedButtons &= ~el.mask;
                break;
            default:
                break;
        }
    }

    private void updateStick(TouchLayout.Element el, float x, float y, boolean left) {
        int w = getWidth(), h = getHeight(), m = minExtent();
        float cx = px(el.cx, w), cy = px(el.cy, h);
        float r = el.radius * m;
        float dx = (x - cx) / r, dy = (y - cy) / r;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len > 1f) { dx /= len; dy /= len; }
        if (left) { leftStickX = dx; leftStickY = dy; }
        else      { rightStickX = dx; rightStickY = dy; }
    }

    private static float clampStick(float v) {
        return v < -1f ? -1f : (v > 1f ? 1f : v);
    }

    private void publish(boolean anyDown) {
        nativeSetState(leftStickX, leftStickY, rightStickX, rightStickY,
                pressedButtons, anyDown);
    }

    // ---------------- HUD (edit toggle + edit-mode buttons) ----------------

    /** Returns true if the down-event was consumed by a HUD control. */
    private boolean handleHudDown(float x, float y) {
        if (!internalHudVisible) return false;
        if (editMode) {
            // Per-button resize bar: takes priority over the main HUD so the
            // user can press - / + even if it sits under a pill. It is only
            // drawn when a button is selected.
            if (selectedButtonId != null) {
                TouchLayout.Element sel = findById(selectedButtonId);
                if (sel != null) {
                    if (btnSizeDownRect.contains(x, y)) {
                        sel.radius = Math.max(BUTTON_SIZE_MIN,
                                sel.radius - BUTTON_SIZE_STEP);
                        invalidate();
                        return true;
                    }
                    if (btnSizeUpRect.contains(x, y)) {
                        sel.radius = Math.min(BUTTON_SIZE_MAX,
                                sel.radius + BUTTON_SIZE_STEP);
                        invalidate();
                        return true;
                    }
                    if (btnSizeLabelRect.contains(x, y)) {
                        // Consume taps on the label so they don't fall through
                        // to the button underneath.
                        return true;
                    }
                }
            }
            // Collapse/expand toggle always takes priority so the user can
            // reveal the rest of the screen underneath.
            if (btnCollapseRect.contains(x, y)) {
                hudCollapsed = !hudCollapsed;
                invalidate();
                return true;
            }
            if (hudCollapsed) {
                // Only the collapse pill is interactive while minimized.
                return false;
            }
            if (btnSaveRect.contains(x, y)) {
                layout.save(getContext());
                editSnapshot = null;
                setEditMode(false);
                return true;
            }
            if (btnCancelRect.contains(x, y)) {
                if (editSnapshot != null) layout.assignFrom(editSnapshot);
                setEditMode(false);
                return true;
            }
            if (btnResetRect.contains(x, y)) {
                TouchLayout defaults = TouchLayout.defaults();
                // Overwrite only geometry; keep same element ids/order.
                for (int i = 0; i < layout.elements.size() && i < defaults.elements.size(); ++i) {
                    TouchLayout.Element dst = layout.elements.get(i);
                    TouchLayout.Element s = defaults.elements.get(i);
                    dst.cx = s.cx; dst.cy = s.cy;
                    dst.radius = s.radius;
                    dst.hw = s.hw; dst.hh = s.hh;
                }
                invalidate();
                return true;
            }
            if (btnSensXDownRect.contains(x, y)) {
                layout.lookSensX = Math.max(LOOK_SENS_MIN, layout.lookSensX - LOOK_SENS_STEP);
                invalidate();
                return true;
            }
            if (btnSensXUpRect.contains(x, y)) {
                layout.lookSensX = Math.min(LOOK_SENS_MAX, layout.lookSensX + LOOK_SENS_STEP);
                invalidate();
                return true;
            }
            if (btnSensYDownRect.contains(x, y)) {
                layout.lookSensY = Math.max(LOOK_SENS_MIN, layout.lookSensY - LOOK_SENS_STEP);
                invalidate();
                return true;
            }
            if (btnSensYUpRect.contains(x, y)) {
                layout.lookSensY = Math.min(LOOK_SENS_MAX, layout.lookSensY + LOOK_SENS_STEP);
                invalidate();
                return true;
            }
            return false;
        }
        // Gameplay: only the floating EDIT pill is interactive in the HUD.
        if (editPillRect.contains(x, y)) {
            setEditMode(true);
            return true;
        }
        return false;
    }

    // ---------------- Edit mode ----------------

    private boolean onEditTouch(MotionEvent ev) {
        int action = ev.getActionMasked();
        int idx = ev.getActionIndex();
        int w = getWidth(), h = getHeight();
        float x = ev.getX(idx), y = ev.getY(idx);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                TouchLayout.Element el = hitTestEditable(x, y);
                if (el == null) {
                    // Tap outside any editable element clears selection.
                    if (selectedButtonId != null) {
                        selectedButtonId = null;
                        invalidate();
                    }
                    return true;
                }
                draggedId = el.id;
                selectedButtonId = el.id;
                dragOffsetX = x - px(el.cx, w);
                dragOffsetY = y - px(el.cy, h);
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (draggedId == null) return true;
                TouchLayout.Element el = findById(draggedId);
                if (el == null) return true;
                el.cx = Math.max(0.02f, Math.min(0.98f, (x - dragOffsetX) / w));
                el.cy = Math.max(0.02f, Math.min(0.98f, (y - dragOffsetY) / h));
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL:
                draggedId = null;
                break;
        }
        invalidate();
        return true;
    }

    /** Same as hitTest but skips the pads so they cannot be dragged. */
    @Nullable
    private TouchLayout.Element hitTestEditable(float x, float y) {
        int w = getWidth(), h = getHeight(), m = minExtent();
        for (int i = layout.elements.size() - 1; i >= 0; --i) {
            TouchLayout.Element el = layout.elements.get(i);
            if (el.kind == TouchLayout.Kind.LOOK_PAD
                    || el.kind == TouchLayout.Kind.MOVE_PAD) continue;
            float cx = px(el.cx, w), cy = px(el.cy, h);
            float dx = x - cx, dy = y - cy;
            float r = el.radius * m;
            if (dx * dx + dy * dy <= r * r) return el;
        }
        return null;
    }

    // ---------------- Rendering ----------------

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight(), m = minExtent();
        paintLabel.setTextSize(Math.max(16f, m * 0.03f));

        for (TouchLayout.Element el : layout.elements) {
            float cx = px(el.cx, w), cy = px(el.cy, h);
            switch (el.kind) {
                case LEFT_STICK:
                case RIGHT_STICK: {
                    float r = el.radius * m;
                    canvas.drawCircle(cx, cy, r, paintBase);
                    float sx = (el.kind == TouchLayout.Kind.LEFT_STICK) ? leftStickX : rightStickX;
                    float sy = (el.kind == TouchLayout.Kind.LEFT_STICK) ? leftStickY : rightStickY;
                    canvas.drawCircle(cx + sx * r * 0.5f, cy + sy * r * 0.5f, r * 0.45f, paintKnob);
                    break;
                }
                case LOOK_PAD:
                    // Invisible by design — it is simply the right half of
                    // the screen. Nothing to draw.
                    break;
                case MOVE_PAD:
                    // Same deal, but show the floating stick at the anchor
                    // while the user is actually touching the pad so there
                    // is clear feedback for direction + deflection.
                    if (movePointer != -1) {
                        canvas.drawCircle(moveAnchorX, moveAnchorY, moveMaxRadiusPx, paintBase);
                        canvas.drawCircle(
                                moveAnchorX + rightStickX * moveMaxRadiusPx * 0.5f,
                                moveAnchorY + rightStickY * moveMaxRadiusPx * 0.5f,
                                moveMaxRadiusPx * 0.45f, paintKnob);
                    }
                    break;
                case BUTTON: {
                    float r = el.radius * m;
                    Paint p = (pressedButtons & el.mask) != 0 ? paintBtnActive : paintBtnIdle;
                    canvas.drawCircle(cx, cy, r, p);
                    if (el.label != null && !el.label.isEmpty()) {
                        canvas.drawText(el.label, cx,
                                cy + paintLabel.getTextSize() * 0.35f, paintLabel);
                    }
                    break;
                }
            }
            // Pads are not editable, so only show the orange edit outline
            // on buttons.
            if (editMode && el.kind == TouchLayout.Kind.BUTTON) {
                float r = el.radius * m;
                canvas.drawCircle(cx, cy, r, paintEditBorder);
            }
        }

        if (internalHudVisible) {
            drawHud(canvas, w, h, m);
        }
    }

    private void drawHud(Canvas canvas, int w, int h, int m) {
        float dp = m / 400f;
        float pillH = 48 * dp, pillW = 96 * dp;
        float margin = 12 * dp;

        if (editMode) {
            // The collapse pill lives in the top-right corner and is the
            // only HUD control visible when collapsed.
            float collapseW = pillH * 1.1f;
            float collapseX0 = w - margin - collapseW;
            float collapseY0 = margin * 0.3f;
            btnCollapseRect.set(collapseX0, collapseY0,
                    collapseX0 + collapseW, collapseY0 + pillH);

            if (hudCollapsed) {
                drawPill(canvas, btnCollapseRect, 0xAA202028, "EDIT ▼");
                // Clear other rects so stale positions don't swallow taps.
                btnSaveRect.setEmpty(); btnResizeRect.setEmpty();
                btnResetRect.setEmpty(); btnCancelRect.setEmpty();
                btnSensXDownRect.setEmpty(); btnSensXUpRect.setEmpty();
                sensXLabelRect.setEmpty();
                btnSensYDownRect.setEmpty(); btnSensYUpRect.setEmpty();
                sensYLabelRect.setEmpty();
                editPillRect.setEmpty();
                drawButtonResizeBar(canvas, w, h, m);
                return;
            }

            // Two-row HUD. Row 1 = actions. Row 2 = per-axis sensitivity.
            float rowGapY = 6 * dp;
            float barH = pillH * 2 + rowGapY + margin * 1.6f;
            canvas.drawRect(0, 0, w, barH, paintHudBar);

            float gap = 8 * dp;
            float squarePillW = pillH; // small square pill for +/-

            // --- Row 1 ---
            float r1y0 = margin * 0.3f;
            float r1y1 = r1y0 + pillH;
            float x = margin;

            btnSaveRect.set(x, r1y0, x + pillW, r1y1);
            x += pillW + gap;
            btnResetRect.set(x, r1y0, x + pillW, r1y1);
            x += pillW + gap;
            btnCancelRect.set(x, r1y0, x + pillW, r1y1);
            // RESIZE pill removed — per-button resize handles replaced it.
            btnResizeRect.setEmpty();

            // --- Row 2 ---
            float r2y0 = r1y1 + rowGapY;
            float r2y1 = r2y0 + pillH;
            x = margin;

            btnSensXDownRect.set(x, r2y0, x + squarePillW, r2y1);
            x += squarePillW + gap * 0.3f;
            sensXLabelRect.set(x, r2y0, x + pillW * 1.3f, r2y1);
            x += pillW * 1.3f + gap * 0.3f;
            btnSensXUpRect.set(x, r2y0, x + squarePillW, r2y1);
            x += squarePillW + gap * 2f;

            btnSensYDownRect.set(x, r2y0, x + squarePillW, r2y1);
            x += squarePillW + gap * 0.3f;
            sensYLabelRect.set(x, r2y0, x + pillW * 1.3f, r2y1);
            x += pillW * 1.3f + gap * 0.3f;
            btnSensYUpRect.set(x, r2y0, x + squarePillW, r2y1);

            drawPill(canvas, btnSaveRect,  0xFF3a8a3a, "SAVE");
            drawPill(canvas, btnResetRect, 0xFF444444, "RESET");
            drawPill(canvas, btnCancelRect, 0xFF8a3a3a, "CANCEL");

            drawPill(canvas, btnSensXDownRect, 0xFF444444, "-");
            drawPill(canvas, sensXLabelRect, 0xFF222228,
                    String.format("X %.2f", layout.lookSensX));
            drawPill(canvas, btnSensXUpRect, 0xFF444444, "+");

            drawPill(canvas, btnSensYDownRect, 0xFF444444, "-");
            drawPill(canvas, sensYLabelRect, 0xFF222228,
                    String.format("Y %.2f", layout.lookSensY));
            drawPill(canvas, btnSensYUpRect, 0xFF444444, "+");

            // Collapse button last so it sits on top of the bar row.
            drawPill(canvas, btnCollapseRect, 0xAA202028, "▲");

            // Disable the live-edit pill while editing to avoid visual clutter.
            editPillRect.setEmpty();

            drawButtonResizeBar(canvas, w, h, m);
        } else {
            // Floating "EDIT" pill in the top-right corner.
            float x0 = w - margin - pillW;
            float y0 = margin;
            editPillRect.set(x0, y0, x0 + pillW, y0 + pillH);
            drawPill(canvas, editPillRect, 0xAA202028, "EDIT");
        }
    }

    /**
     * Resize bar for the currently selected button: "[ - ] [ r=0.055 ] [ + ]".
     * Positioned just below the button, or just above if that would run
     * off-screen.
     */
    private void drawButtonResizeBar(Canvas canvas, int w, int h, int m) {
        if (selectedButtonId == null) {
            btnSizeDownRect.setEmpty();
            btnSizeLabelRect.setEmpty();
            btnSizeUpRect.setEmpty();
            return;
        }
        TouchLayout.Element el = findById(selectedButtonId);
        if (el == null || el.kind != TouchLayout.Kind.BUTTON) {
            btnSizeDownRect.setEmpty();
            btnSizeLabelRect.setEmpty();
            btnSizeUpRect.setEmpty();
            return;
        }
        float dp = m / 400f;
        float pillH = 44 * dp;
        float squareW = pillH;
        float labelW = 110 * dp;
        float gap = 6 * dp;
        float barW = squareW * 2 + labelW + gap * 2;

        float cx = el.cx * w, cy = el.cy * h, r = el.radius * m;

        float x0 = cx - barW / 2f;
        if (x0 < 4 * dp) x0 = 4 * dp;
        if (x0 + barW > w - 4 * dp) x0 = w - 4 * dp - barW;

        float y0 = cy + r + gap * 1.4f;
        if (y0 + pillH > h - 4 * dp) {
            y0 = cy - r - gap * 1.4f - pillH;
        }

        float x = x0;
        btnSizeDownRect.set(x, y0, x + squareW, y0 + pillH);
        x += squareW + gap;
        btnSizeLabelRect.set(x, y0, x + labelW, y0 + pillH);
        x += labelW + gap;
        btnSizeUpRect.set(x, y0, x + squareW, y0 + pillH);

        drawPill(canvas, btnSizeDownRect, 0xDD2a2a32, "−");
        drawPill(canvas, btnSizeLabelRect, 0xDD111116,
                String.format("r %.3f", el.radius));
        drawPill(canvas, btnSizeUpRect, 0xDD2a2a32, "+");
    }

    private void drawPill(Canvas canvas, RectF rect, int argb, String label) {
        paintEditPillFill.setColor(argb);
        float r = rect.height() * 0.5f;
        canvas.drawRoundRect(rect, r, r, paintEditPillFill);
        paintEditPillText.setTextSize(rect.height() * 0.45f);
        canvas.drawText(label, rect.centerX(),
                rect.centerY() + paintEditPillText.getTextSize() * 0.35f,
                paintEditPillText);
    }

    private native void nativeSetState(float lx, float ly, float rx, float ry,
                                       int buttons, boolean anyDown);
    private native void nativeAddLookDelta(int dx, int dy);
}
