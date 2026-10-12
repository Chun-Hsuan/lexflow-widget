package com.lexflow.widget;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Downloads notes.json from GitHub. Never throws; failures keep the old notes. */
public final class Sync {

    private static final String PREFS = "lexflow_sync";
    private static final long MIN_GAP = 6L * Store.HOUR;

    private Sync() {
    }

    /** Must be called off the main thread. Returns true if the notes changed. */
    public static boolean run(Context c, boolean force) {
        String url = BuildConfig.NOTES_URL;
        if (url == null || url.isEmpty()) return false;
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (!force && now - p.getLong("last", 0) < MIN_GAP) return false;

        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(url + "?t=" + now).openConnection();
            con.setConnectTimeout(8000);
            con.setReadTimeout(8000);
            con.setUseCaches(false);
            if (con.getResponseCode() != 200) return false;
            String body;
            try (InputStream in = con.getInputStream()) {
                body = Store.readAll(in);
            }
            p.edit().putLong("last", now).apply();
            return Store.saveNotes(c, body);
        } catch (Exception e) {
            return false;
        } finally {
            if (con != null) con.disconnect();
        }
    }
}
