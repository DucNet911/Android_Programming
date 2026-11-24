package com.puzzlehub.sudoku.domain;

import androidx.annotation.Nullable;
import com.puzzlehub.sudoku.utils.LocalDay;

public record DailyStreak(@Nullable Long lastDay, int current, int best) {
    public DailyStreak() {
        this(null, 0, 0);
    }

    @Nullable public Long getLastDay() { return lastDay; }
    public int getCurrent() { return current; }
    public int getBest() { return best; }

    public DailyStreak visit(LocalDay day) {
        long today = day.toEpochDay();
        if (lastDay != null && today <= lastDay) {
            return this;
        }
        int next = 1;
        if (lastDay != null && today == lastDay + 1L) {
            next = current + 1;
        }
        return new DailyStreak(today, next, Math.max(best, next));
    }
}
