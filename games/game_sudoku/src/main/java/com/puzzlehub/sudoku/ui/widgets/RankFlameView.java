package com.puzzlehub.sudoku.ui.widgets;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

public final class RankFlameView extends View {
    private static final float SIDE_OVERFLOW_DP = 9f;
    private static final float CARD_HEIGHT_DP = 92f;
    private static final float[] CORNER_OFFSETS = {5f, 18f, 32f, 47f, 62f};
    private static final float[] FLAME_HEIGHTS = {14f, 24f, 21f, 17f, 10f};
    private static final float FULL_TURN = (float) (Math.PI * 2.0);

    private final float density;
    private final Path tongue = new Path();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final LinearGradient outer = new LinearGradient(0, -1, 0, 0.19f,
            new int[]{0xFFC53F6B, 0xFFF85C29, 0xFFFF8425, 0x89E34E23},
            new float[]{0f, 0.43f, 0.76f, 1f}, Shader.TileMode.CLAMP);
    private final LinearGradient inner = new LinearGradient(0, -1, 0, 0.22f,
            new int[]{0xFFFFA521, 0xFFFFD055, 0xFFFFF2A0, 0xCCFBA134},
            new float[]{0f, 0.48f, 0.8f, 1f}, Shader.TileMode.CLAMP);

    private float phase = 0.33f;
    private ValueAnimator animator;

    public RankFlameView(Context context, AttributeSet attributes) {
        super(context, attributes);
        density = getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float top = (getHeight() - CARD_HEIGHT_DP * density) / 2f;
        float bottom = getHeight() - top;
        float left = SIDE_OVERFLOW_DP * density;
        float right = getWidth() - left;

        drawCorner(canvas, left, top, false, false, 0.4f);
        drawCorner(canvas, right, top, true, false, 2.6f);
        drawCorner(canvas, left, bottom, false, true, 1.7f);
        drawCorner(canvas, right, bottom, true, true, 3.7f);
    }

    private void drawCorner(Canvas canvas, float edgeX, float edgeY, boolean right,
                            boolean bottom, float timing) {
        float direction = 1f;
        if (right) {
            direction = -1f;
        }
        canvas.save();
        canvas.translate(edgeX, edgeY);
        canvas.scale(direction * density, density);
        if (bottom) {
            canvas.rotate(151f);
            canvas.scale(-0.78f, 0.64f);
        }
        canvas.translate(2f, 2f);
        for (int index = 0; index < CORNER_OFFSETS.length; index++) {
            float wave = phase * FULL_TURN + timing + index * 0.81f;
            float height = FLAME_HEIGHTS[index] * (0.83f + 0.16f * (float) Math.sin(wave));
            float sway = 0.19f + 0.34f * (float) Math.cos(wave);
            float spread = 15f - index * 0.9f;
            drawTongue(canvas, CORNER_OFFSETS[index], spread, height, sway);
        }
        drawEmbers(canvas, timing);
        canvas.restore();
    }

    private void drawTongue(Canvas canvas, float x, float width, float height, float sway) {
        canvas.save();
        canvas.translate(x, 0);
        canvas.scale(width, height);
        paint.setShader(outer);
        shape(sway);
        canvas.drawPath(tongue, paint);
        canvas.translate(0.11f, 0.07f);
        canvas.scale(0.65f, 0.67f);
        paint.setShader(inner);
        shape(sway + 0.18f);
        canvas.drawPath(tongue, paint);
        paint.setShader(null);
        canvas.restore();
    }

    private void shape(float sway) {
        tongue.rewind();
        tongue.moveTo(-0.64f, 0.12f);
        tongue.cubicTo(-0.85f, -0.08f, -0.73f, -0.31f, -0.38f, -0.46f);
        tongue.cubicTo(-0.19f, -0.55f, -0.36f + sway, -0.74f, -0.27f + sway, -1f);
        tongue.cubicTo(0.03f + sway, -0.78f, 0.34f + sway, -0.61f, 0.28f + sway, -0.34f);
        tongue.cubicTo(0.50f + sway, -0.39f, 0.53f + sway, -0.45f, 0.54f + sway, -0.50f);
        tongue.cubicTo(0.72f + sway, -0.29f, 0.81f, -0.03f, 0.64f, 0.15f);
        tongue.cubicTo(0.34f, 0.23f, -0.38f, 0.25f, -0.64f, 0.12f);
        tongue.close();
    }

    private void drawEmbers(Canvas canvas, float timing) {
        for (int index = 0; index < 6; index++) {
            float travel = (phase + timing * 0.11f + index * 0.18f) % 1f;
            float x = 9f + index * 8.9f + travel * 8f;
            float y = -7f - travel * (13f + index);
            int opacity = (int) (225f * Math.sin(travel * Math.PI));
            paint.setColor(Color.argb(opacity, 255, 173 + index * 9, 66));
            canvas.drawCircle(x, y, 0.7f + (index % 3) * 0.32f, paint);
        }
        paint.setAlpha(255);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateAnimation();
    }

    @Override
    public void onVisibilityAggregated(boolean visible) {
        super.onVisibilityAggregated(visible);
        updateAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnimation();
        super.onDetachedFromWindow();
    }

    private void updateAnimation() {
        if (!isAttachedToWindow() || !isShown() || getWindowVisibility() != VISIBLE
                || (android.os.Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled())) {
            stopAnimation();
            return;
        }
        if (animator != null) {
            return;
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1250L);
        animator.setInterpolator(new LinearInterpolator());
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(frame -> {
            phase = (float) frame.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    private void stopAnimation() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }
}
