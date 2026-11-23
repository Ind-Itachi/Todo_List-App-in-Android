package com.minimal.counter;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

/**
 * Home-screen clock on a rounded liquid-glass panel. The time and date are TextClocks, so they
 * keep themselves up to date. On Android 12+ the light inside the glass follows the phone's tilt
 * (see {@link WidgetTilt}). Tap: open the app (and re-enable the tilt effect if it was turned off).
 */
public class ClockWidget extends AppWidgetProvider {

    static final String EXTRA_FROM_WIDGET = "from_widget";

    // Resting positions of the light layers (must match layout/widget_clock.xml), in dp.
    private static final float GLOW_X = -70f, GLOW_Y = -90f;
    private static final float CAUSTIC_X = 40f, CAUSTIC_Y = 120f;

    @Override
    public void onUpdate(Context c, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) update(c, mgr, id, mgr.getAppWidgetOptions(id));
        BubbleService.refresh(c);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager mgr, int id, Bundle options) {
        update(c, mgr, id, options);
    }

    @Override
    public void onEnabled(Context c) {
        BubbleService.refresh(c);
    }

    @Override
    public void onDisabled(Context c) {
        BubbleService.refresh(c);
    }

    private static void update(Context c, AppWidgetManager mgr, int id, Bundle o) {
        // Portrait size of the widget, in dp.
        int w = o != null ? o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) : 0;
        int h = o != null ? o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) : 0;
        if (w <= 0) w = 250;
        if (h <= 0) h = 110;

        float time = clamp(Math.min(h * 0.48f, w * 0.26f), 22f, 96f);
        float date = clamp(time * 0.17f, 9f, 14f);

        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_clock);
        rv.setTextViewTextSize(R.id.widget_time, TypedValue.COMPLEX_UNIT_DIP, time);
        rv.setTextViewTextSize(R.id.widget_date, TypedValue.COMPLEX_UNIT_DIP, date);
        rv.setViewVisibility(R.id.widget_date, h < 85 ? View.GONE : View.VISIBLE);

        Intent open = new Intent(c, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_FROM_WIDGET, true);
        rv.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(c, 10, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));

        mgr.updateAppWidget(id, rv);
    }

    /**
     * Positions the light layers: offsets (dp) from their resting places for the specular glow,
     * the caustic glow and the time text. Android 12+ only.
     */
    static void placeLights(RemoteViews rv, float glowDx, float glowDy,
                            float causticDx, float causticDy, float textDx, float textDy) {
        if (Build.VERSION.SDK_INT < 31) return;
        int u = TypedValue.COMPLEX_UNIT_DIP;
        rv.setViewLayoutMargin(R.id.widget_glow, RemoteViews.MARGIN_LEFT, GLOW_X + glowDx, u);
        rv.setViewLayoutMargin(R.id.widget_glow, RemoteViews.MARGIN_TOP, GLOW_Y + glowDy, u);
        rv.setViewLayoutMargin(R.id.widget_caustic, RemoteViews.MARGIN_LEFT, CAUSTIC_X + causticDx, u);
        rv.setViewLayoutMargin(R.id.widget_caustic, RemoteViews.MARGIN_TOP, CAUSTIC_Y + causticDy, u);
        rv.setViewLayoutMargin(R.id.widget_text, RemoteViews.MARGIN_LEFT, textDx, u);
        rv.setViewLayoutMargin(R.id.widget_text, RemoteViews.MARGIN_TOP, textDy, u);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
