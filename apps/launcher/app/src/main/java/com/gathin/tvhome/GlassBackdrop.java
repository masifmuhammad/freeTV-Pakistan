package com.gathin.tvhome;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.view.View;

import com.google.android.material.color.utilities.Hct;

/**
 * Liquid Glass's backdrop: the scheme's surface with a few large, soft pools of colour in vivid hues
 * around the seed's. It has no edges and no detail, so see-through glass over it reads as frosted
 * without a blur. Painted once per scheme into a quarter-size bitmap and stretched: every frame after
 * that is one bitmap draw, and nothing moves.
 */
@SuppressLint("RestrictedApi")
final class GlassBackdrop extends View {

    private static final int SCALE_DOWN = 4;
    /** Each pool: centre x, centre y and radius as shares of the width, hue turn from the seed, opacity. */
    private static final float[][] POOLS = {
            {0.12f, 0.95f, 0.42f, 0f, 1f},
            {0.78f, 0.72f, 0.48f, 55f, 1f},
            {0.46f, 1.12f, 0.38f, -60f, 0.9f},
            {0.97f, 0.08f, 0.26f, 150f, 0.55f}};
    private static final double CHROMA_DARK = 56, TONE_DARK = 42, CHROMA_LIGHT = 40, TONE_LIGHT = 84;
    /** The share of a pool's opacity left halfway out, for a falloff softer than a straight fade. */
    private static final float HALFWAY = 0.45f;

    private final Paint stretch = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect screen = new Rect();
    private Palette palette;
    private Bitmap field;

    GlassBackdrop(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setPalette(Palette p) {
        palette = p;
        paintField();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        paintField();
    }

    private void paintField() {
        if (palette == null || getWidth() == 0 || getHeight() == 0) return;
        int width = Math.max(1, getWidth() / SCALE_DOWN), height = Math.max(1, getHeight() / SCALE_DOWN);
        if (field == null || field.getWidth() != width || field.getHeight() != height) {
            field = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        }
        Canvas canvas = new Canvas(field);
        canvas.drawColor(palette.surface);
        double seedHue = Hct.fromInt(palette.seed).getHue();
        Paint pool = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        for (float[] shape : POOLS) {
            int color = poolColor(seedHue + shape[3]);
            float x = shape[0] * width, y = shape[1] * height, radius = shape[2] * width;
            int[] fade = {Palette.alpha(color, Math.round(255 * shape[4])),
                    Palette.alpha(color, Math.round(255 * shape[4] * HALFWAY)), Palette.alpha(color, 0)};
            pool.setShader(new RadialGradient(x, y, radius, fade, new float[]{0f, 0.5f, 1f},
                    Shader.TileMode.CLAMP));
            canvas.drawCircle(x, y, radius, pool);
        }
    }

    private int poolColor(double hue) {
        double turned = ((hue % 360) + 360) % 360;
        return palette.dark ? Hct.from(turned, CHROMA_DARK, TONE_DARK).toInt()
                : Hct.from(turned, CHROMA_LIGHT, TONE_LIGHT).toInt();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (field == null) return;
        screen.set(0, 0, getWidth(), getHeight());
        canvas.drawBitmap(field, null, screen, stretch);
    }
}
