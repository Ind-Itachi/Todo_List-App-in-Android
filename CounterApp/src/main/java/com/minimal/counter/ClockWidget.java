package com.minimal.counter;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.RemoteViews;

/**
 * Home-screen clock in the same liquid-glass bubble as the floating counter. The time and date
 * are TextClocks, so they keep themselves up to date with no background work. Tap: open the app.
 */
public class ClockWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context c, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) update(c, mgr, id, mgr.getAppWidgetOptions(id));
    }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager mgr, int id, Bundle options) {
        update(c, mgr, id, options);
    }

    private static void update(Context c, AppWidgetManager mgr, int id, Bundle o) {
        // Portrait size of the widget, in dp.
        int w = o != null ? o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) : 0;
        int h = o != null ? o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) : 0;
        if (w <= 0) w = 110;
        if (h <= 0) h = 110;

        // The bubble fills the shorter side; its glass disc is 92/104 of the drawable.
        float disc = Math.min(w, h) * 92f / 104f;
        float time = clamp(disc * 0.27f, 18f, 72f);
        float date = clamp(disc * 0.085f, 9f, 18f);

        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_clock);
        rv.setTextViewTextSize(R.id.widget_time, TypedValue.COMPLEX_UNIT_DIP, time);
        rv.setTextViewTextSize(R.id.widget_date, TypedValue.COMPLEX_UNIT_DIP, date);
        rv.setViewVisibility(R.id.widget_date, disc < 80 ? android.view.View.GONE : android.view.View.VISIBLE);

        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        rv.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(c, 10, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));

        mgr.updateAppWidget(id, rv);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
