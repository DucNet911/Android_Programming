package com.team.game2048;

import static org.junit.Assert.*;

import com.team.game2048.data.SnapshotCodec;
import com.team.game2048.engine.Game2048Engine;
import org.junit.Test;

public class SnapshotCodecTest {
  @Test
  public void roundTripPreservesStateAndFutureSpawns() {
    Game2048Engine a = new Game2048Engine(345);
    a.move(Game2048Engine.Direction.LEFT);
    Game2048Engine b = SnapshotCodec.decode(SnapshotCodec.encode(a));
    assertArrayEquals(a.board(), b.board());
    assertEquals(a.score(), b.score());
    assertEquals(a.hasWon(), b.hasWon());
    for (int i = 0; i < 100; i++) {
      a.move(Game2048Engine.Direction.values()[i % 4]);
      b.move(Game2048Engine.Direction.values()[i % 4]);
      assertArrayEquals(a.board(), b.board());
    }
  }

  @Test
  public void keepsWinFlag() {
    long[] b = new long[16];
    b[0] = 2048;
    Game2048Engine e = new Game2048Engine(b, 2048, true, 2);
    assertTrue(SnapshotCodec.decode(SnapshotCodec.encode(e)).hasWon());
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsUnknownVersion() {
    SnapshotCodec.decode(
        SnapshotCodec.encode(new Game2048Engine(2)).replace("\"schema\":1", "\"schema\":2"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsMissingFields() {
    SnapshotCodec.decode("{}");
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsMalformedJson() {
    SnapshotCodec.decode("not-json");
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsFractionalScore() {
    SnapshotCodec.decode(
        SnapshotCodec.encode(new Game2048Engine(2)).replace("\"score\":0", "\"score\":0.5"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsBadBoard() {
    SnapshotCodec.decode(
        "{\"schema\":1,\"board\":[2],\"score\":0,\"won\":false,\"randomState\":1}");
  }
}
