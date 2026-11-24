package com.puzzlehub.sudoku.explore;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import com.example.arcade.core.GameContract;
import com.example.arcade.core.GameDefinition;
import com.example.arcade.core.GameErrorCode;
import com.example.arcade.core.GameExitResponse;
import com.example.arcade.core.GameLaunchRequest;
import com.example.arcade.core.GameOutcome;
import com.example.arcade.core.GameResult;
import com.example.arcade.core.GameSession;
import com.example.arcade.core.LaunchMode;
import java.util.UUID;

/** Lifecycle and persistence for Sudoku's internal Explore mini-games. */
public abstract class ExploreGameActivity extends Activity {
    private GameLaunchRequest request;
    private ExploreSessionStore store;
    private GameSession checkpoint;
    private GameResult result;
    private long foregroundSince = -1;
    private long elapsedMs;
    private boolean ready;

    protected abstract GameDefinition definition();
    protected abstract void createGame(String difficultyId);
    protected abstract void restoreGame(String snapshotJson);
    protected abstract String encodeGame();
    protected abstract void showGame();

    @Override
    @SuppressWarnings("deprecation")
    protected final void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        String gameId = definition().id;
        String playerId = "guest";
        try {
            request = GameContract.readRequest(getIntent());
            playerId = request.playerId;
            GameDefinition definition = definition();
            if (!definition.id.equals(request.gameId)) {
                throw new IllegalArgumentException("Game does not match intent");
            }
            String resolvedDifficulty = ExploreGames.resolveDifficulty(definition, request.difficultyId);
            store = new ExploreSessionStore(this);
            checkpoint = store.load(request.gameId, request.playerId);
            if (savedState != null && checkpoint == null) {
                String sessionId = savedState.getString("session");
                Intent finished = savedState.getParcelable("finishedResponse");
                if (finished != null) {
                    exit(GameContract.readResponse(finished));
                    return;
                }
                for (GameResult pending : store.pendingResults(request.playerId)) {
                    if (pending.gameId.equals(request.gameId) && pending.sessionId.equals(sessionId)) {
                        exit(GameExitResponse.finished(pending));
                        return;
                    }
                }
            }
            if (savedState != null || request.launchMode == LaunchMode.RESUME) {
                restoreCheckpoint(definition, savedState);
            } else {
                if (checkpoint != null) {
                    throw new ExploreSessionStore.Failure(GameErrorCode.SESSION_EXISTS);
                }
                createCheckpoint(resolvedDifficulty);
            }
        } catch (ExploreSessionStore.Failure error) {
            exit(GameExitResponse.error(gameId, playerId, error.code));
            return;
        } catch (IllegalArgumentException | NullPointerException error) {
            exit(GameExitResponse.error(gameId, playerId, GameErrorCode.INVALID_REQUEST));
            return;
        }

        ready = true;
        showGame();
    }

    private void createCheckpoint(String difficultyId) {
        createGame(difficultyId);
        GameSession newSession = new GameSession(request.gameId, request.playerId, UUID.randomUUID().toString(),
                difficultyId, 0, encodeGame(), false);
        store.save(newSession);
        checkpoint = newSession;
    }

    protected final void playAgain() {
        if (result == null) {
            return;
        }
        try {
            createCheckpoint(checkpoint.difficultyId);
            result = null;
            elapsedMs = 0;
            foregroundSince = SystemClock.elapsedRealtime();
            showGame();
        } catch (ExploreSessionStore.Failure error) {
            exit(GameExitResponse.error(request.gameId, request.playerId, error.code));
        }
    }

    private void restoreCheckpoint(GameDefinition definition, Bundle savedState) {
        String expectedId = request.sessionId;
        if (savedState != null) {
            expectedId = savedState.getString("session");
        }
        if (checkpoint == null || !checkpoint.sessionId.equals(expectedId)) {
            throw new ExploreSessionStore.Failure(GameErrorCode.SESSION_NOT_FOUND);
        }
        if (request.difficultyId != null && !request.difficultyId.equals(checkpoint.difficultyId)) {
            throw new IllegalArgumentException("Difficulty does not match checkpoint");
        }
        try {
            ExploreGames.resolveDifficulty(definition, checkpoint.difficultyId);
            restoreGame(checkpoint.snapshot);
            elapsedMs = checkpoint.activeDurationMs;
        } catch (RuntimeException error) {
            throw new ExploreSessionStore.Failure(GameErrorCode.CORRUPT_SAVE);
        }
    }

    @Override
    protected final void onResume() {
        super.onResume();
        if (ready && result == null) {
            foregroundSince = SystemClock.elapsedRealtime();
        }
    }

    @Override
    protected final void onPause() {
        if (foregroundSince >= 0) {
            elapsedMs = activeDurationMs();
            foregroundSince = -1;
            checkpointGame();
        }
        super.onPause();
    }

    @Override
    protected final void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (result != null) {
            state.putParcelable("finishedResponse", GameContract.responseIntent(GameExitResponse.finished(result)));
        } else if (ready && checkpointGame()) {
            state.putString("session", checkpoint.sessionId);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public final void onBackPressed() {
        saveAndExit();
    }

    protected final boolean checkpointGame() {
        if (!ready || result != null) {
            return true;
        }
        try {
            GameSession updated = new GameSession(checkpoint.gameId, checkpoint.playerId, checkpoint.sessionId,
                    checkpoint.difficultyId, activeDurationMs(), encodeGame(), false);
            store.save(updated);
            checkpoint = updated;
            return true;
        } catch (ExploreSessionStore.Failure error) {
            exit(GameExitResponse.error(request.gameId, request.playerId, error.code));
            return false;
        }
    }

    protected final boolean completeGame(GameOutcome outcome, Long score) {
        if (result != null) {
            return true;
        }
        try {
            GameResult finished = new GameResult(checkpoint.gameId, checkpoint.playerId, checkpoint.sessionId,
                    checkpoint.difficultyId, outcome, score, activeDurationMs(), System.currentTimeMillis(), false);
            store.finish(finished);
            result = finished;
            return true;
        } catch (ExploreSessionStore.Failure error) {
            exit(GameExitResponse.error(request.gameId, request.playerId, error.code));
            return false;
        }
    }

    protected final void saveAndExit() {
        if (result != null) {
            exit(GameExitResponse.finished(result));
        } else if (ready && checkpointGame()) {
            exit(GameExitResponse.saved(checkpoint));
        }
    }

    private long activeDurationMs() {
        if (foregroundSince < 0) {
            return elapsedMs;
        }
        return elapsedMs + SystemClock.elapsedRealtime() - foregroundSince;
    }

    private void exit(GameExitResponse response) {
        ready = false;
        setResult(RESULT_OK, GameContract.responseIntent(response));
        finish();
    }
}
