package com.lexflow.widget;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Notes and reading progress.
 *
 * Rules:
 * - One note is "current" and shows on the widget until the user taps 了解.
 * - 了解 schedules the note to come back after 1, 3, 7, 14, then every 30 days.
 * - A note left untouched for 24 hours is swapped out and sent to the back.
 * - Next note: a review that is due first, otherwise an unread note,
 *   otherwise the review that comes due soonest.
 */
public final class Store {

    static final long HOUR = 60L * 60L * 1000L;
    static final long DAY = 24L * HOUR;
    static final long SWAP_AFTER = DAY;
    static final int[] INTERVAL_DAYS = {1, 3, 7, 14, 30};

    private static final String PREFS = "lexflow_state";
    private static final String KEY_STATE = "state";
    private static final String NOTES_FILE = "notes.json";

    public static final class Note {
        public String id;
        public String title;
        public String type;
        public String core;
        public String html;
    }

    public static final class Progress {
        public int understood;
        public int total;
    }

    static final class Data {
        String version;
        List<Note> notes = new ArrayList<>();
        JSONObject state;
        JSONObject items;

        static Data of(List<Note> notes, JSONObject state) {
            Data d = new Data();
            d.notes = notes;
            d.state = state;
            d.items = state.optJSONObject("items");
            if (d.items == null) {
                d.items = new JSONObject();
                try {
                    state.put("items", d.items);
                } catch (JSONException ignored) {
                }
            }
            return d;
        }

        Note find(String id) {
            if (id == null) return null;
            for (Note n : notes) if (n.id.equals(id)) return n;
            return null;
        }

        JSONObject item(String id) {
            JSONObject it = items.optJSONObject(id);
            if (it == null) {
                it = new JSONObject();
                try {
                    items.put(id, it);
                } catch (JSONException ignored) {
                }
            }
            return it;
        }

        String currentId() {
            return state.optString("current", null);
        }
    }

    private Store() {
    }

    // ---------------------------------------------------------------- public

    /** The note to show now. Applies the 24-hour swap rule. */
    public static synchronized Note current(Context c) {
        Data d = load(c);
        if (ensureCurrent(d, System.currentTimeMillis())) save(c, d);
        return d.find(d.currentId());
    }

    /**
     * Marks the note the user just read as understood and moves on.
     * Returns the number of days until it comes back, or 0 if nothing changed
     * (for example the widget had already swapped to another note).
     */
    public static synchronized int understood(Context c, String noteId) {
        Data d = load(c);
        int days = applyUnderstood(d, noteId, System.currentTimeMillis());
        if (days > 0) save(c, d);
        return days;
    }

    static int applyUnderstood(Data d, String noteId, long now) {
        ensureCurrent(d, now);
        String cur = d.currentId();
        if (noteId == null || !noteId.equals(cur)) return 0;
        try {
            JSONObject it = d.item(cur);
            int level = it.optInt("level", 0) + 1;
            int days = INTERVAL_DAYS[Math.min(level, INTERVAL_DAYS.length) - 1];
            it.put("level", level);
            it.put("due", now + days * DAY);
            it.put("skippedAt", 0);
            setCurrent(d, pickNext(d, cur, now), now);
            return days;
        } catch (JSONException e) {
            return 0;
        }
    }

    public static synchronized Progress progress(Context c) {
        Data d = load(c);
        Progress p = new Progress();
        p.total = d.notes.size();
        for (Note n : d.notes) {
            if (d.item(n.id).optInt("level", 0) > 0) p.understood++;
        }
        return p;
    }

    /**
     * Stores a freshly downloaded notes.json. Returns true if the notes changed.
     * Invalid or empty files are ignored so a bad upload never wipes the notes.
     */
    public static synchronized boolean saveNotes(Context c, String json) {
        List<Note> parsed;
        String version;
        try {
            JSONObject root = new JSONObject(json);
            version = root.optString("version", "");
            parsed = parseNotes(root);
        } catch (JSONException e) {
            return false;
        }
        if (parsed.isEmpty()) return false;
        Data d = load(c);
        if (version.equals(d.version)) return false;
        File tmp = new File(c.getFilesDir(), NOTES_FILE + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            return false;
        }
        return tmp.renameTo(new File(c.getFilesDir(), NOTES_FILE));
    }

    // ------------------------------------------------------------- scheduling

