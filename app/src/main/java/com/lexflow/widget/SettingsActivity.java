package com.lexflow.widget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Pick the widget's background color with three sliders and see it live.
 * Also the widget's configure screen (long-press the widget, then its settings).
 */
public class SettingsActivity extends Activity {

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private boolean updating;

    private GradientDrawable cardBg;
    private TextView hexText;
    private TextView hueLabel;
    private TextView satLabel;
    private TextView valLabel;
    private SeekBar hueBar;
    private SeekBar satBar;
    private SeekBar valBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent in = getIntent();
        if (in != null && in.getExtras() != null) {
            widgetId = in.getExtras().getInt(
                    AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }
        // Backing out of the configure screen must not cancel adding the widget by accident.
        setResult(RESULT_CANCELED, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId));

        int[] cur = WidgetTheme.load(this);

        ScrollView scroll = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(24), dp(28), dp(24), dp(40));
        scroll.addView(col, new ViewGroup.LayoutParams(MATCH, WRAP));
        setContentView(scroll);

        TextView title = text("小工具底色", 26, R.color.fg);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(title, new LinearLayout.LayoutParams(MATCH, WRAP));

        TextView hint = text("拉下面的滑桿，卡片會即時變色。按「套用」才會換到主畫面的小工具。", 15, R.color.muted);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        hintLp.topMargin = dp(6);
        col.addView(hint, hintLp);

        // Preview card, same proportions as the widget.
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(22), dp(18), dp(22), dp(18));
        cardBg = new GradientDrawable();
        cardBg.setCornerRadius(dp(28));
        card.setBackground(cardBg);
        TextView cardTitle = text("devoted / faithful / loyal", 24, R.color.widget_title);
        cardTitle.setTypeface(Typeface.DEFAULT_BOLD);
        cardTitle.setSingleLine(true);
        card.addView(cardTitle, new LinearLayout.LayoutParams(MATCH, WRAP));
        TextView cardCore = text("devoted 因為愛而付出；faithful 因為承諾而不背叛；loyal 因為關係而不倒戈。",
                16, R.color.widget_core);
        cardCore.setLineSpacing(0, 1.25f);
        LinearLayout.LayoutParams coreLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        coreLp.topMargin = dp(12);
        card.addView(cardCore, coreLp);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        cardLp.topMargin = dp(20);
        col.addView(card, cardLp);

        hexText = text("", 18, R.color.fg);
        hexText.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        LinearLayout.LayoutParams hexLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        hexLp.topMargin = dp(14);
        col.addView(hexText, hexLp);

        hueLabel = new TextView(this);
        hueBar = slider(col, hueLabel, 360, cur[0]);
        satLabel = new TextView(this);
        satBar = slider(col, satLabel, 100, cur[1]);
        valLabel = new TextView(this);
        valBar = slider(col, valLabel, 40, cur[2]);

        Button defGreen = button("預設（微綠）", false);
        defGreen.setOnClickListener(v -> setAll(WidgetTheme.DEF_HUE, WidgetTheme.DEF_SAT, WidgetTheme.DEF_VAL));
        Button oldGray = button("原本的墨灰", false);
        oldGray.setOnClickListener(v -> setAll(216, 14, 14));
        Button pureBlack = button("純黑", false);
        pureBlack.setOnClickListener(v -> setAll(0, 0, 0));

        LinearLayout presets = new LinearLayout(this);
        presets.setOrientation(LinearLayout.HORIZONTAL);
        presets.addView(defGreen, new LinearLayout.LayoutParams(0, WRAP, 1f));
        presets.addView(oldGray, new LinearLayout.LayoutParams(0, WRAP, 1f));
        presets.addView(pureBlack, new LinearLayout.LayoutParams(0, WRAP, 1f));
        LinearLayout.LayoutParams presetLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        presetLp.topMargin = dp(22);
        col.addView(presets, presetLp);

        Button apply = button("套用到小工具", true);
        apply.setOnClickListener(v -> apply());
        LinearLayout.LayoutParams applyLp = new LinearLayout.LayoutParams(MATCH, dp(56));
        applyLp.topMargin = dp(18);
        col.addView(apply, applyLp);

        readBars();
    }

    private void apply() {
        int h = hueBar.getProgress();
        int s = satBar.getProgress();
        int v = valBar.getProgress();
        WidgetTheme.save(this, h, s, v);
        NoteWidget.refreshAll(this);
        setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId));
        Toast.makeText(this, "已套用 " + HsvColor.hex(HsvColor.rgb(h, s, v)), Toast.LENGTH_SHORT).show();
        finish();
    }

    private void setAll(int h, int s, int v) {
        updating = true;
        hueBar.setProgress(h);
        satBar.setProgress(s);
        valBar.setProgress(v);
        updating = false;
        readBars();
    }

    private void readBars() {
        int h = hueBar.getProgress();
        int s = satBar.getProgress();
        int v = valBar.getProgress();
        int color = HsvColor.rgb(h, s, v);
        cardBg.setColor(color);
        hexText.setText("目前色碼  " + HsvColor.hex(color));
        hueLabel.setText("色調  " + h + "　（120 綠、150 青綠、180 青）");
        satLabel.setText("顏色濃淡  " + s + "　（0 沒有顏色，越大越明顯）");
        valLabel.setText("明暗  " + v + "　（0 純黑，越大越亮）");
    }

    private SeekBar slider(LinearLayout col, TextView label, int max, int progress) {
        label.setTextSize(16);
        label.setTextColor(getColor(R.color.fg));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.topMargin = dp(20);
        col.addView(label, lp);
        SeekBar bar = new SeekBar(this);
        bar.setMax(max);
        bar.setProgress(progress);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (!updating) readBars();
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        col.addView(bar, new LinearLayout.LayoutParams(MATCH, WRAP));
        return bar;
    }

    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        if (primary) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(getColor(R.color.accent));
            bg.setCornerRadius(dp(16));
            b.setBackground(bg);
            b.setTextColor(getColor(R.color.on_accent));
        }
        return b;
    }

    private TextView text(String s, int sp, int colorRes) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(getColor(colorRes));
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
