package com.minimal.counter;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

/**
 * A clear "liquid glass" disc showing a number.
 *
 * Look: faint smoky frost, a darker refraction band toward the edge, a bright specular rim
 * (strong top-left, glint bottom-right), a sheen on the top half and a soft caustic glow at the
 * bottom. Drawn entirely by the app, so it looks the same on every phone.
 *
 * Feel: squishes when pressed, wobbles like a drop of liquid when released, sends a light ripple
 * out from the finger on a tap, and stretches in the direction it's dragged.
 */
final class GlassView extends View {

    private final float dp;
    private final float pad; // space around the disc for wobble + shadow

    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint smoke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint frost = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint caustic = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ripple = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path disc = new Path();
    private final RectF oval = new RectF();

    private String label = "0";
    private float cx, cy, r;

    // Animated state.
    private float sx = 1f, sy = 1f;   // shape scale (wobble / squash / stretch)
    private float press;              // 0..1, brightens the glass while touched
    private float rippleT = -1f;      // 0..1 while a ripple is running
    private float rippleX, rippleY;
    private ValueAnimator wobbleAnim, pressAnim, rippleAnim;

    GlassView(Context c, float padPx) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        pad = padPx;
        setLayerType(LAYER_TYPE_HARDWARE, null);

        smoke.setColor(0x33141419);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(0.9f * dp);
        ripple.setStyle(Paint.Style.STROKE);

