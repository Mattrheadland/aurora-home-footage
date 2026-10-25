package com.mattheadland.aurora;

import android.content.Context;

final class Prefs {
    private Prefs() {}
    static void suppressFor(Context c, long ms) {
        c.getSharedPreferences("aurora", Context.MODE_PRIVATE).edit().putLong("suppressUntil", System.currentTimeMillis() + ms).apply();
    }
    static boolean suppressed(Context c) {
        return System.currentTimeMillis() < c.getSharedPreferences("aurora", Context.MODE_PRIVATE).getLong("suppressUntil", 0);
    }
}
