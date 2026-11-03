package io.github.arcanearmorcollector.android;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;

import java.util.HashSet;
import java.util.Set;

/**
 * First-pass Odin / Android gamepad bridge.
 * Sends keyboard and pointer events into WebView so that Ruffle sees the
 * same inputs as the original Flash game. Does not change direct touch input.
 */
final class GamepadBridge {
    private static final String TAG = "ArcaneGamepad";
    private static final float DEADZONE = 0.20f;
    // Use hysteresis for movement: engage away from centre, release sooner.
    // This reduces ghost movement from analogue stick drift and axis noise.
    private static final float MOVE_ON = 0.38f;
    private static final float MOVE_OFF = 0.22f;
    private final WebView webView;
    final Cursor cursor;

    private final Set<Integer> pressedKeys = new HashSet<>();
    private boolean keyUp, keyDown, keyLeft, keyRight;
    private boolean stickUp, stickDown, stickLeft, stickRight;
    private boolean hatUp, hatDown, hatLeft, hatRight;
    private boolean r1, r2, triggerAxis, attacking;
    private boolean pointerInitialized, loopRunning;
    private float pointerX, pointerY, aimX, aimY;
    private long attackDownTime, lastFrameTime, lastScriptAimTime;

    GamepadBridge(Activity activity, WebView view) {
        webView = view;
        cursor = new Cursor(activity);
    }

