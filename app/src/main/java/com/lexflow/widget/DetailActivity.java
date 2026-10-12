package com.lexflow.widget;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Random;

/**
 * The note page.
 *
 * Review (this class, opened from the widget): today's note. Hold the round button for
 * 0.6 s to mark it Done; the page then closes and the widget moves on. Runs in its own task
 * so closing it returns to the home screen, not to the library.
 *
 * Browse ({@link BrowseActivity}, opened from the library): any note, read freely. The button
 * is an outline "Close"; holding it returns to the list. Nothing here touches the schedule
 * or the daily limit.
 *
 * In both: a single tap flashes the ring as a hint to hold; a double tap asks to add or remove
 * the star. A starred note shows a small star inside its category pill.
 */
public class DetailActivity extends Activity {

    static final String EXTRA_NOTE_ID = "noteId";

    /** Shown briefly after Done, picked at random. */
    private static final String[] PHRASES = {
            "Confirmed.", "Noted.", "Got it.", "Filed away.", "Saved.",
            "Locked in.", "One more down.", "In the bag.", "Nice.", "Sharp.",
            "Well done.", "Keep going.", "Onward.", "Another one for the essay.",
    };
    private static final Random RANDOM = new Random();

    /** 墨水 set: the button takes one of these at random each time the page opens. */
    private static final int[] INK_COLORS = {
            0xFF2E4A6B, 0xFF1F5F5B, 0xFF5B3A63, 0xFF8A4B2A, 0xFF4F5B2E, 0xFF3A3F47,
    };

    private static final long HOLD_MS = 600;     // hold this long to finish
    private static final long RING_DELAY_MS = 100; // quick taps barely show the ring
    private static final long TAP_MS = 300;      // shorter than this counts as a tap
    private static final long DOUBLE_TAP_MS = 280;

    private static final String STAR_SVG = "<svg id=\"star\" viewBox=\"0 0 24 24\"%s>"
            + "<path fill=\"currentColor\" d=\"M12 3.2l2.6 5.4 5.9.8-4.3 4.1 1 5.9L12 16.6l-5.3 2.8 1-5.9-4.3-4.1 5.9-.8z\"/></svg>";
    private static final String STAR_CSS = ".tag{display:inline-flex;align-items:center;gap:4px}"
            + "#star{width:13px;height:13px}#star.pop{animation:pop .35s ease-out}"
            + "@keyframes pop{0%{transform:scale(.3)}70%{transform:scale(1.25)}100%{transform:scale(1)}}";
    private static final String STAR_JS = "function setStar(on){var s=document.getElementById('star');"
            + "if(!s)return;s.style.display=on?'':'none';s.classList.remove('pop');"
            + "if(on){void s.getBoundingClientRect();s.classList.add('pop');}}";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView web;
    private TextView button;
    private HoldRing ring;
    private String shownId;
    private boolean pressing;
    private long downAt;
    private boolean tapPending;
    private boolean leaving;

    private final Runnable holdDone = this::onHoldComplete;
    private final Runnable singleTap = () -> {
        tapPending = false;
        ring.flash();
    };

