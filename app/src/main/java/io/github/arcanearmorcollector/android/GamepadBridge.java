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
    // Movement is translated to Arcane's fixed WASD directions. D-pad
    // remains digital; only physical/virtual analogue movement uses this.
    private int movementMode = 0; // 0 = 8-way, 1 = 4-way
    private float moveThreshold = MOVE_ON;
    private boolean invertMoveX, invertMoveY;
    // Re-send a neutral key-up after the WebView has had time to process a
    // scene change or touch. This recovers a key release lost by Ruffle.
    private static final long NEUTRAL_KEY_RECOVERY_MS = 55L;
    private static final int NEUTRAL_RECOVERY_PASSES = 4;
    private final WebView webView;
    static final String[] ACTION_NAMES = {
        "Attack", "Alternate Attack", "Switch Weapon", "Swap Armour",
        "Throw", "Previous Throwable", "Next Throwable"
    };
    static final int UNMAPPED = -1;
    private static final int[] DEFAULT_BUTTONS = {
        KeyEvent.KEYCODE_BUTTON_R2, KeyEvent.KEYCODE_BUTTON_R1,
        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B,
        KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y,
        KeyEvent.KEYCODE_BUTTON_L1
    };
    private final int[] bindings = DEFAULT_BUTTONS.clone();
    private final Set<Integer> hardwareButtonsDown = new HashSet<>();
    private boolean rightTriggerAxisDown, leftTriggerAxisDown;
    private boolean invertAimY;
    private float aimSensitivity = 0.9f;
    final Cursor cursor;

    private final Set<Integer> pressedKeys = new HashSet<>();
    private boolean keyUp, keyDown, keyLeft, keyRight;
    private boolean touchUp, touchDown, touchLeft, touchRight;
    // The virtual D-pad sends discrete directions without analogue tuning.
    private boolean touchDpadUp, touchDpadDown, touchDpadLeft, touchDpadRight;
    private boolean touchTrackpadAttacking;
    private final boolean[] touchActionsDown = new boolean[ACTION_NAMES.length];
    private float physicalAimX, physicalAimY, touchAimX, touchAimY;
    private boolean touchAimActive;
    private boolean stickUp, stickDown, stickLeft, stickRight;
    private boolean hatUp, hatDown, hatLeft, hatRight;
    private boolean attacking;
    private boolean pointerInitialized, loopRunning, neutralRecoveryQueued, wasMoving;
    private int lastMovementMask, neutralRecoveryPass;
    private boolean nativeCursorEnabled = true;
    private boolean widescreenDisplay;
    private float pointerX, pointerY, aimX, aimY;
    private long attackDownTime, lastFrameTime, lastScriptAimTime;

    GamepadBridge(Activity activity, WebView view) {
        webView = view;
        cursor = new Cursor(activity);
    }

    static boolean canBindButton(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_BUTTON_A: case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_BUTTON_X: case KeyEvent.KEYCODE_BUTTON_Y:
            case KeyEvent.KEYCODE_BUTTON_L1: case KeyEvent.KEYCODE_BUTTON_R1:
            case KeyEvent.KEYCODE_BUTTON_L2: case KeyEvent.KEYCODE_BUTTON_R2:
            case KeyEvent.KEYCODE_BUTTON_THUMBL: case KeyEvent.KEYCODE_BUTTON_THUMBR:
                return true;
            default: return false;
        }
    }

    static String buttonName(int code) {
        switch (code) {
            case UNMAPPED: return "Unmapped";
            case KeyEvent.KEYCODE_BUTTON_A: return "A";
            case KeyEvent.KEYCODE_BUTTON_B: return "B";
            case KeyEvent.KEYCODE_BUTTON_X: return "X";
            case KeyEvent.KEYCODE_BUTTON_Y: return "Y";
            case KeyEvent.KEYCODE_BUTTON_L1: return "L1";
            case KeyEvent.KEYCODE_BUTTON_R1: return "R1";
            case KeyEvent.KEYCODE_BUTTON_L2: return "L2";
            case KeyEvent.KEYCODE_BUTTON_R2: return "R2";
            case KeyEvent.KEYCODE_BUTTON_THUMBL: return "L3";
            case KeyEvent.KEYCODE_BUTTON_THUMBR: return "R3";
            default: return "Unknown";
        }
    }

    static int defaultButton(int action) { return DEFAULT_BUTTONS[action]; }
    int getButton(int action) { return bindings[action]; }

    void setButton(int action, int code) {
        if (action < 0 || action >= bindings.length ||
            (code != UNMAPPED && !canBindButton(code))) return;
        if (bindings[action] == code) return;
        // Taking an assigned button leaves the old action unmapped.
        // Never silently swap the displaced action onto another button.
        if (code != UNMAPPED) {
            for (int i = 0; i < bindings.length; i++) {
                if (i != action && bindings[i] == code)
                    bindings[i] = UNMAPPED;
            }
        }
        bindings[action] = code;
        release();
    }

    void resetButtons() {
        System.arraycopy(DEFAULT_BUTTONS, 0, bindings, 0, DEFAULT_BUTTONS.length);
        release();
    }

    void setAimOptions(boolean invert, float sensitivity) {
        invertAimY = invert;
        aimSensitivity = Math.max(0.4f, Math.min(2.0f, sensitivity));
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
            default:
                if (!canBindButton(event.getKeyCode())) return false;
                if (down) hardwareButtonsDown.add(event.getKeyCode());
                else hardwareButtonsDown.remove(event.getKeyCode());
                updateMappedButtons();
                return true;
        }
    }

    boolean handleMotion(MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK ||
            event.getAction() != MotionEvent.ACTION_MOVE) return false;

        float leftX = event.getAxisValue(MotionEvent.AXIS_X);
        float leftY = event.getAxisValue(MotionEvent.AXIS_Y);
        boolean priorStickUp = stickUp, priorStickDown = stickDown;
        boolean priorStickLeft = stickLeft, priorStickRight = stickRight;
        boolean priorHatUp = hatUp, priorHatDown = hatDown;
        boolean priorHatLeft = hatLeft, priorHatRight = hatRight;
        applyStickDirection(leftX, leftY, false);

        float hx = event.getAxisValue(MotionEvent.AXIS_HAT_X);
        float hy = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
        hatLeft = hx < -0.5f;
        hatRight = hx > 0.5f;
        hatUp = hy < -0.5f;
        hatDown = hy > 0.5f;
        if (priorStickUp != stickUp || priorStickDown != stickDown ||
            priorStickLeft != stickLeft || priorStickRight != stickRight ||
            priorHatUp != hatUp || priorHatDown != hatDown ||
            priorHatLeft != hatLeft || priorHatRight != hatRight) {
            Log.d(TAG, "movement-axis x=" + leftX + " y=" + leftY +
                " hatX=" + hx + " hatY=" + hy +
                " stick[U,D,L,R]=" + stickUp + "," + stickDown + "," +
                stickLeft + "," + stickRight + " hat=" +
                hatUp + "," + hatDown + "," + hatLeft + "," + hatRight);
        }
        updateMovement();

        // Most Android controllers use Z/RZ for the right stick;
        // some report RX/RY instead.
        float zx = event.getAxisValue(MotionEvent.AXIS_Z);
        float zy = event.getAxisValue(MotionEvent.AXIS_RZ);
        float rx = event.getAxisValue(MotionEvent.AXIS_RX);
        float ry = event.getAxisValue(MotionEvent.AXIS_RY);
        if (Math.abs(zx) + Math.abs(zy) > 0.01f || Math.abs(rx) + Math.abs(ry) <= 0.01f) {
            physicalAimX = deadzone(zx);
            physicalAimY = deadzone(zy);
        } else {
            physicalAimX = deadzone(rx);
            physicalAimY = deadzone(ry);
        }
        updateAimFromSources();

        rightTriggerAxisDown = Math.max(event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
            event.getAxisValue(MotionEvent.AXIS_GAS)) > 0.5f;
        leftTriggerAxisDown = Math.max(event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
            event.getAxisValue(MotionEvent.AXIS_BRAKE)) > 0.5f;
        updateMappedButtons();
        return true;
    }

    private static float deadzone(float value) {
        return Math.abs(value) <= DEADZONE ? 0 : value;
    }

    void setMovementOptions(int mode, float threshold, boolean invertX, boolean invertY) {
        movementMode = Math.max(0, Math.min(1, mode));
        moveThreshold = Math.max(0.27f, Math.min(0.55f, threshold));
        invertMoveX = invertX;
        invertMoveY = invertY;
        // Clear old axis state when controls change. Never synthesize a
        // movement key-down while the user is inside the options overlay.
        stickUp = stickDown = stickLeft = stickRight = false;
        touchUp = touchDown = touchLeft = touchRight = false;
        updateMovement();
    }

    void setTouchMovement(float x, float y) {
        applyStickDirection(x, y, true);
        updateMovement();
    }

    void setTouchDpad(boolean up, boolean down, boolean left, boolean right) {
        if (touchDpadUp == up && touchDpadDown == down &&
            touchDpadLeft == left && touchDpadRight == right) return;
        touchDpadUp = up;
        touchDpadDown = down;
        touchDpadLeft = left;
        touchDpadRight = right;
        updateMovement();
    }

    // Relative touch aiming is like a laptop trackpad. The first touch
    // doesn't warp the cursor to the finger's location: each subsequent
    // move changes the existing crosshair position.
    void beginTouchTrackpad() {
        initializePointer();
        if (!pointerInitialized) return;
        touchAimActive = true;
        touchAimX = touchAimY = 0f;
        updateAimFromSources();
        touchTrackpadAttacking = true;
        updateMappedButtons();
    }

    // In Direct mode, aim and attack use the actual second-finger position.
    // This goes through the same multi-touch overlay as the virtual movement
    // pad; dispatching a native tap only from the WebView used to lose the
    // other finger's movement gesture.
    void beginTouchDirect(float x, float y) {
        initializePointer();
        if (!pointerInitialized) return;
        pointerX = x;
        pointerY = y;
        clampPointer();
        touchAimActive = true;
        touchAimX = touchAimY = 0f;
        updateAimFromSources();
        touchTrackpadAttacking = true;
        updateMappedButtons();
    }

    void tapTouchRelative() {
        initializePointer();
        if (!pointerInitialized || touchTrackpadAttacking || attacking) return;
        // A short arena tap fires exactly at the existing relative crosshair.
        // Never route this gesture through Direct touch coordinates: doing so
        // makes the character face whichever part of the screen was tapped.
        touchTrackpadAttacking = true;
        updateMappedButtons();
        touchTrackpadAttacking = false;
        updateMappedButtons();
    }

    // Keep the arena Relative except for clickable game HUD chrome.
    // Positions below come from the original SWF's 640x480 HUD sprite:
    // top controls occupy the small upper HUD strip; the three potions
    // are centred at x=508, 552 and 596, y=443 in stage coordinates.
    // Compute stage-relative position so both 4:3 and Stretch work.
    boolean isCombatHudTouch(float x, float y) {
        float vw = webView.getWidth(), vh = webView.getHeight();
        if (vw <= 0f || vh <= 0f) return false;
        float stageW = widescreenDisplay ? vw : Math.min(vw, vh * 4f / 3f);
        float stageH = widescreenDisplay ? vh : Math.min(vh, vw * 3f / 4f);
        float gx = (x - (vw - stageW) * 0.5f) * 640f / stageW;
        float gy = (y - (vh - stageH) * 0.5f) * 480f / stageH;
        if (gx < 0 || gx > 640 || gy < 0 || gy > 480) return false;
        // Exact HUD sprite origins from the user's original SWF:
        // potions: (508,443),(552,443),(596,443)
        // stats/options subclips: (205,206),(335,207). These subclips
        // contain menus, so their exact clickable child bounds can vary.
        // Retain an upper strip for the HUD's other in-arena icons.
        boolean topHud = gy <= 102f;
        boolean statOrOptions = gy >= 171f && gy <= 244f &&
            ((gx >= 169f && gx <= 241f) ||
             (gx >= 299f && gx <= 371f));
        boolean potionHud = gy >= 405f && gy <= 478f &&
            (Math.abs(gx - 508f) <= 29f ||
             Math.abs(gx - 552f) <= 29f ||
             Math.abs(gx - 596f) <= 29f);
        boolean hit = topHud || statOrOptions || potionHud;
        Log.d("ArcaneHUD", "arena touch=(" + x + "," + y +
            ") stage=(" + gx + "," + gy + ") hit=" + hit +
            " top=" + topHud + " statsOptions=" + statOrOptions +
            " potions=" + potionHud);
        return hit;
    }

    // HUD touches must be real DOWN/UP gestures. The old HUD handler
    // sent both events consecutively on finger release, which Ruffle often
    // did not register as a click. Preserve the Relative crosshair and use
    // the normal Direct touch gesture only inside recognised HUD areas.
    private boolean hudTouchActive;
    private float hudSavedX, hudSavedY;

    void beginTouchHud(float x, float y) {
        if (hudTouchActive || touchTrackpadAttacking || attacking) return;
        initializePointer();
        if (!pointerInitialized) return;
        hudSavedX = pointerX;
        hudSavedY = pointerY;
        hudTouchActive = true;
        beginTouchDirect(x, y);
    }

    void moveTouchHud(float x, float y) {
        if (hudTouchActive) moveTouchDirect(x, y);
    }

    void endTouchHud() {
        if (!hudTouchActive) return;
        hudTouchActive = false;
        endTouchDirect();
        pointerX = hudSavedX;
        pointerY = hudSavedY;
        clampPointer();
        // Restore Flash-side aim only after Ruffle has consumed the mouse-up.
        webView.postOnAnimation(() -> {
            if (hudTouchActive || touchTrackpadAttacking || attacking ||
                !pointerInitialized) return;
            sendHover();
            float nx = pointerX / Math.max(1, webView.getWidth());
            float ny = pointerY / Math.max(1, webView.getHeight());
            webView.evaluateJavascript(
                "if(window.arcaneControllerAim)window.arcaneControllerAim(" +
                nx + "," + ny + ");", null);
        });
    }

    void tapTouchDirect(float x, float y) {
        initializePointer();
        if (!pointerInitialized || attacking || touchTrackpadAttacking) return;
        // Click the actual menu coordinates, but restore the relative aim
        // position so choosing an inventory item does not throw the arena
        // crosshair into the corner when combat starts.
        float savedX = pointerX, savedY = pointerY;
        beginTouchDirect(x, y);
        endTouchDirect();
        pointerX = savedX;
        pointerY = savedY;
        clampPointer();
        // The original game reads the mouse position for facing even
        // after a click finishes. Restore its Ruffle-side pointer to the
        // saved Relative crosshair, not just the native overlay cursor.
        webView.postOnAnimation(() -> {
            if (!pointerInitialized || touchTrackpadAttacking || attacking) return;
            sendHover();
            float nx = pointerX / Math.max(1, webView.getWidth());
            float ny = pointerY / Math.max(1, webView.getHeight());
            webView.evaluateJavascript(
                "if(window.arcaneControllerAim)window.arcaneControllerAim(" +
                nx + "," + ny + ");", null);
        });
    }

    void moveTouchDirect(float x, float y) {
        if (!touchTrackpadAttacking || !pointerInitialized) return;
        pointerX = x;
        pointerY = y;
        clampPointer();
        if (attacking) sendPointer(MotionEvent.ACTION_MOVE);
        else sendHover();
    }

    void endTouchDirect() {
        endTouchTrackpad();
    }

    void moveTouchTrackpad(float deltaX, float deltaY) {
        if (!touchTrackpadAttacking || !pointerInitialized) return;
        float gain = Math.max(0.65f, Math.min(2.5f, aimSensitivity * 1.35f));
        pointerX += deltaX * gain;
        pointerY += deltaY * gain;
        clampPointer();
        if (attacking) sendPointer(MotionEvent.ACTION_MOVE);
        else sendHover();
    }

    void endTouchTrackpad() {
        if (!touchTrackpadAttacking) return;
        touchTrackpadAttacking = false;
        updateMappedButtons();
        touchAimActive = false;
        touchAimX = touchAimY = 0f;
        updateAimFromSources();
    }

    private void applyStickDirection(float x, float y, boolean touch) {
        if (invertMoveX) x = -x;
        if (invertMoveY) y = -y;
        boolean prevUp = touch ? touchUp : stickUp;
        boolean prevDown = touch ? touchDown : stickDown;
        boolean prevLeft = touch ? touchLeft : stickLeft;
        boolean prevRight = touch ? touchRight : stickRight;
        float dominant = Math.max(Math.abs(x), Math.abs(y));
        float release = moveThreshold * (MOVE_OFF / MOVE_ON);
        boolean previouslyActive = prevUp || prevDown || prevLeft || prevRight;
        boolean enabled = dominant > (previouslyActive ? release : moveThreshold);
        boolean up = false, down = false, left = false, right = false;
        if (enabled) {
            boolean horizontal = Math.abs(x) >= Math.abs(y);
            boolean vertical = Math.abs(y) >= Math.abs(x);
            // 8-way uses a 28-degree cone on each side of the diagonal.
            // 4-way selects only the most significant direction.
            if (movementMode == 0 && dominant > 0f) {
                float minor = Math.min(Math.abs(x), Math.abs(y));
                if (minor >= dominant * 0.53f) {
                    horizontal = true;
                    vertical = true;
                }
            }
            if (horizontal) {
                left = x < 0;
                right = x > 0;
            }
            if (vertical) {
                up = y < 0;
                down = y > 0;
            }
        }
        if (touch) {
            touchUp = up; touchDown = down;
            touchLeft = left; touchRight = right;
        } else {
            stickUp = up; stickDown = down;
            stickLeft = left; stickRight = right;
        }
    }

    void setTouchAim(float x, float y, boolean active) {
        touchAimActive = active;
        touchAimX = Math.abs(x) < DEADZONE ? 0f : x;
        touchAimY = Math.abs(y) < DEADZONE ? 0f : y;
        updateAimFromSources();
    }

    void setTouchAction(int action, boolean down) {
        if (action < 0 || action >= touchActionsDown.length) return;
        if (touchActionsDown[action] == down) return;
        touchActionsDown[action] = down;
        updateMappedButtons();
    }

    private void updateAimFromSources() {
        aimX = touchAimActive ? touchAimX : physicalAimX;
        aimY = touchAimActive ? touchAimY : physicalAimY;
        if ((aimX != 0 || aimY != 0) && !loopRunning) {
            loopRunning = true;
            lastFrameTime = SystemClock.uptimeMillis();
            webView.postOnAnimation(aimLoop);
        }
    }

    void releaseTouchControls() {
        hudTouchActive = false;
        touchUp = touchDown = touchLeft = touchRight = false;
        touchDpadUp = touchDpadDown = touchDpadLeft = touchDpadRight = false;
        touchTrackpadAttacking = false;
        touchAimActive = false;
        touchAimX = touchAimY = 0f;
        for (int i = 0; i < touchActionsDown.length; i++) touchActionsDown[i] = false;
        updateAimFromSources();
        updateMovement();
        updateMappedButtons();
    }

    void showTouchCursor() {
        initializePointer();
        if (pointerInitialized) clampPointer();
    }

    void setNativeCursorEnabled(boolean enabled) {
        if (nativeCursorEnabled == enabled && cursor.visible ==
            (enabled && pointerInitialized)) return;
        nativeCursorEnabled = enabled;
        cursor.visible = enabled && pointerInitialized;
        cursor.invalidate();
    }

    private int currentMovementMask() {
        int mask = 0;
        if (keyUp || stickUp || hatUp || touchUp || touchDpadUp) mask |= 1;
        if (keyDown || stickDown || hatDown || touchDown || touchDpadDown) mask |= 2;
        if (keyLeft || stickLeft || hatLeft || touchLeft || touchDpadLeft) mask |= 4;
        if (keyRight || stickRight || hatRight || touchRight || touchDpadRight) mask |= 8;
        return mask;
    }

    private void updateMovement() {
        int mask = currentMovementMask();
        if (mask != lastMovementMask) {
            // Release every inactive movement direction on EACH transition,
            // even when our native key cache believes it is already up.
            // Ruffle can drop an earlier key-up while aim touches change
            // WebView focus, leaving a stale opposite direction pressed.
            int[] codes = {KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_S,
                KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_D};
            int[] bits = {1, 2, 4, 8};
            for (int i = 0; i < codes.length; i++) {
                if ((mask & bits[i]) != 0) continue;
                pressedKeys.remove(codes[i]);
                dispatchTranslatedKey(codes[i], false);
            }
            for (int i = 0; i < codes.length; i++) {
                if ((mask & bits[i]) != 0) setKey(codes[i], true);
            }
            Log.d(TAG, "movement-direction mask=" + mask +
                " previous=" + lastMovementMask);
            lastMovementMask = mask;
        }

        if (mask == 0) {
            if (wasMoving) {
                webView.removeCallbacks(neutralMovementRecovery);
                neutralRecoveryQueued = true;
                neutralRecoveryPass = 0;
                webView.postDelayed(neutralMovementRecovery, NEUTRAL_KEY_RECOVERY_MS);
            }
        } else if (neutralRecoveryQueued) {
            webView.removeCallbacks(neutralMovementRecovery);
            neutralRecoveryQueued = false;
            neutralRecoveryPass = 0;
        }
        wasMoving = mask != 0;
    }

    private final Runnable neutralMovementRecovery = new Runnable() {
        @Override public void run() {
            neutralRecoveryQueued = false;
            // Do not release movement keys while any physical or touchscreen
            // direction is genuinely held.
            if (currentMovementMask() != 0) return;
            for (int key : new int[] {
                KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_A,
                KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D
            }) {
                pressedKeys.remove(key);
                dispatchTranslatedKey(key, false);
            }
            webView.evaluateJavascript(
                "if(window.arcaneClearMovementKeys)window.arcaneClearMovementKeys();",
                null);
            neutralRecoveryPass++;
            Log.d(TAG, "movement-neutral-release pass=" + neutralRecoveryPass);
            // Repeat briefly to cover Ruffle frames that miss the first
            // release during simultaneous touch/attack. Only key-UP events
            // are sent; this never synthesises movement or jump presses.
            if (neutralRecoveryPass < NEUTRAL_RECOVERY_PASSES) {
                neutralRecoveryQueued = true;
                webView.postDelayed(this, 90L);
            }
        }
    };

    private void setKey(int code, boolean down) {
        if (pressedKeys.contains(code) == down) return;
        if (down) pressedKeys.add(code);
        else pressedKeys.remove(code);
        dispatchTranslatedKey(code, down);
    }

    private void dispatchTranslatedKey(int code, boolean down) {
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
        cursor.visible = nativeCursorEnabled;
        cursor.invalidate();
    }

    void setDisplayMode(String mode) {
        widescreenDisplay = !"original".equals(mode);
        if (pointerInitialized) clampPointer();
    }

    private void clampPointer() {
        float vw = webView.getWidth(), vh = webView.getHeight();
        // 4:3 only restricts aim to the original centered game viewport.
        // Fill/Stretch place the game across the whole display.
        float stageW = widescreenDisplay ? vw : Math.min(vw, vh * 4f / 3f);
        float stageH = widescreenDisplay ? vh : Math.min(vh, vw * 3f / 4f);
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

    private boolean isButtonDown(int code) {
        if (code == UNMAPPED) return false;
        return hardwareButtonsDown.contains(code) ||
            (code == KeyEvent.KEYCODE_BUTTON_R2 && rightTriggerAxisDown) ||
            (code == KeyEvent.KEYCODE_BUTTON_L2 && leftTriggerAxisDown);
    }

    private void updateMappedButtons() {
        setKey(KeyEvent.KEYCODE_SPACE, touchActionsDown[2] || isButtonDown(bindings[2]));
        setKey(KeyEvent.KEYCODE_B, touchActionsDown[3] || isButtonDown(bindings[3]));
        setKey(KeyEvent.KEYCODE_E, touchActionsDown[4] || isButtonDown(bindings[4]));
        setKey(KeyEvent.KEYCODE_R, touchActionsDown[5] || isButtonDown(bindings[5]));
        setKey(KeyEvent.KEYCODE_T, touchActionsDown[6] || isButtonDown(bindings[6]));
        boolean desired = touchTrackpadAttacking || touchActionsDown[0] ||
            touchActionsDown[1] || isButtonDown(bindings[0]) || isButtonDown(bindings[1]);
        if (desired != attacking) {
            attacking = desired;
            sendPointer(desired ? MotionEvent.ACTION_DOWN : MotionEvent.ACTION_UP);
        }
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
                float speed = Math.min(webView.getWidth(), webView.getHeight() * 4f / 3f) * aimSensitivity;
                pointerX += aimX * speed * dt;
                pointerY += (invertAimY ? -aimY : aimY) * speed * dt;
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
        webView.removeCallbacks(neutralMovementRecovery);
        neutralRecoveryQueued = false;
        neutralRecoveryPass = 0;
        lastMovementMask = 0;
        wasMoving = false;
        aimX = aimY = 0;
        physicalAimX = physicalAimY = touchAimX = touchAimY = 0;
        touchAimActive = false;
        touchUp = touchDown = touchLeft = touchRight = false;
        touchDpadUp = touchDpadDown = touchDpadLeft = touchDpadRight = false;
        touchTrackpadAttacking = false;
        for (int i = 0; i < touchActionsDown.length; i++) touchActionsDown[i] = false;
        loopRunning = false;
        lastScriptAimTime = 0;
        webView.removeCallbacks(aimLoop);
        keyUp = keyDown = keyLeft = keyRight = false;
        stickUp = stickDown = stickLeft = stickRight = false;
        hatUp = hatDown = hatLeft = hatRight = false;
        hardwareButtonsDown.clear();
        rightTriggerAxisDown = leftTriggerAxisDown = false;
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
