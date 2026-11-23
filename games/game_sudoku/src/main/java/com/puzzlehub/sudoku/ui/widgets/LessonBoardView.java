package com.puzzlehub.sudoku.ui.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.puzzlehub.sudoku.domain.SudokuBoard;
import com.puzzlehub.sudoku.game.R;
import java.util.Collections;
import java.util.Set;

/** Read-only diagram used by the guide and tutor. */
public final class LessonBoardView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private SudokuBoard board = SudokuBoard.empty();
    private Set<Integer> highlighted = Collections.emptySet();
    private int target = -1;
    private Integer answer;
    private Set<Integer> candidates = Collections.emptySet();

    public LessonBoardView(Context context) {
        this(context, null);
    }

    public LessonBoardView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public SudokuBoard getBoard() { return board; }
    public void setBoard(SudokuBoard value) { board = value; invalidate(); }
    public Set<Integer> getHighlighted() { return highlighted; }
    public void setHighlighted(Set<Integer> value) { highlighted = value; invalidate(); }
    public int getTarget() { return target; }
    public void setTarget(int value) { target = value; invalidate(); }
    @Nullable public Integer getAnswer() { return answer; }
    public void setAnswer(@Nullable Integer value) { answer = value; invalidate(); }
    public Set<Integer> getCandidates() { return candidates; }
    public void setCandidates(Set<Integer> value) { candidates = value; invalidate(); }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float side = Math.min(getWidth(), getHeight()) - 4 * density;
        if (side <= 0f) {
            return;
        }
        float left = (getWidth() - side) / 2f;
        float top = (getHeight() - side) / 2f;
        float cell = side / 9f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color(R.color.surface));
        canvas.drawRoundRect(left, top, left + side, top + side, cell * 0.35f, cell * 0.35f, paint);
        for (int index = 0; index < 81; index++) {
            drawCell(canvas, index, left + index % 9 * cell, top + index / 9 * cell, cell);
        }
        paint.setStyle(Paint.Style.STROKE);
        for (int line = 0; line <= 9; line++) {
            if (line % 3 == 0) {
                paint.setColor(color(R.color.grid_major));
                paint.setStrokeWidth(density * 1.4f);
            } else {
                paint.setColor(color(R.color.grid));
                paint.setStrokeWidth(density * 0.5f);
            }
            canvas.drawLine(left + line * cell, top, left + line * cell, top + side, paint);
            canvas.drawLine(left, top + line * cell, left + side, top + line * cell, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawCell(Canvas canvas, int index, float x, float y, float cell) {
        boolean selected = index == target;
        if (selected || highlighted.contains(index)) {
            int highlight = R.color.related;
            if (selected) {
                highlight = R.color.selected;
            }
            paint.setColor(color(highlight));
            canvas.drawRoundRect(x + 1, y + 1, x + cell - 1, y + cell - 1, cell * 0.16f, cell * 0.16f, paint);
        }
        int value = board.get(index);
        paint.setColor(color(R.color.ink));
        paint.setTypeface(Typeface.DEFAULT);
        if (selected) {
            paint.setColor(color(R.color.primary));
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            if (answer != null) {
                value = answer;
            }
        }
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(cell * 0.53f);
        float baseline = y + cell / 2f - (paint.ascent() + paint.descent()) / 2f;
        if (value != 0) {
            canvas.drawText(String.valueOf(value), x + cell / 2f, baseline, paint);
        } else if (selected && !candidates.isEmpty()) {
            paint.setTextSize(cell * 0.22f);
            for (int number : candidates) {
                canvas.drawText(String.valueOf(number), x + ((number - 1) % 3 + 0.5f) * cell / 3,
                        y + ((number - 1) / 3 + 0.66f) * cell / 3, paint);
            }
        } else if (selected) {
            canvas.drawText("?", x + cell / 2f, baseline, paint);
        }
    }

    private int color(int resource) {
        return ContextCompat.getColor(getContext(), resource);
    }
}
