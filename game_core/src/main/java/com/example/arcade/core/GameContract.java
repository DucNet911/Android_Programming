package com.example.arcade.core;
import android.content.Intent;
import android.os.Bundle;

/** Only primitive Bundle fields cross the Activity boundary; no Serializable casts. */
public final class GameContract {
    public static final int VERSION = 1;
    public static final String EXTRA_REQUEST = "com.example.arcade.REQUEST";
    public static final String EXTRA_RESPONSE = "com.example.arcade.RESPONSE";
    private GameContract() {}
    public static Intent putRequest(Intent intent, GameLaunchRequest r) {
        Bundle b = base(r.gameId, r.playerId, r.sessionId);
        b.putString("mode", r.launchMode.name());
        b.putString("difficulty", r.difficultyId);
        return intent.putExtra(EXTRA_REQUEST, b);
    }
    public static GameLaunchRequest readRequest(Intent intent) {
        Bundle b = required(intent, EXTRA_REQUEST);
        return new GameLaunchRequest(b.getString("game"), b.getString("player"),
                LaunchMode.valueOf(b.getString("mode", "")), b.getString("session"), b.getString("difficulty"));
    }
    public static Intent responseIntent(GameExitResponse r) {
        Bundle b = base(r.gameId, r.playerId, r.sessionId);
        b.putString("reason", r.reason.name());
        if (r.result != null) b.putBundle("result", encodeResult(r.result));
        if (r.errorCode != null) b.putString("error", r.errorCode.name());
        return new Intent().putExtra(EXTRA_RESPONSE, b);
    }
    public static GameExitResponse readResponse(Intent intent) {
        Bundle b = required(intent, EXTRA_RESPONSE);
        String game = b.getString("game"), player = b.getString("player"), session = b.getString("session");
        ExitReason reason = ExitReason.valueOf(b.getString("reason", ""));
        switch (reason) {
            case FINISHED:
                GameResult result = decodeResult(b.getBundle("result"));
                if (!result.gameId.equals(game) || !result.playerId.equals(player) || !result.sessionId.equals(session))
                    throw new IllegalArgumentException("Response identity mismatch");
                return GameExitResponse.finished(result);
            case SAVED_FOR_LATER: return GameExitResponse.saved(game, player, session);
            case CANCELLED: return GameExitResponse.cancelled(game, player);
            case ERROR: return GameExitResponse.error(game, player, GameErrorCode.valueOf(b.getString("error", "")));
            default: throw new IllegalArgumentException("Unknown exit reason");
        }
    }
    private static Bundle base(String game, String player, String session) {
        Bundle b = new Bundle(); b.putInt("version", VERSION);
        b.putString("game", game); b.putString("player", player); b.putString("session", session);
        return b;
    }
    private static Bundle required(Intent intent, String key) {
        if (intent == null) throw new IllegalArgumentException("Missing Intent");
        Bundle b = intent.getBundleExtra(key);
        if (b == null) throw new IllegalArgumentException("Missing payload");
        checkVersion(b); return b;
    }
    private static void checkVersion(Bundle b) {
        if (b == null || b.getInt("version", -1) != VERSION)
            throw new IllegalArgumentException("Unsupported contract version");
    }
    private static Bundle encodeResult(GameResult r) {
        Bundle b = base(r.gameId, r.playerId, r.sessionId);
        b.putString("difficulty", r.difficultyId); b.putString("outcome", r.outcome.name());
        if (r.score != null) b.putLong("score", r.score);
        b.putLong("duration", r.activeDurationMs); b.putLong("finished", r.finishedAtEpochMs);
        b.putBoolean("demo", r.demo); return b;
    }
    private static GameResult decodeResult(Bundle b) {
        checkVersion(b);
        if (!b.containsKey("duration") || !b.containsKey("demo"))
            throw new IllegalArgumentException("Incomplete result");
        return new GameResult(b.getString("game"), b.getString("player"), b.getString("session"),
                b.getString("difficulty"), GameOutcome.valueOf(b.getString("outcome", "")),
                b.containsKey("score") ? b.getLong("score") : null,
                b.getLong("duration"), b.getLong("finished"), b.getBoolean("demo"));
    }
}
