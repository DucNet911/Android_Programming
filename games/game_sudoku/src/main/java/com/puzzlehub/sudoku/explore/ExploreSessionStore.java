package com.puzzlehub.sudoku.explore;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import com.example.arcade.core.GameErrorCode;
import com.example.arcade.core.GameOutcome;
import com.example.arcade.core.GameResult;
import com.example.arcade.core.GameSession;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.json.JSONException;
import org.json.JSONObject;

/** Isolated session store for Sudoku's internal Explore mini-games (never touches ArcadePlatform's store). */
public final class ExploreSessionStore {
    private static final Object LOCK = new Object();
    private final SharedPreferences prefs;

    public static final class Failure extends RuntimeException {
        public final GameErrorCode code;
        public Failure(GameErrorCode code) {
            super(code.name());
            this.code = code;
        }
    }

    public ExploreSessionStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("sudoku_explore_sessions_v1", Context.MODE_PRIVATE);
    }

    private static String slot(String game, String player) {
        return "save/" + Uri.encode(game) + "/" + Uri.encode(player);
    }

    private static String resultKey(GameResult r) {
        return "result/" + Uri.encode(r.gameId) + "/" + Uri.encode(r.playerId) + "/" + Uri.encode(r.sessionId);
    }

    public GameSession load(String game, String player) {
        synchronized (LOCK) {
            String json = prefs.getString(slot(game, player), null);
            if (json == null) {
                return null;
            }
            try {
                GameSession s = decodeSession(json);
                if (!s.gameId.equals(game) || !s.playerId.equals(player)) {
                    throw new Failure(GameErrorCode.CORRUPT_SAVE);
                }
                return s;
            } catch (JSONException | IllegalArgumentException ex) {
                throw new Failure(GameErrorCode.CORRUPT_SAVE);
            }
        }
    }

    public void save(GameSession session) {
        synchronized (LOCK) {
            GameSession existing = load(session.gameId, session.playerId);
            if (existing != null && !existing.sessionId.equals(session.sessionId)) {
                throw new Failure(GameErrorCode.SESSION_EXISTS);
            }
            String key = "result/" + Uri.encode(session.gameId) + "/" + Uri.encode(session.playerId) + "/" + Uri.encode(session.sessionId);
            if (prefs.contains(key)) {
                throw new Failure(GameErrorCode.STORAGE_ERROR);
            }
            try {
                commit(prefs.edit().putString(slot(session.gameId, session.playerId), encodeSession(session)));
            } catch (JSONException ex) {
                throw new Failure(GameErrorCode.STORAGE_ERROR);
            }
        }
    }

    public void finish(GameResult result) {
        synchronized (LOCK) {
            GameSession existing = load(result.gameId, result.playerId);
            String key = resultKey(result);
            if (existing == null && prefs.contains(key)) {
                return;
            }
            if (existing == null || !existing.sessionId.equals(result.sessionId)
                    || !Objects.equals(existing.difficultyId, result.difficultyId)) {
                throw new Failure(GameErrorCode.SESSION_NOT_FOUND);
            }
            try {
                commit(prefs.edit().remove(slot(result.gameId, result.playerId)).putString(key, encodeResult(result)));
            } catch (JSONException ex) {
                throw new Failure(GameErrorCode.STORAGE_ERROR);
            }
        }
    }

    public List<GameResult> pendingResults(String player) {
        synchronized (LOCK) {
            List<GameResult> results = new ArrayList<>();
            String prefix = "result/";
            for (Map.Entry<String, ?> item : prefs.getAll().entrySet()) {
                if (!item.getKey().startsWith(prefix)) {
                    continue;
                }
                try {
                    GameResult r = decodeResult((String) item.getValue());
                    if (player == null || player.equals(r.playerId)) {
                        results.add(r);
                    }
                } catch (JSONException | RuntimeException ignored) {
                    // Skip unreadable entries.
                }
            }
            return results;
        }
    }

    public void acknowledge(GameResult result) {
        synchronized (LOCK) {
            commit(prefs.edit().remove(resultKey(result)));
        }
    }

    public void removeCorruptCheckpoint(String game, String player) {
        synchronized (LOCK) {
            commit(prefs.edit().remove(slot(game, player)));
        }
    }

    private static void commit(SharedPreferences.Editor editor) {
        if (!editor.commit()) {
            throw new Failure(GameErrorCode.STORAGE_ERROR);
        }
    }

    private static String encodeSession(GameSession s) throws JSONException {
        JSONObject j = new JSONObject();
        j.put("version", 1);
        j.put("game", s.gameId);
        j.put("player", s.playerId);
        j.put("session", s.sessionId);
        j.put("difficulty", s.difficultyId == null ? JSONObject.NULL : s.difficultyId);
        j.put("duration", s.activeDurationMs);
        j.put("demo", s.demo);
        j.put("snapshot", s.snapshot);
        return j.toString();
    }

    private static GameSession decodeSession(String text) throws JSONException {
        JSONObject j = new JSONObject(text);
        if (j.getInt("version") != 1) {
            throw new JSONException("Unsupported version");
        }
        String diff = j.isNull("difficulty") ? null : j.getString("difficulty");
        return new GameSession(j.getString("game"), j.getString("player"), j.getString("session"),
                diff, j.getLong("duration"), j.getString("snapshot"), j.getBoolean("demo"));
    }

    private static String encodeResult(GameResult r) throws JSONException {
        JSONObject j = new JSONObject();
        j.put("version", 1);
        j.put("game", r.gameId);
        j.put("player", r.playerId);
        j.put("session", r.sessionId);
        j.put("difficulty", r.difficultyId == null ? JSONObject.NULL : r.difficultyId);
        j.put("duration", r.activeDurationMs);
        j.put("demo", r.demo);
        j.put("score", r.score == null ? JSONObject.NULL : r.score);
        j.put("outcome", r.outcome.name());
        j.put("finished", r.finishedAtEpochMs);
        return j.toString();
    }

    private static GameResult decodeResult(String text) throws JSONException {
        JSONObject j = new JSONObject(text);
        if (j.getInt("version") != 1) {
            throw new JSONException("Unsupported version");
        }
        String diff = j.isNull("difficulty") ? null : j.getString("difficulty");
        Long score = j.isNull("score") ? null : j.getLong("score");
        return new GameResult(j.getString("game"), j.getString("player"), j.getString("session"),
                diff, GameOutcome.valueOf(j.getString("outcome")), score,
                j.getLong("duration"), j.getLong("finished"), j.getBoolean("demo"));
    }
}
