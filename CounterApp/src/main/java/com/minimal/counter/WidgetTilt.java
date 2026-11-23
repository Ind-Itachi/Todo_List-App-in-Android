package com.minimal.counter;

import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.TypedValue;
import android.widget.RemoteViews;

/**
 * Moves the light inside the Glass clock widget as the phone tilts: a soft specular glow slides
 * against the tilt, a caustic glow at the bottom slides with it, and the time floats a little,
 * so the panel reads like a piece of glass catching the light.
 *
 * Uses the gravity sensor (fused from the gyroscope + accelerometer). Runs only while the screen
 * is on and at least one Glass clock is on the home screen; sends a small update (positions only,
 * no images) at most ~20 times a second, and only when something actually moved.
 */
final class WidgetTilt implements SensorEventListener {

    private static final long FRAME_MS = 50;
    private static final float GLOW_TRAVEL_DP = 70f;
    private static final float CAUSTIC_TRAVEL_DP = 40f;
    private static final float TEXT_TRAVEL_DP = 4f;

    private final Context ctx;
    private final SensorManager sensors;
    private final Sensor gravity;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean running, listening, receiverOn;
    // Raw gravity, its slow-moving baseline (so the light drifts back to centre when the phone is
    // held still), and the smoothed tilt that's drawn. Tilt is roughly -1..1 on each axis.
    private float gx, gy, baseX, baseY;
    private boolean haveBase;
    private float tiltX, tiltY;
    private float sentX = Float.NaN, sentY = Float.NaN;

    WidgetTilt(Context c) {
        ctx = c.getApplicationContext();
        sensors = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        Sensor g = sensors.getDefaultSensor(Sensor.TYPE_GRAVITY);
        gravity = g != null ? g : sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    /** True when the tilt effect should run: supported phone, effect on, a widget placed. */
    static boolean wanted(Context c) {
        return Build.VERSION.SDK_INT >= 31 && Counter.tiltEnabled(c) && widgetIds(c).length > 0;
    }

    static int[] widgetIds(Context c) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(c);
        if (mgr == null) return new int[0];
        return mgr.getAppWidgetIds(new ComponentName(c, ClockWidget.class));
    }

    void setActive(boolean on) {
        if (on == running) return;
        running = on;
        if (on) {
            if (!receiverOn) {
                IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
                f.addAction(Intent.ACTION_SCREEN_OFF);
                ctx.registerReceiver(screen, f);
                receiverOn = true;
            }
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            if (pm == null || pm.isInteractive()) listen(true);
        } else {
            listen(false);
            if (receiverOn) {
                try {
                    ctx.unregisterReceiver(screen);
                } catch (RuntimeException ignored) {
                }
                receiverOn = false;
            }
            resetWidgets();
        }
    }

    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            listen(running && Intent.ACTION_SCREEN_ON.equals(i.getAction()));
        }
    };

    private void listen(boolean on) {
        if (on == listening || gravity == null) return;
        listening = on;
        if (on) {
            haveBase = false;
            sensors.registerListener(this, gravity, SensorManager.SENSOR_DELAY_GAME);
            handler.post(frame);
        } else {
            sensors.unregisterListener(this);
            handler.removeCallbacks(frame);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        gx = e.values[0];
        gy = e.values[1];
        if (!haveBase) {
            baseX = gx;
            baseY = gy;
            haveBase = true;
        }
        // Baseline follows slowly (~2 s), so tilting moves the light and it then settles back.
        baseX += (gx - baseX) * 0.012f;
        baseY += (gy - baseY) * 0.012f;
    }

    @Override
    public void onAccuracyChanged(Sensor s, int accuracy) {}

    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            if (!listening) return;
            float targetX = clamp((gx - baseX) / 3.5f);
            float targetY = clamp((gy - baseY) / 3.5f);
            // Ease toward the target for a fluid, slightly heavy liquid feel.
            tiltX += (targetX - tiltX) * 0.35f;
            tiltY += (targetY - tiltY) * 0.35f;
            float moved = Float.isNaN(sentX) ? 99f
                    : Math.max(Math.abs(tiltX - sentX), Math.abs(tiltY - sentY)) * GLOW_TRAVEL_DP;
            if (moved >= 0.6f) push(tiltX, tiltY);
            handler.postDelayed(this, FRAME_MS);
        }
    };

    private void resetWidgets() {
        sentX = sentY = Float.NaN;
        tiltX = tiltY = 0f;
        if (Build.VERSION.SDK_INT >= 31 && widgetIds(ctx).length > 0) push(0f, 0f);
    }

    /**
     * Phone tilted right (gravity x goes negative) → light slides left, like a reflection.
     * Applied as layout margins on top of the resting positions set in the layout.
     */
    private void push(float tx, float ty) {
        int[] ids = widgetIds(ctx);
        if (ids.length == 0 || Build.VERSION.SDK_INT < 31) return;
        sentX = tx;
        sentY = ty;
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_clock);
        ClockWidget.placeLights(rv,
                tx * GLOW_TRAVEL_DP, -ty * GLOW_TRAVEL_DP,
                -tx * CAUSTIC_TRAVEL_DP, ty * CAUSTIC_TRAVEL_DP,
                -tx * TEXT_TRAVEL_DP, ty * TEXT_TRAVEL_DP);
        try {
            AppWidgetManager.getInstance(ctx).partiallyUpdateAppWidget(ids, rv);
        } catch (RuntimeException ignored) {
        }
    }

    private static float clamp(float v) {
        return Math.max(-1f, Math.min(1f, v));
    }

    /** Unit helper shared with ClockWidget. */
    static final int DIP = TypedValue.COMPLEX_UNIT_DIP;
}
