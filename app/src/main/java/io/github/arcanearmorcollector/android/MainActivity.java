package io.github.arcanearmorcollector.android;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Bundle;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.JavascriptInterface;

import androidx.webkit.WebViewAssetLoader;

import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends Activity {
    private WebView gameView;
    private GamepadBridge gamepad;
    private boolean backgrounded;
    private volatile Thread speakerTestThread;
    private FrameLayout root;
    private FrameLayout optionsOverlay;
    private TouchControlsOverlay touchControls;
    private ScrollView optionsScroll;
    private LinearLayout optionRows;
    private boolean renderedControls, renderedTouchControls, renderedMovementControls;
    private int renderedOptionsTab = -1;
    private View floatingOptionsButton;
    private boolean optionsOpen, showingControls, showingTouchControls, showingMovementControls;
    private FrameLayout unmappedWarningLayer;
    private TextView warningKeepEditing, warningContinue;
    private int warningSelection;
    private boolean warningExitMenu;
    private int menuSelection, menuAxisLatch, bindingAction = -1;
    // FrostFrontend's top navigation: Graphics, Controls, Touch Settings.
    private int optionsTab;
    private int pendingOptionsTab = -1;
    private String displayMode = "original";
    private boolean invertAimY;
    private int aimSpeedIndex = 1;
    private int touchMode, touchSizeIndex = 1, touchOpacityIndex = 1, touchInsetIndex = 1;
    private boolean touchSwapped, touchDpad, touchRelativeAim = true;
    private boolean sceneKnown, sceneArena;
    private static final String[] TOUCH_MOVEMENT = {"Joystick", "D-pad"};
    private static final String[] TOUCH_AIM = {"Relative", "Direct"};
    private int movementMode, movementDeadzoneIndex = 1;
    private boolean invertMoveX, invertMoveY;
    private static final String[] MOVEMENT_MODES = {"8-way", "4-way"};
    private static final String[] DEADZONE_NAMES = {"Low", "Normal", "High"};
    private static final float[] DEADZONE_VALUES = {0.30f, 0.38f, 0.48f};
    private static final String[] TOUCH_MODES = {"Auto", "On", "Off"};
    private static final String[] TOUCH_SIZES = {"Small", "Medium", "Large"};
    private static final float[] TOUCH_SIZE_VALUES = {0.8f, 1.0f, 1.15f};
    private static final String[] TOUCH_OPACITIES = {"Low", "Medium", "High"};
    private static final float[] TOUCH_OPACITY_VALUES = {0.38f, 0.60f, 0.82f};
    private static final String[] TOUCH_INSETS = {"Edge", "Normal", "Inset"};
    private static final int[] TOUCH_INSET_VALUES = {8, 18, 34};
    private static final String[] DISPLAY_MODES = {"original", "stretch"};
    private static final String[] DISPLAY_NAMES = {"Original 4:3", "Stretch to Screen"};
    private static final float[] AIM_SPEEDS = {0.6f, 0.9f, 1.2f, 1.5f, 1.8f};
    private static final String ORIGIN = "https://appassets.androidplatform.net";

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        displayMode = prefs.getString("display_mode", "original");
        // Old "fill" crops the playfield on widescreen handhelds.
        // Migrate anyone who selected it to the usable stretched mode.
        if ("fill".equals(displayMode)) {
            displayMode = "stretch";
            prefs.edit().putString("display_mode", displayMode).apply();
        }
        if (displayModeIndex() < 0) displayMode = "original";
        invertAimY = prefs.getBoolean("invert_aim_y", false);
        aimSpeedIndex = prefs.getInt("aim_speed", 1);
        aimSpeedIndex = Math.max(0, Math.min(AIM_SPEEDS.length - 1, aimSpeedIndex));
        touchMode = Math.max(0, Math.min(2, prefs.getInt("touch_mode", 0)));
        touchSizeIndex = Math.max(0, Math.min(2, prefs.getInt("touch_size", 1)));
        touchOpacityIndex = Math.max(0, Math.min(2, prefs.getInt("touch_opacity", 1)));
        touchInsetIndex = Math.max(0, Math.min(2, prefs.getInt("touch_inset", 1)));
        touchSwapped = prefs.getBoolean("touch_swap", false);
        touchDpad = prefs.getBoolean("touch_dpad", false);
        touchRelativeAim = prefs.getBoolean("touch_relative_aim", true);
        movementMode = Math.max(0, Math.min(1, prefs.getInt("movement_mode", 0)));
        movementDeadzoneIndex = Math.max(0, Math.min(2, prefs.getInt("movement_deadzone", 1)));
        invertMoveX = prefs.getBoolean("invert_move_x", false);
        invertMoveY = prefs.getBoolean("invert_move_y", false);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setImmersive();

        WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", path -> {
                if (path.contains("..") || path.startsWith("/")) return null;
                try {
                    InputStream in = getAssets().open("www/" + path);
                    if ("arcane.swf".equals(path)) {
                        byte[] instrumented;
                        try (InputStream original = in) {
                            instrumented = ArcaneSceneMarker.patch(original);
                        }
                        in = new java.io.ByteArrayInputStream(instrumented);
                    }
                    String mime = "application/octet-stream";
                    if (path.endsWith(".html")) mime = "text/html";
                    else if (path.endsWith(".js")) mime = "application/javascript";
                    else if (path.endsWith(".css")) mime = "text/css";
                    else if (path.endsWith(".wasm")) mime = "application/wasm";
                    else if (path.endsWith(".swf")) mime = "application/x-shockwave-flash";
                    else if (path.endsWith(".json")) mime = "application/json";
                    return new WebResourceResponse(mime, "UTF-8", in);
                } catch (IOException e) {
                    return null;
                }
            }).build();

        gameView = new WebView(this);
        gameView.setBackgroundColor(0xFF000000);
        gameView.setFocusable(true);
        gameView.setFocusableInTouchMode(true);
        WebSettings s = gameView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);

        gameView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView v, String url) {
                super.onPageFinished(v, url);
                applyDisplayModeToPage();
                if (optionsOpen) v.evaluateJavascript(
                    "if(window.arcaneSetMenuPaused)window.arcaneSetMenuPaused(true);", null);
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest request) {
                if (!ORIGIN.equals(request.getUrl().getScheme() + "://" + request.getUrl().getHost())) {
                    return new WebResourceResponse("text/plain", "UTF-8", new java.io.ByteArrayInputStream(new byte[0]));
                }
                return loader.shouldInterceptRequest(request.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                return !ORIGIN.equals(request.getUrl().getScheme() + "://" + request.getUrl().getHost());
            }
        });
        // The local, trusted SWF uses in-memory FSCommand frame markers to
        // report arena/menu transitions via Ruffle. No network or file writes.
        gameView.addJavascriptInterface(new GameSceneBridge(), "ArcaneHost");
        gameView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onConsoleMessage(ConsoleMessage m) {
                Log.d("ArcaneRuffle", m.messageLevel() + ": " + m.message());
                return true;
            }
        });
        root = new FrameLayout(this);
        root.addView(gameView, new FrameLayout.LayoutParams(-1, -1));
        gamepad = new GamepadBridge(this, gameView);
        gamepad.setDisplayMode(displayMode);
        gamepad.setAimOptions(invertAimY, AIM_SPEEDS[aimSpeedIndex]);
        gamepad.setMovementOptions(movementMode, DEADZONE_VALUES[movementDeadzoneIndex],
            invertMoveX, invertMoveY);
        for (int action = 0; action < GamepadBridge.ACTION_NAMES.length; action++) {
            gamepad.setButton(action, prefs.getInt(
                "button_" + action, GamepadBridge.defaultButton(action)));
        }
        // Always show the controller's aiming guide. Some Arcane scenes
        // hide the Flash cursor, but the native guide remains visible.
        gamepad.cursor.setClickable(false);
        gamepad.cursor.setFocusable(false);
        root.addView(gamepad.cursor, new FrameLayout.LayoutParams(-1, -1));
        touchControls = new TouchControlsOverlay(this, gamepad);
        root.addView(touchControls, new FrameLayout.LayoutParams(-1, -1));
        touchControls.configure(TOUCH_SIZE_VALUES[touchSizeIndex],
            TOUCH_OPACITY_VALUES[touchOpacityIndex], touchSwapped,
            TOUCH_INSET_VALUES[touchInsetIndex], touchDpad, touchRelativeAim);
        refreshTouchControls();
        // Render a genuine three-line icon instead of a font glyph, which
        // Android WebView / system fonts can draw off-centre.
        floatingOptionsButton = new MenuIconView();
        floatingOptionsButton.setContentDescription("Pause and open Arcane options");
        floatingOptionsButton.setOnClickListener(v -> openOptions());
        FrameLayout.LayoutParams floatParams = new FrameLayout.LayoutParams(
            dp(44), dp(44), Gravity.RIGHT | Gravity.TOP);
        floatParams.topMargin = dp(18);
        floatParams.rightMargin = dp(18);
        root.addView(floatingOptionsButton, floatParams);
        setContentView(root);
        gameView.requestFocus();
        gameView.loadUrl(ORIGIN + "/assets/index.html");
    }

    private final class GameSceneBridge {
        @JavascriptInterface public void reportPhase(String phase) {
            if (!"arena".equals(phase) && !"menu".equals(phase)) return;
            runOnUiThread(() -> {
                boolean arena = "arena".equals(phase);
                if (sceneKnown && sceneArena == arena) return;
                sceneKnown = true;
                sceneArena = arena;
                if (touchControls != null) touchControls.setGameScene(true, arena);
                Log.d("ArcanePhase", "Ruffle scene: " + phase);
                refreshTouchControls();
            });
        }
    }

    private int dp(float points) {
        return Math.round(points * getResources().getDisplayMetrics().density);
    }

    private final class MenuIconView extends View {
        private final Paint lines = new Paint(Paint.ANTI_ALIAS_FLAG);

        MenuIconView() {
            super(MainActivity.this);
            setBackground(roundedBackground(0xB8221B2A, 0, 9));
            setClickable(true);
            setElevation(dp(4));
            lines.setColor(0xFFFFFFFF);
            lines.setStrokeWidth(dp(2.5f));
            lines.setStrokeCap(Paint.Cap.ROUND);
            lines.setStyle(Paint.Style.STROKE);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float halfWidth = dp(9);
            float spacing = dp(6);
            for (int i = -1; i <= 1; i++) {
                float y = cy + i * spacing;
                canvas.drawLine(cx - halfWidth, y, cx + halfWidth, y, lines);
            }
        }
    }

    private final class CloseIconView extends View {
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);

        CloseIconView() {
            super(MainActivity.this);
            setBackground(roundedBackground(0xFF3B2939, 0xFFBA986A, 9));
            setClickable(true);
            setContentDescription("Close FrostFrontend and resume game");
            stroke.setColor(0xFFFFE0A1);
            stroke.setStrokeWidth(dp(2.5f));
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStyle(Paint.Style.STROKE);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() * 0.5f;
            float cy = getHeight() * 0.5f;
            float d = dp(7);
            canvas.drawLine(cx - d, cy - d, cx + d, cy + d, stroke);
            canvas.drawLine(cx - d, cy + d, cx + d, cy - d, stroke);
        }
    }

    private GradientDrawable roundedBackground(int color, int stroke, int radius) {
        GradientDrawable b = new GradientDrawable();
        b.setColor(color);
        b.setCornerRadius(dp(radius));
        if (stroke != 0) b.setStroke(dp(1), stroke);
        return b;
    }

    private TextView makeText(String label, int size, int color, int background) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setBackground(roundedBackground(background, 0, 9));
        t.setPadding(dp(14), dp(10), dp(14), dp(10));
        return t;
    }

    private int displayModeIndex() {
        for (int i = 0; i < DISPLAY_MODES.length; i++)
            if (DISPLAY_MODES[i].equals(displayMode)) return i;
        return -1;
    }

    private void applyDisplayModeToPage() {
        if (gameView != null) gameView.evaluateJavascript(
            "if(window.arcaneSetDisplayMode)window.arcaneSetDisplayMode('" +
            displayMode + "');", null);
    }

    private void cycleDisplay(int direction) {
        int index = displayModeIndex();
        if (index < 0) index = 0;
        displayMode = DISPLAY_MODES[(index + direction + DISPLAY_MODES.length) % DISPLAY_MODES.length];
        getPreferences(MODE_PRIVATE).edit().putString("display_mode", displayMode).apply();
        gamepad.setDisplayMode(displayMode);
        applyDisplayModeToPage();
        refreshSettingRows();
    }

    private boolean hasGamepad() {
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            if (device != null &&
                (device.getSources() & (InputDevice.SOURCE_GAMEPAD |
                    InputDevice.SOURCE_JOYSTICK)) != 0) return true;
        }
        return false;
    }

    private void refreshTouchControls() {
        if (touchControls == null) return;
        boolean visible = !optionsOpen && !backgrounded &&
            (touchMode == 1 || (touchMode == 0 && !hasGamepad()));
        int visibility = visible ? View.VISIBLE : View.GONE;
        if (touchControls.getVisibility() != visibility)
            touchControls.setVisibility(visibility);
        // Direct touch mode should not leave an unrelated controller crosshair
        // sitting over the Flash game. Restore it when relative aiming or
        // physical controller-only play resumes.
        boolean relativeCombat = touchRelativeAim && (!sceneKnown || sceneArena);
        gamepad.setNativeCursorEnabled(!visible || relativeCombat);
        if (visible && relativeCombat) gamepad.showTouchCursor();
    }

    private void saveMovementPreferences() {
        getPreferences(MODE_PRIVATE).edit()
            .putInt("movement_mode", movementMode)
            .putInt("movement_deadzone", movementDeadzoneIndex)
            .putBoolean("invert_move_x", invertMoveX)
            .putBoolean("invert_move_y", invertMoveY).apply();
        gamepad.setMovementOptions(movementMode, DEADZONE_VALUES[movementDeadzoneIndex],
            invertMoveX, invertMoveY);
    }

    private void resetMovementPreferences() {
        movementMode = 0;
        movementDeadzoneIndex = 1;
        invertMoveX = invertMoveY = false;
        saveMovementPreferences();
        refreshSettingRows();
    }

    private void saveTouchPreferences() {
        getPreferences(MODE_PRIVATE).edit()
            .putInt("touch_mode", touchMode)
            .putInt("touch_size", touchSizeIndex)
            .putInt("touch_opacity", touchOpacityIndex)
            .putInt("touch_inset", touchInsetIndex)
            .putBoolean("touch_swap", touchSwapped)
            .putBoolean("touch_dpad", touchDpad)
            .putBoolean("touch_relative_aim", touchRelativeAim).apply();
        touchControls.configure(TOUCH_SIZE_VALUES[touchSizeIndex],
            TOUCH_OPACITY_VALUES[touchOpacityIndex], touchSwapped,
            TOUCH_INSET_VALUES[touchInsetIndex], touchDpad, touchRelativeAim);
        refreshTouchControls();
    }

    private void resetTouchPreferences() {
        touchMode = 0;
        touchSizeIndex = touchOpacityIndex = touchInsetIndex = 1;
        touchSwapped = false;
        touchDpad = false;
        touchRelativeAim = true;
        saveTouchPreferences();
        refreshSettingRows();
    }

    private void setAimPreferences() {
        getPreferences(MODE_PRIVATE).edit()
            .putBoolean("invert_aim_y", invertAimY)
            .putInt("aim_speed", aimSpeedIndex).apply();
        gamepad.setAimOptions(invertAimY, AIM_SPEEDS[aimSpeedIndex]);
    }

    private void saveBindings() {
        SharedPreferences.Editor e = getPreferences(MODE_PRIVATE).edit();
        for (int i = 0; i < GamepadBridge.ACTION_NAMES.length; i++)
            e.putInt("button_" + i, gamepad.getButton(i));
        e.apply();
    }

    private void restoreControls() {
        gamepad.release();
        gamepad.resetButtons();
        invertAimY = false;
        aimSpeedIndex = 1;
        setAimPreferences();
        saveBindings();
        renderOptions();
    }

    private void assignButton(int keyCode) {
        if (bindingAction < 0 || !GamepadBridge.canBindButton(keyCode)) return;
        gamepad.setButton(bindingAction, keyCode);
        saveBindings();
        bindingAction = -1;
        renderOptions();
    }

    private void openOptions() {
        if (optionsOpen) return;
        if (touchControls != null) touchControls.releaseAll();
        gamepad.release();
        optionsOpen = true;
        showingControls = false;
        showingTouchControls = false;
        showingMovementControls = false;
        optionsTab = 0;
        pendingOptionsTab = -1;
        refreshTouchControls();
        menuSelection = 0;
        menuAxisLatch = 0;
        bindingAction = -1;
        floatingOptionsButton.setVisibility(View.GONE);
        gameView.evaluateJavascript(
            "if(window.arcaneSetMenuPaused)window.arcaneSetMenuPaused(true);", null);
        optionsOverlay = new FrameLayout(this);
        optionsOverlay.setBackgroundColor(0xC9000000);
        optionsOverlay.setClickable(true);
        optionsOverlay.setFocusableInTouchMode(true);
        root.addView(optionsOverlay, new FrameLayout.LayoutParams(-1, -1));
        renderOptions();
        optionsOverlay.requestFocus();
    }

    private void closeOptions() {
        if (!optionsOpen) return;
        optionsOpen = false;
        unmappedWarningLayer = null;
        warningKeepEditing = warningContinue = null;
        bindingAction = -1;
        pendingOptionsTab = -1;
        menuAxisLatch = 0;
        gamepad.release();
        if (optionsOverlay != null) {
            root.removeView(optionsOverlay);
            optionsOverlay = null;
        }
        optionsScroll = null;
        optionRows = null;
        floatingOptionsButton.setVisibility(View.VISIBLE);
        refreshTouchControls();
        gameView.evaluateJavascript(
            "if(window.arcaneSetMenuPaused)window.arcaneSetMenuPaused(false);", null);
        gameView.requestFocus();
    }

    private void requestOptionsClose() {
        if (unmappedWarningLayer != null) return;
        if (showingControls) {
            // Closing must respect the existing unmapped-controls warning.
            if (bindingAction >= 0) bindingAction = -1;
            pendingOptionsTab = -1;
            leaveControls(true);
        } else {
            closeOptions();
        }
    }

    private void selectOptionsTab(int tab) {
        if (!optionsOpen || unmappedWarningLayer != null || tab < 0 || tab > 2) return;
        if (showingControls) {
            if (bindingAction >= 0) bindingAction = -1;
            pendingOptionsTab = tab;
            leaveControls(false);
            return;
        }
        pendingOptionsTab = -1;
        optionsTab = tab;
        showingControls = false;
        showingMovementControls = false;
        showingTouchControls = tab == 2;
        menuSelection = 0;
        menuAxisLatch = 0;
        renderOptions();
    }

    private void backOptions() {
        if (unmappedWarningLayer != null) {
            dismissUnmappedWarning();
            return;
        }
        if (bindingAction >= 0) {
            bindingAction = -1;
            renderOptions();
        } else if (showingControls) {
            pendingOptionsTab = -1;
            leaveControls(false);
        } else if (showingMovementControls) {
            showingMovementControls = false;
            menuSelection = 1;
            renderOptions();
        } else if (optionsTab != 0) {
            selectOptionsTab(0);
        } else {
            requestOptionsClose();
        }
    }

    private void leaveControls(boolean exitMenu) {
        boolean hasUnmapped = false;
        for (int i = 0; i < GamepadBridge.ACTION_NAMES.length; i++) {
            if (gamepad.getButton(i) == GamepadBridge.UNMAPPED) {
                hasUnmapped = true;
                break;
            }
        }
        if (hasUnmapped) showUnmappedWarning(exitMenu);
        else finishLeavingControls(exitMenu);
    }

    private void finishLeavingControls(boolean exitMenu) {
        if (exitMenu) {
            pendingOptionsTab = -1;
            closeOptions();
        } else {
            showingControls = false;
            showingMovementControls = false;
            optionsTab = pendingOptionsTab >= 0 ? pendingOptionsTab : 1;
            showingTouchControls = optionsTab == 2;
            pendingOptionsTab = -1;
            menuSelection = 0;
            renderOptions();
        }
    }

    private void dismissUnmappedWarning() {
        if (unmappedWarningLayer != null && optionsOverlay != null)
            optionsOverlay.removeView(unmappedWarningLayer);
        unmappedWarningLayer = null;
        warningKeepEditing = warningContinue = null;
        if (optionsOverlay != null) optionsOverlay.requestFocus();
    }

    private void confirmUnmappedWarning(boolean continueAnyway) {
        if (unmappedWarningLayer == null) return;
        boolean exitMenu = warningExitMenu;
        dismissUnmappedWarning();
        if (continueAnyway) finishLeavingControls(exitMenu);
    }

    private void selectWarningChoice(int index) {
        warningSelection = Math.max(0, Math.min(1, index));
        if (warningKeepEditing != null && warningContinue != null) {
            TextView[] choices = {warningKeepEditing, warningContinue};
            for (int i = 0; i < choices.length; i++) {
                boolean selected = i == warningSelection;
                choices[i].setTextColor(selected ? 0xFFFFE1A2 : 0xFFEAE7EA);
                choices[i].setBackground(roundedBackground(
                    selected ? 0xFF433147 : 0xFF292331,
                    selected ? 0xFFD0AA69 : 0xFF615064, 9));
            }
        }
    }

    private void showUnmappedWarning(boolean exitMenu) {
        if (unmappedWarningLayer != null || optionsOverlay == null) return;
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < GamepadBridge.ACTION_NAMES.length; i++) {
            if (gamepad.getButton(i) == GamepadBridge.UNMAPPED) {
                if (names.length() > 0) names.append("\n");
                names.append("• ").append(GamepadBridge.ACTION_NAMES[i]);
            }
        }
        if (names.length() == 0) {
            finishLeavingControls(exitMenu);
            return;
        }

        warningExitMenu = exitMenu;
        warningSelection = 0;
        unmappedWarningLayer = new FrameLayout(this);
        unmappedWarningLayer.setBackgroundColor(0x99000000);
        unmappedWarningLayer.setClickable(true);
        unmappedWarningLayer.setFocusableInTouchMode(true);
        optionsOverlay.addView(unmappedWarningLayer, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout dialog = new LinearLayout(this);
        dialog.setOrientation(LinearLayout.VERTICAL);
        dialog.setPadding(dp(20), dp(17), dp(20), dp(19));
        dialog.setBackground(roundedBackground(0xFF251D2D, 0xFFD0AA69, 12));
        int screenW = getResources().getDisplayMetrics().widthPixels;
        FrameLayout.LayoutParams dialogParams = new FrameLayout.LayoutParams(
            Math.min(dp(460), screenW - dp(56)), -2, Gravity.CENTER);
        unmappedWarningLayer.addView(dialog, dialogParams);

        TextView title = makeText("UNMAPPED CONTROLS", 19, 0xFFFFC65C, 0x00000000);
        title.setGravity(Gravity.CENTER);
        dialog.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView message = makeText(
            "These actions have no button assigned:\n\n" + names +
            "\n\nYou can continue, but those actions won't work in-game.",
            14, 0xFFEAE7EA, 0x00000000);
        dialog.addView(message, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, -2);
        actionParams.topMargin = dp(10);
        dialog.addView(actions, actionParams);
        warningKeepEditing = makeText("Keep Editing", 14, 0xFFFFFFFF, 0xFF433147);
        warningContinue = makeText("Continue Anyway", 14, 0xFFFFFFFF, 0xFF292331);
        for (int i = 0; i < 2; i++) {
            final int index = i;
            TextView choice = i == 0 ? warningKeepEditing : warningContinue;
            choice.setGravity(Gravity.CENTER);
            choice.setOnClickListener(v -> confirmUnmappedWarning(index == 1));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(47), 1f);
            if (i == 1) lp.leftMargin = dp(9);
            actions.addView(choice, lp);
        }
        selectWarningChoice(0);
        unmappedWarningLayer.requestFocus();
    }

    private boolean handleUnmappedWarningKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_UP:
                selectWarningChoice(0); break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                selectWarningChoice(1); break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                confirmUnmappedWarning(warningSelection == 1); break;
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_BUTTON_START:
                confirmUnmappedWarning(false); break;
            default: break;
        }
        return true;
    }

    private void navigateOptions(int direction) {
        if (bindingAction >= 0) return;
        int count = showingControls ? 11 : showingMovementControls ? 6 :
            showingTouchControls ? 8 : optionsTab == 1 ? 2 : 2;
        menuSelection = (menuSelection + direction + count) % count;
        updateOptionsSelection();
    }

    private void adjustOption(int direction) {
        if (bindingAction >= 0) return;
        if (optionsTab == 0 && !showingControls && !showingMovementControls &&
            !showingTouchControls && menuSelection == 0) {
            cycleDisplay(direction);
        } else if (showingMovementControls) {
            switch (menuSelection) {
                case 1: movementMode = (movementMode + direction + 2) % 2; break;
                case 2: movementDeadzoneIndex = (movementDeadzoneIndex + direction + 3) % 3; break;
                case 3: invertMoveX = !invertMoveX; break;
                case 4: invertMoveY = !invertMoveY; break;
                default: return;
            }
            saveMovementPreferences();
            refreshSettingRows();
        } else if (showingTouchControls) {
            switch (menuSelection) {
                case 0: touchMode = (touchMode + direction + 3) % 3; break;
                case 1: touchSizeIndex = (touchSizeIndex + direction + 3) % 3; break;
                case 2: touchOpacityIndex = (touchOpacityIndex + direction + 3) % 3; break;
                case 3: touchDpad = !touchDpad; break;
                case 4: touchRelativeAim = !touchRelativeAim; break;
                case 5: touchSwapped = !touchSwapped; break;
                case 6: touchInsetIndex = (touchInsetIndex + direction + 3) % 3; break;
                default: return;
            }
            saveTouchPreferences();
            refreshSettingRows();
        } else if (showingControls && menuSelection == 8) {
            invertAimY = !invertAimY;
            setAimPreferences();
            refreshSettingRows();
        } else if (showingControls && menuSelection == 9) {
            aimSpeedIndex = (aimSpeedIndex + direction + AIM_SPEEDS.length) % AIM_SPEEDS.length;
            setAimPreferences();
            refreshSettingRows();
        }
    }

    private void activateOption() {
        if (bindingAction >= 0) return;
        if (showingTouchControls) {
            if (menuSelection == 7) resetTouchPreferences();
            else adjustOption(1);
        } else if (showingMovementControls) {
            if (menuSelection == 0) backOptions();
            else if (menuSelection == 5) resetMovementPreferences();
            else adjustOption(1);
        } else if (showingControls) {
            if (menuSelection == 0) backOptions();
            else if (menuSelection <= 7) {
                bindingAction = menuSelection - 1;
                renderOptions();
            } else if (menuSelection == 8 || menuSelection == 9) {
                adjustOption(1);
            } else if (menuSelection == 10) restoreControls();
        } else if (optionsTab == 0) {
            if (menuSelection == 0) cycleDisplay(1);
            else if (menuSelection == 1) playSpeakerTest();
        } else if (optionsTab == 1) {
            showingControls = menuSelection == 0;
            showingMovementControls = menuSelection == 1;
            showingTouchControls = false;
            menuSelection = 0;
            renderOptions();
        }
    }


    private void updateSettingRow(int index, String name, String detail) {
        if (optionRows == null || index < 0 || index >= optionRows.getChildCount()) return;
        View child = optionRows.getChildAt(index);
        if (!(child instanceof OptionRow)) return;
        OptionRow row = (OptionRow) child;
        row.setContent(name, detail);
        row.setHighlighted(index == menuSelection);
    }

    private void refreshSettingRows() {
        if (!optionsOpen || optionRows == null) return;
        if (showingMovementControls) {
            updateSettingRow(1, "Stick Directions: " + MOVEMENT_MODES[movementMode],
                "WASD · D-pad unchanged");
            updateSettingRow(2, "Stick Deadzone: " + DEADZONE_NAMES[movementDeadzoneIndex],
                "Physical + touch sticks");
            updateSettingRow(3, "Invert Movement X: " + (invertMoveX ? "On" : "Off"), "");
            updateSettingRow(4, "Invert Movement Y: " + (invertMoveY ? "On" : "Off"), "");
        } else if (showingTouchControls) {
            updateSettingRow(0, "Touch Controls: " + TOUCH_MODES[touchMode],
                touchMode == 0 ? "Auto-hide with controller" :
                touchMode == 1 ? "Always show" : "Hidden");
            updateSettingRow(1, "Control Size: " + TOUCH_SIZES[touchSizeIndex], "");
            updateSettingRow(2, "Opacity: " + TOUCH_OPACITIES[touchOpacityIndex], "");
            updateSettingRow(3, "Movement Pad: " + TOUCH_MOVEMENT[touchDpad ? 1 : 0],
                "Digital D-pad or analogue joystick");
            updateSettingRow(4, "Touch Aiming: " + TOUCH_AIM[touchRelativeAim ? 0 : 1],
                touchRelativeAim ? "Swipe to aim · touch to fire" : "Tap the target to fire");
            updateSettingRow(5, "Sides: " + (touchSwapped ? "Swapped" : "Standard"), "");
            updateSettingRow(6, "Position: " + TOUCH_INSETS[touchInsetIndex],
                "Distance from edge");
        } else if (showingControls) {
            updateSettingRow(8, "Invert Right-Stick Y: " +
                (invertAimY ? "On" : "Off"), "Flip vertical aim");
            updateSettingRow(9, "Aim Sensitivity: " + AIM_SPEEDS[aimSpeedIndex] + "×",
                "Right-stick speed");
        } else if (optionsTab == 0) {
            int mode = Math.max(0, displayModeIndex());
            updateSettingRow(0, "Display Mode: " + DISPLAY_NAMES[mode],
                mode == 0 ? "Unstretched · side bars" : "Full widescreen · stretched");
        }
        // Never recreate the panel or animate ScrollView when a value changes.
        updateOptionsSelection();
    }

    private void updateOptionsSelection() {
        if (optionRows == null || optionsScroll == null) return;
        for (int i = 0; i < optionRows.getChildCount(); i++) {
            View child = optionRows.getChildAt(i);
            if (child instanceof OptionRow)
                ((OptionRow) child).setHighlighted(i == menuSelection);
        }
        if (menuSelection < 0 || menuSelection >= optionRows.getChildCount()) return;
        View row = optionRows.getChildAt(menuSelection);
        int top = row.getTop(), bottom = row.getBottom();
        int scrollTop = optionsScroll.getScrollY();
        int scrollHeight = optionsScroll.getHeight();
        if (scrollHeight <= 0) return;
        int padding = dp(4);
        if (top < scrollTop + padding)
            optionsScroll.scrollTo(0, Math.max(0, top - padding));
        else if (bottom > scrollTop + scrollHeight - padding)
            optionsScroll.scrollTo(0, bottom - scrollHeight + padding);
    }

    // The warning triangle is drawn, not a font emoji, so its position is
    // reliable across Android versions and device fonts.
    private final class WarningOptionRow extends TextView {
        private final Paint warningPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path triangle = new Path();

        WarningOptionRow() {
            super(MainActivity.this);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float x = getWidth() - dp(22);
            float y = getHeight() / 2f;
            float radius = dp(9);
            triangle.reset();
            triangle.moveTo(x, y - radius);
            triangle.lineTo(x + radius, y + radius * 0.8f);
            triangle.lineTo(x - radius, y + radius * 0.8f);
            triangle.close();
            warningPaint.setStyle(Paint.Style.FILL);
            warningPaint.setColor(0xFFFFC65C);
            canvas.drawPath(triangle, warningPaint);
            warningPaint.setColor(0xFF332317);
            warningPaint.setStrokeWidth(dp(2));
            warningPaint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(x, y - dp(3), x, y + dp(2), warningPaint);
            canvas.drawCircle(x, y + dp(5), dp(1), warningPaint);
        }
    }

    // Small one-line settings rows with right-aligned values. Only the
    // highlighted row shows its explanation, leaving more options visible.
    // Keep the outer row as a single child in optionRows for controller
    // selection indices, scrolling and in-place option refresh.
    private final class OptionRow extends LinearLayout {
        final TextView nameView;
        final TextView valueView;
        final TextView detailView;
        private String details = "";

        OptionRow(boolean unmapped) {
            super(MainActivity.this);
            setOrientation(LinearLayout.VERTICAL);
            setPadding(dp(10), dp(5), dp(10), dp(5));
            setMinimumHeight(dp(36));
            setClickable(true);
            setFocusable(false);

            LinearLayout top = new LinearLayout(MainActivity.this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);
            addView(top, new LinearLayout.LayoutParams(-1, -2));

            nameView = new TextView(MainActivity.this);
            nameView.setTextSize(14);
            nameView.setTextColor(0xFFECE7EF);
            nameView.setGravity(Gravity.CENTER_VERTICAL);
            nameView.setSingleLine(true);
            nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            top.addView(nameView, new LinearLayout.LayoutParams(0, -2, 1f));

            valueView = new TextView(MainActivity.this);
            valueView.setTextSize(13);
            valueView.setTextColor(0xFFE0CCAA);
            valueView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            valueView.setSingleLine(true);
            valueView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-2, -2);
            vlp.leftMargin = dp(9);
            top.addView(valueView, vlp);

            if (unmapped) {
                TextView warning = new WarningOptionRow();
                top.addView(warning, new LinearLayout.LayoutParams(dp(30), dp(29)));
            }

            detailView = new TextView(MainActivity.this);
            detailView.setTextSize(11);
            detailView.setTextColor(0xFFBDB1C8);
            detailView.setPadding(dp(2), dp(2), 0, 0);
            detailView.setVisibility(View.GONE);
            addView(detailView, new LinearLayout.LayoutParams(-1, -2));
        }

        void setContent(String text, String detail) {
            int sep = text.indexOf(": ");
            String title = sep < 0 ? text : text.substring(0, sep);
            String value = sep < 0 ? "" : text.substring(sep + 2);
            nameView.setText(title);
            valueView.setText(value);
            valueView.setVisibility(value.isEmpty() ? View.GONE : View.VISIBLE);
            details = detail;
            detailView.setText(detail);
        }

        void setHighlighted(boolean selected) {
            nameView.setTextColor(selected ? 0xFFFFE1A2 : 0xFFECE7EF);
            valueView.setTextColor(selected ? 0xFFFFE1A2 : 0xFFE0CCAA);
            detailView.setVisibility(selected && !details.isEmpty()
                ? View.VISIBLE : View.GONE);
            setBackground(roundedBackground(
                selected ? 0xFF433147 : 0x55201A26,
                selected ? 0xFFD0AA69 : 0x00000000, 8));
        }
    }

    private void addOptionRow(LinearLayout rows, ScrollView scroll,
                              String name, String detail, int index) {
        addOptionRow(rows, scroll, name, detail, index, false);
    }

    private void addOptionRow(LinearLayout rows, ScrollView scroll,
                              String name, String detail, int index, boolean unmapped) {
        OptionRow row = new OptionRow(unmapped);
        row.setContent(name, detail);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(3);
        rows.addView(row, lp);
        row.setOnClickListener(v -> {
            if (bindingAction >= 0) return;
            menuSelection = index;
            activateOption();
            if (optionsOpen) updateOptionsSelection();
        });
    }

    private void playSpeakerTest() {
        // The WebAudio test was silent on the Odin when the SWF/WebView was
        // suspended by FrostFrontend. Bypass that route entirely: use the
        // Android media output directly to isolate device channel routing.
        if (speakerTestThread != null && speakerTestThread.isAlive()) {
            Toast.makeText(this, "Speaker test is playing", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this,
            "Speaker test: LEFT, then RIGHT, then BOTH. Check media volume.",
            Toast.LENGTH_LONG).show();
        Thread worker = new Thread(this::runSpeakerTest, "ArcaneSpeakerTest");
        speakerTestThread = worker;
        worker.start();
    }

    private void runSpeakerTest() {
        AudioTrack track = null;
        final String tag = "ArcaneAudioTest";
        final int sampleRate = 48000;
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                Log.d(tag, "mediaVolume=" + am.getStreamVolume(AudioManager.STREAM_MUSIC) +
                    "/" + am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            }
            int minBuffer = AudioTrack.getMinBufferSize(sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
            if (minBuffer <= 0) {
                Log.e(tag, "No supported stereo AudioTrack buffer: " + minBuffer);
                return;
            }
            AudioFormat format = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build();
            AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
            track = new AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(Math.max(minBuffer, sampleRate * 4 / 5))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();
            if (track.getState() != AudioTrack.STATE_INITIALIZED) {
                Log.e(tag, "Native stereo AudioTrack could not initialize");
                return;
            }
            Log.d(tag, "start: sampleRate=" + track.getSampleRate() +
                " channels=" + track.getChannelCount() +
                " mode=PCM16/stereo media");
            track.play();
            int samplesPerStep = (int) (sampleRate * 0.75f);
            int fade = (int) (sampleRate * 0.02f);
            for (int channel = 0; channel < 3; channel++) {
                if (Thread.currentThread().isInterrupted()) break;
                String label = new String[] {"LEFT", "RIGHT", "BOTH"}[channel];
                Log.d(tag, "step=" + label);
                short[] sound = new short[samplesPerStep * 2];
                for (int frame = 0; frame < samplesPerStep; frame++) {
                    double envelope = Math.min(1.0,
                        Math.min(frame / (double) fade,
                            (samplesPerStep - 1 - frame) / (double) fade));
                    short sample = (short) (Math.sin(
                        2.0 * Math.PI * 440.0 * frame / sampleRate) *
                        Math.max(0, envelope) * Short.MAX_VALUE * 0.18);
                    sound[frame * 2] = channel == 1 ? 0 : sample;
                    sound[frame * 2 + 1] = channel == 0 ? 0 : sample;
                }
                if (!writeStereoAudio(track, sound)) break;
                if (channel != 2) {
                    short[] silence = new short[(int) (sampleRate * 0.18f) * 2];
                    if (!writeStereoAudio(track, silence)) break;
                }
            }
            // Give the hardware time to drain the queued stream.
            while (!Thread.currentThread().isInterrupted() &&
                   track.getPlaybackHeadPosition() < sampleRate * 2.5f) {
                Thread.sleep(25);
            }
            Log.d(tag, "finished");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.d(tag, "cancelled");
        } catch (Exception e) {
            Log.e(tag, "Native stereo test failed", e);
        } finally {
            if (track != null) {
                try { track.pause(); } catch (Exception ignored) { }
                try { track.flush(); } catch (Exception ignored) { }
                track.release();
            }
            if (speakerTestThread == Thread.currentThread()) speakerTestThread = null;
        }
    }

    private boolean writeStereoAudio(AudioTrack track, short[] sound) {
        int pos = 0;
        while (pos < sound.length && !Thread.currentThread().isInterrupted()) {
            int written = track.write(sound, pos, sound.length - pos,
                AudioTrack.WRITE_BLOCKING);
            if (written <= 0) {
                Log.e("ArcaneAudioTest", "AudioTrack write failed: " + written);
                return false;
            }
            pos += written;
        }
        return pos == sound.length;
    }

    private void renderOptions() {
        if (!optionsOpen || optionsOverlay == null) return;
        // Preserve scrolling only within the same page. Changing a setting
        // updates its existing row directly, so the menu never bounces.
        int restoreY = optionsScroll != null && renderedControls == showingControls &&
            renderedTouchControls == showingTouchControls &&
            renderedMovementControls == showingMovementControls &&
            renderedOptionsTab == optionsTab ? optionsScroll.getScrollY() : 0;

        optionsOverlay.removeAllViews();
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(8), dp(12), dp(10));
        panel.setBackground(roundedBackground(0xF5221A2B, 0xFFC09A62, 16));

        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
            Math.max(1, Math.min(dp(820), screenW - dp(52))),
            Math.max(1, Math.min(dp(600), screenH - dp(44))), Gravity.CENTER);
        optionsOverlay.addView(panel, panelParams);

        // FrostFrontend: keep section headings on the top line beside Close.
        // Removes the separate SETTINGS title row, giving the scrollable
        // settings list much more vertical room on landscape handhelds.
        FrameLayout header = new FrameLayout(this);
        header.setContentDescription("FrostFrontend settings navigation");
        panel.addView(header, new LinearLayout.LayoutParams(-1, dp(43)));

        // Leave a permanent right-hand slot for the exit X.
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        FrameLayout.LayoutParams tabsParams = new FrameLayout.LayoutParams(
            -1, -1, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        tabsParams.rightMargin = dp(43);
        header.addView(tabs, tabsParams);
        String[] tabNames = {"Graphics", "Controls", "Touch Settings"};
        for (int i = 0; i < tabNames.length; i++) {
            final int tabIndex = i;
            boolean selected = optionsTab == i;
            TextView tab = makeText(tabNames[i], 18,
                selected ? 0xFFFFDFA2 : 0xFFDDD6E0,
                selected ? 0xFF433147 : 0xFF2B2432);
            tab.setGravity(Gravity.CENTER);
            tab.setTypeface(android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD);
            tab.setPadding(dp(4), dp(1), dp(4), dp(1));
            tab.setBackground(roundedBackground(selected ? 0xFF433147 : 0xFF2B2432,
                selected ? 0xFFD0AA69 : 0xFF564761, 9));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
            if (i > 0) lp.leftMargin = dp(6);
            tabs.addView(tab, lp);
            tab.setOnClickListener(v -> selectOptionsTab(tabIndex));
        }
        CloseIconView close = new CloseIconView();
        close.setOnClickListener(v -> requestOptionsClose());
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(
            dp(33), dp(33), Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        header.addView(close, closeParams);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows, new ScrollView.LayoutParams(-1, -2));
        optionsScroll = scroll;
        optionRows = rows;
        renderedControls = showingControls;
        renderedTouchControls = showingTouchControls;
        renderedMovementControls = showingMovementControls;
        renderedOptionsTab = optionsTab;

        if (showingControls) {
            addOptionRow(rows, scroll, "Back to Controls", "", 0);
            for (int i = 0; i < GamepadBridge.ACTION_NAMES.length; i++) {
                boolean unmapped = gamepad.getButton(i) == GamepadBridge.UNMAPPED;
                addOptionRow(rows, scroll,
                    GamepadBridge.ACTION_NAMES[i] + ": " +
                    GamepadBridge.buttonName(gamepad.getButton(i)),
                    bindingAction == i ? "Press a button…" : "",
                    i + 1, unmapped);
            }
            addOptionRow(rows, scroll, "Invert Right-Stick Y: " +
                (invertAimY ? "On" : "Off"), "Flip vertical aim", 8);
            addOptionRow(rows, scroll, "Aim Sensitivity: " +
                AIM_SPEEDS[aimSpeedIndex] + "×", "Right-stick speed", 9);
            addOptionRow(rows, scroll, "Restore Default Controls", "Restore original mappings", 10);
        } else if (showingMovementControls) {
            addOptionRow(rows, scroll, "Back to Controls", "", 0);
            addOptionRow(rows, scroll, "Stick Directions: " + MOVEMENT_MODES[movementMode],
                "WASD · D-pad unchanged", 1);
            addOptionRow(rows, scroll, "Stick Deadzone: " + DEADZONE_NAMES[movementDeadzoneIndex],
                "Physical + touch sticks", 2);
            addOptionRow(rows, scroll, "Invert Movement X: " +
                (invertMoveX ? "On" : "Off"), "", 3);
            addOptionRow(rows, scroll, "Invert Movement Y: " +
                (invertMoveY ? "On" : "Off"), "", 4);
            addOptionRow(rows, scroll, "Reset Movement Settings", "Restore defaults", 5);
        } else if (showingTouchControls) {
            addOptionRow(rows, scroll, "Touch Controls: " + TOUCH_MODES[touchMode],
                touchMode == 0 ? "Auto-hide with controller" :
                touchMode == 1 ? "Always show" : "Hidden", 0);
            addOptionRow(rows, scroll, "Control Size: " + TOUCH_SIZES[touchSizeIndex], "", 1);
            addOptionRow(rows, scroll, "Opacity: " + TOUCH_OPACITIES[touchOpacityIndex], "", 2);
            addOptionRow(rows, scroll, "Movement Pad: " +
                TOUCH_MOVEMENT[touchDpad ? 1 : 0],
                "Digital D-pad or analogue joystick", 3);
            addOptionRow(rows, scroll, "Touch Aiming: " +
                TOUCH_AIM[touchRelativeAim ? 0 : 1],
                touchRelativeAim ? "Swipe to aim · touch to fire" : "Tap the target to fire", 4);
            addOptionRow(rows, scroll, "Sides: " +
                (touchSwapped ? "Swapped" : "Standard"), "", 5);
            addOptionRow(rows, scroll, "Position: " + TOUCH_INSETS[touchInsetIndex],
                "Distance from edge", 6);
            addOptionRow(rows, scroll, "Reset Touch Controls", "Restore defaults", 7);
        } else if (optionsTab == 1) {
            addOptionRow(rows, scroll, "Button Mapping", "Assign controller buttons", 0);
            addOptionRow(rows, scroll, "Movement Settings", "Stick directions, deadzone and inversion", 1);
        } else {
            int mode = Math.max(0, displayModeIndex());
            addOptionRow(rows, scroll, "Display Mode: " + DISPLAY_NAMES[mode],
                mode == 0 ? "Unstretched · side bars" :
                "Full widescreen · stretched", 0);
            addOptionRow(rows, scroll, "Speaker Test: Left → Right → Both",
                "Listen to each speaker in order; game audio stays paused", 1);
        }

        if (bindingAction >= 0) {
            TextView prompt = makeText("WAITING FOR BUTTON…", 14, 0xFFFFE6AD, 0xFF48323F);
            prompt.setGravity(Gravity.CENTER);
            panel.addView(prompt, new LinearLayout.LayoutParams(-1, dp(38)));
        }
        updateOptionsSelection();
        scroll.post(() -> {
            if (!optionsOpen || optionsScroll != scroll) return;
            scroll.scrollTo(0, restoreY);
            updateOptionsSelection();
        });
    }

    private boolean isGamepadKey(KeyEvent event) {
        return (event.getSource() & (InputDevice.SOURCE_GAMEPAD |
            InputDevice.SOURCE_DPAD | InputDevice.SOURCE_JOYSTICK)) != 0;
    }

    private boolean handleOptionsKey(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN &&
            event.getAction() != KeyEvent.ACTION_UP) return true;
        if (event.getAction() == KeyEvent.ACTION_UP) return true;
        if (event.getRepeatCount() > 0) return true;
        int code = event.getKeyCode();
        if (unmappedWarningLayer != null) return handleUnmappedWarningKey(code);
        if (code == KeyEvent.KEYCODE_BACK) {
            backOptions();
            return true;
        }
        if (bindingAction >= 0) {
            if (GamepadBridge.canBindButton(code)) assignButton(code);
            return true;
        }
        if (code == KeyEvent.KEYCODE_BUTTON_L1) {
            selectOptionsTab((optionsTab + 2) % 3);
            return true;
        }
        if (code == KeyEvent.KEYCODE_BUTTON_R1) {
            selectOptionsTab((optionsTab + 1) % 3);
            return true;
        }
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: navigateOptions(-1); break;
            case KeyEvent.KEYCODE_DPAD_DOWN: navigateOptions(1); break;
            case KeyEvent.KEYCODE_DPAD_LEFT: adjustOption(-1); break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: adjustOption(1); break;
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER: activateOption(); break;
            case KeyEvent.KEYCODE_BUTTON_B: backOptions(); break;
            default: break;
        }
        return true;
    }

    private boolean handleOptionsMotion(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_MOVE) return true;
        if (unmappedWarningLayer != null) {
            float x = event.getAxisValue(MotionEvent.AXIS_X);
            int direction = x > 0.68f ? 1 : x < -0.68f ? -1 : 0;
            if (direction == 0) {
                if (Math.abs(x) < 0.28f) menuAxisLatch = 0;
            } else if (menuAxisLatch == 0) {
                menuAxisLatch = direction;
                selectWarningChoice(direction > 0 ? 1 : 0);
            }
            return true;
        }
        if (bindingAction >= 0) {
            if (event.getAxisValue(MotionEvent.AXIS_RTRIGGER) > 0.8f ||
                event.getAxisValue(MotionEvent.AXIS_GAS) > 0.8f)
                assignButton(KeyEvent.KEYCODE_BUTTON_R2);
            else if (event.getAxisValue(MotionEvent.AXIS_LTRIGGER) > 0.8f ||
                     event.getAxisValue(MotionEvent.AXIS_BRAKE) > 0.8f)
                assignButton(KeyEvent.KEYCODE_BUTTON_L2);
            return true;
        }
        float y = event.getAxisValue(MotionEvent.AXIS_Y);
        int direction = y > 0.68f ? 1 : y < -0.68f ? -1 : 0;
        if (direction == 0) {
            if (Math.abs(y) < 0.28f) menuAxisLatch = 0;
        } else if (menuAxisLatch == 0) {
            menuAxisLatch = direction;
            navigateOptions(direction);
        }
        return true;
    }

    private void setImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) setImmersive();
        else if (gamepad != null) gamepad.release();
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_START &&
            isGamepadKey(event)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN &&
                event.getRepeatCount() == 0) {
                if (unmappedWarningLayer != null) dismissUnmappedWarning();
                else if (optionsOpen) requestOptionsClose();
                else openOptions();
            }
            return true;
        }
        if (optionsOpen) return handleOptionsKey(event);
        if (isGamepadKey(event) && touchMode == 0) refreshTouchControls();
        if (gamepad != null && gamepad.handleKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }
    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (optionsOpen && (event.getSource() & InputDevice.SOURCE_JOYSTICK) != 0)
            return handleOptionsMotion(event);
        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) != 0 && touchMode == 0)
            refreshTouchControls();
        if (gamepad != null && gamepad.handleMotion(event)) return true;
        return super.dispatchGenericMotionEvent(event);
    }
    @Override public void onBackPressed() {
        if (optionsOpen) backOptions();
        else openOptions();
    }
    @Override public void onPause() {
        backgrounded = true;
        if (speakerTestThread != null) speakerTestThread.interrupt();
        if (touchControls != null) touchControls.releaseAll();
        if (gamepad != null) gamepad.release();
        refreshTouchControls();
        if (gameView != null) {
            // WebView.onPause() alone doesn't pause JavaScript or Ruffle audio.
            // Suspend the SWF before pausing timers, to stop both sounds and frames.
            final WebView pausedView = gameView;
            pausedView.evaluateJavascript(
                "if (window.arcaneSuspend) window.arcaneSuspend();",
                ignored -> {
                    if (backgrounded && gameView == pausedView) pausedView.pauseTimers();
                });
            pausedView.onPause();
            // Safety fallback if the JavaScript callback doesn't arrive after backgrounding.
            pausedView.postDelayed(() -> {
                if (backgrounded && gameView == pausedView) pausedView.pauseTimers();
            }, 250);
        }
        super.onPause();
    }
    @Override public void onResume() {
        super.onResume();
        backgrounded = false;
        refreshTouchControls();
        if (gameView != null) {
            gameView.resumeTimers();
            gameView.onResume();
            gameView.evaluateJavascript(
                "if (window.arcaneResume) window.arcaneResume();" +
                "if (window.arcaneClearMovementKeys) window.arcaneClearMovementKeys();",
                null);
        }
    }
    @Override public void onDestroy() {
        backgrounded = true;
        if (speakerTestThread != null) speakerTestThread.interrupt();
        if (gamepad != null) gamepad.release();
        if (gameView != null) {
            gameView.stopLoading();
            ViewGroup parent = (ViewGroup) gameView.getParent();
            if (parent != null) parent.removeView(gameView);
            gameView.destroy();
            gameView = null;
        }
        super.onDestroy();
    }
}
