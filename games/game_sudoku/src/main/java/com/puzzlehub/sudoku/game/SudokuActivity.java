package com.puzzlehub.sudoku.game;

import android.os.Bundle;
import android.widget.FrameLayout;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.Fragment;
import com.example.arcade.core.GameContract;
import com.example.arcade.core.GameErrorCode;
import com.example.arcade.core.GameExitResponse;
import com.example.arcade.core.GameLaunchRequest;
import com.example.arcade.core.GameSession;
import com.example.arcade.core.GameSessionStore;
import com.example.arcade.core.LaunchMode;
import com.puzzlehub.sudoku.data.repository.AiTutorRepository;
import com.puzzlehub.sudoku.data.repository.LocalAiTutorRepository;
import com.puzzlehub.sudoku.domain.Difficulty;
import com.puzzlehub.sudoku.ui.game.GameFragment;
import com.puzzlehub.sudoku.ui.game.SudokuGameServices;
import com.puzzlehub.sudoku.ui.game.SudokuGameStorage;

public final class SudokuActivity extends AppCompatActivity implements SudokuGameServices {
    private final AiTutorRepository tutor = new LocalAiTutorRepository();
    private ContractSudokuStorage storage;
    private GameLaunchRequest request;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            request = GameContract.readRequest(getIntent());
        } catch (IllegalArgumentException error) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }
        if (!SudokuPlugin.ID.equals(request.gameId)) {
            exitWithError(GameErrorCode.INVALID_REQUEST);
            return;
        }

        GameSessionStore store = new GameSessionStore(this);
        GameSession checkpoint;
        try {
            checkpoint = store.load(request.gameId, request.playerId);
            storage = new ContractSudokuStorage(store, request, checkpoint != null);
            if (checkpoint != null) {
                storage.loadCheckpoint();
            }
        } catch (RuntimeException error) {
            exitWithError(GameErrorCode.CORRUPT_SAVE);
            return;
        }

        String requestedDifficulty = request.difficultyId;
        if (request.launchMode == LaunchMode.RESUME && savedInstanceState == null) {
            if (checkpoint == null || !checkpoint.sessionId.equals(request.sessionId)) {
                exitWithError(GameErrorCode.SESSION_NOT_FOUND);
                return;
            }
            if (requestedDifficulty != null && !requestedDifficulty.equals(checkpoint.difficultyId)) {
                exitWithError(GameErrorCode.INVALID_REQUEST);
                return;
            }
            requestedDifficulty = checkpoint.difficultyId;
        } else if (checkpoint != null && savedInstanceState == null) {
            exitWithError(GameErrorCode.SESSION_EXISTS);
            return;
        }

        Difficulty difficulty;
        try {
            difficulty = SudokuPlugin.resolveDifficulty(requestedDifficulty);
        } catch (IllegalArgumentException error) {
            exitWithError(GameErrorCode.INVALID_REQUEST);
            return;
        }

        if (savedInstanceState != null && checkpoint == null) {
            try {
                if (storage.restoreExit(savedInstanceState.getString("finishedSession"))) {
                    finishGame();
                    return;
                }
            } catch (IllegalStateException error) {
                exitWithError(GameErrorCode.STORAGE_ERROR);
                return;
            }
        }

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        FrameLayout container = new FrameLayout(this);
        container.setId(R.id.sudoku_play_host);
        container.setBackgroundResource(R.drawable.bg_app);
        ViewCompat.setOnApplyWindowInsetsListener(container, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        setContentView(container);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishGame();
            }
        });
        if (savedInstanceState == null) {
            GameFragment fragment;
            if (request.launchMode == LaunchMode.RESUME) {
                fragment = new GameFragment();
            } else {
                fragment = GameFragment.newGame(difficulty);
            }
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.sudoku_play_host, fragment)
                    .commit();
        }
    }

    public void finishGame() {
        if (storage == null) {
            exitWithError(GameErrorCode.STORAGE_ERROR);
            return;
        }
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.sudoku_play_host);
        if (fragment instanceof GameFragment) {
            ((GameFragment) fragment).saveBeforeExit();
        }
        try {
            exit(storage.exitResponse());
        } catch (IllegalStateException error) {
            exitWithError(GameErrorCode.STORAGE_ERROR);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        if (storage != null) {
            state.putString("finishedSession", storage.finishedSession());
        }
        super.onSaveInstanceState(state);
    }

    private void exitWithError(GameErrorCode code) {
        exit(GameExitResponse.error(request.gameId, request.playerId, code));
    }

    private void exit(GameExitResponse response) {
        setResult(RESULT_OK, GameContract.responseIntent(response));
        finish();
    }

    @Override
    public SudokuGameStorage getSudokuStorage() {
        return storage;
    }

    @Override
    public AiTutorRepository getSudokuTutor() {
        return tutor;
    }
}
