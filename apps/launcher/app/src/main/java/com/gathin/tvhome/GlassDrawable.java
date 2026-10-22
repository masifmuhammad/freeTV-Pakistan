package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A Liquid Glass surface without the blur these GPUs cannot afford: a thin tint that lets the soft
 * {@link GlassBackdrop} show through, a sheen that is brightest along the top edge, and a specular rim
 * that catches the light at the top-left and bottom-right corners. All three are gradients, built once
 * per size, so a frame costs three rounded rectangles.
 */
final class GlassDrawable extends Drawable {

    private static final int WHITE = 0xFFFFFF;
    /** White at these alphas: the sheen from the top edge down, the rim along the diagonal. */
    private static final int[] SHEEN_DARK = {0x2E, 0x0D, 0x05}, SHEEN_LIGHT = {0x73, 0x26, 0x14};
    private static final float[] SHEEN_STOPS = {0f, 0.45f, 1f};
    private static final int[] RIM_DARK = {0xC0, 0x30, 0x10, 0x66}, RIM_LIGHT = {0xFF, 0x99, 0x4D, 0xCC};
    private static final float[] RIM_STOPS = {0f, 0.3f, 0.65f, 1f};
    private static final float RIM_DP = 1.5f;

    private final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final boolean dark;
    private final float radius;

    GlassDrawable(Palette p, float radiusPx, float density) {
        dark = p.dark;
        radius = radiusPx;
        tint.setColor(tint(p));
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(RIM_DP * density);
    }

    /** The glass's own colour: dark glass dims what is behind it, light glass is milky. */
    static int tint(Palette p) {
        return p.dark ? Palette.alpha(p.surface, 0x5C) : Palette.alpha(WHITE, 0x80);
    }

    /** A flat rim, for shapes that change their own corners (pills, menus) and carry no gradient. */
    static int edge(Palette p) {
        return Palette.alpha(WHITE, p.dark ? 0x59 : 0xCC);
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        sheen.setShader(new LinearGradient(0, bounds.top, 0, bounds.bottom,
                whites(dark ? SHEEN_DARK : SHEEN_LIGHT), SHEEN_STOPS, Shader.TileMode.CLAMP));
        rim.setShader(new LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
                whites(dark ? RIM_DARK : RIM_LIGHT), RIM_STOPS, Shader.TileMode.CLAMP));
    }

    private static int[] whites(int[] alphas) {
        int[] colors = new int[alphas.length];
        for (int index = 0; index < alphas.length; index++) {
            colors[index] = Palette.alpha(WHITE, alphas[index]);
        }
        return colors;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        rect.set(getBounds());
        float corner = Math.min(radius, rect.height() / 2f);
        canvas.drawRoundRect(rect, corner, corner, tint);
        canvas.drawRoundRect(rect, corner, corner, sheen);
        float half = rim.getStrokeWidth() / 2f;
        rect.inset(half, half);
        canvas.drawRoundRect(rect, corner - half, corner - half, rim);
    }

    @Override
    public void setAlpha(int alpha) {
        tint.setAlpha(alpha);
        sheen.setAlpha(alpha);
        rim.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter filter) {
        tint.setColorFilter(filter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
