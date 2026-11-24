package app.alyawmiyat.journal;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final String HOME = "https://" + HOST + "/index.html";
    private static final int PICK_PHOTOS = 51;
    private static final int WRITE_BACKUP = 52;
    private static final int READ_BACKUP = 53;
    private static final int WRITE_HTML = 54;
    private static final int NOTIFICATION_PERMISSION = 55;
    private static final int CHUNK = 32_000;
    private WebView web;
    private JournalStore store;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private boolean pageReady;
    private boolean pendingLaunch;
    private String pendingRevisit;
    private boolean pickerOpen;
    private boolean busy;
    private String photoEntry = "";
    private String photoDay = "";
    private String exportEntry = "";

    @Override public void onCreate(Bundle previous) {
        super.onCreate(previous);
        store = new JournalStore(this);
        pendingLaunch = getIntent().getBooleanExtra(Reminders.OPEN,false);
        getIntent().removeExtra(Reminders.OPEN);
        pendingRevisit = getIntent().getStringExtra(RevisitReminders.OPEN);
        getIntent().removeExtra(RevisitReminders.OPEN);
        if (previous != null) {
            pickerOpen = previous.getBoolean("picker");
            photoEntry = previous.getString("photo-entry","");
            photoDay = previous.getString("photo-day","");
            exportEntry = previous.getString("export-entry","");
        }
        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(244,248,243));
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(false);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !HOME.equals(request.getUrl().toString());
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return asset(request.getUrl());
            }
            @Override public void onPageFinished(WebView view,String url) {
                if (HOME.equals(url)) {
                    pageReady = true;
                    routeReminder();
                }
            }
        });
        web.addJavascriptInterface(new Bridge(),"JournalNative");
        setContentView(web);
        web.loadUrl(HOME);
    }

    private WebResourceResponse asset(Uri uri) {
        String path = uri.getPath();
        if (!"https".equals(uri.getScheme()) || !HOST.equals(uri.getHost()) || path == null) return missing();
        if (path.startsWith("/media/")) {
            try {
                byte[] photo = store.photo(path.substring(7));
                if (photo != null) {
                    return new WebResourceResponse("image/jpeg",null,200,"OK",
                        java.util.Collections.singletonMap("Cache-Control","no-store"),new ByteArrayInputStream(photo));
                }
            } catch (RuntimeException ignored) {}
            return missing();
        }
        String type;
        switch (path) {
            case "/index.html": type = "text/html"; break;
            case "/style.css": type = "text/css"; break;
            case "/app.js": type = "text/javascript"; break;
            case "/journal.webp": type = "image/webp"; break;
            case "/noto-Regular.woff": case "/noto-Bold.woff": type = "font/woff"; break;
            default: return missing();
        }
        try {
            return new WebResourceResponse(type,type.startsWith("text/") ? "UTF-8" : null,getAssets().open(path.substring(1)));
        } catch (IOException ignored) { return missing(); }
    }

    private WebResourceResponse missing() {
        return new WebResourceResponse("text/plain","UTF-8",404,"Not Found",null,new ByteArrayInputStream(new byte[0]));
    }

    @Override protected void onResume() {
        super.onResume();
        Reminders.schedule(this,false);
        RevisitReminders.schedule(this,false);
        js("window.journalReminderChanged && window.journalReminderChanged()");
    }

    @Override protected void onPause() {
        js("window.journalFlush && window.journalFlush()");
        super.onPause();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        pendingLaunch = intent.getBooleanExtra(Reminders.OPEN,false);
        intent.removeExtra(Reminders.OPEN);
        pendingRevisit = intent.getStringExtra(RevisitReminders.OPEN);
        intent.removeExtra(RevisitReminders.OPEN);
        routeReminder();
    }

    private void routeReminder() {
        if (!pageReady) return;
        if (EntryData.validId(pendingRevisit)) {
            String id = JSONObject.quote(pendingRevisit);
            pendingRevisit = null;
            js("window.journalRevisitOpened && window.journalRevisitOpened(" + id + ")");
        } else if (pendingLaunch) {
            pendingLaunch = false;
            js("window.journalReminderOpened && window.journalReminderOpened()");
        }
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putBoolean("picker",pickerOpen);
        out.putString("photo-entry",photoEntry);
        out.putString("photo-day",photoDay);
        out.putString("export-entry",exportEntry);
        super.onSaveInstanceState(out);
    }

    @Override public void onBackPressed() {
        if (busy) return;
        web.evaluateJavascript("window.journalBack && window.journalBack()", result -> {
            if (!"true".equals(result)) finish();
        });
    }

    @Override protected void onDestroy() {
        pageReady = false;
        if (busy) io.execute(store::close);
        else store.close();
        io.shutdown();
        web.destroy();
        super.onDestroy();
    }

    private void js(String expression) {
        if (pageReady && !isDestroyed()) web.evaluateJavascript(expression,null);
    }

    private void permission(boolean openSettings,boolean revisit) {
        if (revisit ? RevisitReminders.allowed(this) : Reminders.allowed(this)) return;
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            (!Reminders.prefs(this).getBoolean("asked",false) || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))) {
            Reminders.prefs(this).edit().putBoolean("asked",true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIFICATION_PERMISSION);
        } else if (openSettings) {
            Intent settings = Build.VERSION.SDK_INT >= 26 ?
                new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()) :
                new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:" + getPackageName()));
            startActivity(settings);
        }
    }

    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(request,permissions,results);
        if (request == NOTIFICATION_PERMISSION) {
            Reminders.schedule(this,false);
            RevisitReminders.schedule(this,false);
            js("window.journalReminderChanged && window.journalReminderChanged()");
        }
    }

    private final class Bridge {
        private String reading = "";
        private final StringBuilder staged = new StringBuilder();
        private boolean invalidStage;

        @JavascriptInterface public String list(String query,String day,int offset) {
            try { return store.list(query,day,offset); }
            catch (Exception e) { return ""; }
        }
        @JavascriptInterface public String entry(String id,int offset) {
            try {
                if (offset == 0) reading = store.read(id);
                if (offset < 0 || offset >= reading.length()) { reading = ""; return ""; }
                int end = Math.min(reading.length(),offset + CHUNK);
                if (end < reading.length() && Character.isHighSurrogate(reading.charAt(end - 1))) end--;
                return reading.substring(offset,end);
            } catch (Exception e) { reading = ""; return ""; }
        }
        @JavascriptInterface public void stage(String part,boolean first) {
            if (first) { staged.setLength(0); invalidStage = false; }
            if (part == null || part.length() > CHUNK || staged.length() + part.length() > EntryData.MAX_JSON) {
                invalidStage = true;
            } else if (!invalidStage) staged.append(part);
        }
        @JavascriptInterface public boolean save() {
            try {
                if (invalidStage) return false;
                if (store.save(EntryData.parse(staged.toString()))) {
                    RevisitReminders.schedule(getApplicationContext(),true);
                }
                return true;
            } catch (Exception e) { return false; }
            finally { staged.setLength(0); invalidStage = false; }
        }
        @JavascriptInterface public boolean delete(String id) {
            try {
                if (!store.deleteEntry(id)) return false;
                RevisitReminders.dismiss(MainActivity.this,id);
                RevisitReminders.schedule(getApplicationContext(),true);
                return true;
            } catch (RuntimeException e) { return false; }
        }
        @JavascriptInterface public String photos(String id) {
            try { return store.photos(id); } catch (RuntimeException e) { return "[]"; }
        }
        @JavascriptInterface public boolean removePhoto(String entry,String photo) {
            try { return store.deletePhoto(entry,photo); } catch (RuntimeException e) { return false; }
        }
        @JavascriptInterface public void addPhotos(String id,String day,int available) {
            if (EntryData.validId(id) && EntryData.validDate(day) && available >= 1 && available <= JournalStore.MAX_PHOTOS) {
                runOnUiThread(() -> pickPhotos(id,day,available));
            }
        }
        @JavascriptInterface public void document(String format,String id) {
            runOnUiThread(() -> pickDocument(format,id));
        }
        @JavascriptInterface public String reminderSettings() { return Reminders.settings(MainActivity.this); }
        @JavascriptInterface public int penSize() { return Reminders.prefs(MainActivity.this).getInt("pen-size",1); }
        @JavascriptInterface public void setPenSize(int size) {
            if (size >= 0 && size <= 2) Reminders.prefs(MainActivity.this).edit().putInt("pen-size",size).apply();
        }
        @JavascriptInterface public String revisitDate(String day,String period) {
            return RevisitReminders.dueDate(day,period);
        }
        @JavascriptInterface public String revisitData(String id) {
            try { return store.revisitData(id); }
            catch (Exception e) { return ""; }
        }
        @JavascriptInterface public String setRevisit(String id,String period) {
            try {
                String data = store.setRevisit(id,period);
                RevisitReminders.dismiss(MainActivity.this,id);
                if (!period.isEmpty()) {
                    RevisitReminders.createChannel(MainActivity.this);
                    runOnUiThread(() -> permission(false,true));
                }
                RevisitReminders.schedule(getApplicationContext(),true);
                return data;
            } catch (Exception e) { return ""; }
        }
        @JavascriptInterface public boolean revisitAllowed() { return RevisitReminders.allowed(MainActivity.this); }
        @JavascriptInterface public void revisitPermission() { runOnUiThread(() -> permission(true,true)); }
        @JavascriptInterface public void configureReminder(boolean enabled,int minute) {
            runOnUiThread(() -> {
                boolean old = Reminders.enabled(MainActivity.this);
                Reminders.configure(MainActivity.this,enabled,minute);
                RevisitReminders.schedule(MainActivity.this,true);
                if (enabled && !old) permission(false,false);
                js("window.journalReminderChanged && window.journalReminderChanged()");
            });
        }
        @JavascriptInterface public void reminderPermission() { runOnUiThread(() -> permission(true,false)); }
        @JavascriptInterface public boolean reminderTest() { return Reminders.sendTest(MainActivity.this); }
    }

    private void pickPhotos(String id,String day,int available) {
        if (pickerOpen || busy) return;
        photoEntry = id;
        photoDay = day;
        if (Build.VERSION.SDK_INT >= 33) {
            Intent photoPicker = new Intent(MediaStore.ACTION_PICK_IMAGES).setType("image/*");
            if (available > 1) photoPicker.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX,available);
            if (launch(photoPicker,PICK_PHOTOS)) return;
        }
        Intent open = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE,available > 1);
        if (!launch(open,PICK_PHOTOS)) result(false,"تعذّر فتح منتقي الصور.","photo");
    }

    private void pickDocument(String format,String id) {
        if (pickerOpen || busy) return;
        Intent intent;
        int request;
        if ("restore".equals(format)) {
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
            request = READ_BACKUP;
        } else if ("backup".equals(format) || "html".equals(format)) {
            boolean backup = "backup".equals(format);
            exportEntry = !backup && EntryData.validId(id) ? id : "";
            request = backup ? WRITE_BACKUP : WRITE_HTML;
            intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType(backup ? "application/zip" : "text/html").putExtra(Intent.EXTRA_TITLE,
                    backup ? "Alyawmiyat-backup.zip" : exportEntry.isEmpty() ? "Alyawmiyat.html" : "Alyawmiyat-entry.html");
        } else return;
        if (!launch(intent,request)) result(false,"تعذّر فتح منتقي الملفات.","file");
    }

    private boolean launch(Intent intent,int request) {
        try {
            startActivityForResult(intent,request);
            pickerOpen = true;
            return true;
        } catch (ActivityNotFoundException e) { return false; }
    }

    @Override protected void onActivityResult(int request,int status,Intent data) {
        super.onActivityResult(request,status,data);
        pickerOpen = false;
        if (status != RESULT_OK || data == null) return;
        if (request == PICK_PHOTOS) {
            List<Uri> images = new ArrayList<>();
            ClipData clip = data.getClipData();
            if (clip != null) {
                for (int i = 0; i < Math.min(clip.getItemCount(),JournalStore.MAX_PHOTOS); i++) {
                    Uri uri = clip.getItemAt(i).getUri();
                    if (uri != null) images.add(uri);
                }
            } else if (data.getData() != null) images.add(data.getData());
            if (!images.isEmpty()) copyPhotos(images,photoEntry,photoDay);
        } else if (data.getData() != null) {
            processDocument(request,data.getData(),exportEntry);
        }
    }

    private void processing(boolean active) {
        busy = active;
        js("window.journalProcessing && window.journalProcessing(" + active + ")");
    }

    private void copyPhotos(List<Uri> uris,String id,String day) {
        if (!EntryData.validId(id) || !EntryData.validDate(day)) return;
        processing(true);
        io.execute(() -> {
            int loaded = 0;
            for (Uri uri : uris) {
                try {
                    byte[] photo = PhotoCodec.read(getContentResolver(),uri);
                    if (store.addPhoto(EntryData.emptyText(id,day),UUID.randomUUID().toString(),photo)) loaded++;
                } catch (Exception ignored) {}
            }
            int count = loaded;
            runOnUiThread(() -> {
                processing(false);
                if (count > 0) js("window.journalPhotosChanged && window.journalPhotosChanged(" + JSONObject.quote(id) + ")");
                String message = count == 0 ? "تعذّر إضافة الصور أو امتلأت المرفقات." :
                    count == uris.size() ? "أُضيفت الصور" : "حُفظت الصور المتاحة";
                result(count > 0,message,"photo");
            });
        });
    }

    private void processDocument(int request,Uri uri,String entry) {
        processing(true);
        io.execute(() -> {
            boolean ok = false;
            int count = 0;
            try {
                if (request == READ_BACKUP) {
                    try (InputStream in = getContentResolver().openInputStream(uri)) {
                        if (in == null) throw new IOException("Cannot open");
                        count = ArchiveIO.importBackup(store,in);
                        RevisitReminders.dismissAll(getApplicationContext());
                        RevisitReminders.schedule(getApplicationContext(),true);
                    }
                } else {
                    try (OutputStream out = getContentResolver().openOutputStream(uri,"wt")) {
                        if (out == null) throw new IOException("Cannot write");
                        if (request == WRITE_BACKUP) ArchiveIO.exportBackup(store,out);
                        else ArchiveIO.exportReadable(store,out,entry);
                    }
                }
                ok = true;
            } catch (Exception ignored) {}
            final boolean success = ok;
            final int restored = count;
            runOnUiThread(() -> {
                processing(false);
                if (request == READ_BACKUP) result(success,success ? "استُعيدت اليوميات (" + restored + ")" :
                    "تعذّر استيراد النسخة. بقي سجلّك محفوظًا.","restore");
                else result(success,success ? "حُفظ الملف" : "تعذّر حفظ الملف.","file");
            });
        });
    }

    private void result(boolean ok,String message,String kind) {
        js("window.journalNativeResult && window.journalNativeResult(" + ok + "," +
            JSONObject.quote(message) + "," + JSONObject.quote(kind) + ")");
    }
}
