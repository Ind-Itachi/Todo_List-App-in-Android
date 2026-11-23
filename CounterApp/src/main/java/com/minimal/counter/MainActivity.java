package com.minimal.counter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A minimal tally counter.
 *
 * Tap anywhere: +1. "−" button: −1. "Reset": back to 0 (asks first).
 * Volume up / down also count +1 / −1, so you can count without looking.
 * The count is saved and survives closing the app.
 */
public class MainActivity extends Activity {

    private static final String PREFS = "counter";
    private static final String KEY_COUNT = "count";
    // View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR (API 26); literal so we can compile against an older SDK.
    private static final int FLAG_LIGHT_NAV_BAR = 0x00000010;

    private int count;
    private TextView countView;
    private TextView hintView;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        count = Math.max(0, prefs.getInt(KEY_COUNT, 0));

        int fg = getResources().getColor(R.color.fg, getTheme());
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
        countView.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
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
        hintView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        hintView.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams hintLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        hintLp.topMargin = dp(120);
        root.addView(hintView, hintLp);

        // Bottom bar: "−" on the left, "Reset" on the right.
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), 0, dp(12), dp(16));

        TextView minus = textButton(getString(R.string.minus), 34, fg);
        minus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                change(-1, v);
            }
        });
        bar.addView(minus, new LinearLayout.LayoutParams(dp(72), dp(72)));

        View spacer = new View(this);
        bar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        TextView reset = textButton(getString(R.string.reset), 15, faint);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmReset();
            }
        });
        bar.addView(reset, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(72)));

        root.addView(bar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

        setContentView(root);
        render(false);
    }

    private TextView textButton(String label, int sp, int color) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextColor(color);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), 0, dp(16), 0);
        b.setClickable(true);
        b.setSoundEffectsEnabled(false);
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true);
        b.setBackgroundResource(tv.resourceId);
        return b;
    }

    private void change(int delta, View source) {
        int next = Math.max(0, count + delta);
        if (next == count) return;
        count = next;
        save();
        if (source != null) {
            source.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }
        render(true);
    }

    private void confirmReset() {
        if (count == 0) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.reset_title)
                .setPositiveButton(R.string.reset, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        count = 0;
                        save();
                        render(false);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void render(boolean animate) {
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

    private void save() {
        prefs.edit().putInt(KEY_COUNT, count).apply();
    }

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
