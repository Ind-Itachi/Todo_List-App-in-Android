package com.minimal.counter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * A minimal tally counter.
 *
 * Tap anywhere: +1. "−" button: −1. "Reset": back to 0 (asks first).
 * Volume up / down also count +1 / −1, so you can count without looking.
 * Dot (bottom centre): turns the floating counter on/off. When on, a clear glass dot showing
 * the count floats over other apps while this app is minimized; tap it to count.
 */
public class MainActivity extends Activity implements Counter.Listener {

    // View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR (API 26); literal so we can compile against an older SDK.
    private static final int FLAG_LIGHT_NAV_BAR = 0x00000010;
    private static final String KEY_ASKED_BUBBLE = "asked_bubble";

    private TextView countView;
    private TextView hintView;
    private View dotToggle;
    private int fg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        fg = getResources().getColor(R.color.fg, getTheme());
        int faint = withAlpha(fg, 0x66);

        if (!isNight()) {
            View decor = getWindow().getDecorView();
            decor.setSystemUiVisibility(decor.getSystemUiVisibility() | FLAG_LIGHT_NAV_BAR);
        }

        // Root: the whole screen is the "+1" button.
        FrameLayout root = new FrameLayout(this);
        root.setClickable(true);
        root.setFocusable(false);
        root.setSoundEffectsEnabled(false);
        root.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                change(+1, v);
            }
        });

        // Big centered number.
        countView = new TextView(this);
        countView.setTextColor(fg);
        countView.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        countView.setLetterSpacing(-0.02f);
        countView.setFontFeatureSettings("tnum"); // fixed-width digits so the number doesn't jiggle
        countView.setGravity(Gravity.CENTER);
        countView.setIncludeFontPadding(false);
        countView.setMaxLines(1);
        root.addView(countView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        // Faint hint shown only at zero.
        hintView = new TextView(this);
        hintView.setText(R.string.hint);
        hintView.setTextColor(faint);
        hintView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        hintView.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        hintView.setLetterSpacing(0.22f);
        hintView.setAllCaps(true);
        hintView.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams hintLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        hintLp.topMargin = dp(120);
        root.addView(hintView, hintLp);

        // Bottom bar: "−" left, floating-counter toggle centre, "Reset" right.
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), 0, dp(12), dp(16));

        TextView minus = textButton(getString(R.string.minus), 34, fg);
        minus.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        minus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                change(-1, v);
            }
        });
        bar.addView(minus, new LinearLayout.LayoutParams(dp(96), dp(72)));

        bar.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));

        FrameLayout dotHolder = new FrameLayout(this);
        dotHolder.setClickable(true);
        dotHolder.setSoundEffectsEnabled(false);
        dotHolder.setContentDescription(getString(R.string.bubble_toggle));
        dotHolder.setBackgroundResource(borderlessRipple());
        dotToggle = new View(this);
        dotHolder.addView(dotToggle, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        dotHolder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleBubble();
            }
        });
        bar.addView(dotHolder, new LinearLayout.LayoutParams(dp(72), dp(72)));

        bar.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));

        TextView reset = textButton(getString(R.string.reset), 11, faint);
        reset.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        reset.setLetterSpacing(0.22f);
        reset.setAllCaps(true);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmReset();
            }
        });
        bar.addView(reset, new LinearLayout.LayoutParams(dp(96), dp(72)));

        root.addView(bar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

        setContentView(root);
        render(false);
        handleWidgetTap(getIntent());

        if (savedInstanceState == null) maybeOfferBubble();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleWidgetTap(intent);
    }

    /** Opening the app from the Glass clock turns its tilt effect back on if it was switched off. */
    private void handleWidgetTap(Intent i) {
        if (i != null && i.getBooleanExtra(ClockWidget.EXTRA_FROM_WIDGET, false)) {
            Counter.setTiltEnabled(this, true);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        BubbleService.setAppVisible(true);
        Counter.addListener(this);
        render(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (BubbleService.shouldRun(this)) BubbleService.start(this);
        else BubbleService.stop(this);
        renderDot();
    }

    @Override
    protected void onStop() {
        Counter.removeListener(this);
        BubbleService.setAppVisible(false); // app minimized: show the floating dot
        super.onStop();
    }

    @Override
    public void onCountChanged(int count) {
        render(false);
    }

    // ---------------------------------------------------------------- counting

    private void change(int delta, View source) {
        if (!Counter.add(this, delta)) return;
        if (source != null) source.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        render(true);
    }

    private void confirmReset() {
        if (Counter.get(this) == 0) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.reset_title)
                .setPositiveButton(R.string.reset, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Counter.set(MainActivity.this, 0);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void render(boolean animate) {
        int count = Counter.get(this);
        String text = String.valueOf(count);
        countView.setText(text);
        countView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeFor(text.length()));
        hintView.setVisibility(count == 0 ? View.VISIBLE : View.INVISIBLE);
        if (animate) {
            countView.animate().cancel();
            countView.setScaleX(1.06f);
            countView.setScaleY(1.06f);
            countView.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
        }
    }

    /** Shrink the digits as the number grows so it always fits on one line. */
    private static float sizeFor(int digits) {
        if (digits <= 3) return 128;
        if (digits == 4) return 104;
        if (digits == 5) return 84;
        if (digits == 6) return 70;
        if (digits == 7) return 60;
        return 48;
    }

    // ------------------------------------------------------------ bubble toggle

    private boolean bubbleActive() {
        return Counter.bubbleEnabled(this) && Compat.canDrawOverlays(this);
    }

    private void toggleBubble() {
        if (!Compat.canDrawOverlays(this)) {
            Counter.setBubbleEnabled(this, true);
            requestOverlayPermission();
            return;
        }
        boolean on = !Counter.bubbleEnabled(this);
        Counter.setBubbleEnabled(this, on);
        if (BubbleService.shouldRun(this)) BubbleService.start(this);
        else BubbleService.stop(this);
        renderDot();
        Toast.makeText(this, on ? R.string.bubble_enabled : R.string.bubble_disabled,
                Toast.LENGTH_SHORT).show();
    }

    /** First launch: explain the floating dot once and offer to turn it on. */
    private void maybeOfferBubble() {
        if (Compat.canDrawOverlays(this)) return;
        if (Counter.prefs(this).getBoolean(KEY_ASKED_BUBBLE, false)) return;
        Counter.prefs(this).edit().putBoolean(KEY_ASKED_BUBBLE, true).apply();
        new AlertDialog.Builder(this)
                .setTitle(R.string.offer_title)
                .setMessage(R.string.offer_text)
                .setPositiveButton(R.string.offer_allow, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Counter.setBubbleEnabled(MainActivity.this, true);
                        requestOverlayPermission();
                    }
                })
                .setNegativeButton(R.string.not_now, null)
                .show();
    }

    private void requestOverlayPermission() {
        Toast.makeText(this, R.string.allow_appear_on_top, Toast.LENGTH_LONG).show();
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (RuntimeException e) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
        }
    }

    private void renderDot() {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        if (bubbleActive()) {
            d.setColor(fg);
        } else {
            d.setColor(0x00000000);
            d.setStroke(dp(2), withAlpha(fg, 0x99));
        }
        dotToggle.setBackground(d);
    }

    // ---------------------------------------------------------------- keys

    // Volume keys count too (single press = one step; holding the key doesn't run away).
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.getRepeatCount() == 0) {
                change(keyCode == KeyEvent.KEYCODE_VOLUME_UP ? +1 : -1, countView);
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    // ---------------------------------------------------------------- utils

    private TextView textButton(String label, int sp, int color) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextColor(color);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        b.setGravity(Gravity.CENTER);
        b.setClickable(true);
        b.setSoundEffectsEnabled(false);
        b.setBackgroundResource(borderlessRipple());
        return b;
    }

    private int borderlessRipple() {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true);
        return tv.resourceId;
    }

    private boolean isNight() {
        int mode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}
