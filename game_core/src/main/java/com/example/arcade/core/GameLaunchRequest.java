package com.example.arcade.core;
import java.util.Objects;
public final class GameLaunchRequest {
    public final String gameId, playerId, sessionId, difficultyId;
    public final LaunchMode launchMode;
    public GameLaunchRequest(String gameId, String playerId, LaunchMode mode,
                             String sessionId, String difficultyId) {
        this.gameId = Checks.text(gameId, "gameId");
        this.playerId = Checks.text(playerId, "playerId");
        this.launchMode = Objects.requireNonNull(mode, "launchMode");
        if (mode == LaunchMode.RESUME) Checks.text(sessionId, "sessionId");
        if (mode == LaunchMode.NEW_GAME && sessionId != null)
            throw new IllegalArgumentException("New game must not supply sessionId");
        this.sessionId = sessionId;
        this.difficultyId = difficultyId == null ? null : Checks.text(difficultyId, "difficultyId");
    }
}
