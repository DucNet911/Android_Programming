package com.puzzlehub.sudoku.ui.widgets;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.puzzlehub.sudoku.game.R;

/** Blur the backdrop while keeping the child icons and text sharp. */
public final class FrostedGlassLayout extends FrameLayout {
    private static final int COLUMNS = 24;
    private static final int ROWS = 8;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF bounds = new RectF();
    private final Path clipping = new Path();
    private final Path reflection = new Path();
    private final float[] mesh = new float[(COLUMNS + 1) * (ROWS + 1) * 2];
    private final ViewTreeObserver.OnScrollChangedListener scrollListener = this::refreshBackdrop;
    private final OnLayoutChangeListener layoutListener = (view, l, t, r, b, ol, ot, or, ob) -> refreshBackdrop();
    private View backdrop;
    private Bitmap snapshot;
    private boolean observing;
    private boolean queued;
    private long refreshUntil;
    private boolean liquid;
    private int blurRadius = 1;
    private float liquidFocus = 0.5f;
    private float liquidMotion;
    private int glassTint = Color.TRANSPARENT;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            queued = false;
            captureBackdrop();
            if (isAttachedToWindow() && isShown() && SystemClock.uptimeMillis() < refreshUntil) {
                queued = true;
                postDelayed(this, 40L);
            }
        }
    };

    public FrostedGlassLayout(Context context) { this(context, null); }

    public FrostedGlassLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
    }

    public void enableLiquidMode() {
        enableLiquidMode(1);
    }

    public void enableLiquidMode(int radius) {
        liquid = true;
        blurRadius = Math.max(1, Math.min(radius, 6));
        refreshBackdrop();
        invalidate();
    }

    public void setGlassTint(int tint) {
        glassTint = tint;
        invalidate();
    }

    public void setLiquidFocus(float position, float motion) {
        liquidFocus = Math.max(0f, Math.min(position, 1f));
        liquidMotion = Math.max(0f, Math.min(motion, 1f));
        invalidate();
    }

    public void bindBackdrop(View view) {
        if (view == this || isAncestorOf(view)) {
            throw new IllegalArgumentException("Glass backdrop must be a separate view.");
        }
        stopObserving();
        backdrop = view;
        if (isAttachedToWindow()) {
            startObserving();
        }
        refreshBackdrop();
    }

    private boolean isAncestorOf(View view) {
        for (ViewParent parent = view.getParent(); parent instanceof View; parent = parent.getParent()) {
            if (parent == this) {
                return true;
            }
        }
        return false;
    }

    public void refreshBackdrop() { refreshBackdrop(0L); }

    public void refreshBackdrop(long duration) {
        refreshUntil = Math.max(refreshUntil, SystemClock.uptimeMillis() + duration);
        if (!queued && isAttachedToWindow()) {
            queued = true;
            postOnAnimation(refresh);
        }
    }

    private void startObserving() {
        if (observing || backdrop == null) {
            return;
        }
        backdrop.getViewTreeObserver().addOnScrollChangedListener(scrollListener);
        backdrop.addOnLayoutChangeListener(layoutListener);
        observing = true;
    }

    private void stopObserving() {
        if (observing && backdrop != null) {
            ViewTreeObserver observer = backdrop.getViewTreeObserver();
            if (observer.isAlive()) {
                observer.removeOnScrollChangedListener(scrollListener);
            }
            backdrop.removeOnLayoutChangeListener(layoutListener);
        }
        observing = false;
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startObserving();
        refreshBackdrop();
    }

    @Override protected void onDetachedFromWindow() {
        stopObserving();
        removeCallbacks(refresh);
        queued = false;
        snapshot = null;
        super.onDetachedFromWindow();
    }

    @Override protected void onSizeChanged(int w, int h, int oldWidth, int oldHeight) {
        super.onSizeChanged(w, h, oldWidth, oldHeight);
        bounds.set(0f, 0f, w, h);
        clipping.reset();
        float radius = Math.min(28f * density(), h / 2f);
        clipping.addRoundRect(bounds, radius, radius, Path.Direction.CW);
        refreshBackdrop();
    }

    private void captureBackdrop() {
        if (backdrop == null || !isShown() || getWidth() == 0 || getHeight() == 0 || !backdrop.isLaidOut()) {
            return;
        }
        int reduction = 6;
        int radius = 3;
        int passes = 2;
        if (liquid) {
            reduction = 3;
            radius = blurRadius;
            passes = 1;
        }
        int sampleWidth = Math.max(1, getWidth() / reduction);
        int sampleHeight = Math.max(1, getHeight() / reduction);
        Bitmap bitmap = Bitmap.createBitmap(sampleWidth, sampleHeight, Bitmap.Config.ARGB_8888);
        int[] position = new int[2];
        int[] origin = new int[2];
        getLocationOnScreen(position);
        backdrop.getLocationOnScreen(origin);
        Canvas canvas = new Canvas(bitmap);
        canvas.scale((float) sampleWidth / getWidth(), (float) sampleHeight / getHeight());
        canvas.translate(origin[0] - position[0], origin[1] - position[1]);
        backdrop.draw(canvas);
        BitmapEffects.blur(bitmap, radius, passes);
        snapshot = bitmap;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        int checkpoint = canvas.save();
        canvas.clipPath(clipping);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setAlpha(255);
        if (liquid) {
            drawLiquid(canvas);
        } else {
            drawFrosted(canvas);
        }
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        canvas.restoreToCount(checkpoint);
    }

    private void drawFrosted(Canvas canvas) {
        if (snapshot != null) {
            canvas.drawBitmap(snapshot, null, bounds, paint);
        }
        paint.setShader(new LinearGradient(0f, 0f, getWidth(), getHeight(),
                color(R.color.glass_top), color(R.color.glass_bottom), Shader.TileMode.CLAMP));
        canvas.drawRect(bounds, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(density());
        paint.setColor(color(R.color.glass_rim));
        float radius = 24f * density();
        canvas.drawRoundRect(0.6f, 0.6f, getWidth() - 0.6f, getHeight() - 0.6f, radius, radius, paint);
    }

    private void drawLiquid(Canvas canvas) {
        float width = getWidth();
        float height = getHeight();
        float density = density();
        if (snapshot != null) {
            float bend = Math.min(width, height) * 0.035f * (1f + 0.25f * liquidMotion);
            int vertex = 0;
            for (int row = 0; row <= ROWS; row++) {
                for (int column = 0; column <= COLUMNS; column++) {
                    float u = (float) column / COLUMNS;
                    float v = (float) row / ROWS;
                    mesh[vertex++] = u * width + (float) (Math.sin(2 * Math.PI * u) * Math.sin(Math.PI * v)) * bend;
                    mesh[vertex++] = v * height + (float) (Math.sin(2 * Math.PI * v) * Math.sin(Math.PI * u)) * bend;
                }
            }
            canvas.drawBitmapMesh(snapshot, COLUMNS, ROWS, mesh, 0, null, 0, paint);
        }
        paint.setShader(new LinearGradient(0f, 0f, width, height,
                color(R.color.liquid_top), color(R.color.liquid_bottom), Shader.TileMode.CLAMP));
        canvas.drawRect(bounds, paint);
        paint.setShader(new RadialGradient(width * liquidFocus, 0f, Math.max(48f * density, height),
                color(R.color.liquid_glint), Color.TRANSPARENT, Shader.TileMode.CLAMP));
        canvas.drawRect(bounds, paint);
        if (glassTint != Color.TRANSPARENT) {
            paint.setShader(null);
            paint.setColor(glassTint);
            canvas.drawRect(bounds, paint);
        }
        reflection.rewind();
        reflection.moveTo(0f, height * 0.36f);
        reflection.cubicTo(width * 0.3f, height * 0.48f, width * 0.55f, height * 0.02f, width, height * 0.12f);
        reflection.lineTo(width, 0f);
        reflection.lineTo(0f, 0f);
        reflection.close();
        paint.setShader(new LinearGradient(0f, 0f, width * 0.6f, height * 0.5f,
                color(R.color.liquid_sheen), Color.TRANSPARENT, Shader.TileMode.CLAMP));
        canvas.drawPath(reflection, paint);

        float radius = Math.min(28f * density, height / 2f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f * density);
        paint.setShader(new LinearGradient(0f, 0f, width, height,
                new int[]{color(R.color.liquid_edge), color(R.color.liquid_edge_soft), color(R.color.liquid_edge)},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(density, density, width - density, height - density, radius, radius, paint);
        paint.setStrokeWidth(0.55f * density);
        paint.setShader(new LinearGradient(0f, 0f, 0f, height, Color.TRANSPARENT,
                color(R.color.liquid_edge_soft), Shader.TileMode.CLAMP));
        float inset = 3 * density;
        canvas.drawRoundRect(inset, inset, width - inset, height - inset, radius, radius, paint);
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        int checkpoint = canvas.save();
        canvas.clipPath(clipping);
        super.dispatchDraw(canvas);
        canvas.restoreToCount(checkpoint);
    }

    private int color(int resource) { return ContextCompat.getColor(getContext(), resource); }
    private float density() { return getResources().getDisplayMetrics().density; }
}
