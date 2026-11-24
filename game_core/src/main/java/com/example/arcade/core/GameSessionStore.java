package com.example.arcade.core;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Small offline checkpoints, one active session per game/player. Stores snapshots opaquely.
 * Call from one app process. commit() is deliberate: do not report success before bytes persist.
 * Large boards/assets need a module-owned file/DB implementation, not larger preference blobs.
 */
public final class GameSessionStore {
    private static final Object LOCK = new Object();
    private final SharedPreferences prefs;
    public GameSessionStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("arcade_sessions_v1", Context.MODE_PRIVATE);
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
            if (json == null) return null;
            try {
                GameSession s = GameJson.session(json);
                if (!s.gameId.equals(game) || !s.playerId.equals(player)) throw new IllegalStateException("Save identity mismatch");
                return s;
            } catch (JSONException | IllegalArgumentException ex) { throw new IllegalStateException("Corrupt save", ex); }
        }
    }
    public void save(GameSession session) {
        synchronized (LOCK) {
            GameSession existing = load(session.gameId, session.playerId);
            if (existing != null && !existing.sessionId.equals(session.sessionId))
                throw new IllegalStateException("Another active session exists");
            String key = "result/" + Uri.encode(session.gameId) + "/" + Uri.encode(session.playerId) + "/" + Uri.encode(session.sessionId);
            if (prefs.contains(key)) throw new IllegalStateException("Session has already finished");
            try { committed(prefs.edit().putString(slot(session.gameId, session.playerId), GameJson.session(session))); }
            catch (JSONException ex) { throw new IllegalStateException(ex); }
        }
    }
    public void finish(GameResult result) {
        synchronized (LOCK) {
            GameSession existing = load(result.gameId, result.playerId);
            String key = resultKey(result);
            // Retrying a persisted finish is safe; it must never overwrite another active session.
            if (existing == null && prefs.contains(key)) return;
            if (existing == null || !existing.sessionId.equals(result.sessionId)
                    || !java.util.Objects.equals(existing.difficultyId, result.difficultyId)
                    || existing.demo != result.demo)
                throw new IllegalStateException("Result does not match active session");
            try {
                committed(prefs.edit().remove(slot(result.gameId, result.playerId)).putString(key, GameJson.result(result)));
            } catch (JSONException ex) { throw new IllegalStateException(ex); }
        }
    }
    /** Upgrade housekeeping: remove only data explicitly marked as a previous demo. */
    public void clearDemoData() {
        synchronized (LOCK) {
            SharedPreferences.Editor editor = prefs.edit();
            boolean changed = false;
            for (Map.Entry<String, ?> item : prefs.getAll().entrySet()) {
                try {
                    boolean demo = item.getKey().startsWith("save/") ? GameJson.session((String)item.getValue()).demo
                            : item.getKey().startsWith("result/") && GameJson.result((String)item.getValue()).demo;
                    if (demo) { editor.remove(item.getKey()); changed = true; }
                } catch (JSONException | RuntimeException ignored) { /* Preserve unreadable data for explicit recovery. */ }
            }
            if (changed) committed(editor);
        }
    }
    public List<GameResult> pendingResults() {
        synchronized (LOCK) {
            List<GameResult> results = new ArrayList<>();
            for (Map.Entry<String, ?> item : prefs.getAll().entrySet()) {
                if (!item.getKey().startsWith("result/")) continue;
                try { results.add(GameJson.result((String) item.getValue())); }
                catch (JSONException | RuntimeException ex) {
                    throw new IllegalStateException("Corrupt pending result", ex);
                }
            }
            return results;
        }
    }
    public void acknowledge(GameResult result) {
        synchronized (LOCK) { committed(prefs.edit().remove(resultKey(result))); }
    }
    /** Explicit user-confirmed recovery only; does not fabricate a game result. */
    public boolean discard(String game, String player, String sessionId) {
        synchronized (LOCK) {
            GameSession saved = load(game, player);
            return saved != null && saved.sessionId.equals(sessionId)
                    && prefs.edit().remove(slot(game, player)).commit();
        }
    }
    public void deleteCorruptSave(String game, String player) {
        synchronized (LOCK) { committed(prefs.edit().remove(slot(game, player))); }
    }
    private static void committed(SharedPreferences.Editor editor) {
        if (!editor.commit()) throw new IllegalStateException("Cannot persist game data");
    }
}
