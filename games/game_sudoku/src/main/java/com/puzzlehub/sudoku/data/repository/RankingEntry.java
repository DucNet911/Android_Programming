package com.puzzlehub.sudoku.data.repository;
public record RankingEntry(SocialPlayer player, long durationMs) {
    public SocialPlayer getPlayer() { return player; }
    public long getDurationMs() { return durationMs; }
}
