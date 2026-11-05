package com.example.arcade.core;
import java.util.Objects;
public final class GameExitResponse {
    public final ExitReason reason;
    public final String gameId, playerId, sessionId;
    public final GameResult result;
    public final GameErrorCode errorCode;
    private GameExitResponse(ExitReason reason, String gameId, String playerId, String sessionId,
                             GameResult result, GameErrorCode errorCode) {
        this.reason = Objects.requireNonNull(reason);
        this.gameId = Checks.text(gameId, "gameId");
        this.playerId = Checks.text(playerId, "playerId");
        this.sessionId = sessionId;
        this.result = result;
        this.errorCode = errorCode;
    }
    public static GameExitResponse finished(GameResult result) {
        Objects.requireNonNull(result);
        return new GameExitResponse(ExitReason.FINISHED, result.gameId, result.playerId,
                result.sessionId, result, null);
    }
    public static GameExitResponse saved(GameSession session) {
        return new GameExitResponse(ExitReason.SAVED_FOR_LATER, session.gameId, session.playerId,
                session.sessionId, null, null);
    }
    public static GameExitResponse cancelled(String gameId, String playerId) {
        return new GameExitResponse(ExitReason.CANCELLED, gameId, playerId, null, null, null);
    }
    public static GameExitResponse error(String gameId, String playerId, GameErrorCode code) {
        return new GameExitResponse(ExitReason.ERROR, gameId, playerId, null, null, Objects.requireNonNull(code));
    }
    static GameExitResponse saved(String gameId, String playerId, String sessionId) {
        Checks.text(sessionId, "sessionId");
        return new GameExitResponse(ExitReason.SAVED_FOR_LATER, gameId, playerId, sessionId, null, null);
    }
}
