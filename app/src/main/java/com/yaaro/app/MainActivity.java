package com.yaaro.app;

import android.Manifest;
import android.media.AudioManager;
import android.webkit.JavascriptInterface;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.webkit.WebResourceResponse;
import java.io.IOException;
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

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String YAARO_URL = "https://qyaaro.github.io/yaaro-web/";
    private static final int PERMISSION_REQUEST_CODE = 1001;

    public static volatile boolean isAppForeground = false;
    
private final ActivityResultLauncher<Intent> ringtonePickerLauncher =
    registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> {
            if (result.getResultCode() == RESULT_OK
                    && result.getData() != null) {

                Uri uri = result.getData().getParcelableExtra(
                    android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI
                );

                YaaroRingtonePlayer.saveRingtone(this, uri);
            }
        }
    );

    private WebView webView;
    private ProgressBar progressBar;
    private PermissionRequest pendingWebPermissionRequest;
    private ValueCallback<Uri[]> filePathCallback;

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
        getWindow().setBackgroundDrawable(
    new android.graphics.drawable.ColorDrawable(
        android.graphics.Color.WHITE
    )
);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        webView = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progress);

        // Register the Telecom account while the app is visible; this does not
        // initiate a call, request microphone access, or open another screen.
        YaaroTelecomAccount.register(getApplicationContext());

        fetchAndStoreFcmToken();
        setupWebView();
        ensurePermissionsThenLoad();
    }
   @Override
protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);

    if (webView != null) {
        webView.evaluateJavascript(
            "if (typeof checkNativeAccept === 'function') checkNativeAccept();" +
            "if (typeof checkNativeDecline === 'function') checkNativeDecline();" +
            "if (typeof checkNativeViewCall === 'function') checkNativeViewCall();",
            null
        );
    }
}
    private void fetchAndStoreFcmToken() {
        try {
            FirebaseMessaging.getInstance().getToken()
                    .addOnCompleteListener(task -> {
                        if (!task.isSuccessful() || task.getResult() == null) return;
                        String token = task.getResult();
                        getSharedPreferences("yaaro", MODE_PRIVATE)
                                .edit()
                                .putString("fcm_token", token)
                                .apply();
                    });
        } catch (Exception ignored) {}
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        webView.addJavascriptInterface(new Object() {
   
@JavascriptInterface
public void signInNativeFirebase(String email, String password, String expectedUid) {
    // Only accept credentials from the trusted QYAARO origin.
    if (!isTrustedYaaroPage() || email == null || email.isEmpty() ||
            password == null || password.isEmpty() ||
            expectedUid == null || expectedUid.isEmpty()) return;
    FirebaseAuth auth = FirebaseAuth.getInstance();
    auth.signInWithEmailAndPassword(email, password)
        .addOnCompleteListener(task -> {
            FirebaseUser nativeUser = auth.getCurrentUser();
            if (!task.isSuccessful() || nativeUser == null ||
                    !expectedUid.equals(nativeUser.getUid()) || !nativeUser.isEmailVerified()) {
                auth.signOut();
                Log.w("YaaroNativeAuth", "Native Firebase sign-in or identity check failed");
                notifyNativeAuthResult(expectedUid, false);
                return;
            }
            Log.d("YaaroNativeAuth", "Native Firebase session established");
            notifyNativeAuthResult(expectedUid, true);
        });
}

private void notifyNativeAuthResult(String expectedUid, boolean success) {
    runOnUiThread(() -> {
        if (webView == null || !isTrustedYaaroPage()) return;
        String uid = org.json.JSONObject.quote(expectedUid);
        webView.evaluateJavascript(
            "if (typeof window.onYaaroNativeAuthResult === 'function') " +
            "window.onYaaroNativeAuthResult(" + uid + "," + success + ");", null);
    });
}

@JavascriptInterface
public boolean isNativeFirebaseSignedIn(String expectedUid) {
    if (!isTrustedYaaroPage() || expectedUid == null) return false;
    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
    return user != null && expectedUid.equals(user.getUid()) && user.isEmailVerified();
}

@JavascriptInterface
public void verifyNativeFirebaseDatabase(String expectedUid) {
    if (!isTrustedYaaroPage() || expectedUid == null || expectedUid.isEmpty()) return;
    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
    if (user == null || !expectedUid.equals(user.getUid()) || !user.isEmailVerified()) {
        notifyNativeDatabaseResult(expectedUid, false);
        return;
    }
    // Read only the signed-in user's profile; Firebase Database rules decide access.
    com.google.firebase.database.FirebaseDatabase.getInstance()
        .getReference("users").child(user.getUid()).get()
        .addOnCompleteListener(task ->
            notifyNativeDatabaseResult(expectedUid, task.isSuccessful() &&
                task.getResult() != null && task.getResult().exists()));
}

private void notifyNativeDatabaseResult(String expectedUid, boolean success) {
    runOnUiThread(() -> {
        if (webView == null || !isTrustedYaaroPage()) return;
        webView.evaluateJavascript(
            "if (typeof window.onYaaroNativeDatabaseResult === 'function') " +
            "window.onYaaroNativeDatabaseResult(" +
            org.json.JSONObject.quote(expectedUid) + "," + success + ");", null);
    });
}

@JavascriptInterface
public void signOutNativeFirebase() {
    if (!isTrustedYaaroPage()) return;
    FirebaseAuth.getInstance().signOut();
    // Prevent a later notification action from reusing the previous user's
    // cached credentials after logout or account switching.
    getSharedPreferences("yaaro", MODE_PRIVATE).edit()
        .remove("firebase_id_token")
        .remove("firebase_uid")
        .apply();
}

@JavascriptInterface
public void setFirebaseIdToken(String token) {
    getSharedPreferences("yaaro", MODE_PRIVATE)
        .edit()
        .putString("firebase_id_token", token == null ? "" : token)
        .apply();
}

@JavascriptInterface
public void setFirebaseUid(String uid) {
    getSharedPreferences("yaaro", MODE_PRIVATE)
        .edit()
        .putString("firebase_uid", uid == null ? "" : uid)
        .apply();
}
         
// Read-only bridge for notification-body taps. The web call screen can
// consume this once a matching incoming/active call is verified.
@JavascriptInterface
public String getPendingViewCallId() {
    Intent intent = MainActivity.this.getIntent();
    if (intent == null) return "";

    String action = intent.getStringExtra(
        YaaroFirebaseMessagingService.EXTRA_FCM_ACTION
    );
    if (!"view".equals(action)) return "";

    String callId = intent.getStringExtra(
        YaaroFirebaseMessagingService.EXTRA_CALL_ID
    );
    return callId != null ? callId : "";
}

@JavascriptInterface
public void completePendingView(String completedCallId) {
    if (completedCallId == null || completedCallId.isEmpty()) return;
    runOnUiThread(() -> {
        Intent intent = getIntent();
        if (intent == null) return;
        String action = intent.getStringExtra(
            YaaroFirebaseMessagingService.EXTRA_FCM_ACTION
        );
        String callId = intent.getStringExtra(
            YaaroFirebaseMessagingService.EXTRA_CALL_ID
        );
        if (!"view".equals(action) || !completedCallId.equals(callId)) return;
        intent.removeExtra(YaaroFirebaseMessagingService.EXTRA_FCM_ACTION);
        intent.removeExtra(YaaroFirebaseMessagingService.EXTRA_CALL_ID);
    });
}

@JavascriptInterface
public String getPendingCallId() {
    Intent intent = MainActivity.this.getIntent();
    if (intent == null) return "";

    String action = intent.getStringExtra(
        YaaroFirebaseMessagingService.EXTRA_FCM_ACTION
    );

    if (!"accept".equals(action)) return "";

    String callId = intent.getStringExtra(
        YaaroFirebaseMessagingService.EXTRA_CALL_ID
    );

    return callId != null ? callId : "";
}

@JavascriptInterface
public void completePendingAccept(String completedCallId) {
    if (completedCallId == null || completedCallId.isEmpty()) return;

    runOnUiThread(() -> {
        Intent intent = getIntent();
        if (intent == null) return;

        String action = intent.getStringExtra(
            YaaroFirebaseMessagingService.EXTRA_FCM_ACTION
        );

        String callId = intent.getStringExtra(
            YaaroFirebaseMessagingService.EXTRA_CALL_ID
        );

        if (!"accept".equals(action) ||
            !completedCallId.equals(callId)) return;

        intent.removeExtra(
            YaaroFirebaseMessagingService.EXTRA_FCM_ACTION
        );
        intent.removeExtra(
            YaaroFirebaseMessagingService.EXTRA_CALL_ID
        );

        YaaroRingtonePlayer.stop();
        YaaroFirebaseMessagingService.cancelCallNotification(MainActivity.this, completedCallId);
    });
}
            
@JavascriptInterface
    public String getPendingDeclineCallId() {
    Intent intent = MainActivity.this.getIntent();
    if (intent == null) return "";

    String action = intent.getStringExtra(
        YaaroFirebaseMessagingService.EXTRA_FCM_ACTION
    );

    if (!"decline".equals(action)) return "";

    String callId = intent.getStringExtra(
        YaaroFirebaseMessagingService.EXTRA_CALL_ID
    );

    return callId != null ? callId : "";
}
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
            
@JavascriptInterface
public void chooseRingtone() {
    runOnUiThread(() -> {
        try {
            Intent intent = new Intent(
                android.media.RingtoneManager.ACTION_RINGTONE_PICKER
            );
            intent.putExtra(
                android.media.RingtoneManager.EXTRA_RINGTONE_TYPE,
                android.media.RingtoneManager.TYPE_RINGTONE
            );
            intent.putExtra(
                android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT,
                false
            );
            ringtonePickerLauncher.launch(intent);
        } catch (Exception e) {
            android.util.Log.e("YaaroRingtone", "Picker failed", e);
        }
    });
}

            @JavascriptInterface
            public void stopRingtone() {
            runOnUiThread(() -> {
              YaaroRingtonePlayer.stop();
    });
}
            @JavascriptInterface
            public String getFcmToken() {
                SharedPreferences sp = getSharedPreferences("yaaro", MODE_PRIVATE);
                String t = sp.getString("fcm_token", "");
                return t != null ? t : "";
            }
        }, "YaaroAndroid");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                // Feature-branch test APK only: serve the reviewed web feature HTML
                // at the existing trusted HTTPS origin. Never intercept Firebase APIs.
                Uri uri = request.getUrl();
                if (request.isForMainFrame() && "GET".equalsIgnoreCase(request.getMethod()) &&
                        YAARO_URL.equals(uri.toString())) {
                    try {
                        WebResourceResponse response = new WebResourceResponse(
                            "text/html", "UTF-8",
                            getAssets().open("qa_auth_preview.html"));
                        response.setResponseHeaders(java.util.Collections.singletonMap(
                            "Cache-Control", "no-store"));
                        return response;
                    } catch (IOException e) {
                        Log.e("YaaroAuthPreview", "Bundled web preview unavailable", e);
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri target = request.getUrl();
                if (!request.isForMainFrame()) {
                    // The legacy JavaScript interface is exposed to every frame.
                    // Reject cross-origin frame navigations until the bridge is
                    // migrated to an origin-scoped WebMessageListener.
                    return !isTrustedYaaroUri(target);
                }
                // Do not navigate the privileged bridge to third-party pages.
                return !isTrustedYaaroUri(target);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                view.evaluateJavascript(
                "if (typeof checkNativeAccept === 'function') checkNativeAccept();" +
                "if (typeof checkNativeDecline === 'function') checkNativeDecline();" +
            "if (typeof checkNativeViewCall === 'function') checkNativeViewCall();",
                null
              );
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    Uri origin = request.getOrigin();
                    if (!isTrustedYaaroUri(origin)) {
                        request.deny();
                        return;
                    }
                    // Grant only the camera/microphone resources the app supports.
                    java.util.List<String> allowed = new java.util.ArrayList<>();
                    for (String resource : request.getResources()) {
                        if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource) ||
                                PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) {
                            allowed.add(resource);
                        } else {
                            request.deny();
                            return;
                        }
                    }
                    if (allowed.isEmpty()) {
                        request.deny();
                        return;
                    }
                    if (hasAllPermissions()) {
                        request.grant(allowed.toArray(new String[0]));
                    } else {
                        pendingWebPermissionRequest = request;
                        requestAndroidPermissions();
                    }
                });
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                if (!isTrustedYaaroPage()) {
                    filePathCallback.onReceiveValue(null);
                    return false;
                }
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }
                MainActivity.this.filePathCallback = filePathCallback;
                Intent intent = fileChooserParams.createIntent();
                try {
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

    private boolean onlySupportedCaptureResources(String[] resources) {
        if (resources == null || resources.length == 0) return false;
        for (String resource : resources) {
            if (!PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource) &&
                    !PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) return false;
        }
        return true;
    }

    private boolean isTrustedYaaroUri(Uri uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) ||
                !"qyaaro.github.io".equalsIgnoreCase(uri.getHost()) ||
                uri.getPort() != -1) return false;
        String path = uri.getPath();
        return "/yaaro-web/".equals(path) || "/yaaro-web/index.html".equals(path);
    }

    private boolean isTrustedYaaroPage() {
        if (webView == null) return false;
        // WebView's current URL is not proof of the calling iframe's origin.
        // Keep this check as defense-in-depth, not as frame authentication.
        return isTrustedYaaroUri(Uri.parse(webView.getUrl() == null ? "" : webView.getUrl()));
    }

    private void ensurePermissionsThenLoad() {
    webView.loadUrl(YAARO_URL);

    if (!hasAllPermissions()) {
        requestAndroidPermissions();
    }
}
    private boolean hasAllPermissions() {
        for (String p : getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private String[] getRequiredPermissions() {
        List<String> list = new ArrayList<>();
        list.add(Manifest.permission.CAMERA);
        list.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= 33) {
            list.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        return list.toArray(new String[0]);
    }

    private void requestAndroidPermissions() {
        ActivityCompat.requestPermissions(this, getRequiredPermissions(), PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (pendingWebPermissionRequest != null && hasAllPermissions()) {
                if (isTrustedYaaroUri(pendingWebPermissionRequest.getOrigin()) &&
                        onlySupportedCaptureResources(pendingWebPermissionRequest.getResources())) {
                    pendingWebPermissionRequest.grant(pendingWebPermissionRequest.getResources());
                } else {
                    pendingWebPermissionRequest.deny();
                }
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
    isAppForeground = true;

    if (webView != null) {
        webView.onResume();
        webView.evaluateJavascript(
            "if (typeof checkNativeAccept === 'function') " +
            "checkNativeAccept();",
            null
        );
    }
}

    @Override
    protected void onPause() {
    isAppForeground = false;
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
