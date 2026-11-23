package com.minimal.counter;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

import java.lang.reflect.Constructor;

/**
 * Android 8.0+ (API 26) calls, made through reflection so the project builds against the
 * Android 6 SDK that Ubuntu packages. minSdk is 26, so these methods always exist at runtime.
 */
final class Compat {

    /** WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY (API 26). */
    static final int TYPE_APPLICATION_OVERLAY = 2038;
    /** NotificationManager.IMPORTANCE_LOW. */
    private static final int IMPORTANCE_LOW = 2;

    private Compat() {}

    static boolean canDrawOverlays(Context c) {
        return Settings.canDrawOverlays(c);
    }

    static void startForegroundService(Context c, Intent intent) {
        try {
            Context.class.getMethod("startForegroundService", Intent.class).invoke(c, intent);
        } catch (Exception e) {
            c.startService(intent);
        }
    }

    /** Creates the (silent) notification channel if needed and returns a builder bound to it. */
    static Notification.Builder notificationBuilder(Context c, String channelId, CharSequence channelName) {
        try {
            Class<?> channelClass = Class.forName("android.app.NotificationChannel");
            Object channel = channelClass
                    .getConstructor(String.class, CharSequence.class, int.class)
                    .newInstance(channelId, channelName, IMPORTANCE_LOW);
            channelClass.getMethod("setShowBadge", boolean.class).invoke(channel, false);
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationManager.class.getMethod("createNotificationChannel", channelClass).invoke(nm, channel);

            Constructor<Notification.Builder> ctor =
                    Notification.Builder.class.getConstructor(Context.class, String.class);
            return ctor.newInstance(c, channelId);
        } catch (Exception e) {
            return new Notification.Builder(c);
        }
    }
}