    /** Makes sure there is a valid current note. Returns true if state changed. */
    static boolean ensureCurrent(Data d, long now) {
        if (d.notes.isEmpty()) return false;
        String cur = d.currentId();
        if (d.find(cur) == null) {
            setCurrent(d, pickNext(d, null, now), now);
            return true;
        }
        long since = d.state.optLong("shownSince", now);
        if (since > now) since = now; // clock moved backwards
        if (now - since < SWAP_AFTER) return false;

        // Untouched for a day: send it to the back without counting it as read.
        try {
            JSONObject it = d.item(cur);
            if (it.optInt("level", 0) == 0) {
                it.put("skippedAt", now);
            } else {
                it.put("due", now);
            }
        } catch (JSONException ignored) {
        }
        setCurrent(d, pickNext(d, cur, now), now);
        return true;
    }

    static String pickNext(Data d, String exclude, long now) {
        // 1. A review that is due, oldest first.
        Note best = null;
        long bestDue = Long.MAX_VALUE;
        for (Note n : d.notes) {
            if (n.id.equals(exclude)) continue;
            JSONObject it = d.item(n.id);
            if (it.optInt("level", 0) == 0) continue;
            long due = it.optLong("due", 0);
            if (due <= now && due < bestDue) {
                best = n;
                bestDue = due;
            }
        }
        if (best != null) return best.id;

        // 2. An unread note: file order first, skipped ones after, oldest skip first.
        Note fresh = null;
        long freshKey = Long.MAX_VALUE;
        for (int i = 0; i < d.notes.size(); i++) {
            Note n = d.notes.get(i);
            if (n.id.equals(exclude)) continue;
            JSONObject it = d.item(n.id);
            if (it.optInt("level", 0) != 0) continue;
            long skipped = it.optLong("skippedAt", 0);
            long key = skipped == 0 ? i : skipped; // timestamps are always larger than indexes
            if (key < freshKey) {
                fresh = n;
                freshKey = key;
            }
        }
        if (fresh != null) return fresh.id;

        // 3. Everything read and nothing due: the review that comes due soonest.
        Note soon = null;
        long soonDue = Long.MAX_VALUE;
        for (Note n : d.notes) {
            if (n.id.equals(exclude)) continue;
            long due = d.item(n.id).optLong("due", Long.MAX_VALUE);
            if (due < soonDue) {
                soon = n;
                soonDue = due;
            }
        }
        if (soon != null) return soon.id;
        return exclude; // only one note exists
    }

    private static void setCurrent(Data d, String id, long now) {
        try {
            if (id == null) {
                d.state.remove("current");
            } else {
                d.state.put("current", id);
            }
            d.state.put("shownSince", now);
        } catch (JSONException ignored) {
        }
    }

    // ---------------------------------------------------------------- storage

    private static Data load(Context c) {
        Data d = new Data();
        String json = null;
        File cached = new File(c.getFilesDir(), NOTES_FILE);
        if (cached.exists()) {
            try (InputStream in = new FileInputStream(cached)) {
                json = readAll(in);
            } catch (IOException ignored) {
            }
        }
        if (json == null) {
            try (InputStream in = c.getAssets().open(NOTES_FILE)) {
                json = readAll(in);
            } catch (IOException ignored) {
            }
        }
        if (json != null) {
            try {
                JSONObject root = new JSONObject(json);
                d.version = root.optString("version", "");
                d.notes = parseNotes(root);
            } catch (JSONException ignored) {
            }
        }

        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            d.state = new JSONObject(p.getString(KEY_STATE, "{}"));
        } catch (JSONException e) {
            d.state = new JSONObject();
        }
        d.items = d.state.optJSONObject("items");
        if (d.items == null) {
            d.items = new JSONObject();
            try {
                d.state.put("items", d.items);
            } catch (JSONException ignored) {
            }
        }
        return d;
    }

    private static void save(Context c, Data d) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_STATE, d.state.toString())
                .commit();
    }

    private static List<Note> parseNotes(JSONObject root) throws JSONException {
        List<Note> list = new ArrayList<>();
        JSONArray arr = root.getJSONArray("notes");
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            Note n = new Note();
            n.id = o.getString("id");
            n.title = o.optString("title", "");
            n.type = o.optString("type", "");
            n.core = o.optString("core", "");
            n.html = o.optString("html", "");
            list.add(n);
        }
        return list;
    }

    static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int r;
        while ((r = in.read(chunk)) != -1) buf.write(chunk, 0, r);
        return new String(buf.toByteArray(), StandardCharsets.UTF_8);
    }
}
