package com.puzzlehub.sudoku.game;

import android.content.Context;
import android.content.Intent;
import com.example.arcade.core.ExitReason;
import com.example.arcade.core.GameContract;
import com.example.arcade.core.GameDefinition;
import com.example.arcade.core.GameExitResponse;
import com.example.arcade.core.GameLaunchRequest;
import com.example.arcade.core.GameSession;
import com.example.arcade.core.GameSessionStore;
import com.example.arcade.core.LaunchMode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public final class SudokuPluginTest {
    private Context context;
    private GameSessionStore store;

    @Before
    public void setup() {
        org.robolectric.shadows.ShadowChoreographer.setPaused(true);
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("arcade_sessions_v1", 0).edit().clear().commit();
        store = new GameSessionStore(context);
    }

    @Test
    public void definitionRegistersSingleModeJumpingDirectlyToSudokuHome() {
        SudokuPlugin plugin = new SudokuPlugin();
        GameDefinition definition = plugin.getDefinition(context);
        assertEquals(GameContract.VERSION, plugin.getContractVersion());
        assertEquals("sudoku", definition.id);
        assertEquals("Sudoku", definition.title);
        assertEquals(SudokuActivity.class, definition.activity);
        assertEquals(GameDefinition.Ranking.LOWEST_WIN_TIME, definition.ranking);
        assertEquals(1, definition.difficultyCount());
        assertEquals("standard", definition.difficultyId(0));
        assertNull(store.load("sudoku", "guest"));
        assertTrue(store.pendingResults().isEmpty());
    }

    @Test
    public void corruptOrLegacySaveAutoHealsAndAlwaysOpensSudokuHome() {
        store.save(new GameSession("sudoku", "guest", "corrupt", "easy", 0, "{}", false));
        Intent corruptResume = GameContract.putRequest(new Intent(context, SudokuActivity.class),
                new GameLaunchRequest("sudoku", "guest", LaunchMode.RESUME, "corrupt", "easy"));
        try (ActivityController<SudokuActivity> controller = Robolectric.buildActivity(SudokuActivity.class, corruptResume).setup()) {
            assertFalse(controller.get().isFinishing());
            assertNotNull(controller.get().findViewById(R.id.bottomNav));
            assertNull(store.load("sudoku", "guest"));
        }
    }

    @Test
    public void legacySavedSessionWithOldDifficultyIdResumesIntoFullSudokuHomeCleanly() {
        com.puzzlehub.sudoku.domain.Puzzle puzzle = new com.puzzlehub.sudoku.domain.SudokuGenerator(new java.util.Random(7))
                .generatePuzzle(com.puzzlehub.sudoku.domain.Difficulty.EASY);
        com.puzzlehub.sudoku.domain.GameSession legacy = new com.puzzlehub.sudoku.domain.GameSession(puzzle, "guest");
        store.save(new GameSession("sudoku", "guest", legacy.id(), "easy", 5000L,
                com.puzzlehub.sudoku.data.local.SessionCodec.encode(legacy), false));

        Intent resume = GameContract.putRequest(new Intent(context, SudokuActivity.class),
                new GameLaunchRequest("sudoku", "guest", LaunchMode.RESUME, legacy.id(), "easy"));
        try (ActivityController<SudokuActivity> controller = Robolectric.buildActivity(SudokuActivity.class, resume).setup()) {
            assertFalse(controller.get().isFinishing());
            assertNotNull(controller.get().findViewById(R.id.bottomNav));
            controller.get().getOnBackPressedDispatcher().onBackPressed();
            GameExitResponse response = GameContract.readResponse(Shadows.shadowOf(controller.get()).getResultIntent());
            assertEquals(ExitReason.SAVED_FOR_LATER, response.reason);
            assertEquals(legacy.id(), response.sessionId);
        }
    }
}
