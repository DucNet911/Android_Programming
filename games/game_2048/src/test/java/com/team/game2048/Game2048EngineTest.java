package com.team.game2048;

import static org.junit.Assert.*;

import com.team.game2048.engine.Game2048Engine;
import com.team.game2048.engine.Game2048Engine.Direction;
import java.util.Arrays;
import java.util.Random;
import org.junit.Test;

public class Game2048EngineTest {
  private Game2048Engine row(long... values) {
    long[] b = new long[16];
    System.arraycopy(values, 0, b, 0, values.length);
    return new Game2048Engine(b, 0, Arrays.stream(b).anyMatch(x -> x >= 2048), 12345);
  }

  private long sum(long[] b) {
    long s = 0;
    for (long v : b) s += v;
    return s;
  }

  @Test
  public void startsWithTwoTiles() {
    for (int seed = 0; seed < 100; seed++) {
      Game2048Engine e = new Game2048Engine(seed);
      assertEquals(2, Arrays.stream(e.board()).filter(v -> v != 0).count());
      assertEquals(0, e.score());
    }
  }

  @Test
  public void fourTwosBecomeTwoFours() {
    Game2048Engine e = row(2, 2, 2, 2);
    Game2048Engine.Move m = e.move(Direction.LEFT);
    assertEquals(4, e.board()[0]);
    assertEquals(4, e.board()[1]);
    assertEquals(8, e.score());
    assertEquals(2, m.merged.size());
    assertEquals(4, m.motions.size());
  }

  @Test
  public void noDoubleMerge() {
    Game2048Engine e = row(2, 2, 4, 0);
    e.move(Direction.LEFT);
    assertEquals(4, e.board()[0]);
    assertEquals(4, e.board()[1]);
    assertEquals(4, e.score());
  }

  @Test
  public void gapsDoNotPreventMerging() {
    Game2048Engine e = row(2, 0, 2, 0);
    e.move(Direction.LEFT);
    assertEquals(4, e.board()[0]);
    assertEquals(4, e.score());
  }

  @Test
  public void rightUsesRightmostPair() {
    Game2048Engine e = row(2, 2, 2, 0);
    e.move(Direction.RIGHT);
    assertEquals(4, e.board()[3]);
    assertEquals(2, e.board()[2]);
  }

  @Test
  public void verticalDirections() {
    for (Direction d : new Direction[] {Direction.UP, Direction.DOWN}) {
      long[] b = new long[16];
      b[0] = 2;
      b[8] = 2;
      Game2048Engine e = new Game2048Engine(b, 0, false, 1);
      e.move(d);
      assertEquals(4, e.board()[d == Direction.UP ? 0 : 12]);
      assertEquals(4, e.score());
    }
  }

  @Test
  public void invalidMoveDoesNotSpawnOrConsumeRandomness() {
    Game2048Engine e = row(2, 4);
    long[] before = e.board();
    long rng = e.randomState();
    assertFalse(e.move(Direction.LEFT).changed);
    assertArrayEquals(before, e.board());
    assertEquals(rng, e.randomState());
    assertEquals(0, e.score());
  }

  @Test
  public void winOnlyCelebratesOnceAndPlayContinues() {
    Game2048Engine e = row(1024, 1024);
    assertTrue(e.move(Direction.LEFT).firstWin);
    assertTrue(e.hasWon());
    assertTrue(e.move(Direction.RIGHT).changed);
    assertFalse(e.move(Direction.LEFT).firstWin);
  }

  @Test
  public void gameOverRequiresNoEmptyOrEqualNeighbours() {
    long[] b = {2, 4, 2, 4, 4, 2, 4, 2, 2, 4, 2, 4, 4, 2, 4, 2};
    assertTrue(new Game2048Engine(b, 0, false, 1).isGameOver());
    b[1] = 2;
    assertFalse(new Game2048Engine(b, 0, false, 1).isGameOver());
    b[1] = 0;
    assertFalse(new Game2048Engine(b, 0, false, 1).isGameOver());
  }

  @Test
  public void boardIsDefensiveCopy() {
    Game2048Engine e = row(2);
    long[] b = e.board();
    b[0] = 99;
    assertEquals(2, e.board()[0]);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsInvalidTile() {
    row(3);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsInconsistentWin() {
    long[] b = new long[16];
    b[0] = 2048;
    new Game2048Engine(b, 0, false, 1);
  }

  @Test
  public void seededGamesAreReproducible() {
    Game2048Engine a = new Game2048Engine(91), b = new Game2048Engine(91);
    for (int i = 0; i < 300; i++) {
      Direction d = Direction.values()[i % 4];
      a.move(d);
      b.move(d);
      assertArrayEquals(a.board(), b.board());
      assertEquals(a.score(), b.score());
    }
  }

  @Test
  public void randomizedMoveInvariants() {
    Random r = new Random(734);
    for (int game = 0; game < 100; game++) {
      Game2048Engine e = new Game2048Engine(r.nextLong());
      for (int turn = 0; turn < 500; turn++) {
        long total = sum(e.board()), score = e.score();
        Game2048Engine.Move m = e.move(Direction.values()[r.nextInt(4)]);
        if (m.changed) {
          long added = sum(e.board()) - total;
          assertTrue(added == 2 || added == 4);
          assertTrue(m.spawned >= 0);
        } else {
          assertEquals(total, sum(e.board()));
          assertEquals(-1, m.spawned);
        }
        assertTrue(e.score() >= score);
        for (long v : e.board()) assertTrue(v == 0 || (v >= 2 && (v & (v - 1)) == 0));
      }
    }
  }
}
