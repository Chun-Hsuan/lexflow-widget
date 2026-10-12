package com.lexflow.widget;

import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Random;

/** Full note with the Done button. Also the app's launcher screen. */
public class DetailActivity extends Activity {

    /** Shown briefly after Done, picked at random. */
    private static final String[] PHRASES = {
            "Confirmed.", "Noted.", "Got it.", "Filed away.", "Saved.",
            "Locked in.", "One more down.", "In the bag.", "Nice.", "Sharp.",
            "Well done.", "Keep going.", "Onward.", "Another one for the essay.",
    };
    private static final Random RANDOM = new Random();

    private WebView web;
    private TextView button;
    private String shownId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);
        web = findViewById(R.id.web);
        button = findViewById(R.id.btn_understood);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(false);
        s.setAllowFileAccess(false);
        web.setBackgroundColor(getColor(R.color.bg));

        button.setOnClickListener(v -> onUnderstood());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Store.finishedToday(this)) {
            // Done for today: the note page stays closed until tomorrow.
            Toast.makeText(this, getString(R.string.end_title), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        show();
        new Thread(() -> {
            if (Sync.run(getApplicationContext(), true)) {
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    NoteWidget.refreshAll(this);
                    Store.Note n = Store.current(this);
                    if (n == null || !n.id.equals(shownId)) show();
                });
            }
        }).start();
    }

    private void show() {
        boolean finished = Store.finishedToday(this);
        Store.Note n = finished ? null : Store.current(this);
        shownId = n == null ? null : n.id;
        button.setEnabled(n != null);
        button.setVisibility(n == null ? View.GONE : View.VISIBLE);

        String body;
        if (finished) {
            body = "<header><h1>" + esc(getString(R.string.end_title)) + "</h1>"
                    + "<p class=\"core\">" + esc(getString(R.string.end_body)) + "</p></header>";
        } else if (n == null) {
            body = "<header><h1>" + esc(getString(R.string.empty_title)) + "</h1>"
                    + "<p class=\"core\">" + esc(getString(R.string.empty_body)) + "</p></header>";
        } else {
            body = "<header><span class=\"tag\">" + esc(n.type) + "</span>"
                    + "<h1>" + esc(n.title) + "</h1>"
                    + "<p class=\"core\">" + esc(n.core) + "</p></header>"
                    + "<article>" + n.html + "</article>";
        }
        String html = "<!doctype html><html><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<style>" + NoteCss.CSS + "</style></head>"
                + "<body>" + body + "</body></html>";
        web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
        web.scrollTo(0, 0);
    }

    private void onUnderstood() {
        int days = Store.understood(this, shownId);
        NoteWidget.refreshAll(this);
        if (days > 0) {
            String msg = Store.finishedToday(this)
                    ? getString(R.string.end_title)
                    : PHRASES[RANDOM.nextInt(PHRASES.length)];
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            finish(); // back to the home screen; the widget already shows the next note
        } else {
            // The widget already moved on while this page was open; show the new note.
            show();
        }
    }

    private static String esc(String s) {
        return s == null ? "" : TextUtils.htmlEncode(s);
    }
}
