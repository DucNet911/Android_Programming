package com.team.tetris;

import com.example.arcade.core.GameContract;
import com.example.arcade.core.GameDefinition;
import com.example.arcade.core.GameSessionStore;
import com.team.tetris.ui.TetrisMenuActivity;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk = 28)
public class TetrisPluginTest {
    @Test public void definitionDescribesRealGameWithoutCreatingSession() {
        TetrisPlugin plugin = new TetrisPlugin();
        GameDefinition definition = plugin.getDefinition(RuntimeEnvironment.getApplication());
        assertEquals(GameContract.VERSION, plugin.getContractVersion());
        assertEquals("tetris", definition.id); assertEquals("Tetris", definition.title);
        assertEquals(TetrisMenuActivity.class, definition.activity);
        assertEquals(GameDefinition.Ranking.HIGH_SCORE, definition.ranking);
        assertEquals(1, definition.difficultyCount()); assertEquals("classic", definition.difficultyId(0));
        GameSessionStore store = new GameSessionStore(RuntimeEnvironment.getApplication());
        assertNull(store.load("tetris", "guest")); assertTrue(store.pendingResults().isEmpty());
    }
}
