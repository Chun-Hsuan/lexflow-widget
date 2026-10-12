package com.lexflow.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.RemoteViews;

/**
 * Home screen widget: shows the current note's type, title and one-line core.
 * Text sizes follow the widget's height so a bigger widget gets bigger text.
 */
public class NoteWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        final Context app = context.getApplicationContext();
        final PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                Sync.run(app, false);
            } catch (Throwable ignored) {
            }
            try {
                render(app, manager, ids);
            } finally {
                pending.finish();
            }
        }).start();
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int id, Bundle options) {
        render(context.getApplicationContext(), manager, new int[]{id});
    }

    /** Redraws every widget instance. Safe to call from the main thread. */
    public static void refreshAll(Context context) {
        Context app = context.getApplicationContext();
        AppWidgetManager manager = AppWidgetManager.getInstance(app);
        int[] ids = manager.getAppWidgetIds(new ComponentName(app, NoteWidget.class));
        if (ids != null && ids.length > 0) render(app, manager, ids);
    }

    private static void render(Context c, AppWidgetManager manager, int[] ids) {
        Store.Note n = Store.current(c);
        boolean finished = Store.finishedToday(c);
        String type, title, core;
        if (finished) {
            type = c.getString(R.string.app_name);
            title = c.getString(R.string.end_title);
            core = c.getString(R.string.end_body);
        } else if (n == null) {
            type = c.getString(R.string.app_name);
            title = c.getString(R.string.empty_title);
            core = c.getString(R.string.empty_body);
        } else {
            type = n.type;
            title = n.title;
            core = n.core;
        }

        for (int id : ids) {
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
            v.setTextViewText(R.id.w_type, type);
            v.setTextViewText(R.id.w_title, title);
            v.setTextViewText(R.id.w_core, core);
            size(v, manager.getAppWidgetOptions(id));

            // After today's limit the widget only shows the end message; tapping does nothing.
            // updateAppWidget replaces the whole view, so leaving out the click clears it.
            if (!finished) {
                Intent open = new Intent(c, DetailActivity.class);
                open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                PendingIntent pi = PendingIntent.getActivity(
                        c, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                v.setOnClickPendingIntent(R.id.w_root, pi);
            }
            manager.updateAppWidget(id, v);
        }
    }

    /** Scales text to the widget's height (in dp) so the content fills the card. */
    private static void size(RemoteViews v, Bundle options) {
        int h = 0;
        if (options != null) {
            // Portrait home screens report their real height as MAX_HEIGHT.
            h = Math.max(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0),
                    options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0));
        }
        if (h <= 0) h = 180;

        float typeSp = clamp(h * 0.07f, 12f, 16f);
        float titleSp = clamp(h * 0.125f, 17f, 30f);
        float coreSp = clamp(h * 0.095f, 14f, 22f);
        v.setTextViewTextSize(R.id.w_type, TypedValue.COMPLEX_UNIT_SP, typeSp);
        v.setTextViewTextSize(R.id.w_title, TypedValue.COMPLEX_UNIT_SP, titleSp);
        v.setTextViewTextSize(R.id.w_core, TypedValue.COMPLEX_UNIT_SP, coreSp);

        // Room left for the core after padding, type label and up to two title lines.
        float left = h - 40f - typeSp * 1.4f - titleSp * 1.35f * 2f - 12f;
        int lines = (int) Math.floor(left / (coreSp * 1.45f));
        v.setInt(R.id.w_core, "setMaxLines", Math.max(2, Math.min(lines, 8)));
    }

    private static float clamp(float x, float lo, float hi) {
        return Math.max(lo, Math.min(hi, x));
    }
}
