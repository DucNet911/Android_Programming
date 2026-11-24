package com.puzzlehub.sudoku.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;

@Entity(tableName = "ranking_best", primaryKeys = {"playerId", "difficulty"})
public final class RankingBestEntity {
    @NonNull
    public final String playerId;
    @NonNull
    public final String difficulty;
    public final long durationMs;

    public RankingBestEntity(@NonNull String playerId, @NonNull String difficulty, long durationMs) {
        this.playerId = playerId;
        this.difficulty = difficulty;
        this.durationMs = durationMs;
    }
}
