package com.puzzlehub.sudoku.ui.game;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.puzzlehub.sudoku.data.repository.AiTutorRepository;
import com.puzzlehub.sudoku.domain.Difficulty;
import com.puzzlehub.sudoku.domain.GameController;
import com.puzzlehub.sudoku.domain.GameSession;
import com.puzzlehub.sudoku.domain.GameStatus;
import com.puzzlehub.sudoku.domain.Hint;
import com.puzzlehub.sudoku.domain.HintLesson;
import com.puzzlehub.sudoku.domain.SudokuBoard;
import com.puzzlehub.sudoku.domain.SudokuGenerator;
import com.puzzlehub.sudoku.domain.SudokuHintEngine;
import com.puzzlehub.sudoku.utils.SudokuTask;
import com.puzzlehub.sudoku.utils.SudokuTaskException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class GameViewModel extends ViewModel {
    private static final long TICK_MS = 1_000L;
    private final SudokuGameStorage storage;
    private final AiTutorRepository tutor;
    private final ExecutorService tutorWorker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MutableLiveData<GameUiState> state = new MutableLiveData<>();
    private final Runnable timer = this::onTimer;
    private GameController game;
    private int selected = -1;
    private boolean pencil;
    private boolean highlight = true;
    private boolean loading;
    private boolean started;
    private boolean foreground;
    private volatile boolean cleared;
    private String message;
    private Hint hint;
    private String explanation = "";
    private boolean explaining;
    private Integer correctCell;
    private long moveVersion;
    private int hintStep;
    private long clockStarted = -1;
    private long remainingMs;

    private record StartingGame(GameSession session, boolean highlight) {}

    public GameViewModel(SudokuGameStorage storage, AiTutorRepository tutor) {
        this.storage = Objects.requireNonNull(storage);
        this.tutor = Objects.requireNonNull(tutor);
        publish();
    }

    LiveData<GameUiState> state() {
        return state;
    }

    void start(@Nullable Difficulty difficulty) {
        if (!started) {
            startSession(difficulty, difficulty == null || storage.restoreOnCreate());
        }
    }

    private void startSession(@Nullable Difficulty difficulty, boolean resume) {
        started = true;
        loading = true;
        message = null;
        publish();
        String playerId = storage.rankingPlayerId();
        storage.highlightEnabled().thenCompose(enabled ->
                prepareSession(difficulty, resume, playerId).thenApply(session -> new StartingGame(session, enabled))
        ).whenComplete((starting, failure) -> main.post(() -> {
            if (cleared) {
                return;
            }
            if (failure != null) {
                loading = false;
                message = messageOf(failure);
                publish();
                return;
            }
            GameController controller = new GameController(starting.session());
            storage.saveGame(controller.getSession()).whenComplete((ignored, saveError) -> main.post(() -> {
                if (cleared) {
                    return;
                }
                loading = false;
                if (saveError == null) {
                    game = controller;
                    highlight = starting.highlight();
                    refreshClock();
                } else {
                    message = messageOf(saveError);
                }
                publish();
            }));
        }));
    }

    private SudokuTask<GameSession> prepareSession(@Nullable Difficulty difficulty, boolean resume, String playerId) {
        return storage.loadGame().thenCompose(saved -> {
            if (resume) {
                if (saved == null) {
                    throw new IllegalStateException("Không có ván đang chơi.");
                }
                return SudokuTask.completedFuture(saved.withStatus(GameStatus.PLAYING));
            }
            SudokuTask<Void> previous = SudokuTask.completedFuture(null);
            if (saved != null) {
                previous = storage.saveGame(saved.withStatus(GameStatus.ABANDONED));
            }
            return previous.thenApplyAsync(ignored -> {
                Difficulty level = Objects.requireNonNull(difficulty);
                return new GameSession(new SudokuGenerator().generatePuzzle(level), playerId);
            });
        });
    }

    void setForeground(boolean visible) {
        foreground = visible;
        refreshClock();
    }

    void select(int index) {
        selected = index;
        publish();
    }

    void togglePencil() {
        pencil = !pencil;
        publish();
    }

    void enter(int value) {
        if (selected < 0 || game == null) {
            return;
        }
        GameSession before = game.getSession();
        boolean note = pencil && value != 0;
        updateElapsed();
        if (game.enter(selected, value, note)) {
            Integer correct = null;
            if (!note && value != 0 && value == before.puzzle().solution().get(selected)
                    && game.getSession().status() == GameStatus.PLAYING) {
                correct = selected;
            }
            afterMove(correct);
        }
    }

    void undo() {
        if (game != null && game.undo()) {
            afterMove(null);
        }
    }

    void pause(boolean paused) {
        if (game == null) {
            return;
        }
        updateElapsed();
        game.pause(paused);
        refreshClock();
        save();
        publish();
    }

    void restart() {
        if (game == null || loading) {
            return;
        }
        Difficulty difficulty = game.getSession().puzzle().difficulty();
        game = null;
        selected = -1;
        pencil = false;
        correctCell = null;
        remainingMs = 0;
        clearHintState();
        refreshClock();
        startSession(difficulty, false);
    }

    void requestHint() {
        if (!playing()) {
            return;
        }
        GameSession session = game.getSession();
        for (int index = 0; index < SudokuBoard.CELL_COUNT; index++) {
            int value = session.board().get(index);
            if (value != 0 && value != session.puzzle().solution().get(index)) {
                message = "Hãy xóa hoặc sửa số sai trước khi xem gợi ý.";
                publish();
                return;
            }
        }
        clearHintState();
        hint = new SudokuHintEngine().nextHint(session.board());
        if (hint != null) {
            selected = hint.getIndex();
            explanation = hint.explanation();
        }
        publish();
    }

    void changeHintStep(int direction) {
        hintStep = Math.max(0, Math.min(2, hintStep + direction));
        publish();
    }

    int hintStep() {
        return hintStep;
    }

    void explainHint() {
        if (game == null || hint == null || explaining) {
            return;
        }
        SudokuBoard board = game.getSession().board();
        Hint requested = hint;
        explaining = true;
        publish();
        tutorWorker.execute(() -> {
            String detail;
            try {
                detail = tutor.explainHint(requested, board);
            } catch (RuntimeException error) {
                detail = HintLesson.details(requested, board);
            }
            String resolved = detail;
            main.post(() -> {
                if (cleared || game == null || requested != hint || !board.equals(game.getSession().board())) {
                    return;
                }
                explaining = false;
                explanation = resolved;
                publish();
            });
        });
    }

    void applyHint() {
        if (playing() && hint != null) {
            updateElapsed();
            game.applyHint(hint);
            afterMove(null);
        }
    }

    void clearHint() {
        clearHintState();
        publish();
    }

    void consumeCorrectCell() {
        correctCell = null;
        publish();
    }

    void clearMessage() {
        message = null;
        publish();
    }

    void save() {
        if (game == null) {
            return;
        }
        updateElapsed();
        GameSession snapshot = game.getSession();
        storage.saveGame(snapshot).whenComplete((ignored, error) -> {
            if (error != null) {
                main.post(() -> {
                    if (!cleared && game != null && game.getSession().id().equals(snapshot.id())) {
                        message = "Không lưu được tiến trình: " + messageOf(error);
                        publish();
                    }
                });
            }
        });
    }

    private void afterMove(@Nullable Integer correct) {
        clearHintState();
        correctCell = correct;
        moveVersion++;
        refreshClock();
        save();
        publish();
    }

    private void clearHintState() {
        hint = null;
        hintStep = 0;
        explanation = "";
        explaining = false;
    }

    private boolean playing() {
        return game != null && game.getSession().status() == GameStatus.PLAYING;
    }

    private void refreshClock() {
        if (foreground && playing()) {
            if (clockStarted < 0) {
                clockStarted = SystemClock.elapsedRealtime();
                main.postDelayed(timer, TICK_MS - remainingMs);
            }
        } else {
            updateElapsed();
            clockStarted = -1;
            main.removeCallbacks(timer);
        }
    }

    private void updateElapsed() {
        if (clockStarted < 0 || !playing()) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        long accrued = now - clockStarted + remainingMs;
        game.addElapsedSeconds(accrued / TICK_MS);
        remainingMs = accrued % TICK_MS;
        clockStarted = now;
    }

    private void onTimer() {
        if (!foreground || !playing()) {
            return;
        }
        long previous = game.getSession().elapsedSeconds();
        updateElapsed();
        long current = game.getSession().elapsedSeconds();
        if (current / 5 > previous / 5) {
            save();
        }
        publish();
        main.postDelayed(timer, TICK_MS - remainingMs);
    }

    private void publish() {
        GameSession session = null;
        if (game != null) {
            session = game.getSession();
        }
        state.setValue(new GameUiState(session, selected, pencil, loading, message, hint,
                explanation, explaining, highlight, correctCell, moveVersion, hintStep));
    }

    private static String messageOf(Throwable error) {
        Throwable cause = error;
        if (cause instanceof SudokuTaskException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? "Không mở được ván Sudoku." : cause.getMessage();
    }

    @Override
    protected void onCleared() {
        cleared = true;
        tutorWorker.shutdownNow();
        main.removeCallbacksAndMessages(null);
        super.onCleared();
    }
}
