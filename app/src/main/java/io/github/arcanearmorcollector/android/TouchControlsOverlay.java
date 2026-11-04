package io.github.arcanearmorcollector.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

/**
 * FrostFrontend touchscreen controls. A single movement joystick and five
 * secondary action buttons leave the game's screen available for direct
 * tap-to-aim-and-attack and drag-to-aim-while-attacking input.
 * Touches outside these views fall through to the WebView's normal Ruffle input.
 */
final class TouchControlsOverlay extends FrameLayout {
    private final GamepadBridge bridge;
    private final int gold = 0xFFFFDF9F;
    private final int border = 0xFFBD9765;
    private final int background = 0xC8272032;
    private float scale = 1f;
    private boolean swapped;
    private int insetDp = 18;

    private AxisPad movePad;
    private ActionButton[] buttons;

    TouchControlsOverlay(Context context, GamepadBridge bridge) {
        super(context);
        this.bridge = bridge;
        setClipChildren(false);
        setClipToPadding(false);
        setFocusable(false);
        setClickable(false);
        rebuild();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int sz(float size) {
        return dp(size * scale);
    }

    void configure(float size, float opacity, boolean swap, int inset) {
        size = Math.max(0.7f, Math.min(1.3f, size));
        opacity = Math.max(0.2f, Math.min(0.95f, opacity));
        inset = Math.max(6, Math.min(64, inset));
        boolean changed = scale != size || swapped != swap || insetDp != inset;
        if (changed) {
            releaseAll();
            scale = size;
            swapped = swap;
            insetDp = inset;
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
        movePad = new AxisPad(getContext());
        int diameter = sz(134);
        position(movePad, diameter, diameter, moveSide, dp(16));

        // No right joystick or separate attack buttons: tapping/dragging
        // the game screen aims and attacks using Ruffle's own touch input.
        String[] names = {"WEAPON", "ARMOUR", "THROW", "PREV", "NEXT"};
        buttons = new ActionButton[names.length];
        for (int i = 0; i < names.length; i++) {
            buttons[i] = new ActionButton(getContext(), i + 2, names[i]);
            positionButton(buttons[i], actionSide, i * 50, 16, 44);
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
