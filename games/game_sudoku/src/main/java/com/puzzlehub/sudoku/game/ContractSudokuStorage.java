package com.puzzlehub.sudoku.game;

import com.example.arcade.core.GameErrorCode;
import com.example.arcade.core.GameExitResponse;
import com.example.arcade.core.GameLaunchRequest;
import com.example.arcade.core.GameOutcome;
import com.example.arcade.core.GameResult;
import com.example.arcade.core.GameSessionStore;
import com.puzzlehub.sudoku.data.local.SessionCodec;
import com.puzzlehub.sudoku.domain.GameSession;
import com.puzzlehub.sudoku.domain.GameStatus;
import com.puzzlehub.sudoku.ui.game.SudokuGameStorage;

final class ContractSudokuStorage implements SudokuGameStorage {
    private final GameSessionStore store;
    private final GameLaunchRequest request;
    private final boolean restoreOnCreate;
    private GameResult result;
    private GameErrorCode lastError;

    ContractSudokuStorage(GameSessionStore store, GameLaunchRequest request, boolean restoreOnCreate) {
        this.store = store;
        this.request = request;
        this.restoreOnCreate = restoreOnCreate;
    }

    GameSession loadCheckpoint() {
        com.example.arcade.core.GameSession checkpoint = store.load(request.gameId, request.playerId);
        if (checkpoint == null) {
            return null;
        }
        try {
            GameSession session = SessionCodec.decode(checkpoint.snapshot);
            if (!session.id().equals(checkpoint.sessionId)
                    || !session.puzzle().difficulty().getId().equals(checkpoint.difficultyId)
                    || (session.status() != GameStatus.PLAYING && session.status() != GameStatus.PAUSED)) {
                throw new IllegalArgumentException("Checkpoint does not match Sudoku session");
            }
            return session;
        } catch (RuntimeException error) {
            throw new IllegalStateException("Dữ liệu ván Sudoku bị hỏng.", error);
        }
    }

    @Override
    public boolean restoreOnCreate() {
        return restoreOnCreate;
    }

    @Override
    public GameSession loadGame() {
        return loadCheckpoint();
    }

    @Override
    public void saveGame(GameSession session) {
        try {
            persist(session);
            lastError = null;
        } catch (RuntimeException error) {
            lastError = GameErrorCode.STORAGE_ERROR;
            throw error;
        }
    }

    @Override
    public boolean highlightEnabled() {
        return true;
    }

    @Override
    public String rankingPlayerId() {
        return request.playerId;
    }

    boolean restoreExit(String sessionId) {
        if (sessionId == null) {
            return false;
        }
        for (GameResult pending : store.pendingResults()) {
            if (pending.gameId.equals(request.gameId) && pending.playerId.equals(request.playerId)
                    && pending.sessionId.equals(sessionId)) {
                result = pending;
                return true;
            }
        }
        return false;
    }

    String finishedSession() {
        if (result == null) {
            return null;
        }
        return result.sessionId;
    }

    GameExitResponse exitResponse() {
        if (lastError != null) {
            return GameExitResponse.error(request.gameId, request.playerId, lastError);
        }
        com.example.arcade.core.GameSession checkpoint = store.load(request.gameId, request.playerId);
        if (checkpoint != null) {
            return GameExitResponse.saved(checkpoint);
        }
        if (result != null) {
            return GameExitResponse.finished(result);
        }
        return GameExitResponse.cancelled(request.gameId, request.playerId);
    }

    private void persist(GameSession session) {
        if (result != null && result.sessionId.equals(session.id())) {
            return;
        }
        long elapsedMs = session.elapsedSeconds() * 1_000L;
        String difficulty = session.puzzle().difficulty().getId();
        GameOutcome outcome = null;
        if (session.status() == GameStatus.WON) {
            outcome = GameOutcome.WON;
        } else if (session.status() == GameStatus.LOST) {
            outcome = GameOutcome.LOST;
        } else if (session.status() == GameStatus.ABANDONED) {
            outcome = GameOutcome.ABANDONED;
        }

        if (outcome == null) {
            store.save(new com.example.arcade.core.GameSession(request.gameId, request.playerId, session.id(),
                    difficulty, elapsedMs, SessionCodec.encode(session), false));
        } else {
            GameResult finished = new GameResult(request.gameId, request.playerId, session.id(), difficulty,
                    outcome, null, elapsedMs, System.currentTimeMillis(), false);
            store.finish(finished);
            result = finished;
        }
    }
}
