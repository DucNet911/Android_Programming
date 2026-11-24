package com.puzzlehub.sudoku.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum Achievement {
    FIRE_3, FIRE_7, FIRST_BEGINNER, FIRST_EASY, FIRST_MEDIUM, FIRST_HARD, PERFECT, TEN_WINS;

    private static final Map<Difficulty, Achievement> FIRST_WINS = firstWins();

    private static Map<Difficulty, Achievement> firstWins() {
        Map<Difficulty, Achievement> firstWins = new EnumMap<>(Difficulty.class);
        firstWins.put(Difficulty.BEGINNER, FIRST_BEGINNER);
        firstWins.put(Difficulty.EASY, FIRST_EASY);
        firstWins.put(Difficulty.MEDIUM, FIRST_MEDIUM);
        firstWins.put(Difficulty.HARD, FIRST_HARD);
        return firstWins;
    }

    public static Set<Achievement> earned(int bestDays, Map<Difficulty, Integer> wins, int perfectWins) {
        Set<Achievement> earned = EnumSet.noneOf(Achievement.class);
        if (bestDays >= 3) {
            earned.add(FIRE_3);
        }
        if (bestDays >= 7) {
            earned.add(FIRE_7);
        }

        int totalWins = 0;
        for (Map.Entry<Difficulty, Integer> result : wins.entrySet()) {
            int count = result.getValue();
            if (count > 0) {
                Achievement first = FIRST_WINS.get(result.getKey());
                if (first != null) {
                    earned.add(first);
                }
            }
            totalWins += count;
        }
        if (perfectWins > 0) {
            earned.add(PERFECT);
        }
        if (totalWins >= 10) {
            earned.add(TEN_WINS);
        }
        return earned;
    }
}
