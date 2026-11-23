package com.team.game2048;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import com.example.arcade.core.*;
import com.team.game2048.data.SnapshotCodec;
import com.team.game2048.engine.Game2048Engine;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class Game2048ActivityTest {
  private Context context;
  private GameSessionStore store;

  @Before
  public void clear() {
    context = RuntimeEnvironment.getApplication();
    context.getSharedPreferences("arcade_sessions_v1", 0).edit().clear().commit();
    context.getSharedPreferences("g2048_records", 0).edit().clear().commit();
    store = new GameSessionStore(context);
  }

  private ActivityController<Game2048Activity> launch(
      LaunchMode mode, String session, String difficulty) {
    Intent intent =
        GameContract.putRequest(
            new Intent(context, Game2048Activity.class),
            new GameLaunchRequest("game_2048", "guest", mode, session, difficulty));
    return Robolectric.buildActivity(Game2048Activity.class, intent).setup();
  }

  @Test
  public void newGameCheckpointsAndExitOnlySaves() {
    ActivityController<Game2048Activity> c = launch(LaunchMode.NEW_GAME, null, null);
    GameSession s = store.load("game_2048", "guest");
    assertNotNull(s);
    assertFalse(s.demo);
    c.get().findViewById(R.id.g2048_exit).performClick();
    GameExitResponse response = GameContract.readResponse(shadowOf(c.get()).getResultIntent());
    assertEquals(ExitReason.SAVED_FOR_LATER, response.reason);
    assertEquals(s.sessionId, response.sessionId);
    assertTrue(store.pendingResults().isEmpty());
    c.pause().stop().destroy();
  }

  @Test
  public void recreationKeepsSessionAndBoard() {
    ActivityController<Game2048Activity> c = launch(LaunchMode.NEW_GAME, null, null);
    GameSession s = store.load("game_2048", "guest");
    Bundle state = new Bundle();
    Intent intent = c.get().getIntent();
    c.pause().saveInstanceState(state).stop().destroy();
    ActivityController<Game2048Activity> recreated =
        Robolectric.buildActivity(Game2048Activity.class, intent)
            .create(state)
            .start()
            .resume()
            .visible();
    GameSession restored = store.load("game_2048", "guest");
    assertEquals(s.sessionId, restored.sessionId);
    assertEquals(s.snapshot, restored.snapshot);
    recreated.pause().stop().destroy();
  }

  @Test
  public void resumeRestoresScore() {
    long[] board = new long[16];
    board[0] = 8;
    store.save(
        new GameSession(
            "game_2048",
            "guest",
            "session",
            "default",
            200,
            SnapshotCodec.encode(new Game2048Engine(board, 12, false, 1)),
            false));
    ActivityController<Game2048Activity> c = launch(LaunchMode.RESUME, "session", null);
    assertEquals(
        "12",
        ((android.widget.TextView) c.get().findViewById(R.id.g2048_score)).getText().toString());
    c.pause().stop().destroy();
    assertEquals("session", store.load("game_2048", "guest").sessionId);
  }

  @Test
  public void missingResumeReturnsError() {
    ActivityController<Game2048Activity> c = launch(LaunchMode.RESUME, "missing", null);
    assertEquals(
        GameErrorCode.SESSION_NOT_FOUND,
        GameContract.readResponse(shadowOf(c.get()).getResultIntent()).errorCode);
  }

  @Test
  public void duplicateNewDoesNotOverwrite() {
    ActivityController<Game2048Activity> c = launch(LaunchMode.NEW_GAME, null, null);
    GameSession s = store.load("game_2048", "guest");
    c.pause().stop().destroy();
    ActivityController<Game2048Activity> second = launch(LaunchMode.NEW_GAME, null, null);
    assertEquals(
        GameErrorCode.SESSION_EXISTS,
        GameContract.readResponse(shadowOf(second.get()).getResultIntent()).errorCode);
    assertEquals(s.sessionId, store.load("game_2048", "guest").sessionId);
  }

  @Test
  public void corruptSnapshotIsPreserved() {
    store.save(new GameSession("game_2048", "guest", "broken", "default", 0, "{}", false));
    ActivityController<Game2048Activity> c = launch(LaunchMode.RESUME, "broken", null);
    assertEquals(
        GameErrorCode.CORRUPT_SAVE,
        GameContract.readResponse(shadowOf(c.get()).getResultIntent()).errorCode);
    assertNotNull(store.load("game_2048", "guest"));
  }

  @Test
  public void terminalWinningBoardFinishesOnce() {
    long[] board = {2048, 4, 2, 4, 4, 2, 4, 2, 2, 4, 2, 4, 4, 2, 4, 2};
    store.save(
        new GameSession(
            "game_2048",
            "guest",
            "won",
            "default",
            100,
            SnapshotCodec.encode(new Game2048Engine(board, 4096, true, 1)),
            false));
    ActivityController<Game2048Activity> c = launch(LaunchMode.RESUME, "won", null);
    assertNull(store.load("game_2048", "guest"));
    assertEquals(1, store.pendingResults().size());
    assertEquals(GameOutcome.WON, store.pendingResults().get(0).outcome);
    c.get().findViewById(R.id.g2048_exit).performClick();
    c.pause().stop().destroy();
    assertEquals(1, store.pendingResults().size());
  }

  @Test
  public void reachingTargetKeepsSessionActive() {
    long[] cells = new long[16];
    cells[0] = 1024;
    cells[1] = 1024;
    store.save(
        new GameSession(
            "game_2048",
            "guest",
            "target",
            "default",
            0,
            SnapshotCodec.encode(new Game2048Engine(cells, 0, false, 1)),
            false));
    ActivityController<Game2048Activity> c = launch(LaunchMode.RESUME, "target", null);
    c.get().findViewById(R.id.g2048_left).performClick();
    assertTrue(SnapshotCodec.decode(store.load("game_2048", "guest").snapshot).hasWon());
    assertEquals("target", store.load("game_2048", "guest").sessionId);
    assertTrue(store.pendingResults().isEmpty());
    assertNull(org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog());
    c.pause().stop().destroy();
  }

  @Test
  public void backgroundTimeIsExcluded() {
    ActivityController<Game2048Activity> c = launch(LaunchMode.NEW_GAME, null, null);
    org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(2));
    c.pause();
    long before = store.load("game_2048", "guest").activeDurationMs;
    org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMinutes(5));
    c.resume();
    c.get().findViewById(R.id.g2048_exit).performClick();
    assertEquals(before, store.load("game_2048", "guest").activeDurationMs);
    c.pause().stop().destroy();
  }
}
