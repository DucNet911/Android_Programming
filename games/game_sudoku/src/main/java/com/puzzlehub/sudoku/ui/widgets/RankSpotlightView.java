package com.puzzlehub.sudoku.ui.widgets;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

public final class RankSpotlightView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final float density;
    private final Matrix shaderMatrix = new Matrix();
    private LinearGradient background;
    private RadialGradient glow;
    private boolean showRings = true;
    private View badge;
    private ValueAnimator animator;
    private float phase;

    public RankSpotlightView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
    }

    public void hideRings() {
        showRings = false;
        invalidate();
    }

    public void animateBadge(View target) {
        badge = target;
        updateAnimation();
    }

    @Override
    protected void onSizeChanged(int width, int height, int previousWidth, int previousHeight) {
        super.onSizeChanged(width, height, previousWidth, previousHeight);
        bounds.set(0, 0, width, height);
        if (width > 0 && height > 0) {
            background = new LinearGradient(0, height, width, 0,
                    new int[]{0xFF11285C, 0xFF174589, 0xFF2857AE, 0xFF353E92, 0xFF292757},
                    new float[]{0f, 0.27f, 0.55f, 0.83f, 1f}, Shader.TileMode.CLAMP);
            glow = new RadialGradient(0, 0, width * 0.62f,
                    new int[]{0x5F83CFFF, 0x295E83DF, 0x005E83DF},
                    new float[]{0f, 0.48f, 1f}, Shader.TileMode.CLAMP);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (background == null) {
            return;
        }
        float radius = 23f * density;
        drawBackground(canvas, radius);
        if (showRings) {
            drawBadgeRings(canvas);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(density);
        paint.setColor(0xAA9EA9F9);
        canvas.drawRoundRect(0.5f * density, 0.5f * density,
                getWidth() - 0.5f * density, getHeight() - 0.5f * density, radius, radius, paint);
    }

    private void drawBackground(Canvas canvas, float radius) {
        double angle = phase * 2.0 * Math.PI;
        float travel = (float) Math.sin(angle);
        float width = getWidth();
        float height = getHeight();

        shaderMatrix.setRotate(14f * travel, width * 0.5f, height * 0.5f);
        shaderMatrix.postTranslate(width * 0.28f * travel, height * 0.16f * (float) Math.cos(angle));
        background.setLocalMatrix(shaderMatrix);
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
        paint.setShader(background);
        canvas.drawRoundRect(bounds, radius, radius, paint);

        shaderMatrix.setScale(1f, 0.46f);
        shaderMatrix.postTranslate(width * (0.5f + 0.58f * travel),
                height * (0.42f + 0.17f * (float) Math.cos(angle)));
        glow.setLocalMatrix(shaderMatrix);
        paint.setShader(glow);
        canvas.drawRoundRect(bounds, radius, radius, paint);
        paint.setShader(null);
    }

    private void drawBadgeRings(Canvas canvas) {
        float centerX = 47f * density;
        float centerY = getHeight() * 0.52f;
        for (int ring = 0; ring < 3; ring++) {
            float radiusPx = (24f + ring * 9f) * density;
            paint.setColor(Color.argb(55 - ring * 10, 154, 223, 255));
            paint.setStrokeWidth(density);
            paint.setStyle(Paint.Style.STROKE);
            float offset = phase * 360f + ring * 48f;
            canvas.drawArc(centerX - radiusPx, centerY - radiusPx, centerX + radiusPx, centerY + radiusPx,
                    offset, 225f, false, paint);
        }

    }

    private void updateAnimation() {
        boolean play = isAttachedToWindow() && isShown() && getWindowVisibility() == VISIBLE
                && (android.os.Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled());
        if (!play) {
            stopAnimation();
            return;
        }
        if (animator != null) {
            return;
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(4400);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            phase = (float) animation.getAnimatedValue();
            if (badge != null) {
                double orbit = phase * 2.0 * Math.PI;
                float horizontal = (float) Math.cos(orbit);
                float vertical = (float) Math.sin(orbit);
                float scale = 1f + 0.065f * (1f - horizontal) / 2f;
                badge.setTranslationX(horizontal * 3.2f * density);
                badge.setTranslationY(vertical * 4f * density);
                badge.setScaleX(scale);
                badge.setScaleY(scale);
            }
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateAnimation();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        updateAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnimation();
        super.onDetachedFromWindow();
    }

    private void stopAnimation() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        if (badge != null) {
            badge.setTranslationX(0f);
            badge.setTranslationY(0f);
            badge.setScaleX(1f);
            badge.setScaleY(1f);
        }
    }
}
