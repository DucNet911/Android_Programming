package com.puzzlehub.sudoku.ui.widgets;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

public final class CelebrationOverlay extends View {
    private static final int[] COLORS = {0xFF3F83FF, 0xFFFFBD49, 0xFF4AD6B1, 0xFFB18AFF, 0xFFFF7E99};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float progress;
    private ValueAnimator animator;

    private CelebrationOverlay(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setClickable(false);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1500L);
        animator.addUpdateListener(frame -> {
            progress = (float) frame.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (getParent() instanceof ViewGroup container) {
                    container.removeView(CelebrationOverlay.this);
                }
            }
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float density = getResources().getDisplayMetrics().density;
        for (int index = 0; index < 44; index++) {
            float x = getWidth() * (index * 37 % 101 / 100f)
                    + (float) Math.sin(progress * 5 + index) * 18 * density;
            float y = -getHeight() * 0.15f + progress * getHeight() * 1.25f
                    + (index % 7) * getHeight() * 0.05f;
            paint.setColor(COLORS[index % COLORS.length]);
            paint.setAlpha((int) ((1f - progress) * 220));
            int checkpoint = canvas.save();
            canvas.rotate(progress * 220 + index * 17f, x, y);
            canvas.drawRoundRect(x, y, x + (3 + index % 3) * density, y + 7 * density, density, density, paint);
            canvas.restoreToCount(checkpoint);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.removeAllListeners();
            animator.cancel();
            animator = null;
        }
        super.onDetachedFromWindow();
    }

    public static void showOver(FrameLayout surface) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ValueAnimator.areAnimatorsEnabled()) {
            return;
        }
        if (surface.isLaidOut() && !surface.isLayoutRequested()) {
            addOverlay(surface);
            return;
        }
        surface.addOnLayoutChangeListener(new OnLayoutChangeListener() {
            @Override public void onLayoutChange(View view, int l, int t, int r, int b, int oldL, int oldT, int oldR, int oldB) {
                view.removeOnLayoutChangeListener(this);
                addOverlay(surface);
            }
        });
    }

    private static void addOverlay(FrameLayout surface) {
        surface.addView(new CelebrationOverlay(surface.getContext()),
                new FrameLayout.LayoutParams(surface.getWidth(), surface.getHeight()));
    }
}
