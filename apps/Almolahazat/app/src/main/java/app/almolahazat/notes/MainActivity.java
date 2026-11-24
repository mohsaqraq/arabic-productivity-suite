package app.almolahazat.notes;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final String HOME = "https://" + HOST + "/index.html";
    private static final int BACKUP = 61, RESTORE = 62, MARKDOWN = 63, KEEP = 64, PHOTO = 65;
    private final ExecutorService files = Executors.newSingleThreadExecutor();
    private WebView web;
    private NotesStore store;
    private SharedPreferences settings;
    private boolean ready, picking, busy;
    private String photoDraft, deferred;

    @Override public void onCreate(Bundle previous) {
        super.onCreate(previous);
        store = new NotesStore(this);
        settings = getSharedPreferences("notes-preferences", MODE_PRIVATE);
        if (previous != null) {
            picking = previous.getBoolean("picking");
            photoDraft = previous.getString("photoDraft");
        }
        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(255, 252, 244));
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings config = web.getSettings();
        config.setJavaScriptEnabled(true);
        config.setAllowFileAccess(false);
        config.setAllowContentAccess(false);
        config.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        config.setSupportMultipleWindows(false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !HOME.equals(request.getUrl().toString());
            }

            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return localResource(request.getUrl());
            }

            @Override public void onPageFinished(WebView view, String url) {
                if (!HOME.equals(url)) return;
                ready = true;
                if (busy) js("window.notesWorking(true)");
                if (deferred != null) { js(deferred); deferred = null; }
            }
        });
        web.addJavascriptInterface(new Bridge(), "NotesNative");
        setContentView(web);
        web.loadUrl(HOME);
    }

    private WebResourceResponse localResource(Uri uri) {
        String path = uri.getPath();
        if ("https".equals(uri.getScheme()) && HOST.equals(uri.getHost()) && path != null) {
            if (path.startsWith("/photo/")) {
                try {
                    byte[] jpeg = store.photo(path.substring(7));
                    if (jpeg != null) return new WebResourceResponse("image/jpeg", null, new ByteArrayInputStream(jpeg));
                } catch (RuntimeException ignored) {}
            } else {
                String type;
                switch (path) {
                    case "/index.html": type = "text/html"; break;
                    case "/style.css": type = "text/css"; break;
                    case "/app.js": type = "application/javascript"; break;
                    case "/noto-Regular.woff": case "/noto-Bold.woff": type = "font/woff"; break;
                    case "/blank.webp": type = "image/webp"; break;
                    case "/brand.png": type = "image/png"; break;
                    default: type = null; break;
                }
                if (type != null) {
                    try {
                        return new WebResourceResponse(type, type.startsWith("text/") || type.contains("javascript") ? "UTF-8" : null,
                            getAssets().open(path.substring(1)));
                    } catch (IOException ignored) {}
                }
            }
        }
        return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", null, new ByteArrayInputStream(new byte[0]));
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putBoolean("picking", picking);
        out.putString("photoDraft", photoDraft);
        super.onSaveInstanceState(out);
    }

    @Override protected void onPause() {
        js("window.notesFlush && window.notesFlush()");
        super.onPause();
    }

    @Override public void onBackPressed() {
        if (busy) return;
        web.evaluateJavascript("window.notesBack && window.notesBack()", result -> {
            if (!"true".equals(result)) finish();
        });
    }

    @Override protected void onDestroy() {
        ready = false;
        web.removeJavascriptInterface("NotesNative");
        web.destroy();
        files.execute(store::close);
        files.shutdown();
        super.onDestroy();
    }

    private void js(String code) {
        if (ready && !isDestroyed()) web.evaluateJavascript(code, null);
    }

    private final class Bridge {
        @JavascriptInterface public String newId() { return UUID.randomUUID().toString(); }

        @JavascriptInterface public String list(String state, String type, String label, String search, String order, int offset) {
            try { return store.list(state, type, label, search, order, offset); }
            catch (Exception e) { return ""; }
        }

        @JavascriptInterface public String labels(String state) {
            try { return store.labels(state); }
            catch (Exception e) { return ""; }
        }

        @JavascriptInterface public String get(String id) {
            try {
                Note note = store.get(id);
                return note == null ? "" : note.json(false).toString();
            } catch (Exception e) { return ""; }
        }

        @JavascriptInterface public boolean save(String json) {
            try {
                if (json == null || json.length() > Note.MAX_JSON) return false;
                store.save(Note.parse(new JSONObject(json)));
                return true;
            } catch (Exception e) { return false; }
        }

        @JavascriptInterface public boolean erase(String id) {
            try { return store.erase(id); }
            catch (RuntimeException e) { return false; }
        }

        @JavascriptInterface public String preferences() {
            try {
                return new JSONObject().put("layout", settings.getString("layout", "grid"))
                    .put("order", settings.getString("order", "edited"))
                    .put("font", settings.getString("font", "standard")).toString();
            } catch (JSONException e) { return "{}"; }
        }

        @JavascriptInterface public void preference(String name, String value) {
            boolean valid = ("layout".equals(name) && ("grid".equals(value) || "single".equals(value)))
                || ("order".equals(name) && ("edited".equals(value) || "created".equals(value) || "title".equals(value)))
                || ("font".equals(name) && ("standard".equals(value) || "large".equals(value)));
            if (valid) settings.edit().putString(name, value).apply();
        }

        @JavascriptInterface public void document(String type) { runOnUiThread(() -> openDocument(type)); }

        @JavascriptInterface public void pickPhoto(String json) {
            if (json == null || json.length() > Note.MAX_JSON) return;
            try {
                Note draft = Note.parse(new JSONObject(json));
                // Large saved notes are already in SQLite. Only keep context for a new empty draft.
                String context = draft.meaningful() ? new JSONObject().put("id", draft.id).toString() : draft.json(false).toString();
                runOnUiThread(() -> {
                    if (busy || picking) return;
                    photoDraft = context;
                    pick(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*"), PHOTO);
                });
            } catch (JSONException e) { runOnUiThread(() -> notifyResult(false, "تعذّر فتح الصور.", null)); }
        }

        @JavascriptInterface public void share(String text) {
            if (text == null || text.isEmpty() || text.length() > Note.MAX_JSON) return;
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
                try { startActivity(Intent.createChooser(intent, "مشاركة نص الملاحظة")); }
                catch (ActivityNotFoundException e) { notifyResult(false, "تعذّرت مشاركة النص.", null); }
            });
        }
    }

    private void openDocument(String type) {
        if (busy || picking) return;
        switch (type) {
            case "backup":
                pick(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip")
                    .putExtra(Intent.EXTRA_TITLE, "Almolahazat-backup.zip"), BACKUP);
                break;
            case "markdown":
                pick(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip")
                    .putExtra(Intent.EXTRA_TITLE, "Almolahazat-Markdown.zip"), MARKDOWN);
                break;
            case "restore": pick(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*"), RESTORE); break;
            case "keep": pick(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*"), KEEP); break;
            default: break;
        }
    }

    private void pick(Intent intent, int code) {
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try { startActivityForResult(intent, code); picking = true; }
        catch (ActivityNotFoundException e) { notifyResult(false, "تعذّر فتح منتقي الملفات.", null); }
    }

    @Override protected void onActivityResult(int request, int status, Intent data) {
        super.onActivityResult(request, status, data);
        picking = false;
        if (request < BACKUP || request > PHOTO || status != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        String pending = photoDraft;
        photoDraft = null;
        busy = true;
        js("window.notesWorking(true)");
        files.execute(() -> process(request, uri, pending));
    }

    private void process(int request, Uri uri, String pending) {
        try {
            if (request == PHOTO) {
                if (pending == null) throw new IOException("Invalid photo target");
                Note draft = Note.parse(new JSONObject(pending));
                byte[] jpeg = PhotoDecoder.read(getContentResolver(), uri);
                store.attach(draft, UUID.randomUUID().toString(), jpeg);
                finishFile(true, "أُضيفت الصورة.", draft.id);
            } else if (request == RESTORE || request == KEEP) {
                NotesFiles.ImportResult result;
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    if (input == null) throw new IOException("Cannot read file");
                    result = NotesFiles.restore(store, new BufferedInputStream(input), request == KEEP);
                }
                String message = result.added == 0 ? "ما في ملاحظات جديدة للاستيراد." : "الملاحظات المضافة: " + result.added;
                if (result.existing > 0) message += " · موجودة سابقًا: " + result.existing;
                if (result.attachments > 0) message += " · مرفقات Keep غير المستوردة: " + result.attachments;
                finishFile(true, message, null);
            } else {
                try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                    if (output == null) throw new IOException("Cannot write file");
                    NotesFiles.export(store, new BufferedOutputStream(output), request == MARKDOWN);
                }
                finishFile(true, request == BACKUP ? "حُفظت النسخة الاحتياطية." : "حُفظ ملف Markdown.", null);
            }
        } catch (Exception e) {
            String message = request == PHOTO ? "تعذّر إضافة هذه الصورة." :
                request == KEEP ? "تعذّر قراءة ملاحظات Google Keep من الملف." :
                request == RESTORE ? "تعذّر الاستيراد. اختر نسخة احتياطية صادرة من الملاحظات." :
                "تعذّر حفظ الملف. جرّب اختيار مكان آخر.";
            finishFile(false, message, null);
        }
    }

    private void finishFile(boolean success, String message, String id) {
        runOnUiThread(() -> { busy = false; notifyResult(success, message, id); });
    }

    private void notifyResult(boolean success, String message, String id) {
        String code = "window.notesResult(" + success + "," + JSONObject.quote(message) + ","
            + (id == null ? "null" : JSONObject.quote(id)) + ")";
        if (ready) js(code); else deferred = code;
    }
}
