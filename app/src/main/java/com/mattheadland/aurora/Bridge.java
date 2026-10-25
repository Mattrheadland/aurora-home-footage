package com.mattheadland.aurora;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** What the Aurora page can ask of the Fire TV (window.AuroraAndroid). */
public class Bridge {
    private final Activity act;
    private final WebView web;

    Bridge(Activity act, WebView web) { this.act = act; this.web = web; }

    /** Installed apps the user can open: [{id, name, iconPath(data URI), banner:true/false}] */
    @JavascriptInterface
    public String listApps() {
        JSONArray out = new JSONArray();
        try {
            PackageManager pm = act.getPackageManager();
            Set<String> seen = new HashSet<>();
            seen.add(act.getPackageName());
            for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
                Intent i = new Intent(Intent.ACTION_MAIN).addCategory(cat);
                List<ResolveInfo> list = pm.queryIntentActivities(i, 0);
                for (ResolveInfo ri : list) {
                    ActivityInfo ai = ri.activityInfo;
                    if (ai == null || seen.contains(ai.packageName)) continue;
                    seen.add(ai.packageName);
                    JSONObject o = new JSONObject();
                    o.put("id", ai.packageName);
                    o.put("name", String.valueOf(ri.loadLabel(pm)));
                    Drawable d = null; boolean banner = false;
                    try { d = ai.loadBanner(pm); banner = d != null; } catch (Exception ignored) {}
                    if (d == null) try { d = pm.getApplicationBanner(ai.packageName); banner = d != null; } catch (Exception ignored) {}
                    if (d == null) d = ri.loadIcon(pm);
                    o.put("iconPath", toDataUri(d, banner ? 320 : 192, banner ? 180 : 192));
                    o.put("banner", banner);
                    out.put(o);
                }
            }
        } catch (Exception e) { Log.write(act, "listApps failed: " + e); }
        return out.toString();
    }

    private static String toDataUri(Drawable d, int w, int h) {
        if (d == null) return "";
        try {
            Bitmap b;
            if (d instanceof BitmapDrawable && ((BitmapDrawable) d).getBitmap() != null) {
                b = Bitmap.createScaledBitmap(((BitmapDrawable) d).getBitmap(), w, h, true);
            } else {
                b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(b); d.setBounds(0, 0, w, h); d.draw(c);
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.PNG, 100, bos);
            return "data:image/png;base64," + Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) { return ""; }
    }

    @JavascriptInterface
    public boolean launch(String pkg) {
        try {
            PackageManager pm = act.getPackageManager();
            Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (i == null) i = pm.getLaunchIntentForPackage(pkg);
            if (i == null) return false;
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            act.startActivity(i);
            return true;
        } catch (Exception e) { Log.write(act, "launch " + pkg + " failed: " + e); return false; }
    }

    @JavascriptInterface
    public boolean openSettings() {
        try { act.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true; }
        catch (Exception e) { Log.write(act, "settings failed: " + e); return false; }
    }

    /** Opens Amazon's own home screen and keeps Aurora from bouncing back for a few minutes. */
    @JavascriptInterface
    public boolean openStockHome() {
        try {
            Prefs.suppressFor(act, 10 * 60 * 1000L);
            ComponentName stock = HomeService.stockHome(act);
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (stock != null) i.setComponent(stock);
            act.startActivity(i);
            return true;
        } catch (Exception e) { Log.write(act, "stock home failed: " + e); return false; }
    }

    /** Footage: where a scene's loop plays from right now (local file once saved, else the web). */
    @JavascriptInterface public String footageSrc(String id) { return Footage.get(act).src(id); }
    @JavascriptInterface public String footageStatus() { return Footage.get(act).statusJson(); }
    @JavascriptInterface public void footagePrioritize(String period) { Footage.get(act).prioritize(period); }
    @JavascriptInterface public void log(String m) { Log.write(act, "page: " + m); }
    @JavascriptInterface public String version() {
        try { return act.getPackageManager().getPackageInfo(act.getPackageName(), 0).versionName; } catch (Exception e) { return "?"; }
    }
}
