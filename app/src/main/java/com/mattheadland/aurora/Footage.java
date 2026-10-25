package com.mattheadland.aurora;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Downloads the full-quality loops once from the footage site into app storage and serves local paths. */
public class Footage {
    static final String BASE = "https://mattrheadland.github.io/aurora-home-footage/";
    private static Footage inst;
    static synchronized Footage get(Context c) { if (inst == null) inst = new Footage(c.getApplicationContext()); return inst; }

    private final Context ctx;
    private final File dir;
    private final Map<String, long[]> sizes = new LinkedHashMap<>();       // id -> {bytes}
    private final Map<String, String> periods = new LinkedHashMap<>();
    private volatile boolean started = false, busy = false;
    private volatile String current = "", lastError = "";
    private volatile String preferPeriod = "";

    private Footage(Context c) { ctx = c; dir = new File(c.getFilesDir(), "scenes"); dir.mkdirs(); }

    synchronized void start() {
        if (started) return;
        started = true;
        new Thread(this::run, "footage").start();
    }

    void prioritize(String period) { preferPeriod = period == null ? "" : period; }

    private boolean have(String id) {
        long[] s = sizes.get(id);
        File f = new File(dir, id + ".mp4");
        return s != null && f.isFile() && f.length() == s[0];
    }

    String src(String id) {
        File f = new File(dir, id + ".mp4");
        long[] s = sizes.get(id);
        if (f.isFile() && (s == null || f.length() == s[0])) return "file://" + f.getAbsolutePath();
        return BASE + "scenes/" + id + ".mp4";
    }

    String statusJson() {
        try {
            int done = 0; for (String id : sizes.keySet()) if (have(id)) done++;
            JSONObject o = new JSONObject();
            o.put("enabled", true); o.put("total", sizes.size()); o.put("done", done);
            o.put("busy", busy); o.put("current", current); o.put("error", lastError);
            return o.toString();
        } catch (Exception e) { return "{}"; }
    }

    private void run() {
        int attempt = 0;
        while (sizes.isEmpty()) {
            try { loadManifest(); }
            catch (Exception e) { lastError = "manifest: " + e.getMessage(); Log.write(ctx, lastError); sleep(Math.min(300000, 30000L * ++attempt)); }
        }
        Map<String, Integer> fails = new LinkedHashMap<>();
        while (true) {
            String next = null;
            List<String> missing = new ArrayList<>();
            for (String id : sizes.keySet()) if (!have(id) && fails.getOrDefault(id, 0) < 4) missing.add(id);
            if (missing.isEmpty()) break;
            for (String id : missing) if (preferPeriod.equals(periods.get(id))) { next = id; break; }
            if (next == null) next = missing.get(0);
            busy = true; current = next;
            try { download(next); Log.write(ctx, "saved " + next); }
            catch (Exception e) {
                fails.put(next, fails.getOrDefault(next, 0) + 1);
                lastError = next + ": " + e.getMessage(); Log.write(ctx, "download failed " + lastError);
                sleep(15000);
            }
            busy = false; current = "";
        }
        Log.write(ctx, "footage complete: " + statusJson());
    }

    private void loadManifest() throws Exception {
        String json = new String(fetch(BASE + "manifest.json?t=" + System.currentTimeMillis()), "UTF-8");
        JSONObject scenes = new JSONObject(json).getJSONObject("scenes");
        Map<String, long[]> s = new LinkedHashMap<>();
        Iterator<String> it = scenes.keys();
        while (it.hasNext()) {
            String id = it.next(); JSONObject m = scenes.getJSONObject(id);
            s.put(id, new long[]{m.getLong("bytes")});
            periods.put(id, m.optString("period", ""));
        }
        sizes.putAll(s);
        Log.write(ctx, "manifest: " + s.size() + " scenes");
    }

    private void download(String id) throws Exception {
        File tmp = new File(dir, id + ".part"), dst = new File(dir, id + ".mp4");
        HttpURLConnection c = (HttpURLConnection) new URL(BASE + "scenes/" + id + ".mp4").openConnection();
        c.setConnectTimeout(20000); c.setReadTimeout(60000);
        if (c.getResponseCode() != 200) throw new Exception("http " + c.getResponseCode());
        try (InputStream in = new BufferedInputStream(c.getInputStream()); FileOutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[256 * 1024]; int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } finally { c.disconnect(); }
        if (tmp.length() != sizes.get(id)[0]) { long got = tmp.length(); tmp.delete(); throw new Exception("size " + got + " != " + sizes.get(id)[0]); }
        if (dst.exists()) dst.delete();
        if (!tmp.renameTo(dst)) throw new Exception("rename failed");
    }

    private static byte[] fetch(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000); c.setReadTimeout(30000);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream b = new ByteArrayOutputStream(); byte[] buf = new byte[16384]; int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toByteArray();
        } finally { c.disconnect(); }
    }

    private static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) {} }
}
