package com.lexflow.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.RemoteViews;

/**
 * Home screen widget: shows the current note's type, title and one-line core.
 * Dark card: one-line title, up to three lines of core, vertically centered.
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
        String title, core;
        if (finished) {
            title = c.getString(R.string.end_title);
            core = c.getString(R.string.end_body);
        } else if (n == null) {
            title = c.getString(R.string.empty_title);
            core = c.getString(R.string.empty_body);
        } else {
            title = n.title;
            core = n.core;
        }

        for (int id : ids) {
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
            v.setTextViewText(R.id.w_title, title);
            v.setTextViewText(R.id.w_core, core);

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
}