    /** True for {@link BrowseActivity}. */
    protected boolean browsing() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);
        web = findViewById(R.id.web);
        button = findViewById(R.id.btn_understood);
        ring = findViewById(R.id.ring);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true); // only for the background blobs and the star; the page is our own notes
        s.setAllowFileAccess(false);
        web.setBackgroundColor(getColor(R.color.bg));

        int ink = INK_COLORS[RANDOM.nextInt(INK_COLORS.length)];
        paintButton(ink);
        ring.setColor(ink);
        setupButton();

        if (browsing()) show(Store.find(this, getIntent().getStringExtra(EXTRA_NOTE_ID)));
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.onPause(); // stops the blob animation while the page is hidden
        cancelPress();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        if (browsing()) return;
        if (Store.finishedToday(this)) {
            // Done for today: the widget leads to the library instead.
            openLibrary();
            return;
        }
        Store.Note n = Store.current(this);
        if (n == null || !n.id.equals(shownId)) show(n);
        new Thread(() -> {
            if (Sync.run(getApplicationContext(), true)) {
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    NoteWidget.refreshAll(this);
                    Store.Note cur = Store.current(this);
                    if (cur == null || !cur.id.equals(shownId)) show(cur);
                });
            }
        }).start();
    }

    private void show(Store.Note n) {
        if (n == null && browsing()) {
            finish(); // the note was deleted
            return;
        }
        shownId = n == null ? null : n.id;
        button.setVisibility(n == null ? View.GONE : View.VISIBLE);
        ring.setVisibility(n == null ? View.GONE : View.VISIBLE);
        ring.reset();

        String body;
        if (n == null) {
            body = "<header><h1>" + esc(getString(R.string.empty_title)) + "</h1>"
                    + "<p class=\"core\">" + esc(getString(R.string.empty_body)) + "</p></header>";
        } else {
            String star = String.format(STAR_SVG, Store.isStarred(this, n.id) ? "" : " style=\"display:none\"");
            body = "<header><span class=\"tag\">" + star + esc(n.type) + "</span>"
                    + "<h1>" + esc(n.title) + "</h1></header>" // the summary lives on the widget only
                    + "<article>" + n.html + "</article>";
        }
        String html = "<!doctype html><html><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<style>" + NoteCss.CSS + NoteBlob.CSS + STAR_CSS + "</style></head>"
                + "<body><div id=\"blobs\"></div><div id=\"page\">" + body + "</div>"
                + "<script>" + NoteBlob.JS + STAR_JS + "</script></body></html>";
        web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
        web.scrollTo(0, 0);
    }

    // ------------------------------------------------------------ the button

    @SuppressLint("ClickableViewAccessibility")
    private void setupButton() {
        if (browsing()) button.setText(R.string.close);
        button.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    pressing = true;
                    downAt = SystemClock.uptimeMillis();
                    button.animate().scaleX(.95f).scaleY(.95f).setDuration(100).start();
                    ring.start(RING_DELAY_MS, HOLD_MS - RING_DELAY_MS);
                    handler.postDelayed(holdDone, HOLD_MS);
                    return true;
                case MotionEvent.ACTION_UP:
                    if (pressing) {
                        boolean tap = SystemClock.uptimeMillis() - downAt < TAP_MS;
                        cancelPress();
                        if (tap) onTap();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    cancelPress();
                    return true;
                default:
                    return true;
            }
        });
        // Accessibility services click instead of touching: treat it like a finished hold.
        button.setOnClickListener(v -> onHoldComplete());
    }

    private void cancelPress() {
        if (!pressing) return;
        pressing = false;
        handler.removeCallbacks(holdDone);
        button.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
        ring.cancel();
    }

    private void onTap() {
        if (tapPending) {
            tapPending = false;
            handler.removeCallbacks(singleTap);
            askStar();
        } else {
            tapPending = true;
            handler.postDelayed(singleTap, DOUBLE_TAP_MS);
        }
    }

    private void onHoldComplete() {
        if (leaving || shownId == null) return;
        pressing = false;
        button.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
        ring.full();
        button.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        if (browsing()) {
            leaving = true;
            finish(); // back to the list
        } else {
            onDone();
        }
    }

    private void onDone() {
        int days = Store.understood(this, shownId);
        NoteWidget.refreshAll(this);
        if (days > 0) {
            leaving = true;
            String msg = Store.finishedToday(this)
                    ? getString(R.string.end_title)
                    : PHRASES[RANDOM.nextInt(PHRASES.length)];
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            finish(); // back to the home screen; the widget already shows the next note
        } else if (Store.finishedToday(this)) {
            openLibrary();
        } else {
            // The widget already moved on while this page was open; show the new note.
            show(Store.current(this));
        }
    }

    private void askStar() {
        if (shownId == null) return;
        final String id = shownId;
        final boolean on = Store.isStarred(this, id);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                .setTitle(on ? R.string.star_remove_title : R.string.star_add_title)
                .setMessage(on ? R.string.star_remove_body : R.string.star_add_body)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(on ? R.string.star_remove : R.string.star_add, (d, w) -> {
                    Store.setStarred(this, id, !on);
                    web.evaluateJavascript("setStar(" + !on + ")", null);
                })
                .show();
    }

    private void openLibrary() {
        leaving = true;
        startActivity(new Intent(this, LibraryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }

    private void paintButton(int color) {
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        if (browsing()) {
            // Outline style: reading here does not count.
            circle.setColor(0xEBF9F8F5);
            circle.setStroke(Math.round(2 * getResources().getDisplayMetrics().density), color);
            button.setTextColor(color);
            button.setElevation(Math.round(3 * getResources().getDisplayMetrics().density));
        } else {
            circle.setColor(color);
        }
        int ripple = browsing() ? (color & 0x00FFFFFF) | 0x22000000 : 0x33FFFFFF;
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(ripple), circle, null));
    }

    private static String esc(String s) {
        return s == null ? "" : TextUtils.htmlEncode(s);
    }
}
