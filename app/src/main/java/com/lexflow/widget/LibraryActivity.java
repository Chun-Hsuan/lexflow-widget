package com.lexflow.widget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.IOException;
import java.io.InputStream;

/**
 * 筆記庫: every note with search, categories, stars and a way into settings (res/raw/library.html).
 * Opens from the app icon, from the widget once today's limit is reached, and from the widget's
 * long-press menu (the launcher's "設定" entry), which also opens the keyboard for searching.
 * Reading here never touches the schedule or the daily limit.
 */
public class LibraryActivity extends Activity {

    private WebView web;
    private boolean ready;
    private boolean focusSearch;
    private boolean synced;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent in = getIntent();
        if (in != null && AppWidgetManager.ACTION_APPWIDGET_CONFIGURE.equals(in.getAction())) {
            // Reached from the widget's long-press menu: the user wants to search.
            focusSearch = true;
            int widgetId = in.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId));
        }
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | (focusSearch ? WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                : WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN));

        web = new WebView(this);
        web.setBackgroundColor(getColor(R.color.bg));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true); // the page is our own notes
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        web.addJavascriptInterface(new Bridge(), "Lex");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                ready = true;
                if (focusSearch) {
                    web.requestFocus();
                    web.evaluateJavascript("focusSearch()", null);
                    web.postDelayed(() -> {
                        InputMethodManager imm = getSystemService(InputMethodManager.class);
                        if (imm != null) imm.showSoftInput(web, InputMethodManager.SHOW_IMPLICIT);
                    }, 200);
                }
            }
        });
        setContentView(web);
        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        // Stars may have changed on a note page.
        if (ready) web.evaluateJavascript("setStars(" + Store.starsJson(this) + ")", null);
        boolean force = !synced; // always check once when the library opens
        synced = true;
        new Thread(() -> {
            if (Sync.run(getApplicationContext(), force)) {
                String json = Store.notesJson(getApplicationContext());
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    NoteWidget.refreshAll(this);
                    if (ready) web.evaluateJavascript("setNotes(" + json + ")", null);
                });
            }
        }).start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.onPause();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (!ready) {
            super.onBackPressed();
            return;
        }
        // The side menu closes first; otherwise leave the app.
        web.evaluateJavascript("handleBack()", v -> {
            if (!"true".equals(v)) leave();
        });
    }

    @SuppressWarnings("deprecation")
    private void leave() {
        super.onBackPressed();
    }

    private void load() {
        String page;
        try (InputStream in = getResources().openRawResource(R.raw.library)) {
            page = Store.readAll(in);
        } catch (IOException e) {
            page = "";
        }
        String data = "window.NOTES=" + Store.notesJson(this) + ";window.STARS=" + Store.starsJson(this) + ";";
        page = page.replace("/*DATA*/", data.replace("</", "<\\/"));
        web.loadDataWithBaseURL(null, page, "text/html", "utf-8", null);
    }

    /** Called from the page (on a background thread). */
    private final class Bridge {
        @JavascriptInterface
        public void open(String id) {
            runOnUiThread(() -> startActivity(new Intent(LibraryActivity.this, BrowseActivity.class)
                    .putExtra(DetailActivity.EXTRA_NOTE_ID, id)));
        }

        @JavascriptInterface
        public void settings() {
            runOnUiThread(() -> startActivity(new Intent(LibraryActivity.this, SettingsActivity.class)));
        }
    }
}
