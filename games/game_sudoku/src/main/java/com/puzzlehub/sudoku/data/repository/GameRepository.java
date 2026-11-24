package com.puzzlehub.sudoku.data.repository;

import androidx.annotation.Nullable;
import androidx.core.util.Consumer;
import androidx.core.util.Supplier;
import androidx.lifecycle.FlowLiveDataConversions;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import com.example.arcade.core.GameErrorCode;
import com.example.arcade.core.GameExitResponse;
import com.example.arcade.core.GameOutcome;
import com.example.arcade.core.GameResult;
import com.example.arcade.core.GameSessionStore;
import com.puzzlehub.sudoku.data.local.CompetitiveResultEntity;
import com.puzzlehub.sudoku.data.local.GameHistoryEntity;
import com.puzzlehub.sudoku.data.local.RankingBestEntity;
import com.puzzlehub.sudoku.data.local.SavedGameEntity;
import com.puzzlehub.sudoku.data.local.SessionCodec;
import com.puzzlehub.sudoku.data.local.SettingsEntity;
import com.puzzlehub.sudoku.data.local.SudokuDao;
import com.puzzlehub.sudoku.data.local.SudokuDatabase;
import com.puzzlehub.sudoku.domain.GameSession;
import com.puzzlehub.sudoku.domain.GameStatus;
import com.puzzlehub.sudoku.game.SudokuPlugin;
import com.puzzlehub.sudoku.ui.game.SudokuGameStorage;
import com.puzzlehub.sudoku.utils.SudokuTask;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import kotlinx.coroutines.flow.Flow;

/** One I/O queue orders saves, ranking updates, and ArcadePlatform contract checkpoints. */
public final class GameRepository implements SudokuGameStorage {
    private final SudokuDatabase database;
    private final SudokuDao dao;
    private final GameSessionStore contractStore;
    private final LiveData<List<GameHistoryEntity>> deviceHistory;
    private final LiveData<List<GameHistoryEntity>> history;
    private final MutableLiveData<String> localAccount = new MutableLiveData<>("");
    private final ExecutorService storage = Executors.newSingleThreadExecutor(job -> {
        Thread thread = new Thread(job, "sudoku-storage");
        thread.setDaemon(true);
        return thread;
    });
    private volatile Supplier<String> playerId = () -> "";
    private volatile Consumer<String> rankingChanged = player -> {};
    private volatile String contractPlayerId = "guest";
    private volatile GameResult lastContractResult;
    private volatile GameErrorCode lastContractError;

    public GameRepository(SudokuDatabase database, GameSessionStore contractStore) {
        this.database = database;
        this.contractStore = contractStore;
        dao = database.sudokuDao();
        deviceHistory = dao.observeHistory();
        history = Transformations.switchMap(localAccount, account ->
                Transformations.map(deviceHistory, rows -> accountHistory(rows, account)));
    }

    public void setPlayerId(Supplier<String> provider) {
        playerId = provider;
    }

    public void setOnRankingDataChanged(Consumer<String> listener) {
        rankingChanged = listener;
    }

    public void bindContractVisit(String contractPlayer) {
        this.contractPlayerId = (contractPlayer == null || contractPlayer.trim().isEmpty()) ? "guest" : contractPlayer;
        this.lastContractResult = null;
        this.lastContractError = null;
        storage.execute(this::reconcileSavedStateWithContract);
    }

    private void reconcileSavedStateWithContract() {
        try {
            com.example.arcade.core.GameSession checkpoint = contractStore.load(SudokuPlugin.ID, contractPlayerId);
            SavedGameEntity saved = dao.loadSaved();
            if (checkpoint != null) {
                boolean same = false;
                if (saved != null) {
                    try {
                        same = SessionCodec.decode(saved.payload).id().equals(checkpoint.sessionId);
                    } catch (RuntimeException ignored) {
                        same = false;
                    }
                }
                if (!same) {
                    SavedGameEntity restored = new SavedGameEntity();
                    restored.payload = checkpoint.snapshot;
                    dao.save(restored);
                }
            } else if (saved != null) {
                GameSession session = SessionCodec.decode(saved.payload);
                if (session.status() == GameStatus.PLAYING || session.status() == GameStatus.PAUSED) {
                    contractStore.save(new com.example.arcade.core.GameSession(
                            SudokuPlugin.ID, contractPlayerId, session.id(), SudokuPlugin.MODE_STANDARD,
                            session.elapsedSeconds() * 1000L, saved.payload, false));
                }
            }
        } catch (RuntimeException ignored) {
            // Corrupt checkpoints are handled by explicit platform recovery.
        }
    }