        text.setColor(0xFFFFFFFF);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        text.setFontFeatureSettings("tnum");
        text.setShadowLayer(3 * dp, 0, 0.5f * dp, 0x80000000);
    }

    void setLabel(String s) {
        label = s;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        cx = w / 2f;
        cy = h / 2f;
        r = Math.min(w, h) / 2f - pad;
        if (r <= 0) return;

        disc.reset();
        disc.addCircle(cx, cy, r, Path.Direction.CW);

        shadow.setShader(new RadialGradient(cx, cy + 2 * dp, r + pad * 0.8f,
                new int[] {0x38000000, 0x38000000, 0x00000000},
                new float[] {0f, r * 0.85f / (r + pad * 0.8f), 1f}, Shader.TileMode.CLAMP));
        frost.setShader(new RadialGradient(cx, cy - r * 0.3f, r * 1.2f,
                0x1AFFFFFF, 0x3DFFFFFF, Shader.TileMode.CLAMP));
        band.setShader(new RadialGradient(cx, cy, r,
                new int[] {0x00000000, 0x00000000, 0x1F000000, 0x47FFFFFF},
                new float[] {0f, 0.72f, 0.93f, 1f}, Shader.TileMode.CLAMP));
        sheen.setShader(new LinearGradient(0, cy - r, 0, cy,
                0x80FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        caustic.setShader(new RadialGradient(cx, cy + r * 0.78f, r * 0.55f,
                0x4DFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        rim.setShader(new LinearGradient(cx - r, cy - r, cx + r, cy + r,
                new int[] {0xFFFFFFFF, 0x40FFFFFF, 0x1FFFFFFF, 0xB3FFFFFF},
                new float[] {0f, 0.4f, 0.62f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas c) {
        if (r <= 0) return;

        c.save();
        c.scale(sx, sy, cx, cy);

        c.drawCircle(cx, cy + 2 * dp, r + pad * 0.8f, shadow);

        c.save();
        c.clipPath(disc);
        c.drawCircle(cx, cy, r, smoke);
        frost.setAlpha(Math.round(255 * (0.75f + 0.25f * press)));
        c.drawCircle(cx, cy, r, frost);
        c.drawCircle(cx, cy, r, band);
        sheen.setAlpha(Math.round(255 * (0.7f + 0.3f * press)));
        oval.set(cx - r * 0.72f, cy - r * 0.92f, cx + r * 0.72f, cy + 0.08f * r);
        c.drawOval(oval, sheen);
        c.drawCircle(cx, cy, r, caustic);
        if (rippleT >= 0f) {
            float t = rippleT;
            ripple.setStrokeWidth((1f - t) * 10 * dp + dp);
            ripple.setColor(((int) (0x73 * (1f - t)) << 24) | 0xFFFFFF);
            c.drawCircle(rippleX, rippleY, t * r * 2.1f, ripple);
        }
        c.restore();

        c.drawCircle(cx, cy, r - rim.getStrokeWidth() / 2f, rim);

        int n = label.length();
        float sp = n <= 2 ? 24 : n == 3 ? 21 : n == 4 ? 17 : n == 5 ? 14 : 12;
        text.setTextSize(sp * getResources().getDisplayMetrics().scaledDensity);
        Paint.FontMetrics fm = text.getFontMetrics();
        c.drawText(label, cx, cy - (fm.ascent + fm.descent) / 2f, text);

        c.restore();
    }

    // ------------------------------------------------------------ animations

    /** Finger down: glass squishes a little and brightens. */
    void pressDown() {
        cancel(wobbleAnim);
        animatePress(1f, 90);
        animateScale(0.9f, 0.9f, 110);
    }

    /** Finger up after a tap: liquid wobble + ripple from where the finger was (view coords). */
    void tapRelease(float x, float y) {
        animatePress(0f, 260);
        startRipple(x, y);
        wobble(0.17f);
    }

    /** Finger up after a drag: settle with a smaller wobble. */
    void dragRelease() {
        animatePress(0f, 260);
        wobble(0.10f);
    }

    /** While dragging: stretch along the direction of travel (velocity in px/s). */
    void stretch(float vx, float vy) {
        cancel(wobbleAnim);
        float speed = (float) Math.hypot(vx, vy);
        float amt = Math.min(speed / 12000f, 0.14f);
        float tx, ty;
        if (Math.abs(vx) >= Math.abs(vy)) {
            tx = 1f + amt;
            ty = 1f - amt * 0.6f;
        } else {
            tx = 1f - amt * 0.6f;
            ty = 1f + amt;
        }
        // Smooth toward the target so it feels fluid rather than jumpy.
        sx += (tx - sx) * 0.35f;
        sy += (ty - sy) * 0.35f;
        invalidate();
    }

    /** Damped, out-of-phase oscillation of width and height: a jelly / water-drop settle. */
    private void wobble(final float strength) {
        cancel(wobbleAnim);
        final float sx0 = sx, sy0 = sy;
        wobbleAnim = ValueAnimator.ofFloat(0f, 1f);
        wobbleAnim.setDuration(700);
        wobbleAnim.setInterpolator(new LinearInterpolator());
        wobbleAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                float t = (Float) a.getAnimatedValue() * 0.7f; // seconds
                float decay = (float) Math.exp(-6.5 * t);
                float osc = (float) Math.sin(2 * Math.PI * 3.2 * t);
                float base = 1f + ((sx0 + sy0) / 2f - 1f) * decay * (float) Math.cos(2 * Math.PI * 3.2 * t);
                sx = base + strength * decay * osc;
                sy = base - strength * decay * osc;
                invalidate();
            }
        });
        wobbleAnim.start();
    }

    private void animateScale(float tx, float ty, long ms) {
        final float fx = sx, fy = sy;
        final float dx = tx - fx, dy = ty - fy;
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(ms);
        a.setInterpolator(new DecelerateInterpolator());
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator v) {
                float f = (Float) v.getAnimatedValue();
                sx = fx + dx * f;
                sy = fy + dy * f;
                invalidate();
            }
        });
        wobbleAnim = a;
        a.start();
    }

    private void animatePress(float target, long ms) {
        cancel(pressAnim);
        pressAnim = ValueAnimator.ofFloat(press, target);
        pressAnim.setDuration(ms);
        pressAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                press = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        pressAnim.start();
    }

    private void startRipple(float x, float y) {
        cancel(rippleAnim);
        rippleX = x;
        rippleY = y;
        rippleAnim = ValueAnimator.ofFloat(0f, 1f);
        rippleAnim.setDuration(520);
        rippleAnim.setInterpolator(new DecelerateInterpolator(1.6f));
        rippleAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                rippleT = (Float) a.getAnimatedValue();
                if (a.getAnimatedFraction() >= 1f) rippleT = -1f;
                invalidate();
            }
        });
        rippleAnim.start();
    }

    private static void cancel(ValueAnimator a) {
        if (a != null) a.cancel();
    }
}
