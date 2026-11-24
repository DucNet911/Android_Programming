package com.puzzlehub.sudoku.ui.widgets;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.PathInterpolator;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.puzzlehub.sudoku.game.R;

/** A short trail behind the moving navigation selection. */
public final class SlideParticlesView extends View {
    private static final float END = 1.4f;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final PathInterpolator glide = new PathInterpolator(0.32f, 0f, 0.2f, 1f);
    private float progress = END;
    private float from;
    private float to;
    private ValueAnimator tail;

    public SlideParticlesView(Context context, @Nullable AttributeSet attrs) { super(context, attrs); }

    public void slide(float start, float end) {
        cancelTail();
        from = start;
        to = end;
        progress = 0f;
        invalidate();
    }

    public void setProgress(float value) {
        progress = END;
        if (android.os.Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled()) {
            progress = value;
        }
        invalidate();
    }

    public void finish() {
        cancelTail();
        tail = ValueAnimator.ofFloat(progress, END);
        tail.setDuration(220L);
        tail.addUpdateListener(frame -> setProgress((float) frame.getAnimatedValue()));
        tail.start();
    }

    private void cancelTail() {
        if (tail != null) {
            tail.cancel();
            tail = null;
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        if (progress >= END) {
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        float direction = -1f;
        if (to > from) {
            direction = 1f;
        }
        int blue = ContextCompat.getColor(getContext(), R.color.primary);
        int pale = ContextCompat.getColor(getContext(), R.color.liquid_particle);
        for (int index = 0; index < 30; index++) {
            float step = progress - (index % 10) * 0.035f;
            if (step <= 0f || step >= 1f) {
                continue;
            }
            float wake = (float) Math.sin(Math.PI * step);
            float x = from + (to - from) * glide.getInterpolation(step) - direction * (index % 5) * 3f * density * wake;
            float lane = (index / 10 - 1) * 15f + (index % 4 - 1.5f) * 3f;
            float y = getHeight() / 2f + lane * density * wake + (float) Math.sin(step * Math.PI * 2 + index) * 3f * density;
            paint.setColor(pale);
            if (index % 3 == 0) {
                paint.setColor(blue);
            }
            int alpha = (int) (220 * wake);
            float radius = (0.8f + (index % 4) * 0.25f) * density;
            paint.setAlpha(alpha / 6);
            canvas.drawCircle(x, y, radius * 2.2f, paint);
            paint.setAlpha(alpha);
            canvas.drawCircle(x, y, radius, paint);
        }
    }

    @Override protected void onDetachedFromWindow() {
        cancelTail();
        progress = END;
        super.onDetachedFromWindow();
    }
}
