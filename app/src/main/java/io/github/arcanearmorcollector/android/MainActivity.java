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
import android.os.Bundle;
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

import androidx.webkit.WebViewAssetLoader;

import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends Activity {
    private WebView gameView;
    private GamepadBridge gamepad;
    private boolean backgrounded;
    private FrameLayout root;
    private FrameLayout optionsOverlay;
    private TouchControlsOverlay touchControls;
    private ScrollView optionsScroll;
    private LinearLayout optionRows;
    private boolean renderedControls;
    private View floatingOptionsButton;
    private boolean optionsOpen, showingControls, showingTouchControls;
    private FrameLayout unmappedWarningLayer;
    private TextView warningKeepEditing, warningContinue;
    private int warningSelection;
    private boolean warningExitMenu;
    private int menuSelection, menuAxisLatch, bindingAction = -1;
    private String displayMode = "original";
    private boolean invertAimY;
    private int aimSpeedIndex = 1;
    private int touchMode, touchSizeIndex = 1, touchOpacityIndex = 1, touchInsetIndex = 1;
    private boolean touchSwapped;
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
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setImmersive();

        WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", path -> {
                if (path.contains("..") || path.startsWith("/")) return null;
                try {
                    InputStream in = getAssets().open("www/" + path);
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
            TOUCH_INSET_VALUES[touchInsetIndex]);
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
        renderOptions();
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
        if (visible) gamepad.showTouchCursor();
    }

    private void saveTouchPreferences() {
        getPreferences(MODE_PRIVATE).edit()
            .putInt("touch_mode", touchMode)
            .putInt("touch_size", touchSizeIndex)
            .putInt("touch_opacity", touchOpacityIndex)
            .putInt("touch_inset", touchInsetIndex)
            .putBoolean("touch_swap", touchSwapped).apply();
        touchControls.configure(TOUCH_SIZE_VALUES[touchSizeIndex],
            TOUCH_OPACITY_VALUES[touchOpacityIndex], touchSwapped,
            TOUCH_INSET_VALUES[touchInsetIndex]);
        refreshTouchControls();
    }

    private void resetTouchPreferences() {
        touchMode = 0;
        touchSizeIndex = touchOpacityIndex = touchInsetIndex = 1;
        touchSwapped = false;
        saveTouchPreferences();
        renderOptions();
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

    private void backOptions() {
        if (unmappedWarningLayer != null) {
            dismissUnmappedWarning();
            return;
        }
        if (bindingAction >= 0) {
            bindingAction = -1;
        } else if (showingControls) {
            leaveControls(false);
            return;
        } else if (showingTouchControls) {
            showingTouchControls = false;
            menuSelection = 3;
        } else {
            closeOptions();
            return;
        }
        renderOptions();
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
            closeOptions();
        } else {
            showingControls = false;
            menuSelection = 2;
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
        int count = showingControls ? 11 : 3;
        menuSelection = (menuSelection + direction + count) % count;
        updateOptionsSelection();
    }

    private void adjustOption(int direction) {
        if (bindingAction >= 0) return;
        if (!showingControls && menuSelection == 1) {
            cycleDisplay(direction);
        } else if (showingControls && menuSelection == 8) {
            invertAimY = !invertAimY;
            setAimPreferences();
            renderOptions();
        } else if (showingControls && menuSelection == 9) {
            aimSpeedIndex = (aimSpeedIndex + direction + AIM_SPEEDS.length) % AIM_SPEEDS.length;
            setAimPreferences();
            renderOptions();
        }
    }

    private void activateOption() {
        if (bindingAction >= 0) return;
        if (!showingControls) {
            if (menuSelection == 0) closeOptions();
            else if (menuSelection == 1) cycleDisplay(1);
            else {
                showingControls = true;
                menuSelection = 0;
                renderOptions();
            }
        } else if (menuSelection == 0) {
            backOptions();
        } else if (menuSelection <= 7) {
            bindingAction = menuSelection - 1;
            renderOptions();
        } else if (menuSelection == 8 || menuSelection == 9) {
            adjustOption(1);
        } else if (menuSelection == 10) {
            restoreControls();
        }
    }


    private void updateOptionsSelection() {
        if (optionRows == null || optionsScroll == null) return;
        for (int i = 0; i < optionRows.getChildCount(); i++) {
            View child = optionRows.getChildAt(i);
            if (!(child instanceof TextView)) continue;
            TextView row = (TextView) child;
            boolean selected = i == menuSelection;
            String label = (String) row.getTag();
            row.setText((selected ? "›  " : "   ") + label);
            row.setTextColor(selected ? 0xFFFFE1A2 : 0xFFEAE7EA);
            row.setBackground(roundedBackground(
                selected ? 0xFF433147 : 0x88201A26,
                selected ? 0xFFD0AA69 : 0x00000000, 8));
        }
        if (menuSelection < 0 || menuSelection >= optionRows.getChildCount()) return;
        View row = optionRows.getChildAt(menuSelection);
        int top = row.getTop(), bottom = row.getBottom();
        int scrollTop = optionsScroll.getScrollY();
        int scrollHeight = optionsScroll.getHeight();
        if (scrollHeight <= 0) return;
        // Keep the selected row visible, but never rebuild/animate the
        // entire ScrollView on each D-pad or left-stick movement.
        int padding = dp(6);
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

    private void addOptionRow(LinearLayout rows, ScrollView scroll,
                              String name, String detail, int index) {
        addOptionRow(rows, scroll, name, detail, index, false);
    }

    private void addOptionRow(LinearLayout rows, ScrollView scroll,
                              String name, String detail, int index, boolean unmapped) {
        String label = name + (detail.isEmpty() ? "" : "\n    " + detail);
        TextView row = unmapped ? new WarningOptionRow() : new TextView(this);
        row.setText(label);
        row.setTextSize(showingControls ? 14 : 15);
        row.setTextColor(0xFFEAE7EA);
        row.setGravity(Gravity.CENTER_VERTICAL);
        // Compact single-line controller mappings allow more settings to be
        // visible without resizing FrostFrame or reintroducing scrolling bounce.
        int verticalPadding = showingControls ? dp(6) : dp(10);
        row.setPadding(dp(14), verticalPadding,
            unmapped ? dp(48) : dp(14), verticalPadding);
        row.setTag(label);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = showingControls ? dp(3) : dp(5);
        rows.addView(row, lp);
        row.setOnClickListener(v -> {
            if (bindingAction >= 0) return;
            menuSelection = index;
            activateOption();
            if (optionsOpen) updateOptionsSelection();
        });
    }

    private void renderOptions() {
        if (!optionsOpen || optionsOverlay == null) return;
        // Retain the current scroll offset when a setting changes. A tab
        // switch starts at the top instead of jumping to the old tab's offset.
        int restoreY = optionsScroll != null && renderedControls == showingControls
            ? optionsScroll.getScrollY() : 0;
        optionsOverlay.removeAllViews();
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(22), dp(18), dp(22), dp(18));
        panel.setBackground(roundedBackground(0xF5221A2B, 0xFFC09A62, 16));
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
            Math.min(dp(560), screenW - dp(40)),
            Math.min(dp(525), screenH - dp(38)), Gravity.CENTER);
        optionsOverlay.addView(panel, panelParams);
        TextView heading = makeText(showingControls ? "CONTROLS" : "ARCANE  •  OPTIONS",
            21, 0xFFFFE0A1, 0x00000000);
        heading.setGravity(Gravity.CENTER);
        panel.addView(heading, new LinearLayout.LayoutParams(-1, dp(45)));
        TextView help = makeText(bindingAction >= 0
                ? "Press a button · B: Cancel · Start: Resume"
                : "Stick/D-pad: Move · A: Select · B: Back · Start: Resume",
            11, 0xFFDDD2BF, 0x00000000);
        help.setGravity(Gravity.CENTER);
        panel.addView(help, new LinearLayout.LayoutParams(-1, dp(42)));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows, new ScrollView.LayoutParams(-1, -2));
        optionsScroll = scroll;
        optionRows = rows;
        renderedControls = showingControls;
        if (!showingControls) {
            addOptionRow(rows, scroll, "Resume Game", "Return to game", 0);
            int mode = Math.max(0, displayModeIndex());
            addOptionRow(rows, scroll, "Display Mode: " + DISPLAY_NAMES[mode],
                mode == 0 ? "Unstretched · side bars" :
                "Full widescreen · stretched", 1);
            addOptionRow(rows, scroll, "Controller Settings", "Buttons and aiming", 2);
        } else {
            addOptionRow(rows, scroll, "Back to Options", "", 0);
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
        }
        if (bindingAction >= 0) {
            TextView prompt = makeText("WAITING FOR BUTTON…", 14, 0xFFFFE6AD, 0xFF48323F);
            prompt.setGravity(Gravity.CENTER);
            panel.addView(prompt, new LinearLayout.LayoutParams(-1, dp(38)));
        }
        updateOptionsSelection();
        // Layout is asynchronous. Restore scroll only once after rebuilding,
        // then adjust just enough to reveal the selected row if needed.
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
                else if (optionsOpen && showingControls) {
                    if (bindingAction >= 0) {
                        bindingAction = -1;
                        renderOptions();
                    }
                    leaveControls(true);
                } else if (optionsOpen) closeOptions();
                else openOptions();
            }
            return true;
        }
        if (optionsOpen) return handleOptionsKey(event);
        if (gamepad != null && gamepad.handleKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }
    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (optionsOpen && (event.getSource() & InputDevice.SOURCE_JOYSTICK) != 0)
            return handleOptionsMotion(event);
        if (gamepad != null && gamepad.handleMotion(event)) return true;
        return super.dispatchGenericMotionEvent(event);
    }
    @Override public void onBackPressed() {
        if (optionsOpen) backOptions();
        else openOptions();
    }
    @Override public void onPause() {
        backgrounded = true;
        if (gamepad != null) gamepad.release();
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
