package io.github.arcanearmorcollector.android;

import android.app.Activity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.FrameLayout;
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
    private static final String ORIGIN = "https://appassets.androidplatform.net";

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        FrameLayout root = new FrameLayout(this);
        root.addView(gameView, new FrameLayout.LayoutParams(-1, -1));
        gamepad = new GamepadBridge(this, gameView);
        root.addView(gamepad.cursor, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        gameView.requestFocus();
        gameView.loadUrl(ORIGIN + "/assets/index.html");
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
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (gamepad != null && gamepad.handleKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }
    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (gamepad != null && gamepad.handleMotion(event)) return true;
        return super.dispatchGenericMotionEvent(event);
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
                "if (window.arcaneResume) window.arcaneResume();", null);
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
