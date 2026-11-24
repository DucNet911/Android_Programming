package com.team.colortrail.engine;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ColorTrailEngineTest {
    @Test
    public void snapshotRestoresBoardPathAndNextSuggestion() {
        ColorTrailEngine original = new ColorTrailEngine(1911);
        assertTrue(original.move(original.suggestMove()));
        assertTrue(original.move(original.suggestMove()));
        ColorTrailEngine restored = ColorTrailEngine.restore(original.seed(), original.path());
        assertEquals(original.position(), restored.position());
        assertEquals(original.energy(), restored.energy());
        assertEquals(original.collected(), restored.collected());
        assertEquals(original.suggestMove(), restored.suggestMove());
        assertArrayEquals(original.path(), restored.path());
    }

    @Test
    public void jumpAndReturnAreRejected() {
        ColorTrailEngine game = new ColorTrailEngine(1911);
        assertFalse(game.move(ColorTrailEngine.EXIT));
        assertThrows(IllegalArgumentException.class, () -> ColorTrailEngine.restore(1911, new int[]{ColorTrailEngine.EXIT}));
        assertTrue(game.move(game.suggestMove()));
        assertFalse(game.move(0));
    }
}
