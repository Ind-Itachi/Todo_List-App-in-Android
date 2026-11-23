package com.minimal.counter;

import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.FrameLayout;

/**
 * Keeps a clear "liquid glass" dot with the current count on screen while the app is minimized.
 * Tap the dot: +1. Drag it to move it (it snaps to the nearest side). Long-press: open the app.
 * While dragging, a close target (×) appears at the bottom; drop the dot on it to hide it until
 * the next time the app is opened and minimized.
 *
 * The same foreground service also drives the Glass clock widget's tilt effect ({@link WidgetTilt}),
 * so there is only ever one notification.
 */
public class BubbleService extends Service implements Counter.Listener {

    static final String ACTION_STOP = "com.minimal.counter.STOP_BUBBLE";
    private static final String CHANNEL_ID = "bubble";
    private static final int NOTIFICATION_ID = 1;

    /** True while the app's own screen is showing; the dot hides then. */
    private static boolean appVisible;
    private static BubbleService instance;
    /** Dot was dropped on the close target; stays hidden until the app is opened again. */
    private static boolean dismissed;

    private WidgetTilt tilt;

    private WindowManager wm;
    private GlassView bubble;
    private WindowManager.LayoutParams lp;
    private boolean attached;

    // Close target shown at the bottom while dragging. Its window is added *before* the bubble's
    // so the bubble always draws on top of it.
    private FrameLayout zone;
    private GlassView closeTarget;
    private WindowManager.LayoutParams zoneLp;
    private boolean overTarget;

    private final Handler handler = new Handler(Looper.getMainLooper());

    static void start(Context c) {
        Compat.startForegroundService(c, new Intent(c, BubbleService.class));
    }

    static boolean bubbleWanted(Context c) {
        return Counter.bubbleEnabled(c) && Compat.canDrawOverlays(c);
    }

    static boolean shouldRun(Context c) {
        return bubbleWanted(c) || WidgetTilt.wanted(c);
    }

    /** Start or stop the service to match what's wanted. Safe to call from anywhere. */
    static void refresh(Context c) {
        try {
            if (shouldRun(c)) start(c);
            else if (instance != null) stop(c);
        } catch (RuntimeException e) {
            // Android may refuse to start it from the background; it starts next time the app opens.
        }
    }

    static void stop(Context c) {
        c.stopService(new Intent(c, BubbleService.class));
    }

