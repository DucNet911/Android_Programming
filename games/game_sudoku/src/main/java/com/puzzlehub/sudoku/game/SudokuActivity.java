package com.puzzlehub.sudoku.game;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.arcade.core.GameContract;
import com.example.arcade.core.GameErrorCode;
import com.example.arcade.core.GameExitResponse;
import com.example.arcade.core.GameLaunchRequest;
import com.example.arcade.core.GameSession;
import com.example.arcade.core.GameSessionStore;
import com.puzzlehub.sudoku.data.local.SessionCodec;
import com.puzzlehub.sudoku.domain.GameStatus;
import com.puzzlehub.sudoku.ui.MainActivity;
import com.puzzlehub.sudoku.ui.game.GameFragment;

/**
 * Entry Activity launched from ArcadePlatform's home card.
 * Opens the complete original 4-tab Sudoku application and auto-recovers any stale legacy save.
 */
public final class SudokuActivity extends MainActivity {
    private GameLaunchRequest request;

    @Override
    protected boolean onInitializeHost(@Nullable Bundle saved) {
        Intent intent = getIntent();
        String fallbackPlayer = extractPlayer(intent);
        String fallbackGame = extractGame(intent);
        try {
            request = GameContract.readRequest(intent);
        } catch (IllegalArgumentException error) {
            GameErrorCode code = "Unsupported contract version".equals(error.getMessage())
                    ? GameErrorCode.UNSUPPORTED_VERSION
                    : GameErrorCode.INVALID_REQUEST;
            exit(GameExitResponse.error(fallbackGame, fallbackPlayer, code));
            return false;
        }
        if (!SudokuPlugin.ID.equals(request.gameId)) {
            exit(GameExitResponse.error(request.gameId, request.playerId, GameErrorCode.INVALID_REQUEST));
            return false;
        }

        GameSessionStore store = new GameSessionStore(this);
        try {
            GameSession checkpoint = store.load(request.gameId, request.playerId);
            if (checkpoint != null) {
                com.puzzlehub.sudoku.domain.GameSession decoded = SessionCodec.decode(checkpoint.snapshot);
                if (!decoded.id().equals(checkpoint.sessionId)
                        || (decoded.status() != GameStatus.PLAYING && decoded.status() != GameStatus.PAUSED)) {
                    store.deleteCorruptSave(request.gameId, request.playerId);
                }
            }
        } catch (RuntimeException corrupt) {
            try {
                store.deleteCorruptSave(request.gameId, request.playerId);
            } catch (RuntimeException ignored) {
                // Continue into Sudoku Home cleanly.
            }
        }

        if (saved == null) {
            runtime().getGames().bindContractVisit(request.playerId);
        }
        setResult(RESULT_OK, GameContract.responseIntent(
                GameExitResponse.cancelled(request.gameId, request.playerId)));
        return true;
    }

    @Override
    protected void onExitHost() {
        finishGame();
    }

    public void finishGame() {
        if (request == null) {
            finish();
            return;
        }
        Fragment active = getSupportFragmentManager().findFragmentById(R.id.host);
        if (active instanceof GameFragment) {
            ((GameFragment) active).saveBeforeExit();
        }
        GameExitResponse response = runtime().getGames().buildExitResponse(request.gameId, request.playerId);
        exit(response);
    }

    private void exit(GameExitResponse response) {
        setResult(RESULT_OK, GameContract.responseIntent(response));
        finish();
    }

    private static String extractGame(@Nullable Intent intent) {
        if (intent != null) {
            Bundle b = intent.getBundleExtra(GameContract.EXTRA_REQUEST);
            if (b != null) {
                String g = b.getString("game");
                if (g != null && !g.trim().isEmpty()) {
                    return g;
                }
            }
        }
        return SudokuPlugin.ID;
    }

    private static String extractPlayer(@Nullable Intent intent) {
        if (intent != null) {
            Bundle b = intent.getBundleExtra(GameContract.EXTRA_REQUEST);
            if (b != null) {
                String p = b.getString("player");
                if (p != null && !p.trim().isEmpty()) {
                    return p;
                }
            }
        }
        return "guest";
    }
}