    boolean handleKey(KeyEvent event) {
        int source = event.getSource();
        if ((source & (InputDevice.SOURCE_GAMEPAD | InputDevice.SOURCE_DPAD | InputDevice.SOURCE_JOYSTICK)) == 0) {
            return false;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN && event.getAction() != KeyEvent.ACTION_UP) return false;
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        if (down && event.getRepeatCount() > 0) return true;

        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_UP: keyUp = down; updateMovement(); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: keyDown = down; updateMovement(); return true;
            case KeyEvent.KEYCODE_DPAD_LEFT: keyLeft = down; updateMovement(); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: keyRight = down; updateMovement(); return true;
            case KeyEvent.KEYCODE_BUTTON_A: setKey(KeyEvent.KEYCODE_SPACE, down); return true;
            case KeyEvent.KEYCODE_BUTTON_B: setKey(KeyEvent.KEYCODE_B, down); return true;
            case KeyEvent.KEYCODE_BUTTON_X: setKey(KeyEvent.KEYCODE_E, down); return true;
            case KeyEvent.KEYCODE_BUTTON_Y: setKey(KeyEvent.KEYCODE_R, down); return true;
            case KeyEvent.KEYCODE_BUTTON_L1: setKey(KeyEvent.KEYCODE_T, down); return true;
            case KeyEvent.KEYCODE_BUTTON_R1: r1 = down; updateAttack(); return true;
            case KeyEvent.KEYCODE_BUTTON_R2: r2 = down; updateAttack(); return true;
            default: return false;
        }
    }

    boolean handleMotion(MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK ||
            event.getAction() != MotionEvent.ACTION_MOVE) return false;

        float leftX = event.getAxisValue(MotionEvent.AXIS_X);
        float leftY = event.getAxisValue(MotionEvent.AXIS_Y);
        stickLeft = movementActive(-leftX, stickLeft);
        stickRight = movementActive(leftX, stickRight);
        stickUp = movementActive(-leftY, stickUp);
        stickDown = movementActive(leftY, stickDown);

        float hx = event.getAxisValue(MotionEvent.AXIS_HAT_X);
        float hy = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
        hatLeft = hx < -0.5f;
        hatRight = hx > 0.5f;
        hatUp = hy < -0.5f;
        hatDown = hy > 0.5f;
        updateMovement();

        // Most Android controllers use Z/RZ for the right stick;
        // some report RX/RY instead.
        float zx = event.getAxisValue(MotionEvent.AXIS_Z);
        float zy = event.getAxisValue(MotionEvent.AXIS_RZ);
        float rx = event.getAxisValue(MotionEvent.AXIS_RX);
        float ry = event.getAxisValue(MotionEvent.AXIS_RY);
        if (Math.abs(zx) + Math.abs(zy) > 0.01f || Math.abs(rx) + Math.abs(ry) <= 0.01f) {
            aimX = deadzone(zx);
            aimY = deadzone(zy);
        } else {
            aimX = deadzone(rx);
            aimY = deadzone(ry);
        }
        if ((aimX != 0 || aimY != 0) && !loopRunning) {
            loopRunning = true;
            lastFrameTime = SystemClock.uptimeMillis();
            webView.postOnAnimation(aimLoop);
        }

        triggerAxis = Math.max(event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
            event.getAxisValue(MotionEvent.AXIS_GAS)) > 0.5f;
        updateAttack();
        return true;
    }

    private static float deadzone(float value) {
        return Math.abs(value) <= DEADZONE ? 0 : value;
    }

    private static boolean movementActive(float value, boolean alreadyHeld) {
        return value > (alreadyHeld ? MOVE_OFF : MOVE_ON);
    }

    private void updateMovement() {
        boolean up = keyUp || stickUp || hatUp;
        boolean down = keyDown || stickDown || hatDown;
        boolean left = keyLeft || stickLeft || hatLeft;
        boolean right = keyRight || stickRight || hatRight;
        // Release old directions BEFORE pressing replacements so the Flash
        // game cannot briefly see contradictory movement keys held.
        if (!up) setKey(KeyEvent.KEYCODE_W, false);
        if (!down) setKey(KeyEvent.KEYCODE_S, false);
        if (!left) setKey(KeyEvent.KEYCODE_A, false);
        if (!right) setKey(KeyEvent.KEYCODE_D, false);
        if (up) setKey(KeyEvent.KEYCODE_W, true);
        if (down) setKey(KeyEvent.KEYCODE_S, true);
        if (left) setKey(KeyEvent.KEYCODE_A, true);
        if (right) setKey(KeyEvent.KEYCODE_D, true);
    }

    private void setKey(int code, boolean down) {
        if (pressedKeys.contains(code) == down) return;
        if (down) pressedKeys.add(code);
        else pressedKeys.remove(code);
        if (!webView.hasFocus()) webView.requestFocus();
        long time = SystemClock.uptimeMillis();
        KeyEvent translated = new KeyEvent(time, time,
            down ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP,
            code, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0,
            InputDevice.SOURCE_KEYBOARD);
        webView.dispatchKeyEvent(translated);
        Log.d(TAG, "key " + code + (down ? " down" : " up"));
    }

    private void initializePointer() {
        if (pointerInitialized || webView.getWidth() == 0 || webView.getHeight() == 0) return;
        pointerX = webView.getWidth() / 2f;
        pointerY = webView.getHeight() / 2f;
        pointerInitialized = true;
        cursor.visible = true;
        cursor.invalidate();
    }

    private void clampPointer() {
        float vw = webView.getWidth(), vh = webView.getHeight();
        float stageW = Math.min(vw, vh * 4f / 3f);
        float stageH = Math.min(vh, vw * 3f / 4f);
        pointerX = Math.max((vw - stageW) / 2f + 1,
            Math.min((vw + stageW) / 2f - 1, pointerX));
        pointerY = Math.max((vh - stageH) / 2f + 1,
            Math.min((vh + stageH) / 2f - 1, pointerY));
        cursor.x = pointerX;
        cursor.y = pointerY;
        cursor.invalidate();
    }

    private void sendPointer(int action) {
        initializePointer();
        if (!pointerInitialized) return;
        long now = SystemClock.uptimeMillis();
        if (action == MotionEvent.ACTION_DOWN) {
            attackDownTime = now;
            Log.d(TAG, "native-attack-down webview=(" + webView.getWidth() + "," +
                webView.getHeight() + ") point=(" + pointerX + "," + pointerY + ")");
        }
        MotionEvent event = MotionEvent.obtain(
            attackDownTime, now, action, pointerX, pointerY, 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        webView.dispatchTouchEvent(event);
        event.recycle();
    }

    private void sendHover() {
        if (!pointerInitialized) return;
        long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, MotionEvent.ACTION_HOVER_MOVE,
            pointerX, pointerY, 0);
        event.setSource(InputDevice.SOURCE_MOUSE);
        webView.dispatchGenericMotionEvent(event);
        event.recycle();
    }

    private void updateAttack() {
        boolean desired = r1 || r2 || triggerAxis;
        if (desired == attacking) return;
        attacking = desired;
        sendPointer(desired ? MotionEvent.ACTION_DOWN : MotionEvent.ACTION_UP);
    }

    private final Runnable aimLoop = new Runnable() {
        @Override public void run() {
            if (!loopRunning) return;
            if (aimX == 0 && aimY == 0) {
                loopRunning = false;
                return;
            }
            initializePointer();
            if (pointerInitialized) {
                long now = SystemClock.uptimeMillis();
                float dt = Math.min((now - lastFrameTime) / 1000f, 0.05f);
                lastFrameTime = now;
                float speed = Math.min(webView.getWidth(), webView.getHeight() * 4f / 3f) * 0.9f;
                pointerX += aimX * speed * dt;
                pointerY += aimY * speed * dt;
                clampPointer();
                if (attacking) {
                    sendPointer(MotionEvent.ACTION_MOVE);
                } else {
                    // Android WebView's native hover may move our cursor while
                    // Ruffle misses the actual mousemove event. Inject a
                    // pointermove directly into the player as well.
                    sendHover();
                    if (now - lastScriptAimTime >= 32L) {
                        lastScriptAimTime = now;
                        float nx = pointerX / Math.max(1, webView.getWidth());
                        float ny = pointerY / Math.max(1, webView.getHeight());
                        webView.evaluateJavascript(
                            "if(window.arcaneControllerAim)window.arcaneControllerAim(" +
                            nx + "," + ny + ");", null);
                    }
                }
            }
            webView.postOnAnimation(this);
        }
    };

    void release() {
        aimX = aimY = 0;
        loopRunning = false;
        lastScriptAimTime = 0;
        webView.removeCallbacks(aimLoop);
        keyUp = keyDown = keyLeft = keyRight = false;
        stickUp = stickDown = stickLeft = stickRight = false;
        hatUp = hatDown = hatLeft = hatRight = false;
        r1 = r2 = triggerAxis = false;
        if (attacking) {
            attacking = false;
            sendPointer(MotionEvent.ACTION_UP);
        }
        for (int code : new HashSet<>(pressedKeys)) setKey(code, false);
    }

    final class Cursor extends View {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boolean visible = false;
        float x, y;

        Cursor(Activity activity) { super(activity); setWillNotDraw(false); }

        @Override protected void onDraw(Canvas c) {
            if (!visible) return;
            paint.setColor(Color.BLACK);
            paint.setStrokeWidth(5f);
            c.drawLine(x - 15, y, x + 15, y, paint);
            c.drawLine(x, y - 15, x, y + 15, paint);
            paint.setColor(Color.WHITE);
            paint.setStrokeWidth(2.5f);
            c.drawLine(x - 15, y, x + 15, y, paint);
            c.drawLine(x, y - 15, x, y + 15, paint);
        }
    }
}
