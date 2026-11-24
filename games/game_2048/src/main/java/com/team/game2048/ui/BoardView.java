package com.team.game2048.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.LinearInterpolator;
import com.team.game2048.engine.Game2048Engine;

/** Canvas renderer: slide source tiles, then reveal merges and the spawned tile. */
public final class BoardView extends View {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect = new RectF();
  private int size = 4;
  private long[] board = new long[16];
  private Game2048Engine.Move move;
  private ValueAnimator animation, fireworks;
  private float progress = 1, celebration = 1, startX, startY;

  public interface MoveListener {
    void accept(Game2048Engine.Direction direction);
  }

  private MoveListener listener;
  private boolean dragged;
  private static final int[] COLORS = {
    0xFFEAF0FD,
    0xFF9FC4FF,
    0xFF3471BF,
    0xFF175AE8,
    0xFF103BB0,
    0xFF173071,
    0xFF17766A,
    0xFF08705D,
    0xFF6942AE,
    0xFF553390,
    0xFF44277A
  };

  public BoardView(Context c, AttributeSet a) {
    super(c, a);
    setFocusable(true);
    setClickable(true);
  }

  public void setListener(MoveListener listener) {
    this.listener = listener;
  }

  public boolean isAnimating() {
    return animation != null && animation.isRunning();
  }

  public void show(long[] cells) {
    settle();
    board = cells.clone();
    size = (int) Math.sqrt(board.length);
    invalidate();
  }

  public void animateMove(long[] cells, Game2048Engine.Move movement) {
    settle();
    board = cells.clone();
    size = (int) Math.sqrt(board.length);
    move = movement;
    animation = ValueAnimator.ofFloat(0, 1);
    animation.setDuration(230);
    animation.setInterpolator(new LinearInterpolator());
    animation.addUpdateListener(
        a -> {
          progress = (float) a.getAnimatedValue();
          invalidate();
        });
    animation.start();
  }

  public void celebrate() {
    if (fireworks != null) fireworks.cancel();
    fireworks = ValueAnimator.ofFloat(0, 1);
    fireworks.setDuration(1800);
    fireworks.addUpdateListener(
        a -> {
          celebration = (float) a.getAnimatedValue();
          invalidate();
        });
    fireworks.start();
  }

  public void settle() {
    if (animation != null) animation.cancel();
    progress = 1;
    move = null;
    invalidate();
  }

  public void pauseEffects() {
    settle();
    if (fireworks != null) fireworks.cancel();
    celebration = 1;
  }

  @Override
  protected void onDetachedFromWindow() {
    pauseEffects();
    super.onDetachedFromWindow();
  }

  @Override
  protected void onMeasure(int w, int h) {
    int size =
        Math.min(MeasureSpec.getSize(w), (int) (520 * getResources().getDisplayMetrics().density));
    setMeasuredDimension(size, size);
  }

  private float gap() {
    return getWidth() * 0.025f;
  }

  private float cell() {
    return (getWidth() - (size + 1) * gap()) / size;
  }

  private float x(int i) {
    return gap() + (i % size) * (cell() + gap());
  }

