package io.github.arcanearmorcollector.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

/**
 * FrostFrontend touchscreen controls. User-selectable joystick or digital
 * D-pad movement plus full-screen invisible relative trackpad aim/attack,
 * or direct game-screen taps. Multi-touch splits movement and actions.
 */
final class TouchControlsOverlay extends FrameLayout {
    private final GamepadBridge bridge;
    private final int gold = 0xFFFFDF9F;
    private final int border = 0xFFBD9765;
    private final int background = 0xC8272032;
    private float scale = 1f;
    private boolean swapped;
    private boolean useDpad, relativeAim = true;
    private int insetDp = 18;

    private AxisPad movePad;
    private DirectionPad directionPad;
    private AimSurface aimSurface;
    private ActionButton[] buttons;

    TouchControlsOverlay(Context context, GamepadBridge bridge) {
        super(context);
        this.bridge = bridge;
        setClipChildren(false);
        setClipToPadding(false);
        setFocusable(false);
        setClickable(false);
        setMotionEventSplittingEnabled(true);
        rebuild();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int sz(float size) {
        return dp(size * scale);
    }

    void configure(float size, float opacity, boolean swap, int inset,
                   boolean dpad, boolean aimRelative) {
        size = Math.max(0.7f, Math.min(1.3f, size));
        opacity = Math.max(0.2f, Math.min(0.95f, opacity));
        inset = Math.max(6, Math.min(64, inset));
        boolean changed = scale != size || swapped != swap || insetDp != inset ||
            useDpad != dpad || relativeAim != aimRelative;
        if (changed) {
            releaseAll();
            scale = size;
            swapped = swap;
            insetDp = inset;
            useDpad = dpad;
            relativeAim = aimRelative;
            rebuild();
        }
        setAlpha(opacity);
    }

    @Override public void setVisibility(int visibility) {
        if (visibility != View.VISIBLE) releaseAll();
        super.setVisibility(visibility);
    }

    void releaseAll() {
        if (movePad != null) movePad.reset();
        if (directionPad != null) directionPad.reset();
        if (aimSurface != null) aimSurface.reset();
        if (buttons != null) {
            for (ActionButton button : buttons) if (button != null) button.reset();
        }
        bridge.releaseTouchControls();
    }

    private void position(View view, int width, int height, int side, int bottom) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
            width, height, Gravity.BOTTOM | (side == 0 ? Gravity.LEFT : Gravity.RIGHT));
        lp.bottomMargin = bottom;
        if (side == 0) lp.leftMargin = dp(insetDp);
        else lp.rightMargin = dp(insetDp);
        addView(view, lp);
    }

    private void positionButton(ActionButton button, int side, int horizontalOffset,
                                int bottom, int diameter) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
            sz(diameter), sz(diameter),
            Gravity.BOTTOM | (side == 0 ? Gravity.LEFT : Gravity.RIGHT));
        lp.bottomMargin = dp(bottom);
        if (side == 0) lp.leftMargin = dp(insetDp) + sz(horizontalOffset);
        else lp.rightMargin = dp(insetDp) + sz(horizontalOffset);
        addView(button, lp);
    }

    private void rebuild() {
        removeAllViews();
        int moveSide = swapped ? 1 : 0;
        int actionSide = swapped ? 0 : 1;
        int diameter = sz(134);
        // Both aim modes use the same full-screen input layer behind
        // movement/action views. Routing Direct-mode taps through this layer
        // keeps the two fingers in ONE multi-touch ViewGroup: the movement
        // pad can remain held while the other finger aims and attacks.
        // Relative keeps a fixed crosshair origin; Direct uses screen coords.
        aimSurface = new AimSurface(getContext());
        addView(aimSurface, new FrameLayout.LayoutParams(-1, -1));
        if (useDpad) {
            movePad = null;
            directionPad = new DirectionPad(getContext());
            position(directionPad, diameter, diameter, moveSide, dp(16));
        } else {
            directionPad = null;
            movePad = new AxisPad(getContext());
            position(movePad, diameter, diameter, moveSide, dp(16));
        }

        // Attack is performed within the aim touch zone, not via a separate
        // button. Direct mode uses unmodified Ruffle on-screen tapping.
        String[] names = {"WEAPON", "ARMOUR", "THROW", "PREV", "NEXT"};
        buttons = new ActionButton[names.length];
        for (int i = 0; i < names.length; i++) {
            buttons[i] = new ActionButton(getContext(), i + 2, names[i]);
            positionButton(buttons[i], actionSide, i * 50, 16, 44);
        }
    }

    private final class AimSurface extends View {
        // The Flash game does not expose its inventory/arena state to the
        // Android wrapper. In user-selected Relative mode, treat a short tap
        // as a direct menu click while a swipe or hold operates the existing
        // relative crosshair. This avoids relying on an unreliable timer
        // guessing when an arena begins.
        private static final long HOLD_TO_RELATIVE_MS = 260L;
        private int activePointer = -1;
        private float lastX, lastY, downX, downY;
        private boolean relativeGestureStarted;
        private final Runnable holdToRelative = () -> {
            if (activePointer != -1 && relativeAim && !relativeGestureStarted) {
                relativeGestureStarted = true;
                bridge.beginTouchTrackpad();
            }
        };

        AimSurface(Context context) {
            super(context);
            setClickable(true);
            setContentDescription(relativeAim ?
                "Tap to select menus; swipe or hold to aim and fire" :
                "Tap or drag to aim directly and fire");
        }

        void reset() {
            removeCallbacks(holdToRelative);
            if (activePointer == -1) return;
            activePointer = -1;
            if (relativeAim) {
                if (relativeGestureStarted) bridge.endTouchTrackpad();
            } else {
                bridge.endTouchDirect();
            }
            relativeGestureStarted = false;
        }

        private void finish(boolean commitTap, float x, float y) {
            removeCallbacks(holdToRelative);
            if (activePointer < 0) return;
            if (relativeAim) {
                if (relativeGestureStarted) bridge.endTouchTrackpad();
                else if (commitTap) bridge.tapTouchDirect(x, y);
            } else {
                bridge.endTouchDirect();
            }
            activePointer = -1;
            relativeGestureStarted = false;
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    activePointer = event.getPointerId(0);
                    downX = lastX = event.getX(0);
                    downY = lastY = event.getY(0);
                    relativeGestureStarted = false;
                    if (relativeAim) postDelayed(holdToRelative, HOLD_TO_RELATIVE_MS);
                    else bridge.beginTouchDirect(lastX, lastY);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (activePointer >= 0) {
                        int index = event.findPointerIndex(activePointer);
                        if (index >= 0) {
                            float x = event.getX(index), y = event.getY(index);
                            if (relativeAim) {
                                if (!relativeGestureStarted &&
                                    Math.hypot(x - downX, y - downY) >= dp(11)) {
                                    removeCallbacks(holdToRelative);
                                    relativeGestureStarted = true;
                                    bridge.beginTouchTrackpad();
                                }
                                if (relativeGestureStarted)
                                    bridge.moveTouchTrackpad(x - lastX, y - lastY);
                            } else {
                                bridge.moveTouchDirect(x, y);
                            }
                            lastX = x;
                            lastY = y;
                        }
                    }
                    return true;
                case MotionEvent.ACTION_POINTER_UP:
                    if (activePointer == event.getPointerId(event.getActionIndex())) {
                        int index = event.getActionIndex();
                        finish(true, event.getX(index), event.getY(index));
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    finish(true, event.getX(0), event.getY(0));
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    reset();
                    return true;
                default:
                    return true;
            }
        }
    }

    private final class DirectionPad extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int activePointer = -1;
        private boolean up, down, left, right;

        DirectionPad(Context context) {
            super(context);
            setClickable(true);
            setContentDescription("Digital movement D-pad");
        }

        void reset() {
            if (activePointer < 0 && !up && !down && !left && !right) return;
            activePointer = -1;
            up = down = left = right = false;
            bridge.setTouchDpad(false, false, false, false);
            invalidate();
        }

        private void change(float x, float y) {
            float dx = (x - getWidth()*0.5f) / Math.max(1f, getWidth()*0.5f);
            float dy = (y - getHeight()*0.5f) / Math.max(1f, getHeight()*0.5f);
            float dominant = Math.max(Math.abs(dx), Math.abs(dy));
            boolean h = Math.abs(dx) >= Math.abs(dy);
            boolean v = Math.abs(dy) >= Math.abs(dx);
            if (dominant < 0.21f) {
                h = v = false;
            } else if (Math.min(Math.abs(dx), Math.abs(dy)) > dominant*0.55f) {
                h = v = true;
            }
            left = h && dx < 0f;
            right = h && dx > 0f;
            up = v && dy < 0f;
            down = v && dy > 0f;
            bridge.setTouchDpad(up, down, left, right);
            invalidate();
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    activePointer = event.getPointerId(0);
                    change(event.getX(0), event.getY(0));
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (activePointer >= 0) {
                        int index = event.findPointerIndex(activePointer);
                        if (index >= 0) change(event.getX(index), event.getY(index));
                    }
                    return true;
                case MotionEvent.ACTION_POINTER_UP:
                    if (activePointer == event.getPointerId(event.getActionIndex()))
                        reset();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    reset();
                    return true;
                default: return true;
            }
        }

        private void drawDirection(Canvas canvas, float x, float y,
                                   float size, String arrow, boolean pressed) {
            float half = size * 0.5f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(pressed ? 0xFF80603A : background);
            canvas.drawRoundRect(x - half, y - half, x + half, y + half,
                dp(9), dp(9), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(border);
            canvas.drawRoundRect(x - half, y - half, x + half, y + half,
                dp(9), dp(9), paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(gold);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dp(19));
            canvas.drawText(arrow, x,
                y - (paint.ascent() + paint.descent()) * 0.5f, paint);
        }

        @Override protected void onDraw(Canvas canvas) {
            // Four independent square buttons with visible gaps, rather
            // than the overlapping cross that looked distorted on Android.
            float cx = getWidth() * 0.5f, cy = getHeight() * 0.5f;
            float half = Math.min(cx, cy);
            float size = Math.max(dp(25), half * 0.56f);
            float offset = size + dp(8);
            drawDirection(canvas, cx, cy - offset, size, "↑", up);
            drawDirection(canvas, cx, cy + offset, size, "↓", down);
            drawDirection(canvas, cx - offset, cy, size, "←", left);
            drawDirection(canvas, cx + offset, cy, size, "→", right);
        }
    }

    private final class AxisPad extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int pointerId = -1;
        private float dx, dy;

        AxisPad(Context context) {
            super(context);
            setClickable(true);
            setContentDescription("Touch movement joystick");
        }

        void reset() {
            if (pointerId < 0 && dx == 0f && dy == 0f) return;
            pointerId = -1;
            dx = dy = 0;
            bridge.setTouchMovement(0, 0);
            invalidate();
        }

        private void update(MotionEvent event, int index) {
            float cx = getWidth() * 0.5f, cy = getHeight() * 0.5f;
            float radius = Math.max(1f, Math.min(cx, cy) - dp(12));
            dx = Math.max(-1f, Math.min(1f, (event.getX(index) - cx) / radius));
            dy = Math.max(-1f, Math.min(1f, (event.getY(index) - cy) / radius));
            float length = (float) Math.hypot(dx, dy);
            if (length > 1f) { dx /= length; dy /= length; }
            bridge.setTouchMovement(dx, dy);
            invalidate();
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    pointerId = event.getPointerId(0);
                    update(event, 0);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (pointerId >= 0) {
                        int index = event.findPointerIndex(pointerId);
                        if (index >= 0) update(event, index);
                    }
                    return true;
                case MotionEvent.ACTION_POINTER_UP:
                    if (pointerId == event.getPointerId(event.getActionIndex())) reset();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    reset();
                    return true;
                default:
                    return true;
            }
        }

        @Override protected void onDraw(Canvas c) {
            float cx = getWidth() * 0.5f, cy = getHeight() * 0.5f;
            float r = Math.min(cx, cy) - dp(4);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(background);
            c.drawCircle(cx, cy, r, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(border);
            c.drawCircle(cx, cy, r, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x99F5D5A1);
            c.drawCircle(cx + dx * r * 0.65f, cy + dy * r * 0.65f, r * 0.32f, paint);
            paint.setColor(gold);
            paint.setTextSize(dp(11));
            paint.setTextAlign(Paint.Align.CENTER);
            c.drawText("MOVE", cx, cy + r * 0.73f, paint);
        }
    }

    private final class ActionButton extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int action;
        private final String label;
        private int pointerId = -1;

        ActionButton(Context context, int action, String label) {
            super(context);
            this.action = action;
            this.label = label;
            setClickable(true);
            setContentDescription(label + " action");
        }

        void reset() {
            if (pointerId >= 0) {
                pointerId = -1;
                bridge.setTouchAction(action, false);
                invalidate();
            }
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    pointerId = event.getPointerId(0);
                    bridge.setTouchAction(action, true);
                    invalidate();
                    return true;
                case MotionEvent.ACTION_POINTER_UP:
                    if (pointerId == event.getPointerId(event.getActionIndex())) reset();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    reset();
                    return true;
                default:
                    return true;
            }
        }

        @Override protected void onDraw(Canvas c) {
            float cx = getWidth() * 0.5f, cy = getHeight() * 0.5f;
            float radius = Math.min(cx, cy) - dp(2);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(pointerId >= 0 ? 0xFF80603A : background);
            c.drawCircle(cx, cy, radius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(border);
            paint.setStrokeWidth(dp(2));
            c.drawCircle(cx, cy, radius, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dp(label.length() >= 6 ? 9 : 11));
            paint.setColor(gold);
            c.drawText(label, cx, cy - (paint.ascent() + paint.descent()) * 0.5f, paint);
        }
    }
}
