package com.example.arcade.core;
import java.util.Objects;
public final class GameResult {
    public final String gameId, playerId, sessionId, difficultyId;
    public final GameOutcome outcome;
    public final Long score;
    public final long activeDurationMs, finishedAtEpochMs;
    public final boolean demo;
    public GameResult(String gameId, String playerId, String sessionId, String difficultyId,
                      GameOutcome outcome, Long score, long activeDurationMs,
                      long finishedAtEpochMs, boolean demo) {
        this.gameId = Checks.text(gameId, "gameId");
        this.playerId = Checks.text(playerId, "playerId");
        this.sessionId = Checks.text(sessionId, "sessionId");
        this.difficultyId = difficultyId == null ? null : Checks.text(difficultyId, "difficultyId");
        this.outcome = Objects.requireNonNull(outcome, "outcome");
        if (score != null) Checks.nonNegative(score, "score");
        this.score = score;
        this.activeDurationMs = Checks.nonNegative(activeDurationMs, "activeDurationMs");
        if (finishedAtEpochMs <= 0) throw new IllegalArgumentException("Invalid finish time");
        this.finishedAtEpochMs = finishedAtEpochMs;
        this.demo = demo;
    }
}
