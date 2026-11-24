package com.team.bomber.engine;

import org.junit.Test;
import static org.junit.Assert.*;

public final class BomberEngineTest {
    @Test
    public void bombCountsDownAndHitsOnTheThirdTurn() {
        BomberEngine arena = new BomberEngine(4L);
        assertTrue(arena.place());
        assertTrue(arena.waitTurn());
        assertTrue(arena.waitTurn());
        assertFalse(arena.ended());
        assertTrue(arena.waitTurn());
        assertTrue(arena.ended());
        assertFalse(arena.won());
        assertTrue(arena.flashed(BomberEngine.START));
    }

    @Test
    public void doorNeedsCrystalsBeforeAwardingAWin() {
        BomberEngine initial = new BomberEngine(7L);
        int[] tiles = new int[BomberEngine.AREA];
        int[] bombs = new int[BomberEngine.AREA];
        for (int index = 0; index < tiles.length; index++) {
            tiles[index] = initial.cell(index);
        }
        tiles[69] = BomberEngine.FLOOR;
        BomberEngine locked = new BomberEngine(tiles, bombs, 69, 4, 2, 0);
        assertTrue(locked.move(BomberEngine.DOOR));
        assertFalse(locked.won());
        BomberEngine ready = new BomberEngine(tiles, bombs, 69, 4, 3, 0);
        assertTrue(ready.move(BomberEngine.DOOR));
        assertTrue(ready.won());
        assertTrue(ready.score() > 200);
    }
}
