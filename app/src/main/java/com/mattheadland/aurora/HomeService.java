package com.mattheadland.aurora;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.view.accessibility.AccessibilityEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Whenever Fire OS brings up its own home screen (Home button, startup, leaving an app),
 * open Aurora instead. Only window-change events are requested; no screen content is read.
 */
public class HomeService extends AccessibilityService {
    private final Set<String> stockHomes = new HashSet<>();
    private long lastLaunch = 0;

    @Override protected void onServiceConnected() {
        stockHomes.clear();
        stockHomes.add("com.amazon.tv.launcher");          // Fire TV home (Fire OS 7/8)
        ComponentName c = stockHome(this);
        if (c != null) stockHomes.add(c.getPackageName());
        stockHomes.remove(getPackageName());
        Log.write(this, "HomeService connected; stock homes = " + stockHomes);
        bringUp("service start");
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || e.getPackageName() == null) return;
        String pkg = e.getPackageName().toString();
        if (!stockHomes.contains(pkg)) return;
        if (Prefs.suppressed(this)) return;                  // user chose "Amazon Home" from Aurora
        bringUp("stock home shown: " + pkg + "/" + e.getClassName());
    }

    private void bringUp(String why) {
        long now = System.currentTimeMillis();
        if (now - lastLaunch < 700) return;                  // one launch per burst of window events
        lastLaunch = now;
        try {
            Intent i = new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(i);
            Log.write(this, "bring up (" + why + ")");
        } catch (Exception ex) { Log.write(this, "bring up failed: " + ex); }
    }

    @Override public void onInterrupt() {}

    /** The system's default home activity other than Aurora (i.e. Amazon's). */
    static ComponentName stockHome(Context ctx) {
        try {
            PackageManager pm = ctx.getPackageManager();
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            List<ResolveInfo> all = pm.queryIntentActivities(home, 0);
            for (ResolveInfo ri : all) {
                if (ri.activityInfo == null) continue;
                String p = ri.activityInfo.packageName;
                if (p.equals(ctx.getPackageName()) || p.startsWith("com.spocky")) continue;
                if (p.startsWith("com.amazon")) return new ComponentName(p, ri.activityInfo.name);
            }
            for (ResolveInfo ri : all) {
                if (ri.activityInfo == null) continue;
                String p = ri.activityInfo.packageName;
                if (!p.equals(ctx.getPackageName()) && !p.startsWith("com.spocky")) return new ComponentName(p, ri.activityInfo.name);
            }
        } catch (Exception ignored) {}
        return null;
    }
}
