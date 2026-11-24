package app.altaqwim.calendar;

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
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONObject;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final String HOST="appassets.androidplatform.net";
    private static final String HOME="https://"+HOST+"/index.html";
    private static final int BACKUP=92;
    private static final int RESTORE=93;
    private static final int ICAL=94;
    private static final int NOTICE_PERMISSION=95;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private WebView web;
    private CalendarStore store;
    private SharedPreferences prefs;
    private String launchItem;
    private String deferredResult;
    private boolean ready,picking,working;

    @Override public void onCreate(Bundle previous) {
        super.onCreate(previous);
        store=new CalendarStore(this);
        prefs=getSharedPreferences("calendar-settings",MODE_PRIVATE);
        launchItem=getIntent().getStringExtra(CalendarReminders.OPEN);
        getIntent().removeExtra(CalendarReminders.OPEN);
        picking=previous!=null && previous.getBoolean("picker");
        web=new WebView(this);
        web.setBackgroundColor(Color.rgb(244,247,252));
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings settings=web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(false);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request) {
                return !HOME.equals(request.getUrl().toString());
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request) {
                return asset(request.getUrl());
            }
            @Override public void onPageFinished(WebView view,String url) {
                if (!HOME.equals(url)) return;
                ready=true;
                if (deferredResult!=null) {
                    js(deferredResult);
                    deferredResult=null;
                }
                routeNotice();
            }
        });
        web.addJavascriptInterface(new Bridge(),"CalendarNative");
        setContentView(web);
        web.loadUrl(HOME);
    }

    private WebResourceResponse asset(Uri uri) {
        String name=uri.getPath();
        String type=null;
        if ("https".equals(uri.getScheme()) && HOST.equals(uri.getHost()) && name!=null) {
            switch (name) {
                case "/index.html": type="text/html"; break;
                case "/style.css": type="text/css"; break;
                case "/app.js": type="application/javascript"; break;
                case "/calendar.webp": type="image/webp"; break;
                case "/noto-Regular.woff": case "/noto-Bold.woff": type="font/woff"; break;
            }
        }
        if (type!=null) {
            try {
                return new WebResourceResponse(type,type.startsWith("image/") || type.startsWith("font/") ? null : "UTF-8",
                    getAssets().open(name.substring(1)));
            } catch (IOException ignored) {}
        }
        return new WebResourceResponse("text/plain","UTF-8",404,"Not Found",null,new ByteArrayInputStream(new byte[0]));
    }

    @Override protected void onResume() {
        super.onResume();
        io.execute(() -> CalendarReminders.schedule(getApplicationContext()));
        js("window.calendarPermissions && window.calendarPermissions()");
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        launchItem=intent.getStringExtra(CalendarReminders.OPEN);
        intent.removeExtra(CalendarReminders.OPEN);
        routeNotice();
    }

    private void routeNotice() {
        if (!ready || working || !CalendarItem.validId(launchItem)) return;
        String id=JSONObject.quote(launchItem);
        launchItem=null;
        js("window.calendarOpened && window.calendarOpened("+id+")");
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putBoolean("picker",picking);
        super.onSaveInstanceState(out);
    }

    @Override public void onBackPressed() {
        if (working) return;
        web.evaluateJavascript("window.calendarBack && window.calendarBack()",result -> {
            if (!"true".equals(result)) finish();
        });
    }

    @Override protected void onDestroy() {
        ready=false;
        web.destroy();
        io.execute(store::close);
        io.shutdown();
        super.onDestroy();
    }

    private void js(String code) {
        if (ready && !isDestroyed()) web.evaluateJavascript(code,null);
    }

    private void permission(boolean openSettings) {
        CalendarReminders.ensureChannel(this);
        if (CalendarReminders.allowed(this)) return;
        if (Build.VERSION.SDK_INT>=33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED &&
            (!prefs.getBoolean("asked",false) || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))) {
            prefs.edit().putBoolean("asked",true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTICE_PERMISSION);
        } else if (openSettings) {
            openSystem(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));
        }
    }

    @Override public void onRequestPermissionsResult(int request,String[] names,int[] results) {
        super.onRequestPermissionsResult(request,names,results);
        if (request==NOTICE_PERMISSION) {
            io.execute(() -> CalendarReminders.schedule(getApplicationContext()));
            js("window.calendarPermissions && window.calendarPermissions()");
        }
    }

    private void openSystem(Intent intent) {
        try { startActivity(intent); }
        catch (ActivityNotFoundException e) { result(false,"تعذّر فتح إعدادات أندرويد.","settings"); }
    }

    private final class Bridge {
        @JavascriptInterface public String grid(String first,String last) {
            try { return store.grid(first,last); } catch (Exception e) { return ""; }
        }
        @JavascriptInterface public String board(String first,String last,int limit) {
            try { return store.board(first,last,limit); } catch (Exception e) { return ""; }
        }
        @JavascriptInterface public String list(String first,String last,int offset) {
            try { return store.list(first,last,offset); } catch (Exception e) { return ""; }
        }
        @JavascriptInterface public String search(String value,int offset) {
            try { return store.search(value,offset); } catch (Exception e) { return ""; }
        }
        @JavascriptInterface public String item(String id) {
            try {
                CalendarItem item=store.find(id);
                return item==null ? "" : item.json().toString();
            } catch (Exception e) { return ""; }
        }
        @JavascriptInterface public String conflict(String id,String day,String start,String end) {
            try { return store.conflict(id,day,start,end); } catch (RuntimeException e) { return ""; }
        }
        @JavascriptInterface public String newId() { return UUID.randomUUID().toString(); }
        @JavascriptInterface public boolean save(String input) {
            try {
                if (input==null || input.length()>12000) return false;
                CalendarItem item=CalendarItem.parse(new JSONObject(input));
                store.save(item);
                CalendarReminders.dismiss(MainActivity.this,item.id);
                io.execute(() -> CalendarReminders.schedule(getApplicationContext()));
                if (item.remind>=0 && !item.done) runOnUiThread(() -> permission(false));
                return true;
            } catch (Exception e) { return false; }
        }
        @JavascriptInterface public boolean delete(String id) {
            try {
                if (!store.delete(id)) return false;
                changed(id);
                return true;
            } catch (Exception e) { return false; }
        }
        @JavascriptInterface public boolean status(String id,boolean done) {
            try {
                if (!store.status(id,done)) return false;
                changed(id);
                return true;
            } catch (Exception e) { return false; }
        }
        private void changed(String id) {
            CalendarReminders.dismiss(MainActivity.this,id);
            io.execute(() -> CalendarReminders.schedule(getApplicationContext()));
        }
        @JavascriptInterface public int weekStart() { return prefs.getInt("week-start",6); }
        @JavascriptInterface public void setWeekStart(int start) {
            if (start==6 || start==0 || start==1) prefs.edit().putInt("week-start",start).apply();
        }
        @JavascriptInterface public String notices() {
            try { return CalendarReminders.settings(MainActivity.this); }
            catch (Exception e) { return "{}"; }
        }
        @JavascriptInterface public void noticePermission() { runOnUiThread(() -> permission(true)); }
        @JavascriptInterface public boolean testNotice() { return CalendarReminders.test(MainActivity.this); }
        @JavascriptInterface public void exactPermission() {
            if (Build.VERSION.SDK_INT>=31) runOnUiThread(() -> openSystem(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:"+getPackageName()))));
        }
        @JavascriptInterface public void document(String kind) { runOnUiThread(() -> chooseDocument(kind)); }
    }

    private void chooseDocument(String kind) {
        if (picking || working) return;
        int request;
        Intent intent;
        switch (kind) {
            case "restore":
                request=RESTORE;
                intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*");
                break;
            case "backup":
                request=BACKUP;
                intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json")
                    .putExtra(Intent.EXTRA_TITLE,"Altaqwim-backup.json");
                break;
            case "ical":
                request=ICAL;
                intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/calendar")
                    .putExtra(Intent.EXTRA_TITLE,"Altaqwim.ics");
                break;
            default: return;
        }
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try { startActivityForResult(intent,request); picking=true; }
        catch (ActivityNotFoundException e) { result(false,"تعذّر فتح منتقي الملفات.","file"); }
    }

    @Override protected void onActivityResult(int request,int status,Intent data) {
        super.onActivityResult(request,status,data);
        picking=false;
        if ((request!=RESTORE && request!=BACKUP && request!=ICAL) ||
            status!=RESULT_OK || data==null || data.getData()==null) return;
        Uri uri=data.getData();
        working=true;
        js("window.calendarProcessing && window.calendarProcessing(true)");
        io.execute(() -> process(request,uri));
    }

    private void process(int request,Uri uri) {
        boolean success=false;
        int restored=0;
        try {
            if (request==RESTORE) {
                try (InputStream input=getContentResolver().openInputStream(uri)) {
                    if (input==null) throw new IOException("Missing stream");
                    restored=CalendarFiles.restore(store,input);
                }
                CalendarReminders.dismissAll(getApplicationContext());
                CalendarReminders.schedule(getApplicationContext());
            } else {
                OutputStream target=getContentResolver().openOutputStream(uri,"wt");
                if (target==null) throw new IOException("Missing stream");
                try (OutputStream out=new BufferedOutputStream(target)) {
                    if (request==BACKUP) CalendarFiles.backup(store,out);
                    else CalendarFiles.ical(store,out,ZoneId.systemDefault());
                }
            }
            success=true;
        } catch (Exception ignored) {}
        boolean ok=success;
        int count=restored;
        runOnUiThread(() -> {
            working=false;
            js("window.calendarProcessing && window.calendarProcessing(false)");
            if (request==RESTORE) {
                result(ok,ok ? "استُعيد التقويم ("+count+")" : "تعذّر استيراد النسخة. بقي التقويم محفوظًا.","restore");
            } else result(ok,ok ? "حُفظ الملف" : "تعذّر حفظ الملف.","file");
            routeNotice();
        });
    }

    private void result(boolean ok,String message,String kind) {
        String code="window.calendarResult && window.calendarResult("+ok+","+JSONObject.quote(message)+","+JSONObject.quote(kind)+")";
        if (ready) js(code);
        else deferredResult=code;
    }
}
