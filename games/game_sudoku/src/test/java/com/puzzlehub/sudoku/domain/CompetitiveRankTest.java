package com.puzzlehub.sudoku.domain;

import org.junit.Test;
import static org.junit.Assert.*;

public final class CompetitiveRankTest {
    @Test
    public void pointsUseWinsAndSmallerLossesWithNoDebtBelowZero() {
        long points = CompetitiveRank.pointsAfterMatch(0, false);
        assertEquals(0, points);
        points = CompetitiveRank.pointsAfterMatch(points, true);
        assertEquals(100, points);
        points = CompetitiveRank.pointsAfterMatch(points, false);
        assertEquals(70, points);
        assertEquals(0, CompetitiveRank.pointsAfterMatch(20, false));
        assertEquals(100, CompetitiveRank.pointsAfterMatch(0, true));
    }

    @Test
    public void losingCanDropARankAndWinningCanRestoreIt() {
        assertSame(CompetitiveRank.WARRIOR, CompetitiveRank.fromPoints(0));
        assertSame(CompetitiveRank.ELITE, CompetitiveRank.fromPoints(800));
        long afterLoss = CompetitiveRank.pointsAfterMatch(800, false);
        assertEquals(770, afterLoss);
        assertSame(CompetitiveRank.WARRIOR, CompetitiveRank.fromPoints(afterLoss));
        assertSame(CompetitiveRank.ELITE,
                CompetitiveRank.fromPoints(CompetitiveRank.pointsAfterMatch(afterLoss, true)));
    }

    @Test
    public void thresholdsAreInclusiveAndPromotionGetsProgressivelyHarder() {
        CompetitiveRank[] ranks = CompetitiveRank.values();
        int previousGap = 0;
        for (int index = 1; index < ranks.length; index++) {
            CompetitiveRank previous = ranks[index - 1];
            CompetitiveRank rank = ranks[index];
            assertSame(rank, previous.getNext());
            assertSame(previous, CompetitiveRank.fromPoints(rank.getMinimumPoints() - 1));
            assertSame(rank, CompetitiveRank.fromPoints(rank.getMinimumPoints()));
            int gap = rank.getMinimumPoints() - previous.getMinimumPoints();
            assertTrue(gap > 0);
            assertTrue(gap >= previousGap);
            previousGap = gap;
        }
        assertNull(CompetitiveRank.LEGEND.getNext());
        assertSame(CompetitiveRank.LEGEND, CompetitiveRank.fromPoints(100_000));
    }
}
