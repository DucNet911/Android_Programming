package com.team.game2048.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Android-independent rules. A move exposes every source tile for the renderer. */
public final class Game2048Engine {
  public enum Direction {
    LEFT,
    UP,
    DOWN,
    RIGHT
  }

  public static final class Motion {
    public final int from, to;
    public final long value;

    Motion(int from, int to, long value) {
      this.from = from;
      this.to = to;
      this.value = value;
    }
  }

  public static final class Move {
    public final boolean changed, firstWin;
    public final List<Motion> motions;
    public final List<Integer> merged;
    public final int spawned;

    Move(
        boolean changed,
        boolean firstWin,
        List<Motion> motions,
        List<Integer> merged,
        int spawned) {
      this.changed = changed;
      this.firstWin = firstWin;
      this.motions = Collections.unmodifiableList(motions);
      this.merged = Collections.unmodifiableList(merged);
      this.spawned = spawned;
    }
  }

  private long[] cells;
  private long score, randomState;
  private boolean won;

  public Game2048Engine(long seed) {
    cells = new long[16];
    randomState = seed == 0 ? 1 : seed;
    spawn();
    spawn();
  }

  public Game2048Engine(long[] cells, long score, boolean won, long randomState) {
    if (cells == null || cells.length != 16 || score < 0 || randomState == 0)
      throw new IllegalArgumentException("Invalid state");
    boolean hasTarget = false;
    for (long value : cells) {
      if (value < 0 || (value != 0 && (value < 2 || (value & (value - 1)) != 0)))
        throw new IllegalArgumentException("Invalid tile");
      hasTarget |= value >= 2048;
    }
    if (hasTarget != won) throw new IllegalArgumentException("Inconsistent win flag");
    this.cells = cells.clone();
    this.score = score;
    this.won = won;
    this.randomState = randomState;
  }

  public long[] board() {
    return cells.clone();
  }

  public long score() {
    return score;
  }

  public boolean hasWon() {
    return won;
  }

  public long randomState() {
    return randomState;
  }

  private int next(int bound) {
    long x = randomState;
    x ^= x << 13;
    x ^= x >>> 7;
    x ^= x << 17;
    randomState = x;
    return (int) ((x >>> 1) % bound);
  }

  private static long addExact(long a, long b) {
    if (a > Long.MAX_VALUE - b) throw new ArithmeticException("Score overflow");
    return a + b;
  }

  private int spawn() {
    int[] empty = new int[16];
    int count = 0;
    for (int i = 0; i < 16; i++) if (cells[i] == 0) empty[count++] = i;
    if (count == 0) return -1;
    int at = empty[next(count)];
    cells[at] = next(10) == 0 ? 4 : 2;
    return at;
  }

  private static int index(Direction d, int line, int position) {
    switch (d) {
      case LEFT:
        return line * 4 + position;
      case RIGHT:
        return line * 4 + 3 - position;
      case UP:
        return position * 4 + line;
      default:
        return (3 - position) * 4 + line;
    }
  }

  private static boolean canMerge(long a, long b) {
    return a == b && a > 0 && a <= Long.MAX_VALUE / 2;
  }

  public Move move(Direction direction) {
    if (direction == null) throw new IllegalArgumentException("Missing direction");
    long[] result = new long[16];
    long gained = 0;
    List<Motion> motions = new ArrayList<>();
    List<Integer> merged = new ArrayList<>();
    for (int line = 0; line < 4; line++) {
      int[] sources = new int[4];
      int n = 0;
      for (int p = 0; p < 4; p++) {
        int i = index(direction, line, p);
        if (cells[i] != 0) sources[n++] = i;
      }
      int target = 0;
      for (int p = 0; p < n; p++) {
        int source = sources[p], dest = index(direction, line, target++);
        long value = cells[source];
        motions.add(new Motion(source, dest, value));
        if (p + 1 < n && canMerge(value, cells[sources[p + 1]])) {
          motions.add(new Motion(sources[++p], dest, value));
          value *= 2;
          gained = addExact(gained, value);
          merged.add(dest);
        }
        result[dest] = value;
      }
    }
    if (Arrays.equals(cells, result)) return new Move(false, false, motions, merged, -1);
    long newScore = addExact(score, gained);
    boolean before = won;
    cells = result;
    score = newScore;
    for (long value : cells) if (value >= 2048) won = true;
    return new Move(true, !before && won, motions, merged, spawn());
  }

  public boolean isGameOver() {
    for (int i = 0; i < 16; i++) {
      if (cells[i] == 0) return false;
      if (i % 4 < 3 && canMerge(cells[i], cells[i + 1])) return false;
      if (i < 12 && canMerge(cells[i], cells[i + 4])) return false;
    }
    return true;
  }
}
