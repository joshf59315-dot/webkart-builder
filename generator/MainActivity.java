package __PACKAGE__;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends Activity {
    private static final String START_URL = __URL__;
    private static final String APP_NAME = __APP_NAME__;
    private static final String PRIMARY = "__PRIMARY__";
    private static final String SPLASH_BG = "__SPLASH_BG__";
    private static final String LOADER = "__LOADER__";
    private static final boolean SHOW_PROGRESS = __SHOW_PROGRESS__;
    private static final boolean HAS_SPLASH = __HAS_SPLASH__;
    private static final int REQ_WEB = 11, REQ_GEO = 12, REQ_FILE = 13;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView web;
    private FrameLayout splash;
    private ProgressBar topBar;
    private long startedAt;
    private boolean splashHidden;
    private final List<PermissionRequest> pendingWeb = new ArrayList<>();
    private GeolocationPermissions.Callback geoCallback;
    private String geoOrigin;
    private ValueCallback<Uri[]> fileCallback;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        startedAt = System.currentTimeMillis();
        FrameLayout root = new FrameLayout(this);
        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        topBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        topBar.setMax(100);
        topBar.setProgressTintList(ColorStateList.valueOf(Color.parseColor(PRIMARY)));
        topBar.setVisibility(View.GONE);
        root.addView(topBar, new FrameLayout.LayoutParams(-1, dp(4), Gravity.TOP));
        splash = buildSplash();
        root.addView(splash, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setGeolocationEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if ("http".equals(scheme) || "https".equals(scheme)) return false;
                openExternal(uri);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) { hideSplash(); }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int progress) {
                if (SHOW_PROGRESS) {
                    topBar.setProgress(progress);
                    topBar.setVisibility(progress < 100 ? View.VISIBLE : View.GONE);
                }
                if (progress >= 80) hideSplash();
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) { runOnUiThread(() -> handleWebPermission(request)); }

            @Override
            public void onPermissionRequestCanceled(PermissionRequest request) { runOnUiThread(() -> pendingWeb.remove(request)); }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) { handleGeo(origin, callback); }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), REQ_FILE);
                    return true;
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
            }
        });
        web.setDownloadListener((url, userAgent, disposition, mime, length) -> openExternal(Uri.parse(url)));

        if (state != null) web.restoreState(state); else web.loadUrl(START_URL);
        handler.postDelayed(this::hideSplash, 10000);
    }

    private void openExternal(Uri uri) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) { }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private FrameLayout buildSplash() {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.parseColor(SPLASH_BG));
        frame.setClickable(true);
        int primary = Color.parseColor(PRIMARY);
        if (HAS_SPLASH) {
            ImageView image = new ImageView(this);
            image.setImageResource(getResources().getIdentifier("splash", "drawable", getPackageName()));
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            frame.addView(image, new FrameLayout.LayoutParams(-1, -1));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            lp.bottomMargin = dp(64);
            frame.addView(buildLoader(primary), lp);
        } else {
            LinearLayout column = new LinearLayout(this);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(Gravity.CENTER_HORIZONTAL);
            ImageView icon = new ImageView(this);
            icon.setImageResource(getResources().getIdentifier("ic_launcher", "mipmap", getPackageName()));
            column.addView(icon, new LinearLayout.LayoutParams(dp(96), dp(96)));
            TextView title = new TextView(this);
            title.setText(APP_NAME);
            title.setTextSize(20);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setTextColor(isDark(SPLASH_BG) ? Color.WHITE : Color.rgb(17, 24, 39));
            title.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-2, -2);
            titleLp.topMargin = dp(16);
            titleLp.bottomMargin = dp(28);
            column.addView(title, titleLp);
            column.addView(buildLoader(primary), new LinearLayout.LayoutParams(-2, -2));
            frame.addView(column, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        }
        return frame;
    }

    private boolean isDark(String hex) {
        int c = Color.parseColor(hex);
        return (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000 < 128;
    }

    private View buildLoader(int color) {
        if ("bar".equals(LOADER)) {
            ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            bar.setIndeterminate(true);
            bar.setIndeterminateTintList(ColorStateList.valueOf(color));
            bar.setLayoutParams(new LinearLayout.LayoutParams(dp(140), dp(6)));
            bar.setMinimumWidth(dp(140));
            return bar;
        }
        if ("dots".equals(LOADER)) {
            LinearLayout dots = new LinearLayout(this);
            for (int i = 0; i < 3; i++) {
                TextView dot = new TextView(this);
                dot.setText("\u25CF");
                dot.setTextSize(18);
                dot.setTextColor(color);
                dot.setPadding(dp(4), 0, dp(4), 0);
                dots.addView(dot);
                final int index = i;
                handler.post(new Runnable() {
                    int tick = 0;
                    @Override public void run() {
                        if (splashHidden) return;
                        dot.setAlpha(((tick + 3 - index) % 3) == 0 ? 1f : 0.3f);
                        tick++;
                        handler.postDelayed(this, 250);
                    }
                });
            }
            return dots;
        }
        ProgressBar spinner = new ProgressBar(this);
        spinner.setIndeterminateTintList(ColorStateList.valueOf(color));
        return spinner;
    }

    private void hideSplash() {
        if (splashHidden) return;
        long wait = 1200 - (System.currentTimeMillis() - startedAt);
        if (wait > 0) { handler.postDelayed(this::hideSplash, wait); return; }
        splashHidden = true;
        splash.animate().alpha(0f).setDuration(300).withEndAction(() -> splash.setVisibility(View.GONE)).start();
    }

    private boolean declared(String permission) {
        try {
            String[] requested = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_PERMISSIONS).requestedPermissions;
            return requested != null && Arrays.asList(requested).contains(permission);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean granted(String permission) { return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED; }

    private static String androidPermissionFor(String resource) {
        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) return Manifest.permission.CAMERA;
        if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) return Manifest.permission.RECORD_AUDIO;
        return null;
    }

    // Website camera/microphone access is granted only after the user accepts Android's own prompt.
    // Several requests can arrive together (voice SDKs retry); queue them instead of denying earlier ones.
    private void handleWebPermission(PermissionRequest request) {
        List<String> missing = new ArrayList<>();
        boolean any = false;
        for (String resource : request.getResources()) {
            String permission = androidPermissionFor(resource);
            if (permission == null || !declared(permission)) continue;
            any = true;
            if (!granted(permission) && !missing.contains(permission)) missing.add(permission);
        }
        if (!any) { request.deny(); return; }
        if (missing.isEmpty()) { answerWeb(request); return; }
        boolean alreadyAsking = !pendingWeb.isEmpty();
        pendingWeb.add(request);
        if (!alreadyAsking) requestPermissions(missing.toArray(new String[0]), REQ_WEB);
    }

    private void answerWeb(PermissionRequest request) {
        List<String> allowed = new ArrayList<>();
        for (String resource : request.getResources()) {
            String permission = androidPermissionFor(resource);
            if (permission != null && declared(permission) && granted(permission)) allowed.add(resource);
        }
        try {
            if (allowed.isEmpty()) request.deny(); else request.grant(allowed.toArray(new String[0]));
        } catch (Exception ignored) { }
    }

    private void handleGeo(String origin, GeolocationPermissions.Callback callback) {
        boolean fine = declared(Manifest.permission.ACCESS_FINE_LOCATION);
        boolean coarse = declared(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (!fine && !coarse) { callback.invoke(origin, false, false); return; }
        if ((fine && granted(Manifest.permission.ACCESS_FINE_LOCATION)) || (coarse && granted(Manifest.permission.ACCESS_COARSE_LOCATION))) {
            callback.invoke(origin, true, false);
            return;
        }
        geoOrigin = origin;
        geoCallback = callback;
        List<String> ask = new ArrayList<>();
        if (fine) ask.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (coarse) ask.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        requestPermissions(ask.toArray(new String[0]), REQ_GEO);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == REQ_WEB) {
            List<PermissionRequest> waiting = new ArrayList<>(pendingWeb);
            pendingWeb.clear();
            for (PermissionRequest request : waiting) answerWeb(request);
        } else if (code == REQ_GEO && geoCallback != null) {
            boolean ok = granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION);
            geoCallback.invoke(geoOrigin, ok, false);
            geoCallback = null;
        }
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code == REQ_FILE && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data));
            fileCallback = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
}
