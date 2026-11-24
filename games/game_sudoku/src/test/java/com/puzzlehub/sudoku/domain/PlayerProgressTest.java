package com.puzzlehub.sudoku.domain;

import com.puzzlehub.sudoku.utils.LocalDay;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public final class PlayerProgressTest {
    private static final LocalDay DAY = LocalDay.of(2026, 10, 28);

    @Test public void repeatedVisitDoesNotIncrement() {
        DailyStreak first = new DailyStreak().visit(DAY);
        assertEquals(first, first.visit(DAY));
        assertEquals(1, first.current());
    }

    @Test public void consecutiveDaysAccumulate() {
        DailyStreak state = threeDays();
        assertEquals(3, state.current());
        assertEquals(3, state.best());
    }

    @Test public void missingDayResetsCurrentButPreservesBest() {
        DailyStreak state = threeDays().visit(DAY.plusDays(5));
        assertEquals(1, state.current());
        assertEquals(3, state.best());
    }

    @Test public void backwardClockDoesNotAwardExtraVisit() {
        DailyStreak state = new DailyStreak().visit(DAY);
        assertEquals(state, state.visit(DAY.plusDays(-1)));
        assertEquals(2, state.visit(DAY.plusDays(1)).current());
    }

    @Test public void fireMilestonesUseBestStreak() {
        assertTrue(Achievement.earned(2, Map.of(), 0).isEmpty());
        assertEquals(Set.of(Achievement.FIRE_3), Achievement.earned(3, Map.of(), 0));
        assertEquals(Set.of(Achievement.FIRE_3, Achievement.FIRE_7), Achievement.earned(7, Map.of(), 0));
    }

    @Test public void eachDifficultyHasAnIndependentFirstWin() {
        Achievement[] expected = {Achievement.FIRST_BEGINNER, Achievement.FIRST_EASY,
                Achievement.FIRST_MEDIUM, Achievement.FIRST_HARD};
        for (Difficulty difficulty : Difficulty.values()) {
            assertEquals(Set.of(expected[difficulty.ordinal()]), Achievement.earned(0, Map.of(difficulty, 1), 0));
        }
    }

    @Test public void perfectWinRequiresAnActualPerfectRecord() {
        assertFalse(Achievement.earned(0, Map.of(Difficulty.EASY, 1), 0).contains(Achievement.PERFECT));
        assertTrue(Achievement.earned(0, Map.of(Difficulty.EASY, 1), 1).contains(Achievement.PERFECT));
    }

    @Test public void tenWinsCanSpanDifficulties() {
        assertFalse(Achievement.earned(0, Map.of(Difficulty.EASY, 9), 0).contains(Achievement.TEN_WINS));
        assertTrue(Achievement.earned(0, Map.of(Difficulty.EASY, 5, Difficulty.HARD, 5), 0).contains(Achievement.TEN_WINS));
    }

    private DailyStreak threeDays() {
        return new DailyStreak().visit(DAY).visit(DAY.plusDays(1)).visit(DAY.plusDays(2));
    }
}
