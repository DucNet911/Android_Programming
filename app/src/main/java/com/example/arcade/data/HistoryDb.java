package com.example.arcade.data;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.example.arcade.core.GameOutcome;
import com.example.arcade.core.GameResult;
import java.util.ArrayList;
import java.util.List;
public final class HistoryDb extends SQLiteOpenHelper {
    public HistoryDb(Context context) { super(context.getApplicationContext(), "arcade_history.db", null, 1); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE results (game_id TEXT NOT NULL, player_id TEXT NOT NULL, session_id TEXT NOT NULL, " +
                "difficulty TEXT, outcome TEXT NOT NULL, score INTEGER, duration_ms INTEGER NOT NULL, " +
                "finished_at INTEGER NOT NULL, demo INTEGER NOT NULL, PRIMARY KEY(game_id, player_id, session_id))");
        db.execSQL("CREATE INDEX history_player_date ON results(player_id, finished_at DESC)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("Add a non-destructive migration before raising DB version");
    }
    public void insertOnce(GameResult r) {
        ContentValues v = new ContentValues(); v.put("game_id", r.gameId); v.put("player_id", r.playerId);
        v.put("session_id", r.sessionId); v.put("difficulty", r.difficultyId); v.put("outcome", r.outcome.name());
        if (r.score == null) v.putNull("score"); else v.put("score", r.score);
        v.put("duration_ms", r.activeDurationMs); v.put("finished_at", r.finishedAtEpochMs); v.put("demo", r.demo ? 1 : 0);
        SQLiteDatabase db = getWritableDatabase();
        // Duplicate delivery is normal (Activity callback + recovery onResume).
        db.beginTransaction();
        try {
            try (Cursor c = db.rawQuery("SELECT 1 FROM results WHERE game_id=? AND player_id=? AND session_id=?",
                    new String[]{r.gameId, r.playerId, r.sessionId})) {
                if (!c.moveToFirst()) db.insertOrThrow("results", null, v);
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    public void deleteDemoResults() { getWritableDatabase().delete("results", "demo=1", null); }
    public List<GameResult> list(String playerId, String gameId) {
        List<GameResult> results = new ArrayList<>();
        String where = "player_id=?" + (gameId == null ? "" : " AND game_id=?");
        String[] args = gameId == null ? new String[]{playerId} : new String[]{playerId, gameId};
        try (Cursor c = getReadableDatabase().query("results", null, where, args, null, null, "finished_at DESC, session_id ASC")) {
            while (c.moveToNext()) {
                int scoreIndex = c.getColumnIndexOrThrow("score");
                results.add(new GameResult(c.getString(c.getColumnIndexOrThrow("game_id")),
                    c.getString(c.getColumnIndexOrThrow("player_id")), c.getString(c.getColumnIndexOrThrow("session_id")),
                    c.getString(c.getColumnIndexOrThrow("difficulty")), GameOutcome.valueOf(c.getString(c.getColumnIndexOrThrow("outcome"))),
                    c.isNull(scoreIndex) ? null : c.getLong(scoreIndex), c.getLong(c.getColumnIndexOrThrow("duration_ms")),
                    c.getLong(c.getColumnIndexOrThrow("finished_at")), c.getInt(c.getColumnIndexOrThrow("demo")) == 1));
            }
        }
        return results;
    }
}
