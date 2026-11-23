package com.minimal.counter;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * A round "tinted glass" disc: see-through tint that gets denser toward the edge, a soft sheen
 * on the top half and a bright specular rim (strong top-left, faint bottom-right).
 *
 * Its outline is a round rect with radius = half the size, so when it is used as a window
 * background with a background blur radius (Android 12+), the blur is clipped to the circle.
 */
final class GlassDrawable extends Drawable {

    private final int tint;
    private final float rim;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    /** @param tint opaque RGB tint colour; @param rimPx width of the bright rim in pixels. */
    GlassDrawable(int tint, float rimPx) {
        this.tint = tint & 0x00FFFFFF;
        this.rim = rimPx;
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(rimPx);
        innerEdge.setStyle(Paint.Style.STROKE);
        innerEdge.setStrokeWidth(rimPx);
    }

    @Override
    protected void onBoundsChange(Rect b) {
        float cx = b.exactCenterX();
        float cy = b.exactCenterY();
        float rad = Math.min(b.width(), b.height()) / 2f;
        if (rad <= 0) return;

        // Tint: lighter in the middle, denser at the edge (like thicker glass at the rim).
        fill.setShader(new RadialGradient(cx, cy, rad,
                new int[] {(0x70 << 24) | tint, (0x85 << 24) | tint, (0xB3 << 24) | tint},
                new float[] {0f, 0.7f, 1f}, Shader.TileMode.CLAMP));

        // Soft sheen on the upper half.
        sheen.setShader(new LinearGradient(0, b.top, 0, cy + rad * 0.1f,
                0x4DFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));

        // Specular rim: bright top-left, fading, then a faint glint bottom-right.
        edge.setShader(new LinearGradient(b.left, b.top, b.right, b.bottom,
                new int[] {0xE6FFFFFF, 0x33FFFFFF, 0x1AFFFFFF, 0x80FFFFFF},
                new float[] {0f, 0.45f, 0.6f, 1f}, Shader.TileMode.CLAMP));

        // Very faint darker inner line so the disc reads on white backgrounds too.
        innerEdge.setShader(new LinearGradient(0, b.top, 0, b.bottom,
                0x00000000, 0x26000000, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        float cx = b.exactCenterX();
        float cy = b.exactCenterY();
        float rad = Math.min(b.width(), b.height()) / 2f;

        c.drawCircle(cx, cy, rad, fill);

        r.set(cx - rad * 0.78f, cy - rad * 0.96f, cx + rad * 0.78f, cy + rad * 0.2f);
        c.drawOval(r, sheen);

        c.drawCircle(cx, cy, rad - rim * 1.6f, innerEdge);
        c.drawCircle(cx, cy, rad - rim / 2f, edge);
    }

    @Override
    public void getOutline(Outline outline) {
        Rect b = getBounds();
        outline.setRoundRect(b, Math.min(b.width(), b.height()) / 2f);
        outline.setAlpha(0.3f); // light shadow: it's glass
    }

    @Override
    public void setAlpha(int alpha) {
        fill.setAlpha(alpha);
        sheen.setAlpha(alpha);
        edge.setAlpha(alpha);
        innerEdge.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        fill.setColorFilter(cf);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
