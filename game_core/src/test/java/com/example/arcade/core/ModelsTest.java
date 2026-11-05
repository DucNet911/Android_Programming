package com.example.arcade.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class ModelsTest {
    @Test public void sudokuMayHaveNoScore() {
        GameResult r = new GameResult("sudoku", "guest", "s1", "easy", GameOutcome.WON, null, 5000, 100, true);
        assertNull(r.score); assertEquals(5000, r.activeDurationMs);
    }
    @Test(expected=IllegalArgumentException.class) public void resumeNeedsSession() {
        new GameLaunchRequest("2048", "guest", LaunchMode.RESUME, null, "classic");
    }
    @Test(expected=IllegalArgumentException.class) public void newGameCannotReuseSession() {
        new GameLaunchRequest("2048", "guest", LaunchMode.NEW_GAME, "old", "classic");
    }
    @Test(expected=IllegalArgumentException.class) public void rejectNegativeDuration() {
        new GameResult("tetris", "guest", "s1", "classic", GameOutcome.LOST, 5L, -1, 100, false);
    }
    @Test(expected=IllegalArgumentException.class) public void rejectNegativeScore() {
        new GameResult("2048", "guest", "s1", "classic", GameOutcome.WON, -1L, 5000, 100, false);
    }
    @Test(expected=IllegalArgumentException.class) public void rejectEmptyPlayer() {
        new GameLaunchRequest("tetris", " ", LaunchMode.NEW_GAME, null, null);
    }
}
