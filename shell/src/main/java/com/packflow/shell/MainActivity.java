package com.packflow.shell;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Hosts the game's web files from assets/www/ in a full-screen WebView.
 * Files are served from https://appassets.androidplatform.net/assets/... so fetch(),
 * ES modules and localStorage behave like on a normal website.
 */
public class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final Map<String, String> MIME = new HashMap<>();

    static {
        MIME.put("html", "text/html"); MIME.put("htm", "text/html");
        MIME.put("js", "text/javascript"); MIME.put("mjs", "text/javascript");
        MIME.put("css", "text/css"); MIME.put("json", "application/json");
        MIME.put("png", "image/png"); MIME.put("jpg", "image/jpeg"); MIME.put("jpeg", "image/jpeg");
        MIME.put("gif", "image/gif"); MIME.put("webp", "image/webp"); MIME.put("svg", "image/svg+xml");
        MIME.put("wav", "audio/wav"); MIME.put("mp3", "audio/mpeg"); MIME.put("ogg", "audio/ogg");
        MIME.put("m4a", "audio/mp4"); MIME.put("mp4", "video/mp4"); MIME.put("txt", "text/plain");
        MIME.put("woff", "font/woff"); MIME.put("woff2", "font/woff2"); MIME.put("ttf", "font/ttf");
        MIME.put("wasm", "application/wasm");
    }

    private WebView webView;
    private boolean fullscreen = true;
    private boolean crashOffered = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        readConfig();
        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        webView.addJavascriptInterface(new ErrorBridge(getApplicationContext()), "PackFlowAndroid");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return serveAsset(request.getUrl());
            }
        });
        if (!fullscreen) webView.setFitsSystemWindows(true);
        setContentView(webView);
        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl("https://" + HOST + "/assets/www/index.html");
        }
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBack);
        }
    }

    private void readConfig() {
        try (InputStream in = getAssets().open("packflow_shell.json")) {
            byte[] buf = new byte[8192];
            int n = in.read(buf);
            JSONObject o = new JSONObject(new String(buf, 0, Math.max(n, 0), "UTF-8"));
            fullscreen = o.optBoolean("fullscreen", true);
            String orientation = o.optString("orientation", "sensor");
            if ("landscape".equals(orientation)) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            } else if ("portrait".equals(orientation)) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
            }
        } catch (Exception ignored) {
            // No config: full screen, any orientation.
        }
    }

    private WebResourceResponse serveAsset(Uri url) {
        if (url == null || !HOST.equals(url.getHost())) return null;
        String path = url.getPath();
        if (path == null || !path.startsWith("/assets/")) return null;
        String assetPath = path.substring("/assets/".length());
        if (assetPath.contains("..")) return null;
        String ext = assetPath.contains(".") ? assetPath.substring(assetPath.lastIndexOf('.') + 1).toLowerCase(Locale.US) : "";
        String mime = MIME.containsKey(ext) ? MIME.get(ext) : "application/octet-stream";
        try {
            InputStream in = getAssets().open(assetPath);
            WebResourceResponse r = new WebResourceResponse(mime, mime.startsWith("text/") || ext.equals("json") ? "UTF-8" : null, in);
            Map<String, String> headers = new HashMap<>();
            headers.put("Access-Control-Allow-Origin", "*");
            r.setResponseHeaders(headers);
            return r;
        } catch (IOException e) {
            WebResourceResponse missing = new WebResourceResponse("text/plain", "UTF-8", null);
            missing.setStatusCodeAndReasonPhrase(404, "Not Found");
            return missing;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (fullscreen) hideSystemBars();
        if (webView != null) webView.onResume();
        if (!crashOffered) {
            crashOffered = true;
            offerLastCrash();
        }
    }

    @Override
    protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) webView.saveState(outState);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && fullscreen) hideSystemBars();
    }

    @SuppressWarnings("deprecation")
    private void hideSystemBars() {
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
            WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            w.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
    }

    private void handleBack() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            finish();
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        handleBack();
    }

    private void offerLastCrash() {
        File crash = new File(ShellApp.dir(this), ShellApp.CRASH_FILE);
        if (!crash.exists()) return;
        final String text;
        try {
            text = ShellApp.read(crash);
        } catch (IOException e) {
            return;
        }
        String[] lines = text.split("\n");
        StringBuilder preview = new StringBuilder();
        for (int i = 0; i < Math.min(8, lines.length); i++) preview.append(lines[i]).append('\n');
        new AlertDialog.Builder(this)
                .setTitle("The app closed unexpectedly last time")
                .setMessage(preview.toString())
                .setPositiveButton("Copy last crash", (d, which) -> {
                    String log = "";
                    try {
                        log = ShellApp.read(new File(ShellApp.dir(this), ShellApp.LOG_FILE));
                    } catch (IOException ignored) {
                    }
                    copy(getApplicationContext(), "Last crash", log.isEmpty() ? text : text + "\n\nRecent errors:\n" + tail(log, 30));
                    //noinspection ResultOfMethodCallIgnored
                    crash.delete();
                })
                .setNegativeButton("Dismiss", (d, which) -> {
                    //noinspection ResultOfMethodCallIgnored
                    crash.delete();
                })
                .show();
    }

    static String tail(String text, int lines) {
        String[] all = text.split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = Math.max(0, all.length - lines); i < all.length; i++) sb.append(all[i]).append('\n');
        return sb.toString();
    }

    static void copy(final Context c, final String label, final String text) {
        new Handler(Looper.getMainLooper()).post(() -> {
            ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText(label, text));
            Toast.makeText(c, "Copied", Toast.LENGTH_SHORT).show();
        });
    }

    /** window.PackFlowAndroid for error-catcher.js. */
    static class ErrorBridge {
        private final Context context;

        ErrorBridge(Context context) {
            this.context = context;
        }

        @JavascriptInterface
        public void logError(String json) {
            ShellApp.appendLog(context, "WEB", json);
        }

        @JavascriptInterface
        public void copyText(String text) {
            copy(context, "Errors", text);
        }

        @JavascriptInterface
        public void clearErrors() {
            //noinspection ResultOfMethodCallIgnored
            new File(ShellApp.dir(context), ShellApp.LOG_FILE).delete();
        }

        @JavascriptInterface
        public String lastCrash() {
            try {
                return ShellApp.read(new File(ShellApp.dir(context), ShellApp.CRASH_FILE));
            } catch (IOException e) {
                return "";
            }
        }
    }
}
