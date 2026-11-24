package com.puzzlehub.sudoku.explore;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.example.arcade.core.GameDefinition;
import com.example.arcade.core.GameResult;

public final class ExploreResultRepository extends SQLiteOpenHelper {
    private final ExploreSessionStore sessions;

    public ExploreResultRepository(Context context) {
        super(context, "sudoku_explore_history.db", null, 1);
        sessions = new ExploreSessionStore(context);
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL("CREATE TABLE game_results (game TEXT NOT NULL, player TEXT NOT NULL, "
                + "session TEXT NOT NULL, difficulty TEXT NOT NULL, outcome TEXT NOT NULL, score INTEGER, "
                + "duration INTEGER NOT NULL, finished INTEGER NOT NULL, PRIMARY KEY (game, player, session))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        throw new IllegalStateException("Missing sudoku explore history migration");
    }

    public void recoverPending(String player) {
        SQLiteDatabase database = getWritableDatabase();
        for (GameResult result : sessions.pendingResults(player)) {
            if (result.demo) {
                sessions.acknowledge(result);
                continue;
            }
            ContentValues values = new ContentValues();
            values.put("game", result.gameId);
            values.put("player", result.playerId);
            values.put("session", result.sessionId);
            values.put("difficulty", result.difficultyId == null ? "classic" : result.difficultyId);
            values.put("outcome", result.outcome.name());
            values.put("score", result.score);
            values.put("duration", result.activeDurationMs);
            values.put("finished", result.finishedAtEpochMs);
            database.insertWithOnConflict("game_results", null, values, SQLiteDatabase.CONFLICT_IGNORE);
            sessions.acknowledge(result);
        }
    }

    public Long record(GameDefinition definition, String player) {
        String expression;
        if (definition.ranking == GameDefinition.Ranking.HIGH_SCORE) {
            expression = "MAX(score)";
        } else if (definition.ranking == GameDefinition.Ranking.LOWEST_WIN_TIME) {
            expression = "MIN(CASE WHEN outcome = 'WON' THEN duration END)";
        } else {
            return null;
        }
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT " + expression + " FROM game_results WHERE player = ? AND game = ?",
                new String[]{player, definition.id})) {
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getLong(0);
            }
            return null;
        }
    }
}
