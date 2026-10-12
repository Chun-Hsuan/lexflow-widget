package com.lexflow.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/** Home screen widget: shows the current note's type, title and one-line core. */
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

    /** Redraws every widget instance. Safe to call from the main thread. */
    public static void refreshAll(Context context) {
        Context app = context.getApplicationContext();
        AppWidgetManager manager = AppWidgetManager.getInstance(app);
        int[] ids = manager.getAppWidgetIds(new ComponentName(app, NoteWidget.class));
        if (ids != null && ids.length > 0) render(app, manager, ids);
    }

    private static void render(Context c, AppWidgetManager manager, int[] ids) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
        Store.Note n = Store.current(c);
        if (n == null) {
            v.setTextViewText(R.id.w_type, c.getString(R.string.app_name));
            v.setTextViewText(R.id.w_title, c.getString(R.string.empty_title));
            v.setTextViewText(R.id.w_core, c.getString(R.string.empty_body));
        } else {
            v.setTextViewText(R.id.w_type, n.type);
            v.setTextViewText(R.id.w_title, n.title);
            v.setTextViewText(R.id.w_core, n.core);
        }
        Intent open = new Intent(c, DetailActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pi = PendingIntent.getActivity(
                c, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.w_root, pi);
        manager.updateAppWidget(ids, v);
    }
}
