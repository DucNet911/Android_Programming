package com.example.arcade.core;
import android.app.Activity;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class DefinitionTest {
    private GameDefinition definition(String id, List<GameDifficulty> modes) {
        return new GameDefinition(id,"Title","Description","X",0xff000000,Activity.class,GameDefinition.Ranking.NONE,modes);
    }
    @Test public void arbitraryNewGameDoesNotNeedCoreChanges() {
        GameDefinition g=definition("new_game",Collections.singletonList(new GameDifficulty("default","Default")));
        assertEquals("new_game",g.id); assertEquals("Default",g.difficultyLabel("default"));
    }
    @Test public void modeListIsCopied() {
        List<GameDifficulty> modes=new ArrayList<>();modes.add(new GameDifficulty("default","Default"));
        GameDefinition g=definition("new_game",modes);modes.clear();assertEquals(1,g.difficultyCount());
    }
    @Test(expected=IllegalArgumentException.class) public void rejectDuplicateModes() {
        definition("new_game",Arrays.asList(new GameDifficulty("easy","A"),new GameDifficulty("easy","B")));
    }
    @Test(expected=IllegalArgumentException.class) public void rejectEmptyModes() { definition("new_game",Collections.emptyList()); }
    @Test(expected=IllegalArgumentException.class) public void rejectInvalidId() { definition("Invalid ID",Collections.singletonList(new GameDifficulty("default","Default"))); }
}
