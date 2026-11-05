package com.example.arcade.core;
import org.json.JSONException;
import org.json.JSONObject;

/** JSON format shared by checkpoints and the durable result outbox. */
final class GameJson {
    private GameJson() {}
    private static JSONObject base(String game, String player, String session, String difficulty,
                                   long duration, boolean demo) throws JSONException {
        JSONObject j = new JSONObject(); j.put("version", 1); j.put("game", game);
        j.put("player", player); j.put("session", session);
        j.put("difficulty", difficulty == null ? JSONObject.NULL : difficulty);
        j.put("duration", duration); j.put("demo", demo); return j;
    }
    private static JSONObject read(String text) throws JSONException {
        JSONObject j = new JSONObject(text);
        if (j.getInt("version") != 1) throw new JSONException("Unsupported storage version");
        return j;
    }
    private static String difficulty(JSONObject j) throws JSONException {
        return j.isNull("difficulty") ? null : j.getString("difficulty");
    }
    static String session(GameSession s) throws JSONException {
        JSONObject j = base(s.gameId, s.playerId, s.sessionId, s.difficultyId, s.activeDurationMs, s.demo);
        j.put("snapshot", s.snapshot); return j.toString();
    }
    static GameSession session(String text) throws JSONException {
        JSONObject j = read(text);
        return new GameSession(j.getString("game"), j.getString("player"), j.getString("session"),
                difficulty(j), j.getLong("duration"), j.getString("snapshot"), j.getBoolean("demo"));
    }
    static String result(GameResult r) throws JSONException {
        JSONObject j = base(r.gameId, r.playerId, r.sessionId, r.difficultyId, r.activeDurationMs, r.demo);
        j.put("score", r.score == null ? JSONObject.NULL : r.score);
        j.put("outcome", r.outcome.name()); j.put("finished", r.finishedAtEpochMs); return j.toString();
    }
    static GameResult result(String text) throws JSONException {
        JSONObject j = read(text);
        return new GameResult(j.getString("game"), j.getString("player"), j.getString("session"),
                difficulty(j), GameOutcome.valueOf(j.getString("outcome")),
                j.isNull("score") ? null : j.getLong("score"), j.getLong("duration"),
                j.getLong("finished"), j.getBoolean("demo"));
    }
}
