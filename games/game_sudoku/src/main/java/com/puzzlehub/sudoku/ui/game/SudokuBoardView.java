package com.puzzlehub.sudoku.ui.game;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import androidx.annotation.Nullable;
import androidx.core.util.Consumer;
import androidx.core.content.ContextCompat;
import com.puzzlehub.sudoku.domain.SudokuBoard;
import com.puzzlehub.sudoku.domain.SudokuValidator;
import com.puzzlehub.sudoku.game.R;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Draws input and game state without applying Sudoku rules or changing the board. */
public final class SudokuBoardView extends View {
    private static final int SIDE = SudokuBoard.SIDE;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint feedbackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private SudokuBoard board = SudokuBoard.empty();
    private SudokuBoard initial = SudokuBoard.empty();
    private Map<Integer, Set<Integer>> notes = Collections.emptyMap();
    private Set<Integer> hints = Collections.emptySet();
    private Set<Integer> errors = Collections.emptySet();
    private int selected = -1;
    private boolean highlight = true;
    private int feedbackIndex = -1;
    private float feedbackFraction = 1f;
    private ValueAnimator feedbackAnimator;
    private Consumer<Integer> listener;

    public SudokuBoardView(Context context) {
        this(context, null);
    }

    public SudokuBoardView(Context context, @Nullable AttributeSet attributes) {
        super(context, attributes);
        setClickable(true);
        setFocusable(true);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setOnCellSelected(@Nullable Consumer<Integer> listener) {
        this.listener = listener;
    }

    public void render(SudokuBoard board, SudokuBoard initial, Map<Integer, Set<Integer>> notes, int selected,
            Set<Integer> hinted, Set<Integer> wrong, boolean highlight) {
        if (!this.initial.equals(initial)
                || (feedbackIndex >= 0 && this.board.get(feedbackIndex) != board.get(feedbackIndex))) {
            stopCorrectFeedback();
        }
        this.board = board;
        this.initial = initial;
        this.notes = notes;
        this.selected = selected;
        this.hints = hinted;
        this.errors = new HashSet<>(SudokuValidator.conflicts(board));
        this.errors.addAll(wrong);
        this.highlight = highlight;
        if (selected < 0) {
            setContentDescription(getContext().getString(R.string.number_board));
        } else {
            String cell = "ô trống";
            int value = board.get(selected);
            if (value != 0) {
                cell = "số " + value;
            }
            setContentDescription("Hàng " + (selected / SIDE + 1) + ", cột " + (selected % SIDE + 1) + ", " + cell);
        }
        invalidate();
    }

    public void showCorrectFeedback(int index) {
        if (index < 0 || index >= SudokuBoard.CELL_COUNT || initial.get(index) != 0
                || board.get(index) == 0 || errors.contains(index)) {
            return;
        }
        stopCorrectFeedback();
        feedbackIndex = index;
        feedbackFraction = 0;
        feedbackAnimator = ValueAnimator.ofFloat(0, 1);
        feedbackAnimator.setDuration(480);
        feedbackAnimator.setInterpolator(new DecelerateInterpolator());
        feedbackAnimator.addUpdateListener(animator -> {
            feedbackFraction = (float) animator.getAnimatedValue();
            invalidate();
        });
        feedbackAnimator.start();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(width, width);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cellSize = getWidth() / (float) SIDE;
        float density = getResources().getDisplayMetrics().density;
        for (int index = 0; index < SudokuBoard.CELL_COUNT; index++) {
            drawCell(canvas, index, cellSize, density);
        }
        if (feedbackIndex >= 0 && feedbackFraction < 1) {
            drawCheck(canvas, cellSize, density);
        }
        drawGrid(canvas, cellSize, density);
    }

    private void drawCell(Canvas canvas, int index, float size, float density) {
        int row = index / SIDE;
        int column = index % SIDE;
        float left = column * size;
        float top = row * size;
        paint.setColor(color(cellBackground(index, row, column)));
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(left, top, left + size, top + size, paint);

        boolean pulse = index == feedbackIndex && feedbackFraction < 1;
        if (pulse) {
            float inset = 2f * density;
            feedbackPaint.setStyle(Paint.Style.FILL);
            feedbackPaint.setColor(withAlpha(color(R.color.hint_bg), 210));
            canvas.drawRoundRect(left + inset, top + inset, left + size - inset, top + size - inset,
                    inset * 2, inset * 2, feedbackPaint);
        }

        paint.setTextAlign(Paint.Align.CENTER);
        int value = board.get(index);
        if (value != 0) {
            paint.setColor(color(digitColor(index)));
            float gain = 0;
            if (pulse) {
                gain = (float) Math.sin(Math.PI * feedbackFraction) * 0.035f;
            }
            paint.setTextSize(size * (0.57f + gain));
            canvas.drawText(String.valueOf(value), left + size / 2, top + size / 2 - textOffset(), paint);
        } else {
            paint.setColor(color(R.color.muted));
            paint.setTextSize(size * 0.21f);
            Set<Integer> cellNotes = notes.get(index);
            for (int note : cellNotes == null ? Collections.<Integer>emptySet() : cellNotes) {
                float x = left + ((note - 1) % 3 + 0.5f) * size / 3;
                float y = top + ((note - 1) / 3 + 0.5f) * size / 3;
                canvas.drawText(String.valueOf(note), x, y - textOffset(), paint);
            }
        }
    }

    private int cellBackground(int index, int row, int column) {
        if (index == selected) {
            return R.color.selected;
        }
        if (errors.contains(index)) {
            return R.color.error_bg;
        }
        if (hints.contains(index)) {
            return R.color.hint_bg;
        }
        if (highlight && selected >= 0) {
            if (board.get(selected) != 0 && board.get(index) == board.get(selected)) {
                return R.color.matching;
            }
            boolean sameUnit = row == selected / SIDE || column == selected % SIDE
                    || (row / 3 == selected / 27 && column / 3 == selected % SIDE / 3);
            if (sameUnit) {
                return R.color.related;
            }
        }
        return R.color.surface;
    }

    private int digitColor(int index) {
        if (errors.contains(index)) {
            return R.color.error;
        } else if (hints.contains(index)) {
            return R.color.hint;
        } else if (initial.get(index) != 0) {
            return R.color.ink;
        }
        return R.color.primary;
    }

    private void drawCheck(Canvas canvas, float size, float density) {
        float x = (feedbackIndex % SIDE + 1) * size - 6.5f * density;
        float y = (feedbackIndex / SIDE) * size + 6.5f * density;
        feedbackPaint.setStyle(Paint.Style.FILL);
        feedbackPaint.setColor(withAlpha(color(R.color.hint), 230));
        canvas.drawCircle(x, y, 4.5f * density, feedbackPaint);
        feedbackPaint.setStyle(Paint.Style.STROKE);
        feedbackPaint.setStrokeWidth(1.2f * density);
        feedbackPaint.setStrokeCap(Paint.Cap.ROUND);
        feedbackPaint.setColor(withAlpha(0x00ffffff, 230));
        canvas.drawLine(x - 2 * density, y, x - 0.5f * density, y + 1.5f * density, feedbackPaint);
        canvas.drawLine(x - 0.5f * density, y + 1.5f * density, x + 2 * density, y - 1.5f * density, feedbackPaint);
    }

    private void drawGrid(Canvas canvas, float size, float density) {
        for (int line = 0; line <= SIDE; line++) {
            if (line % 3 == 0) {
                paint.setColor(color(R.color.grid_major));
                paint.setStrokeWidth(density * 1.8f);
            } else {
                paint.setColor(color(R.color.grid));
                paint.setStrokeWidth(density * 0.55f);
            }
            float halfStroke = paint.getStrokeWidth() / 2;
            float position = Math.max(halfStroke, Math.min(getWidth() - halfStroke, line * size));
            canvas.drawLine(position, 0, position, getHeight(), paint);
            canvas.drawLine(0, position, getWidth(), position, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            float size = getWidth() / (float) SIDE;
            int column = Math.max(0, Math.min(SIDE - 1, (int) (event.getX() / size)));
            int row = Math.max(0, Math.min(SIDE - 1, (int) (event.getY() / size)));
            select(row * SIDE + column);
            performClick();
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int delta;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                delta = -1;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                delta = 1;
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                delta = -SIDE;
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                delta = SIDE;
                break;
            default:
                return super.onKeyDown(keyCode, event);
        }
        select(Math.max(0, Math.min(SudokuBoard.CELL_COUNT - 1, Math.max(0, selected) + delta)));
        return true;
    }

    public void select(int index) {
        if (index < 0 || index >= SudokuBoard.CELL_COUNT) {
            throw new IllegalArgumentException("Invalid board index");
        }
        selected = index;
        if (listener != null) {
            listener.accept(index);
        }
    }

    private int color(int resource) {
        return ContextCompat.getColor(getContext(), resource);
    }

    private int withAlpha(int color, int maximum) {
        return (color & 0x00ffffff) | ((int) ((1f - feedbackFraction) * maximum) << 24);
    }

    private float textOffset() {
        return (paint.ascent() + paint.descent()) / 2;
    }

    private void stopCorrectFeedback() {
        if (feedbackAnimator != null) {
            feedbackAnimator.cancel();
            feedbackAnimator = null;
        }
        feedbackIndex = -1;
        feedbackFraction = 1f;
    }

    @Override
    protected void onDetachedFromWindow() {
        stopCorrectFeedback();
        super.onDetachedFromWindow();
    }
}
