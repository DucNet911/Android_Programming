package com.puzzlehub.sudoku.ui.widgets;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.LinearInterpolator;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.puzzlehub.sudoku.game.R;

public final class StreakBadgeView extends View {
    private static final float FULL_TURN = (float) (Math.PI * 2);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path flame = new Path();
    private final Path core = new Path();
    private final LinearGradient heat = new LinearGradient(0f, 2f, 0f, 24f,
            0xFFFFCA55, 0xFFFF6037, Shader.TileMode.CLAMP);
    private ValueAnimator animator;
    private float phase;
    private int days;

    public StreakBadgeView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setClickable(true);
        setFocusable(true);
    }

    public int getDays() { return days; }

    public void display(int current, int best) {
        days = current;
        setContentDescription("Chuỗi " + current + " ngày, tốt nhất " + best + " ngày");
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        float density = getResources().getDisplayMetrics().density;
        paint.setShader(null);
        paint.setColor(0x19FF9C38);
        canvas.drawRoundRect(0f, 0f, getWidth(), getHeight(), 14 * density, 14 * density, paint);
        int checkpoint = canvas.save();
        canvas.translate(3.5f * density, getHeight() / 2f - 15f * density);
        canvas.scale(0.9f * density, 0.9f * density);
        drawFlame(canvas);
        canvas.restoreToCount(checkpoint);

        paint.setColor(ContextCompat.getColor(getContext(), R.color.ink));
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, getResources().getDisplayMetrics()));
        paint.setTextAlign(Paint.Align.CENTER);
        String value = String.valueOf(days);
        if (days > 99) {
            value = "99+";
        }
        canvas.drawText(value, getWidth() * 0.74f,
                getHeight() / 2f - (paint.ascent() + paint.descent()) / 2f, paint);
    }

    private void drawFlame(Canvas canvas) {
        float sway = (float) Math.sin(phase) * 1.4f;
        float flicker = (float) Math.sin(phase * 2f) * 0.7f;
        flame.rewind();
        flame.moveTo(11f + sway, 2f + flicker);
        flame.cubicTo(11f + sway, 8f, 19f + flicker, 10f, 18.5f, 16.5f);
        flame.cubicTo(18f, 26f, 2f, 26f, 2f, 17f);
        flame.cubicTo(1.5f, 12f, 6f, 9f, 6.5f + sway * 0.35f, 7f - flicker);
        flame.cubicTo(6f, 12f, 8f, 14f, 10f, 13f);
        flame.cubicTo(13f, 10f, 12f + sway, 6f, 11f + sway, 2f + flicker);
        flame.close();
        paint.setShader(heat);
        paint.setAlpha(255);
        canvas.drawPath(flame, paint);
        paint.setShader(null);
        paint.setColor(0xFFFFEEA6);
        core.rewind();
        core.moveTo(10f - sway * 0.35f, 13f + flicker);
        core.cubicTo(4f, 19f, 8f, 24f, 11f, 23f);
        core.cubicTo(16f, 21f, 12f, 17f, 10f - sway * 0.35f, 13f + flicker);
        core.close();
        canvas.drawPath(core, paint);
        if (animator != null) {
            for (int index = 0; index < 2; index++) {
                float rise = (phase / FULL_TURN + index * 0.5f) % 1f;
                paint.setColor(0xFFFFAE43);
                paint.setAlpha((int) (Math.sin(rise * Math.PI) * 180));
                canvas.drawCircle(4f + index * 13f + (float) Math.sin(phase + index) * 0.6f,
                        7f - rise * 6f, 0.65f, paint);
            }
        }
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); updateAnimation(); }

    @Override public void onVisibilityAggregated(boolean visible) {
        super.onVisibilityAggregated(visible);
        updateAnimation();
    }

    @Override protected void onDetachedFromWindow() {
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
        animator = ValueAnimator.ofFloat(0f, FULL_TURN);
        animator.setDuration(1200L);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(frame -> { phase = (float) frame.getAnimatedValue(); invalidate(); });
        animator.start();
    }

    private void stopAnimation() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }
}
