package com.minimal.counter;

import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Keeps an orange floating dot with the current count on screen while the app is minimized.
 * Tap the dot: +1. Drag it to move it (it snaps to the nearest side). Long-press: open the app.
 */
public class BubbleService extends Service implements Counter.Listener {

    static final String ACTION_STOP = "com.minimal.counter.STOP_BUBBLE";
    private static final String CHANNEL_ID = "bubble";
    private static final int NOTIFICATION_ID = 1;
    static final int ORANGE = 0xFFFF7A00;

    /** True while the app's own screen is showing; the dot hides then. */
    private static boolean appVisible;
    private static BubbleService instance;

    private WindowManager wm;
    private FrameLayout bubble;
    private TextView label;
    private WindowManager.LayoutParams lp;
    private boolean attached;
    private final Handler handler = new Handler(Looper.getMainLooper());

    static void start(Context c) {
        Compat.startForegroundService(c, new Intent(c, BubbleService.class));
    }

    static void stop(Context c) {
        c.stopService(new Intent(c, BubbleService.class));
    }

    static void setAppVisible(boolean visible) {
        appVisible = visible;
        if (instance != null) instance.updateVisibility();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        buildBubble();
        Counter.addListener(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification());
        if ((intent != null && ACTION_STOP.equals(intent.getAction()))
                || !Counter.bubbleEnabled(this) || !Compat.canDrawOverlays(this)) {
            if (intent != null && ACTION_STOP.equals(intent.getAction())) {
                Counter.setBubbleEnabled(this, false);
            }
            stopSelf();
            return START_NOT_STICKY;
        }
        updateVisibility();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Counter.removeListener(this);
        handler.removeCallbacksAndMessages(null);
        detach();
        if (instance == this) instance = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCountChanged(int count) {
        render(count);
    }

    // ---------------------------------------------------------------- bubble

    private void buildBubble() {
        int size = dp(60);
        int pad = dp(8); // room for the shadow

        label = new TextView(this);
        label.setGravity(Gravity.CENTER);
        label.setTextColor(0xFFFFFFFF);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        label.setFontFeatureSettings("tnum");
        label.setIncludeFontPadding(false);
        label.setMaxLines(1);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(ORANGE);
        label.setBackground(dot);
        label.setElevation(dp(4));

        bubble = new FrameLayout(this);
        bubble.setPadding(pad, pad, pad, pad);
        bubble.setClipToPadding(false);
        bubble.addView(label, new FrameLayout.LayoutParams(size, size));
        bubble.setOnTouchListener(new DragTouch());

        lp = new WindowManager.LayoutParams(
                size + 2 * pad, size + 2 * pad,
                Compat.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        DisplayMetrics m = screen();
        lp.x = Counter.bubbleX(this, m.widthPixels - lp.width);
        lp.y = Counter.bubbleY(this, m.heightPixels / 3);

        render(Counter.get(this));
    }

    private void render(int count) {
        if (label == null) return;
        String text = String.valueOf(count);
        int n = text.length();
        float sp = n <= 2 ? 22 : n == 3 ? 19 : n == 4 ? 16 : n == 5 ? 13 : 11;
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        label.setText(text);
    }

    private void updateVisibility() {
        boolean show = !appVisible && Counter.bubbleEnabled(this) && Compat.canDrawOverlays(this);
        if (show) attach();
        else detach();
    }

    private void attach() {
        if (attached) return;
        render(Counter.get(this));
        clampToScreen();
        try {
            wm.addView(bubble, lp);
            attached = true;
        } catch (RuntimeException e) {
            attached = false; // permission was revoked
        }
    }

    private void detach() {
        if (!attached) return;
        try {
            wm.removeView(bubble);
        } catch (RuntimeException ignored) {
        }
        attached = false;
    }

    private void clampToScreen() {
        DisplayMetrics m = screen();
        lp.x = Math.max(0, Math.min(lp.x, m.widthPixels - lp.width));
        lp.y = Math.max(0, Math.min(lp.y, m.heightPixels - lp.height));
    }

    private void moveTo(int x, int y) {
        lp.x = x;
        lp.y = y;
        clampToScreen();
        if (attached) wm.updateViewLayout(bubble, lp);
    }

    private void snapToEdge() {
        DisplayMetrics m = screen();
        int target = (lp.x + lp.width / 2) < m.widthPixels / 2 ? 0 : m.widthPixels - lp.width;
        ValueAnimator a = ValueAnimator.ofInt(lp.x, target);
        a.setDuration(180);
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator anim) {
                if (!attached) return;
                moveTo((Integer) anim.getAnimatedValue(), lp.y);
            }
        });
        a.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator anim) {
                Counter.saveBubblePosition(BubbleService.this, lp.x, lp.y);
            }
        });
        a.start();
    }

    private void tap() {
        Counter.add(this, +1);
        label.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        label.animate().cancel();
        label.setScaleX(0.88f);
        label.setScaleY(0.88f);
        label.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
    }

    private void openApp() {
        label.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        Intent i = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(i);
    }

    private final class DragTouch implements View.OnTouchListener {
        private final int slop = ViewConfiguration.get(BubbleService.this).getScaledTouchSlop();
        private float downX, downY;
        private int startX, startY;
        private boolean dragging, longPressed;
        private final Runnable longPress = new Runnable() {
            @Override
            public void run() {
                longPressed = true;
                openApp();
            }
        };

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX();
                    downY = e.getRawY();
                    startX = lp.x;
                    startY = lp.y;
                    dragging = false;
                    longPressed = false;
                    handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX() - downX;
                    float dy = e.getRawY() - downY;
                    if (!dragging && !longPressed && (Math.abs(dx) > slop || Math.abs(dy) > slop)) {
                        dragging = true;
                        handler.removeCallbacks(longPress);
                    }
                    if (dragging) moveTo(startX + Math.round(dx), startY + Math.round(dy));
                    return true;
                case MotionEvent.ACTION_UP:
                    handler.removeCallbacks(longPress);
                    if (dragging) snapToEdge();
                    else if (!longPressed) tap();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(longPress);
                    if (dragging) snapToEdge();
                    return true;
                default:
                    return false;
            }
        }
    }

    // ---------------------------------------------------------- notification

    private Notification buildNotification() {
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, BubbleService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE);
        return Compat.notificationBuilder(this, CHANNEL_ID, getString(R.string.bubble_channel))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(getString(R.string.bubble_on))
                .setContentText(getString(R.string.bubble_on_text))
                .setColor(ORANGE)
                .setOngoing(true)
                .setShowWhen(false)
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(0, getString(R.string.turn_off), stop).build())
                .build();
    }

    // ---------------------------------------------------------------- utils

    private DisplayMetrics screen() {
        DisplayMetrics m = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(m);
        return m;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