    public GameExitResponse buildExitResponse(String gameId, String player) {
        try {
            submit(() -> null).get(3, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // Proceed with latest committed state.
        }
        try {
            com.example.arcade.core.GameSession active = contractStore.load(gameId, player);
            if (active != null) {
                return GameExitResponse.saved(active);
            }
        } catch (RuntimeException error) {
            try {
                contractStore.deleteCorruptSave(gameId, player);
            } catch (RuntimeException ignored) {
                // Ignore cleanup failure.
            }
        }
        GameResult finished = lastContractResult;
        if (finished != null && finished.gameId.equals(gameId) && finished.playerId.equals(player)) {
            return GameExitResponse.finished(finished);
        }
        return GameExitResponse.cancelled(gameId, player);
    }

    public void useLocalAccount(String account) {
        if (!account.equals(localAccount.getValue())) {
            localAccount.setValue(account);
        }
    }

    public LiveData<List<GameHistoryEntity>> observeHistory() { return history; }
    public LiveData<List<GameHistoryEntity>> observeDeviceHistory() { return deviceHistory; }
    public Flow<List<GameHistoryEntity>> getHistory() { return FlowLiveDataConversions.asFlow(history); }
    public Flow<List<GameHistoryEntity>> getDeviceHistory() { return FlowLiveDataConversions.asFlow(deviceHistory); }

    private static List<GameHistoryEntity> accountHistory(List<GameHistoryEntity> rows, String account) {
        List<GameHistoryEntity> results = new ArrayList<>();
        for (GameHistoryEntity record : rows) {
            if (account.isEmpty()) {
                if (!DemoPlayers.isHistoryId(record.id)) {
                    results.add(record);
                }
            } else if (DemoPlayers.ownsHistory(account, record.id)) {
                results.add(record);
            }
        }
        if (!account.isEmpty()) {
            results.addAll(DemoPlayers.recordedHistory(account));
            Collections.sort(results, (a, b) -> Long.compare(b.date, a.date));
        }
        return results;
    }

    @Override public String rankingPlayerId() { return playerId.get(); }

    @Override public SudokuTask<GameSession> loadGame() {
        return submit(() -> {
            SavedGameEntity saved = dao.loadSaved();
            if (saved != null) {
                try {
                    return SessionCodec.decode(saved.payload);
                } catch (RuntimeException invalidSnapshot) {
                    dao.deleteSaved();
                }
            }
            try {
                com.example.arcade.core.GameSession checkpoint = contractStore.load(SudokuPlugin.ID, contractPlayerId);
                if (checkpoint != null) {
                    return SessionCodec.decode(checkpoint.snapshot);
                }
            } catch (RuntimeException ignored) {
                // Fall back to no saved game.
            }
            return null;
        });
    }

    @Override public SudokuTask<Void> saveGame(GameSession session) {
        return submit(() -> {
            persist(session);
            return null;
        });
    }

    @Override public SudokuTask<Boolean> highlightEnabled() {
        return submit(() -> loadSettings().highlight);
    }

    public SudokuTask<SettingsEntity> settings() { return submit(this::loadSettings); }
    public SudokuTask<Void> settings(SettingsEntity value) {
        return submit(() -> {
            dao.saveSettings(value);
            return null;
        });
    }

    public SudokuTask<Void> updatePlayerName(String name) {
        return submit(() -> {
            SettingsEntity settings = loadSettings();
            settings.playerName = name;
            dao.saveSettings(settings);
            return null;
        });
    }

    public SudokuTask<SettingsEntity> saveDisplaySettings(boolean dark, boolean highlight) {
        return submit(() -> {
            SettingsEntity value = loadSettings();
            value.darkMode = dark;
            value.highlight = highlight;
            dao.saveSettings(value);
            return value;
        });
    }

    public SudokuTask<List<RankingBestEntity>> rankingBests(String player) {
        return submit(() -> dao.loadRankingBests(player));
    }

    public SudokuTask<Void> saveMultiplayer(GameHistoryEntity game, @Nullable CompetitiveResultEntity result) {
        return submit(() -> {
            database.runInTransaction(() -> {
                dao.insertHistory(game);
                if (result != null) {
                    dao.insertCompetitiveResult(result);
                }
            });
            if (result != null) {
                rankingChanged.accept(result.playerId);
            }
            return null;
        });
    }

    public SudokuTask<List<CompetitiveResultEntity>> pendingCompetitiveResults(String player) {
        return submit(() -> dao.pendingCompetitiveResults(player));
    }

    public SudokuTask<Void> acknowledgeCompetitiveResult(CompetitiveResultEntity result) {
        return submit(() -> {
            dao.acknowledgeCompetitiveResult(result);
            return null;
        });
    }

    private SettingsEntity loadSettings() {
        SettingsEntity settings = dao.loadSettings();
        if (settings == null) {
            return new SettingsEntity();
        }
        return settings;
    }

    private <T> SudokuTask<T> submit(Supplier<T> action) {
        return SudokuTask.supplyAsync(action, storage);
    }

    private void persist(GameSession session) {
        GameStatus status = session.status();
        String encoded = SessionCodec.encode(session);
        long elapsedMs = session.elapsedSeconds() * 1000L;
        if (status != GameStatus.WON && status != GameStatus.LOST && status != GameStatus.ABANDONED) {
            SavedGameEntity saved = new SavedGameEntity();
            saved.payload = encoded;
            dao.save(saved);
            syncContractSave(session.id(), elapsedMs, encoded);
            return;
        }
        String player = session.rankingPlayerId();
        boolean qualifies = !player.trim().isEmpty() && status == GameStatus.WON && session.elapsedSeconds() > 0;
        database.runInTransaction(() -> {
            dao.insertHistory(historyOf(session));
            dao.deleteSaved();
            if (qualifies) {
                String difficulty = session.puzzle().difficulty().getId();
                dao.insertRankingBest(new RankingBestEntity(player, difficulty, elapsedMs));
                dao.improveRankingBest(player, difficulty, elapsedMs);
            }
        });
        syncContractFinish(session.id(), status, elapsedMs, encoded);
        if (qualifies) {
            rankingChanged.accept(player);
        }
    }

    private void syncContractSave(String sessionId, long elapsedMs, String encoded) {
        try {
            String cpPlayer = contractPlayerId;
            com.example.arcade.core.GameSession existing = contractStore.load(SudokuPlugin.ID, cpPlayer);
            String modeId = SudokuPlugin.MODE_STANDARD;
            if (existing != null && !existing.sessionId.equals(sessionId)) {
                contractStore.finish(new GameResult(SudokuPlugin.ID, cpPlayer, existing.sessionId,
                        existing.difficultyId, GameOutcome.ABANDONED, null,
                        existing.activeDurationMs, System.currentTimeMillis(), false));
            } else if (existing != null && existing.difficultyId != null) {
                modeId = existing.difficultyId;
            }
            contractStore.save(new com.example.arcade.core.GameSession(
                    SudokuPlugin.ID, cpPlayer, sessionId, modeId,
                    elapsedMs, encoded, false));
            lastContractError = null;
        } catch (RuntimeException error) {
            lastContractError = GameErrorCode.STORAGE_ERROR;
        }
    }

    private void syncContractFinish(String sessionId, GameStatus status, long elapsedMs, String encoded) {
        try {
            String cpPlayer = contractPlayerId;
            GameOutcome outcome = GameOutcome.ABANDONED;
            if (status == GameStatus.WON) {
                outcome = GameOutcome.WON;
            } else if (status == GameStatus.LOST) {
                outcome = GameOutcome.LOST;
            }
            com.example.arcade.core.GameSession existing = contractStore.load(SudokuPlugin.ID, cpPlayer);
            String modeId = SudokuPlugin.MODE_STANDARD;
            if (existing != null && !existing.sessionId.equals(sessionId)) {
                contractStore.finish(new GameResult(SudokuPlugin.ID, cpPlayer, existing.sessionId,
                        existing.difficultyId, GameOutcome.ABANDONED, null,
                        existing.activeDurationMs, System.currentTimeMillis(), false));
                existing = null;
            } else if (existing != null && existing.difficultyId != null) {
                modeId = existing.difficultyId;
            }
            if (existing == null) {
                contractStore.save(new com.example.arcade.core.GameSession(
                        SudokuPlugin.ID, cpPlayer, sessionId, modeId,
                        elapsedMs, encoded, false));
            }
            GameResult finished = new GameResult(SudokuPlugin.ID, cpPlayer, sessionId,
                    modeId, outcome, null,
                    elapsedMs, System.currentTimeMillis(), false);
            contractStore.finish(finished);
            lastContractResult = finished;
            lastContractError = null;
        } catch (RuntimeException error) {
            lastContractError = GameErrorCode.STORAGE_ERROR;
        }
    }

    private static GameHistoryEntity historyOf(GameSession session) {
        GameHistoryEntity row = new GameHistoryEntity();
        row.id = session.id();
        if (DemoPlayers.canSignIn(session.rankingPlayerId())) {
            row.id = DemoPlayers.historyId(session.rankingPlayerId(), row.id);
        }
        row.date = session.createdAt();
        row.difficulty = session.puzzle().difficulty().name();
        row.mode = "SOLO";
        row.duration = session.elapsedSeconds();
        row.mistakes = session.mistakes();
        row.result = session.status().name();
        row.score = session.getScore();
        row.hints = session.hintCount();
        return row;
    }
}
