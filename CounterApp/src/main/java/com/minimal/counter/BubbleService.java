package com.minimal.counter;

import android.animation.ValueAnimator;
import android.app.Dialog;
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
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Keeps a floating orange "tinted glass" dot with the current count on screen while the app is
 * minimized. On Android 12+ the screen behind the dot is blurred (when the phone allows it).
 * Tap the dot: +1. Drag it to move it (it snaps to the nearest side). Long-press: open the app.
 * While dragging, a close target (×) appears at the bottom; drop the dot on it to hide it until
 * the next time the app is opened and minimized.
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
    // The dot is its own (dialog) window so Android can blur what's behind it.
    private Dialog bubble;
    private Window win;
    private View decor;
    private TextView label;
    private WindowManager.LayoutParams lp;
    private boolean attached;

    // Close target shown at the bottom while dragging. Its window is added *before* the bubble's
    // so the bubble always draws on top of it.
    private FrameLayout zone;
    private TextView closeTarget;
    private WindowManager.LayoutParams zoneLp;
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

        label = new TextView(this);
        label.setGravity(Gravity.CENTER);
        label.setTextColor(0xFFFFFFFF);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        label.setFontFeatureSettings("tnum");
        label.setIncludeFontPadding(false);
        label.setMaxLines(1);
        label.setShadowLayer(dp(3), 0, dp(1), 0x80000000); // keeps white digits readable on light screens
        label.setOnTouchListener(new DragTouch());

        bubble = new Dialog(this, R.style.BubbleTheme);
        bubble.setCancelable(false);
        bubble.setCanceledOnTouchOutside(false);
        bubble.setContentView(label, new ViewGroup.LayoutParams(size, size));

        win = bubble.getWindow();
        win.setType(Compat.TYPE_APPLICATION_OVERLAY);
        win.setFormat(PixelFormat.TRANSLUCENT);
        win.setBackgroundDrawable(new GlassDrawable(ORANGE, dp(1) * 1.3f));
        win.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        win.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        Compat.setBackgroundBlur(win, dp(16));
        decor = win.getDecorView();

        lp = win.getAttributes();
        lp.width = size;
        lp.height = size;
        lp.gravity = Gravity.TOP | Gravity.START;
        DisplayMetrics m = screen();
        lp.x = Counter.bubbleX(this, m.widthPixels - size);
        lp.y = Counter.bubbleY(this, m.heightPixels / 3);
        win.setAttributes(lp);

        buildZone();
        render(Counter.get(this));
    }

    private void buildZone() {
        int target = dp(64);

        closeTarget = new TextView(this);
        closeTarget.setText("\u00D7");
        closeTarget.setTextColor(0xFFFFFFFF);
        closeTarget.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        closeTarget.setGravity(Gravity.CENTER);
        closeTarget.setIncludeFontPadding(false);
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(0x99000000);
        ring.setStroke(dp(2), 0xCCFFFFFF);
        closeTarget.setBackground(ring);

        zone = new FrameLayout(this);
        zone.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x00000000, 0x80000000}));
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(target, target,
                Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        tlp.bottomMargin = dp(56);
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

    private boolean overTarget;

    private void setOverTarget(boolean over) {
        if (over == overTarget) return;
        overTarget = over;
        float s = over ? 1.25f : 1f;
        closeTarget.animate().scaleX(s).scaleY(s).setDuration(120).start();
        if (over) closeTarget.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    /** Hides the dot (drop on the close target). It comes back next time the app is minimized. */
    private void dismiss() {
        showZone(false);
        detach(); // window fades out
        stopSelf();
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
            zone.setAlpha(0f);
            wm.addView(zone, zoneLp); // added first, so the dot stays on top of it
            win.setAttributes(lp);
            bubble.show();
            attached = true;
        } catch (RuntimeException e) {
            attached = false; // permission was revoked
            safeRemove(zone);
        }
    }

    private void detach() {
        if (!attached) return;
        try {
            bubble.dismiss();
        } catch (RuntimeException ignored) {
        }
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
        if (attached) win.setAttributes(lp);
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
                        decor.getLocationOnScreen(loc);
                        offX = loc[0] - lp.x;
                        offY = loc[1] - lp.y;
                        showZone(true);
                    }
                    if (dragging) dragTo(e.getRawX(), e.getRawY(),
                            startX + Math.round(dx), startY + Math.round(dy));
                    return true;
                case MotionEvent.ACTION_UP:
                    handler.removeCallbacks(longPress);
                    if (dragging) endDrag(true);
                    else if (!longPressed) tap();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(longPress);
                    if (dragging) endDrag(false);
                    return true;
                default:
                    return false;
            }
        }

        /** Follows the finger, but sticks to the close target when the finger is near it. */
        private void dragTo(float fingerX, float fingerY, int x, int y) {
            if (closeTarget.getWidth() > 0) {
                closeTarget.getLocationOnScreen(loc);
                float cx = loc[0] + closeTarget.getWidth() / 2f;
                float cy = loc[1] + closeTarget.getHeight() / 2f;
                boolean near = Math.hypot(fingerX - cx, fingerY - cy) < dp(80);
                setOverTarget(near);
                if (near) {
                    x = Math.round(cx - offX - lp.width / 2f);
                    y = Math.round(cy - offY - lp.height / 2f);
                }
            }
            moveTo(x, y);
        }

        private void endDrag(boolean released) {
            if (released && overTarget) {
                dismiss();
            } else {
                showZone(false);
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
