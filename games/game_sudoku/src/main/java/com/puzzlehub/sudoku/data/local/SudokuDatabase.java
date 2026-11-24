package com.puzzlehub.sudoku.data.local;
import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.annotation.NonNull;
import androidx.sqlite.db.SupportSQLiteDatabase;
@Database(entities = {GameHistoryEntity.class, SavedGameEntity.class, SettingsEntity.class, RankingBestEntity.class, CompetitiveResultEntity.class}, version = 4, exportSchema = true)
public abstract class SudokuDatabase extends RoomDatabase {
    public static final Migration ADD_RANKING_BEST = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS ranking_best (playerId TEXT NOT NULL, "
                    + "difficulty TEXT NOT NULL, durationMs INTEGER NOT NULL, PRIMARY KEY (playerId, difficulty))");
        }
    };

    public static final Migration ADD_COMPETITIVE_WINS = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS competitive_wins_pending (playerId TEXT NOT NULL, "
                    + "roomCode TEXT NOT NULL, startedAt INTEGER NOT NULL, finishedAt INTEGER NOT NULL, "
                    + "PRIMARY KEY (playerId, roomCode, startedAt))");
        }
    };

    public static final Migration RENAME_COMPETITIVE_RESULTS = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE competitive_wins_pending RENAME TO competitive_results_pending");
        }
    };

    public abstract SudokuDao sudokuDao();
}
