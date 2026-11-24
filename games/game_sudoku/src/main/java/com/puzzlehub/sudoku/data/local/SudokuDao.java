package com.puzzlehub.sudoku.data.local;
import androidx.lifecycle.LiveData;
import androidx.room.*;
import java.util.List;
@Dao
public interface SudokuDao {
    @Query("SELECT * FROM game_history ORDER BY date DESC") LiveData<List<GameHistoryEntity>> observeHistory();
    @Insert(onConflict = OnConflictStrategy.REPLACE) void insertHistory(GameHistoryEntity game);
    @Query("SELECT * FROM ranking_best WHERE playerId = :playerId")
    List<RankingBestEntity> loadRankingBests(String playerId);
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertRankingBest(RankingBestEntity best);
    @Query("UPDATE ranking_best SET durationMs = :durationMs WHERE playerId = :playerId AND difficulty = :difficulty AND durationMs > :durationMs")
    int improveRankingBest(String playerId, String difficulty, long durationMs);
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertCompetitiveResult(CompetitiveResultEntity result);
    @Query("SELECT * FROM competitive_results_pending WHERE playerId = :playerId ORDER BY finishedAt DESC")
    List<CompetitiveResultEntity> pendingCompetitiveResults(String playerId);
    @Delete void acknowledgeCompetitiveResult(CompetitiveResultEntity result);
    @Query("SELECT * FROM saved_game WHERE slot = 1") SavedGameEntity loadSaved();
    @Insert(onConflict = OnConflictStrategy.REPLACE) void save(SavedGameEntity game);
    @Query("DELETE FROM saved_game") void deleteSaved();
    @Query("SELECT * FROM settings WHERE id = 1") SettingsEntity loadSettings();
    @Insert(onConflict = OnConflictStrategy.REPLACE) void saveSettings(SettingsEntity settings);
}