    static void setAppVisible(boolean visible) {
        appVisible = visible;
        if (visible) dismissed = false;
        if (instance != null) instance.updateVisibility();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        tilt = new WidgetTilt(this);
        buildBubble();
        buildZone();
        Counter.addListener(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification());
        boolean stopAsked = intent != null && ACTION_STOP.equals(intent.getAction());
        if (stopAsked) {
            Counter.setBubbleEnabled(this, false);
            Counter.setTiltEnabled(this, false);
        }
        if (!shouldRun(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        updateVisibility();
        tilt.setActive(WidgetTilt.wanted(this));
        // Refresh the notification text for what's running now.
        startForeground(NOTIFICATION_ID, buildNotification());
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Counter.removeListener(this);
        handler.removeCallbacksAndMessages(null);
        tilt.setActive(false);
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
        bubble.setLabel(String.valueOf(count));
    }

    // ---------------------------------------------------------------- bubble

    private void buildBubble() {
        int disc = dp(60);
        int pad = dp(12); // room for the wobble and the shadow
        int size = disc + 2 * pad;

        bubble = new GlassView(this, pad);
        bubble.setLabel(String.valueOf(Counter.get(this)));
        bubble.setOnTouchListener(new DragTouch());

        lp = new WindowManager.LayoutParams(size, size,
                Compat.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        DisplayMetrics m = screen();
        lp.x = Counter.bubbleX(this, m.widthPixels - size);
        lp.y = Counter.bubbleY(this, m.heightPixels / 3);
    }

    private void buildZone() {
        int pad = dp(8);
        int target = dp(60) + 2 * pad;

        closeTarget = new GlassView(this, pad);
        closeTarget.setLabel("×");

        zone = new FrameLayout(this);
        zone.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x00000000, 0x66000000}));
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(target, target,
                Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        tlp.bottomMargin = dp(48);
        zone.addView(closeTarget, tlp);
        zone.setAlpha(0f);

        zoneLp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, dp(220),
                Compat.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        zoneLp.gravity = Gravity.BOTTOM;
    }

    private void showZone(boolean show) {
        zone.animate().cancel();
        zone.animate().alpha(show ? 1f : 0f).setDuration(150).start();
        if (!show) setOverTarget(false);
    }

    private void setOverTarget(boolean over) {
        if (over == overTarget) return;
        overTarget = over;
        float s = over ? 1.22f : 1f;
        closeTarget.animate().scaleX(s).scaleY(s).setDuration(140).start();
        if (over) closeTarget.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    /** Hides the dot (dropped on the close target). It comes back next time the app is minimized. */
    private void dismiss() {
        showZone(false);
        bubble.animate().scaleX(0f).scaleY(0f).alpha(0f).setDuration(180).withEndAction(new Runnable() {
            @Override
            public void run() {
                dismissed = true;
                detach();
                if (!WidgetTilt.wanted(BubbleService.this)) stopSelf();
            }
        }).start();
    }

    private void updateVisibility() {
        boolean show = !appVisible && !dismissed && bubbleWanted(this);
        if (show) attach();
        else detach();
    }

    private void attach() {
        if (attached) return;
        bubble.setLabel(String.valueOf(Counter.get(this)));
        bubble.setScaleX(1f);
        bubble.setScaleY(1f);
        bubble.setAlpha(1f);
        clampToScreen();
        try {
            zone.setAlpha(0f);
            wm.addView(zone, zoneLp); // added first, so the dot stays on top of it
            wm.addView(bubble, lp);
            attached = true;
        } catch (RuntimeException e) {
            attached = false; // permission was revoked
            safeRemove(bubble);
            safeRemove(zone);
        }
    }

    private void detach() {
        if (!attached) return;
        safeRemove(bubble);
        safeRemove(zone);
        attached = false;
    }

    private void safeRemove(View v) {
        try {
            if (v.isAttachedToWindow()) wm.removeView(v);
        } catch (RuntimeException ignored) {
        }
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
        a.setDuration(220);
        a.setInterpolator(new android.view.animation.OvershootInterpolator(1.2f));
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

    private void openApp() {
        bubble.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        bubble.dragRelease();
        Intent i = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(i);
    }

    private final class DragTouch implements View.OnTouchListener {
        private final int slop = ViewConfiguration.get(BubbleService.this).getScaledTouchSlop();
        private float downX, downY;
        private int startX, startY;
        private boolean dragging, longPressed;
        private VelocityTracker velocity;
        private final int[] loc = new int[2];
        private int offX, offY; // bubble window position -> screen position
        private final Runnable longPress = new Runnable() {
            @Override
            public void run() {
                longPressed = true;
                openApp();
            }
        };

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            if (velocity == null) velocity = VelocityTracker.obtain();
            velocity.addMovement(e);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX();
                    downY = e.getRawY();
                    startX = lp.x;
                    startY = lp.y;
                    dragging = false;
                    longPressed = false;
                    bubble.pressDown();
                    handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX() - downX;
                    float dy = e.getRawY() - downY;
                    if (!dragging && !longPressed && (Math.abs(dx) > slop || Math.abs(dy) > slop)) {
                        dragging = true;
                        handler.removeCallbacks(longPress);
                        bubble.getLocationOnScreen(loc);
                        offX = loc[0] - lp.x;
                        offY = loc[1] - lp.y;
                        showZone(true);
                    }
                    if (dragging) {
                        velocity.computeCurrentVelocity(1000);
                        dragTo(e.getRawX(), e.getRawY(),
                                startX + Math.round(dx), startY + Math.round(dy),
                                velocity.getXVelocity(), velocity.getYVelocity());
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    handler.removeCallbacks(longPress);
                    if (dragging) {
                        endDrag(true);
                    } else if (!longPressed) {
                        Counter.add(BubbleService.this, +1);
                        bubble.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        bubble.tapRelease(e.getX(), e.getY());
                    }
                    recycle();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(longPress);
                    if (dragging) endDrag(false);
                    else bubble.dragRelease();
                    recycle();
                    return true;
                default:
                    return false;
            }
        }

        private void recycle() {
            if (velocity != null) {
                velocity.recycle();
                velocity = null;
            }
        }

        /** Follows the finger, but sticks to the close target when the finger is near it. */
        private void dragTo(float fingerX, float fingerY, int x, int y, float vx, float vy) {
            boolean near = false;
            if (closeTarget.getWidth() > 0) {
                closeTarget.getLocationOnScreen(loc);
                float cx = loc[0] + closeTarget.getWidth() / 2f;
                float cy = loc[1] + closeTarget.getHeight() / 2f;
                near = Math.hypot(fingerX - cx, fingerY - cy) < dp(80);
                setOverTarget(near);
                if (near) {
                    x = Math.round(cx - offX - lp.width / 2f);
                    y = Math.round(cy - offY - lp.height / 2f);
                }
            }
            bubble.stretch(near ? 0 : vx, near ? 0 : vy);
            moveTo(x, y);
        }

        private void endDrag(boolean released) {
            if (released && overTarget) {
                dismiss();
            } else {
                showZone(false);
                bubble.dragRelease();
                snapToEdge();
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
                .setContentTitle(getString(bubbleWanted(this) ? R.string.bubble_on : R.string.tilt_on))
                .setContentText(getString(bubbleWanted(this) ? R.string.bubble_on_text : R.string.tilt_on_text))
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
