package com.lexflow.widget;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.TextView;
import android.widget.Toast;

/** Full note with the 了解 button. Also the app's launcher screen. */
public class DetailActivity extends Activity {

    private WebView web;
    private TextView button;
    private TextView progress;
    private String shownId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);
        web = findViewById(R.id.web);
        button = findViewById(R.id.btn_understood);
        progress = findViewById(R.id.progress);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(false);
        s.setAllowFileAccess(false);
        web.setBackgroundColor(getColor(R.color.bg));

        button.setOnClickListener(v -> onUnderstood());
    }

    @Override
    protected void onResume() {
        super.onResume();
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
        Store.Note n = Store.current(this);
        shownId = n == null ? null : n.id;
        button.setEnabled(n != null);
        button.setVisibility(n == null ? View.GONE : View.VISIBLE);

        Store.Progress p = Store.progress(this);
        progress.setText(getString(R.string.progress, p.understood, p.total));

        String body;
        if (n == null) {
            body = "<header><h1>" + esc(getString(R.string.empty_title)) + "</h1>"
                    + "<p class=\"core\">" + esc(getString(R.string.empty_body)) + "</p></header>";
        } else {
            body = "<header><span class=\"tag\">" + esc(n.type) + "</span>"
                    + "<h1>" + esc(n.title) + "</h1>"
                    + "<p class=\"core\">" + esc(n.core) + "</p></header>"
                    + "<article>" + n.html + "</article>";
        }
        boolean dark = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        String html = "<!doctype html><html><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<style>" + NoteCss.CSS + "</style></head>"
                + "<body class=\"" + (dark ? "dark" : "light") + "\">" + body + "</body></html>";
        web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
        web.scrollTo(0, 0);
    }

    private void onUnderstood() {
        int days = Store.understood(this, shownId);
        NoteWidget.refreshAll(this);
        if (days > 0) {
            Toast.makeText(this, getString(R.string.toast_done, days), Toast.LENGTH_SHORT).show();
            finish();
        } else {
            // The widget already moved on while this page was open; show the new note.
            show();
        }
    }

    private static String esc(String s) {
        return s == null ? "" : TextUtils.htmlEncode(s);
    }
}
