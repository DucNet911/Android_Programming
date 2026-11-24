package com.puzzlehub.sudoku.data.repository;

import com.puzzlehub.sudoku.domain.Achievement;
import com.puzzlehub.sudoku.domain.DailyStreak;
import com.puzzlehub.sudoku.domain.Difficulty;
import java.util.Map;
import java.util.Set;

public record PlayerProgress(DailyStreak daily, Map<Difficulty, Integer> wins, int perfectWins,
                             Map<Achievement, Long> unlocked, Set<Achievement> pending) {
    public DailyStreak getDaily() { return daily; }
    public Map<Difficulty, Integer> getWins() { return wins; }
    public int getPerfectWins() { return perfectWins; }
    public Map<Achievement, Long> getUnlocked() { return unlocked; }
    public Set<Achievement> getPending() { return pending; }
}
