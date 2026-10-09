package com.yaaro.app;

import android.Manifest;
import android.media.AudioManager;
import android.webkit.JavascriptInterface;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * Yaaro Android test wrapper.
 * Loads the EXISTING Netlify web app. Does not modify Yaaro web code.
 */
public class MainActivity extends AppCompatActivity {

    private static final String YAARO_URL = YAARO_URL = "https://qyaaro.github.io/yaaro-web/";
    private static final int PERMISSION_REQUEST_CODE = 1001;

    private WebView webView;
    private ProgressBar progressBar;
    private PermissionRequest pendingWebPermissionRequest;
    private ValueCallback<Uri[]> filePathCallback;

    private final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
    };

    private final ActivityResultLauncher<Intent> fileChooserLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (filePathCallback == null) return;
                Uri[] results = null;
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Intent data = result.getData();
                    if (data.getClipData() != null) {
                        int count = data.getClipData().getItemCount();
                        results = new Uri[count];
                        for (int i = 0; i < count; i++) {
                            results[i] = data.getClipData().getItemAt(i).getUri();
                        }
                    } else if (data.getData() != null) {
                        results = new Uri[]{data.getData()};
                    }
                }
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            });

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        webView = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progress);

        setupWebView();
        ensurePermissionsThenLoad();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        // Bridge for real speakerphone control from web app
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void setSpeakerphoneOn(boolean on) {
                runOnUiThread(() -> {
                    try {
                        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
                        if (am != null) {
                            am.setMode(AudioManager.MODE_IN_COMMUNICATION);
                            am.setSpeakerphoneOn(on);
                        }
                    } catch (Exception ignored) {}
                });
            }
        }, "YaaroAndroid");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String host = request.getUrl().getHost();
                if (host != null && host.contains("netlify.app")) {
                    return false;
                }
                if ("https".equals(request.getUrl().getScheme())) {
                    return false;
                }
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    if (hasAllPermissions()) {
                        request.grant(request.getResources());
                    } else {
                        pendingWebPermissionRequest = request;
                        requestAndroidPermissions();
                    }
                });
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }
                MainActivity.this.filePathCallback = filePathCallback;

                Intent intent = fileChooserParams.createIntent();
                try {
                    // Allow multiple if web requests it
                    if (fileChooserParams.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    }
                    fileChooserLauncher.launch(intent);
                } catch (Exception e) {
                    MainActivity.this.filePathCallback = null;
                    return false;
                }
                return true;
            }

            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress >= 100) {
                    progressBar.setVisibility(View.GONE);
                } else {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(newProgress);
                }
            }
        });
    }

    private void ensurePermissionsThenLoad() {
        if (hasAllPermissions()) {
            webView.loadUrl(YAARO_URL);
        } else {
            requestAndroidPermissions();
            webView.loadUrl(YAARO_URL);
        }
    }

    private boolean hasAllPermissions() {
        for (String p : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void requestAndroidPermissions() {
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (pendingWebPermissionRequest != null && hasAllPermissions()) {
                pendingWebPermissionRequest.grant(pendingWebPermissionRequest.getResources());
                pendingWebPermissionRequest = null;
            } else if (pendingWebPermissionRequest != null) {
                pendingWebPermissionRequest.deny();
                pendingWebPermissionRequest = null;
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
