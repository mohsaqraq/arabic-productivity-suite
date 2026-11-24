package app.almahfaza.wallet;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final String HOME_PAGE = "https://" + HOST + "/index.html";
    private static final int CREATE_FILE = 41;
    private static final int OPEN_BACKUP = 42;
    private static final int NOTIFICATION_PERMISSION = 43;
    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private WebView web;
    private SharedPreferences preferences;
    private boolean pickerOpen;
    private boolean pageReady;
    private int pendingReview;
    private volatile String importedBackup;

    @Override
    public void onCreate(Bundle previous) {
        super.onCreate(previous);
        pickerOpen = previous != null && previous.getBoolean("pickerOpen");
        pendingReview = getIntent().getIntExtra(CoachNotifications.OPEN_REVIEW, 0);
        getIntent().removeExtra(CoachNotifications.OPEN_REVIEW);
        preferences = getSharedPreferences("wallet", MODE_PRIVATE);
        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(248, 247, 252));
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(false);
        settings.setTextZoom(100);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !HOME_PAGE.equals(request.getUrl().toString());
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return localAsset(request.getUrl());
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                if (HOME_PAGE.equals(url)) {
                    pageReady = true;
                    deliverImport();
                    deliverReview();
                }
            }
        });
        web.addJavascriptInterface(new StorageBridge(), "WalletNative");
        setContentView(web);
        web.loadUrl(HOME_PAGE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        CoachNotifications.schedule(this, false);
        refreshCoachSheet();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        pendingReview = intent.getIntExtra(CoachNotifications.OPEN_REVIEW, 0);
        intent.removeExtra(CoachNotifications.OPEN_REVIEW);
        deliverReview();
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == NOTIFICATION_PERMISSION) {
            CoachNotifications.schedule(this, false);
            refreshCoachSheet();
        }
    }

    private void enableNotificationPermission(boolean allowSystemSettings) {
        if (CoachNotifications.allowed(this)) return;
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            (!CoachNotifications.wasAsked(this) || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))) {
            CoachNotifications.markAsked(this);
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION);
        } else if (allowSystemSettings) {
            Intent settings = Build.VERSION.SDK_INT >= 26 ?
                new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()) :
                new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
            startActivity(settings);
        }
    }

    private void refreshCoachSheet() {
        if (pageReady) web.evaluateJavascript("window.walletCoachRefresh && window.walletCoachRefresh()", null);
    }

    private void deliverReview() {
        if (!pageReady || pendingReview == 0) return;
        int review = pendingReview;
        pendingReview = 0;
        if (review != CoachNotifications.REAL && review != CoachNotifications.EXAMPLE) return;
        String notice = review == CoachNotifications.REAL ? CoachNotifications.lastNotice(this) : "";
        web.evaluateJavascript("window.walletCoachOpened && window.walletCoachOpened(" + JSONObject.quote(notice) + ")", null);
    }

    private WebResourceResponse localAsset(Uri uri) {
        String path = uri.getPath();
        if ("https".equals(uri.getScheme()) && HOST.equals(uri.getHost()) && path != null) {
            String type = null;
            switch (path) {
                case "/index.html": type = "text/html"; break;
                case "/style.css": type = "text/css"; break;
                case "/app.js": type = "text/javascript"; break;
                case "/wallet.webp": type = "image/webp"; break;
                case "/noto-Regular.woff":
                case "/noto-Bold.woff": type = "font/woff"; break;
            }
            if (type != null) {
                boolean text = type.startsWith("text/");
                try {
                    return new WebResourceResponse(type, text ? "UTF-8" : null,
                        getAssets().open(path.substring(1)));
                } catch (IOException ignored) {
                    // Fall through to a local empty response.
                }
            }
        }
        return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", null,
            new ByteArrayInputStream(new byte[0]));
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        out.putBoolean("pickerOpen", pickerOpen);
        super.onSaveInstanceState(out);
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.walletBack && window.walletBack()", result -> {
            if (!"true".equals(result)) finish();
        });
    }

    private final class StorageBridge {
        private final StringBuilder staged = new StringBuilder();
        private boolean stagingFailed;

        @JavascriptInterface
        public String read(boolean backup, int offset) {
            String source = backup ? importedBackup : preferences.getString("state", "");
            if (source == null || offset < 0 || offset >= source.length()) return "";
            int end = Math.min(source.length(), offset + 40000);
            if (end < source.length() && Character.isHighSurrogate(source.charAt(end - 1))) end--;
            return source.substring(offset, end);
        }

        @JavascriptInterface
        public void stage(String chunk, boolean first) {
            if (first) {
                staged.setLength(0);
                stagingFailed = false;
            }
            if (chunk == null || chunk.length() > 40000 || staged.length() + chunk.length() > MAX_BYTES) {
                stagingFailed = true;
            } else if (!stagingFailed) {
                staged.append(chunk);
            }
        }

        private String finishStaging() {
            String value = stagingFailed ? "" : staged.toString();
            staged.setLength(0);
            stagingFailed = false;
            return value;
        }

        @JavascriptInterface
        public boolean save() {
            String state = finishStaging();
            return !state.isEmpty() && state.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES
                && preferences.edit().putString("state", state).commit();
        }

        @JavascriptInterface
        public String coachSettings() {
            return CoachNotifications.configuration(MainActivity.this);
        }

        @JavascriptInterface
        public String coachPreview() {
            return CoachNotifications.preview(MainActivity.this);
        }

        @JavascriptInterface
        public void configureCoach(boolean enabled, int minutes) {
            runOnUiThread(() -> {
                boolean previouslyEnabled = CoachNotifications.enabled(MainActivity.this);
                CoachNotifications.configure(MainActivity.this, enabled, minutes);
                if (enabled && !previouslyEnabled) enableNotificationPermission(false);
                refreshCoachSheet();
            });
        }

        @JavascriptInterface
        public void dismissSpendingNotice() {
            CoachNotifications.dismiss(MainActivity.this);
        }

        @JavascriptInterface
        public void requestCoachPermission() {
            runOnUiThread(() -> enableNotificationPermission(true));
        }

        @JavascriptInterface
        public boolean testCoachNotification() {
            return CoachNotifications.enabled(MainActivity.this) &&
                CoachNotifications.sendExample(MainActivity.this);
        }

        @JavascriptInterface
        public void feedback() {
            runOnUiThread(() -> web.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY));
        }

        @JavascriptInterface
        public void exportFile(boolean backup) {
            String body = finishStaging();
            runOnUiThread(() -> launchExport(body, backup));
        }

        @JavascriptInterface
        public void openBackup() {
            runOnUiThread(() -> {
                if (pickerOpen) return;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
                launchPicker(intent, OPEN_BACKUP);
            });
        }

        @JavascriptInterface
        public void clearImport() {
            importedBackup = null;
        }
    }

    private File pendingExport() {
        return new File(getCacheDir(), "wallet-export.tmp");
    }

    private void launchExport(String body, boolean backup) {
        if (pickerOpen) return;
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0 || bytes.length > MAX_BYTES) {
            notifyResult(false);
            return;
        }
        try (FileOutputStream output = new FileOutputStream(pendingExport())) {
            output.write(bytes);
        } catch (IOException e) {
            notifyResult(false);
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(backup ? "application/json" : "text/csv")
            .putExtra(Intent.EXTRA_TITLE, backup ? "almahfaza-backup.json" : "almahfaza-expenses.csv");
        if (!launchPicker(intent, CREATE_FILE)) pendingExport().delete();
    }

    private boolean launchPicker(Intent intent, int request) {
        try {
            startActivityForResult(intent, request);
            pickerOpen = true;
            return true;
        } catch (ActivityNotFoundException e) {
            notifyResult(false);
            return false;
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        pickerOpen = false;
        if (result != RESULT_OK || data == null || data.getData() == null) {
            if (request == CREATE_FILE) pendingExport().delete();
            return;
        }
        Uri uri = data.getData();
        if (request == CREATE_FILE) {
            try {
                try (InputStream input = new FileInputStream(pendingExport());
                     OutputStream output = getContentResolver().openOutputStream(uri, "w")) {
                    copyBounded(input, output);
                }
                notifyResult(true);
            } catch (IOException | SecurityException e) {
                notifyResult(false);
            } finally {
                pendingExport().delete();
            }
        } else if (request == OPEN_BACKUP) {
            try (InputStream input = getContentResolver().openInputStream(uri);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                copyBounded(input, output);
                importedBackup = output.toString("UTF-8");
                deliverImport();
            } catch (IOException | SecurityException e) {
                notifyResult(false);
            }
        }
    }

    private static void copyBounded(InputStream input, OutputStream output) throws IOException {
        if (input == null || output == null) throw new IOException("File unavailable");
        byte[] buffer = new byte[8192];
        int count;
        int total = 0;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > MAX_BYTES) throw new IOException("File too large");
            output.write(buffer, 0, count);
        }
    }

    private void deliverImport() {
        if (pageReady && importedBackup != null) {
            web.evaluateJavascript("window.walletImport && window.walletImport()", null);
        }
    }

    private void notifyResult(boolean ok) {
        web.evaluateJavascript("window.walletFileResult && window.walletFileResult(" + ok + ")", null);
    }
}
