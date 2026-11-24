package com.puzzlehub.sudoku.domain;

public enum CompetitiveRank {
    WARRIOR("Dũng sĩ", 0),
    ELITE("Tinh anh", 800),
    EXPERT("Cao thủ", 1600),
    GRANDMASTER("Đại cao thủ", 2800),
    EPIC("Sử thi", 4500),
    LEGEND("Huyền thoại", 6800);

    public static final int POINTS_PER_WIN = 100;
    public static final int POINTS_PER_LOSS = 30;
    private final String label;
    private final int minimumPoints;

    CompetitiveRank(String label, int minimumPoints) {
        this.label = label;
        this.minimumPoints = minimumPoints;
    }

    public String getLabel() {
        return label;
    }

    public int getMinimumPoints() {
        return minimumPoints;
    }

    public CompetitiveRank getNext() {
        CompetitiveRank[] ranks = values();
        int nextIndex = ordinal() + 1;
        if (nextIndex == ranks.length) {
            return null;
        }
        return ranks[nextIndex];
    }

    public static long pointsAfterMatch(long points, boolean won) {
        if (points < 0) {
            throw new IllegalArgumentException("Rank points cannot be negative");
        }
        if (won) {
            return Math.addExact(points, POINTS_PER_WIN);
        }
        return Math.max(0, points - POINTS_PER_LOSS);
    }

    public static CompetitiveRank fromPoints(long points) {
        CompetitiveRank current = WARRIOR;
        for (CompetitiveRank rank : values()) {
            if (points < rank.minimumPoints) {
                break;
            }
            current = rank;
        }
        return current;
    }
}
