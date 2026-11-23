package com.minimal.counter;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/** The single shared count, used by both the app screen and the floating bubble. Main thread only. */
final class Counter {

    interface Listener {
        void onCountChanged(int count);
    }

    private static final String PREFS = "counter";
    private static final String KEY_COUNT = "count";
    private static final String KEY_BUBBLE = "bubble_enabled";
    private static final String KEY_BUBBLE_X = "bubble_x";
    private static final String KEY_BUBBLE_Y = "bubble_y";
    private static final String KEY_TILT = "tilt_enabled";

    private static Integer cached;
    private static final List<Listener> listeners = new ArrayList<>();

    private Counter() {}

    static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static int get(Context c) {
        if (cached == null) cached = Math.max(0, prefs(c).getInt(KEY_COUNT, 0));
        return cached;
    }

    /** Adds delta (never below 0). Returns true if the count changed. */
    static boolean add(Context c, int delta) {
        int now = get(c);
        int next = Math.max(0, now + delta);
        if (next == now) return false;
        set(c, next);
        return true;
    }

    static void set(Context c, int value) {
        cached = Math.max(0, value);
        prefs(c).edit().putInt(KEY_COUNT, cached).apply();
        for (Listener l : new ArrayList<>(listeners)) l.onCountChanged(cached);
    }

    static void addListener(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    static void removeListener(Listener l) {
        listeners.remove(l);
    }

    static boolean bubbleEnabled(Context c) {
        return prefs(c).getBoolean(KEY_BUBBLE, true);
    }

    static void setBubbleEnabled(Context c, boolean on) {
        prefs(c).edit().putBoolean(KEY_BUBBLE, on).apply();
    }

    static boolean tiltEnabled(Context c) {
        return prefs(c).getBoolean(KEY_TILT, true);
    }

    static void setTiltEnabled(Context c, boolean on) {
        prefs(c).edit().putBoolean(KEY_TILT, on).apply();
    }

    static int bubbleX(Context c, int def) {
        return prefs(c).getInt(KEY_BUBBLE_X, def);
    }

    static int bubbleY(Context c, int def) {
        return prefs(c).getInt(KEY_BUBBLE_Y, def);
    }

    static void saveBubblePosition(Context c, int x, int y) {
        prefs(c).edit().putInt(KEY_BUBBLE_X, x).putInt(KEY_BUBBLE_Y, y).apply();
    }
}
