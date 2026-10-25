package com.mattheadland.aurora;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Belt and braces at startup; the Home service normally gets there first. */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        Log.write(c, "boot completed");
        try {
            c.startActivity(new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) { Log.write(c, "boot launch failed: " + e); }
    }
}
