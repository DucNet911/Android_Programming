package com.puzzlehub.sudoku.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;

@Entity(tableName = "competitive_results_pending", primaryKeys = {"playerId", "roomCode", "startedAt"})
public final class CompetitiveResultEntity {
    @NonNull
    public final String playerId;
    @NonNull
    public final String roomCode;
    public final long startedAt;
    public final long finishedAt;

    public CompetitiveResultEntity(@NonNull String playerId, @NonNull String roomCode, long startedAt, long finishedAt) {
        this.playerId = playerId;
        this.roomCode = roomCode;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }

    public String claimKey() {
        return roomCode + "-" + startedAt;
    }
}