  private float y(int i) {
    return gap() + (i / size) * (cell() + gap());
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    float g = gap();
    paint.setColor(0xFFCDD8EB);
    canvas.drawRoundRect(0, 0, getWidth(), getHeight(), g, g, paint);
    for (int i = 0; i < board.length; i++) {
      paint.setColor(0xFFFFFFFF);
      rect.set(x(i), y(i), x(i) + cell(), y(i) + cell());
      canvas.drawRoundRect(rect, g / 2, g / 2, paint);
    }
    if (move != null && progress < 0.65f) {
      float p = progress / 0.65f;
      p = 1 - (1 - p) * (1 - p) * (1 - p);
      for (Game2048Engine.Motion m : move.motions)
        tile(
            canvas,
            m.value,
            x(m.from) + (x(m.to) - x(m.from)) * p,
            y(m.from) + (y(m.to) - y(m.from)) * p,
            1);
    } else {
      float p = Math.min(1, Math.max(0, (progress - 0.65f) / 0.35f));
      for (int i = 0; i < board.length; i++)
        if (board[i] != 0) {
          float scale = 1;
          if (move != null && progress < 1) {
            if (i == move.spawned) scale = p;
            else if (move.merged.contains(i)) scale = 1 + 0.13f * (float) Math.sin(Math.PI * p);
          }
          tile(canvas, board[i], x(i), y(i), scale);
        }
    }
    if (celebration < 1) {
      int[] colors = {0xFF44277A, 0xFF103BB0, 0xFF4D7C69, 0xFF6BA6CE};
      for (int burst = 0; burst < 3; burst++)
        for (int k = 0; k < 24; k++) {
          double angle = k * Math.PI * 2 / 24 + burst;
          float radius = getWidth() * 0.38f * celebration;
          float cx = getWidth() * (0.25f + 0.25f * burst),
              cy = getHeight() * (burst == 1 ? 0.3f : 0.55f);
          paint.setColor(colors[k % 4]);
          paint.setAlpha((int) (255 * (1 - celebration)));
          canvas.drawCircle(
              cx + (float) Math.cos(angle) * radius,
              cy
                  + (float) Math.sin(angle) * radius
                  + celebration * celebration * getHeight() * 0.2f,
              g * 0.32f,
              paint);
        }
      paint.setAlpha(255);
    }
  }

  private void tile(Canvas canvas, long value, float x, float y, float scale) {
    if (scale <= 0) return;
    canvas.save();
    canvas.scale(scale, scale, x + cell() / 2, y + cell() / 2);
    int power = 63 - Long.numberOfLeadingZeros(value);
    paint.setColor(power <= 11 ? COLORS[Math.max(0, power - 1)] : 0xFF3C3A32);
    rect.set(x, y, x + cell(), y + cell());
    canvas.drawRoundRect(rect, gap() / 2, gap() / 2, paint);
    paint.setColor(value <= 4 ? 0xFF132447 : 0xFFFFFFFF);
    paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
    paint.setTextAlign(Paint.Align.CENTER);
    paint.setTextSize(cell() * 0.44f);
    String text = Long.toString(value);
    float width = paint.measureText(text);
    if (width > cell() * 0.85f) paint.setTextSize(paint.getTextSize() * cell() * 0.85f / width);
    canvas.drawText(
        text, x + cell() / 2, y + cell() / 2 - (paint.ascent() + paint.descent()) / 2, paint);
    canvas.restore();
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (!isEnabled()) return false;
    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        startX = event.getX();
        startY = event.getY();
        dragged = false;
        getParent().requestDisallowInterceptTouchEvent(true);
        return true;
      case MotionEvent.ACTION_MOVE:
        return true;
      case MotionEvent.ACTION_UP:
        getParent().requestDisallowInterceptTouchEvent(false);
        float dx = event.getX() - startX, dy = event.getY() - startY;
        float threshold =
            Math.max(ViewConfiguration.get(getContext()).getScaledTouchSlop(), getWidth() * 0.06f);
        if (Math.max(Math.abs(dx), Math.abs(dy)) > threshold) {
          dragged = true;
          if (listener != null)
            listener.accept(
                Math.abs(dx) > Math.abs(dy)
                    ? (dx > 0 ? Game2048Engine.Direction.RIGHT : Game2048Engine.Direction.LEFT)
                    : (dy > 0 ? Game2048Engine.Direction.DOWN : Game2048Engine.Direction.UP));
        }
        if (!dragged) performClick();
        return true;
      case MotionEvent.ACTION_CANCEL:
        getParent().requestDisallowInterceptTouchEvent(false);
        return true;
      default:
        return true;
    }
  }

  @Override
  public boolean performClick() {
    super.performClick();
    return true;
  }
}
