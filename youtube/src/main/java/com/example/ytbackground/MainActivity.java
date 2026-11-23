package com.example.ytbackground;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity implements PlayerController {

    private static final String HOME_URL = "https://m.youtube.com/";
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");
    /** Ad / ad-tracking servers. Requests to these (and their subdomains) are refused. */
    private static final String[] AD_HOSTS = {
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "googletagservices.com", "googletagmanager.com", "google-analytics.com",
            "imasdk.googleapis.com", "adservice.google.com", "pagead2.googlesyndication.com"
    };
    /** Ad endpoints on youtube.com itself. */
    private static final String[] AD_PATHS = {
            "/pagead/", "/api/stats/ads", "/ptracking", "/get_midroll_info",
            "/youtubei/v1/player/ad_break", "/pcs/activeview", "/ad_data_204", "/generate_204?ad"
    };

    private static final String[] INTERNAL_DOMAINS = {
            "youtube.com", "youtu.be", "youtube-nocookie.com",
            "google.com", "googleusercontent.com", "gstatic.com", "ggpht.com"
    };

    private BackgroundWebView webView;
    private ProgressBar progressBar;
    private FrameLayout fullscreenContainer;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private String injectScript = "";
    private boolean documentStartScriptInstalled;

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progress);
        fullscreenContainer = findViewById(R.id.fullscreenContainer);

        applySystemBarInsets();
        // Ad blocker first, so it is in place before YouTube's scripts run.
        injectScript = readAsset("adblock.js") + "\n" + readAsset("background_play.js");
        setupWebView();
        PlaybackService.setController(this);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (customView != null) {
                    exitFullscreen();
                } else if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    // Keep the app (and the video) alive instead of closing it.
                    moveTaskToBack(true);
                }
            }
        });

        askForNotificationPermission();

        String url = urlFromIntent(getIntent());
        webView.loadUrl(url != null ? url : HOME_URL);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String url = urlFromIntent(intent);
        if (url != null) webView.loadUrl(url);
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        webView.resumeTimers();
        setPageBackground(false);
    }

    @Override
    protected void onPause() {
        // Deliberately NOT calling webView.onPause() / pauseTimers():
        // those would stop the video when the app is minimised.
        setPageBackground(true);
        super.onPause();
    }

    @Override
    protected void onStop() {
        // A full-screen video view may be suspended by the system; drop back to
        // the normal player so playback carries on in the background.
        if (customView != null) exitFullscreen();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        PlaybackService.setController(null);
        PlaybackService.stop();
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }

    // ------------------------------------------------------------------ WebView setup

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setSupportMultipleWindows(false);
        // Look like a normal mobile browser, not an embedded WebView.
        s.setUserAgentString(s.getUserAgentString()
                .replace("; wv)", ")")
                .replace("Version/4.0 ", ""));

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Keep the renderer at high priority even when the app is not visible.
            webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false);
        }

        webView.addJavascriptInterface(new JsBridge(), "YTBridge");

        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, injectScript,
                    new HashSet<>(Arrays.asList("https://*.youtube.com", "https://youtube.com")));
            documentStartScriptInstalled = true;
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleNavigation(request.getUrl());
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (isAdRequest(request.getUrl())) {
                    return new WebResourceResponse("text/plain", "utf-8",
                            new ByteArrayInputStream(new byte[0]));
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (!documentStartScriptInstalled) injectScript();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectScript(); // no-op if already injected
                CookieManager.getInstance().flush();
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                // Don't crash the app if the web renderer dies; just reload.
                recreate();
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                progressBar.setVisibility(newProgress < 100 ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                enterFullscreen(view, callback);
            }

            @Override
            public void onHideCustomView() {
                exitFullscreen();
            }

            @Nullable
            @Override
            public Bitmap getDefaultVideoPoster() {
                // Avoid the grey "play" placeholder WebView shows before a video loads.
                return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            }
        });
    }

    private void injectScript() {
        webView.evaluateJavascript(injectScript, null);
    }

    private void setPageBackground(boolean background) {
        if (webView != null) {
            webView.evaluateJavascript(
                    "window.__ytbg && __ytbg.setBackground(" + background + ")", null);
        }
    }

    /** @return true if the WebView should NOT load the URL itself. */
    private boolean handleNavigation(Uri uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (scheme.equals("http") || scheme.equals("https")) {
            if (isInternalHost(uri.getHost())) return false;
            openExternally(uri);
            return true;
        }
        if (scheme.equals("intent")) {
            // YouTube tries to hand videos off to the official app; stay here instead.
            try {
                Intent intent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME);
                String fallback = intent.getStringExtra("browser_fallback_url");
                if (fallback != null && isInternalHost(Uri.parse(fallback).getHost())) {
                    webView.loadUrl(fallback);
                }
            } catch (Exception ignored) {
            }
            return true;
        }
        if (scheme.equals("vnd.youtube")) return true;
        openExternally(uri); // mailto:, tel:, market:, ...
        return true;
    }

    static boolean isAdRequest(Uri uri) {
        String host = uri.getHost();
        if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);
        for (String d : AD_HOSTS) {
            if (host.equals(d) || host.endsWith("." + d)) return true;
        }
        if (host.equals("youtube.com") || host.endsWith(".youtube.com")) {
            String path = uri.getEncodedPath() == null ? "" : uri.getEncodedPath();
            String query = uri.getEncodedQuery();
            String full = query == null ? path : path + "?" + query;
            for (String p : AD_PATHS) {
                if (full.startsWith(p)) return true;
            }
        }
        return false;
    }

    private static boolean isInternalHost(@Nullable String host) {
        if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);
        for (String d : INTERNAL_DOMAINS) {
            if (host.equals(d) || host.endsWith("." + d)) return true;
        }
        // Regional Google domains used during sign-in (google.co.in, google.de, ...).
        return host.matches("^(.+\\.)?google\\.[a-z.]+$");
    }

    private void openExternally(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_app_for_link, Toast.LENGTH_SHORT).show();
        }
    }

    @Nullable
    private static String urlFromIntent(@Nullable Intent intent) {
        if (intent == null) return null;
        String candidate = null;
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            candidate = intent.getData().toString();
        } else if (Intent.ACTION_SEND.equals(intent.getAction())) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null) {
                Matcher m = URL_PATTERN.matcher(text);
                if (m.find()) candidate = m.group();
            }
        }
        if (candidate == null || !isInternalHost(Uri.parse(candidate).getHost())) return null;
        return candidate;
    }

    // ------------------------------------------------------------------ full screen

    private void enterFullscreen(View view, WebChromeClient.CustomViewCallback callback) {
        if (customView != null) {
            callback.onCustomViewHidden();
            return;
        }
        customView = view;
        customViewCallback = callback;
        fullscreenContainer.addView(view, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        fullscreenContainer.setVisibility(View.VISIBLE);

        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        c.hide(WindowInsetsCompat.Type.systemBars());
        c.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }

    private void exitFullscreen() {
        if (customView == null) return;
        fullscreenContainer.removeView(customView);
        fullscreenContainer.setVisibility(View.GONE);
        customView = null;
        if (customViewCallback != null) customViewCallback.onCustomViewHidden();
        customViewCallback = null;

        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .show(WindowInsetsCompat.Type.systemBars());
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    // ------------------------------------------------------------------ misc

    private void applySystemBarInsets() {
        View content = findViewById(R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout()
                    | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        c.setAppearanceLightStatusBars(false);
        c.setAppearanceLightNavigationBars(false);
    }

    private void askForNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private String readAsset(String name) {
        try (InputStream in = getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            throw new IllegalStateException("Missing asset " + name, e);
        }
    }

    // ------------------------------------------------------------------ PlayerController

    private void runJs(String js) {
        if (webView != null) webView.evaluateJavascript("window.__ytbg && " + js, null);
    }

    @Override
    public void play() {
        runJs("__ytbg.play()");
    }

    @Override
    public void pause() {
        runJs("__ytbg.pause()");
    }

    @Override
    public void seekBy(int seconds) {
        runJs("__ytbg.seekBy(" + seconds + ")");
    }

    @Override
    public void seekTo(long positionMs) {
        runJs("__ytbg.seekTo(" + (positionMs / 1000.0) + ")");
    }

    /** Receives playback updates from background_play.js (called on a background thread). */
    private class JsBridge {
        @JavascriptInterface
        public void onState(boolean playing, double positionSec, double durationSec,
                            String title, String videoId) {
            final long pos = (long) (positionSec * 1000);
            final long dur = (long) (durationSec * 1000);
            runOnUiThread(() -> PlaybackService.onPlayerState(
                    getApplicationContext(), playing, pos, dur,
                    title == null ? "" : title, videoId == null ? "" : videoId));
        }

        /** False while a phone/VoIP call is ringing or active, so we never resume over a call. */
        @JavascriptInterface
        public boolean canAutoResume() {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            return am == null || am.getMode() == AudioManager.MODE_NORMAL;
        }
    }
}
