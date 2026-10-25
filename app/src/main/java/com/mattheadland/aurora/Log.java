package com.mattheadland.aurora;

import android.content.Context;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Small rolling log in app storage; the install script pulls it with run-as for debugging. */
final class Log {
    private Log() {}
    static synchronized void write(Context c, String m) {
        android.util.Log.i("Aurora", m);
        try {
            File f = new File(c.getFilesDir(), "aurora.log");
            if (f.length() > 512 * 1024) f.delete();
            try (FileWriter w = new FileWriter(f, true)) {
                w.write(new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(new Date()) + "  " + m + "\n");
            }
        } catch (Exception ignored) {}
    }
}
