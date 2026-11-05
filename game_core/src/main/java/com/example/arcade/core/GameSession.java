package com.example.arcade.core;
public final class GameSession {
    public final String gameId, playerId, sessionId, difficultyId, snapshot;
    public final long activeDurationMs;
    public final boolean demo;
    public GameSession(String gameId, String playerId, String sessionId, String difficultyId,
                       long activeDurationMs, String snapshot, boolean demo) {
        this.gameId = Checks.text(gameId, "gameId");
        this.playerId = Checks.text(playerId, "playerId");
        this.sessionId = Checks.text(sessionId, "sessionId");
        this.difficultyId = difficultyId == null ? null : Checks.text(difficultyId, "difficultyId");
        this.activeDurationMs = Checks.nonNegative(activeDurationMs, "activeDurationMs");
        if (snapshot == null || snapshot.length() > 100_000)
            throw new IllegalArgumentException("Snapshot must be non-null and at most 100000 characters");
        this.snapshot = snapshot;
        this.demo = demo;
    }
}
