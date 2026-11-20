package com.team.tetris;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import com.example.arcade.core.*;
import com.team.tetris.data.TetrisSnapshot;
import com.team.tetris.engine.TetrisLogic;
import com.team.tetris.ui.TetrisActivity;
import com.team.tetris.ui.TetrisMenuActivity;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk = 28) @LooperMode(LooperMode.Mode.PAUSED)
public class TetrisActivityTest {
    private Context context;
    private GameSessionStore store;
    @Before public void setup() {
        // Advance gameplay time explicitly; continuous logo animations must not drain the test looper.
        org.robolectric.shadows.ShadowChoreographer.setPaused(true);
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("arcade_sessions_v1", 0).edit().clear().commit();
        context.getSharedPreferences("tetris_settings", 0).edit().clear().commit();
        store = new GameSessionStore(context);
    }

    private Intent launch(LaunchMode mode, String session) {
        return GameContract.putRequest(new Intent(context, TetrisActivity.class),
                new GameLaunchRequest("tetris", "guest", mode, session, "classic"));
    }

    private ActivityController<TetrisActivity> start(Intent intent) {
        return Robolectric.buildActivity(TetrisActivity.class, intent).setup();
    }

    private GameExitResponse response(TetrisActivity activity) {
        return GameContract.readResponse(Shadows.shadowOf(activity).getResultIntent());
    }

    private ActivityController<TetrisActivity> recreate(ActivityController<TetrisActivity> controller) {
        Intent intent = controller.get().getIntent();
        Bundle state = new Bundle();
        controller.pause().saveInstanceState(state).stop().destroy();
        return Robolectric.buildActivity(TetrisActivity.class, intent).setup(state);
    }

    @Test public void newGameCreatesRealCheckpoint() {
        try (ActivityController<TetrisActivity> controller = start(launch(LaunchMode.NEW_GAME, null))) {
            GameSession session = store.load("tetris", "guest");
            assertNotNull(session); assertFalse(session.demo);
            assertEquals("classic", session.difficultyId);
            assertFalse(TetrisSnapshot.decode(session.snapshot).isGameOver());
            assertTrue(store.pendingResults().isEmpty());
        }
    }

    @Test public void saveExitAndResumeKeepIdentityAndBoard() {
        GameSession saved;
        try (ActivityController<TetrisActivity> controller = start(launch(LaunchMode.NEW_GAME, null))) {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS);
            controller.get().getOnBackPressedDispatcher().onBackPressed();
            saved = store.load("tetris", "guest");
            assertEquals(ExitReason.SAVED_FOR_LATER, response(controller.get()).reason);
            assertTrue(store.pendingResults().isEmpty());
        }
        try (ActivityController<TetrisActivity> controller = start(launch(LaunchMode.RESUME, saved.sessionId))) {
            GameSession resumed = store.load("tetris", "guest");
            assertEquals(saved.sessionId, resumed.sessionId); assertEquals(saved.snapshot, resumed.snapshot);
            assertEquals(saved.activeDurationMs, resumed.activeDurationMs);
        }
    }

    @Test public void backgroundAndPauseDoNotAdvanceBoardOrDuration() {
        try (ActivityController<TetrisActivity> controller = start(launch(LaunchMode.NEW_GAME, null))) {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(500, TimeUnit.MILLISECONDS);
            controller.pause();
            GameSession before = store.load("tetris", "guest");
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(30, TimeUnit.SECONDS);
            assertEquals(before.snapshot, store.load("tetris", "guest").snapshot);
            controller.resume();
            assertEquals(View.VISIBLE, controller.get().findViewById(R.id.tetris_img_paused_menu).getVisibility());
            controller.get().findViewById(R.id.tetris_btn_resume).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(500, TimeUnit.MILLISECONDS);
            assertEquals(before.activeDurationMs + 500, store.load("tetris", "guest").activeDurationMs);
        }
    }

    @Test public void recreationKeepsSameSession() {
        ActivityController<TetrisActivity> controller = start(launch(LaunchMode.NEW_GAME, null));
        try {
            String id = store.load("tetris", "guest").sessionId;
            controller = recreate(controller);
            assertEquals(id, store.load("tetris", "guest").sessionId);
            assertTrue(store.pendingResults().isEmpty());
        } finally { controller.close(); }
    }

    @Test public void missingResumeDoesNotCreateNewSession() {
        try (ActivityController<TetrisActivity> controller = start(launch(LaunchMode.RESUME, "missing"))) {
            assertEquals(GameErrorCode.SESSION_NOT_FOUND, response(controller.get()).errorCode);
            assertNull(store.load("tetris", "guest"));
        }
    }

    @Test public void newGameCannotOverwriteAnExistingSession() {
        store.save(new GameSession("tetris", "guest", "existing", "classic", 0,
                TetrisSnapshot.encode(new TetrisLogic()), false));
        try (ActivityController<TetrisActivity> controller = start(launch(LaunchMode.NEW_GAME, null))) {
            assertEquals(GameErrorCode.SESSION_EXISTS, response(controller.get()).errorCode);
            assertEquals("existing", store.load("tetris", "guest").sessionId);
        }
    }

    @Test public void corruptSnapshotIsReportedAndPreservedForRecovery() {
        store.save(new GameSession("tetris", "guest", "broken", "classic", 0, "{}", false));
        try (ActivityController<TetrisActivity> controller = start(launch(LaunchMode.RESUME, "broken"))) {
            assertEquals(GameErrorCode.CORRUPT_SAVE, response(controller.get()).errorCode);
            assertEquals("{}", store.load("tetris", "guest").snapshot);
        }
    }

    @Test public void invalidDifficultyAndVersionAreReportedBeforeSaving() {
        Intent bad = GameContract.putRequest(new Intent(context, TetrisActivity.class),
                new GameLaunchRequest("tetris", "guest", LaunchMode.NEW_GAME, null, "unsupported"));
        try (ActivityController<TetrisActivity> controller = start(bad)) {
            assertEquals(GameErrorCode.INVALID_REQUEST, response(controller.get()).errorCode);
        }
        Intent version = launch(LaunchMode.NEW_GAME, null);
        version.getBundleExtra(GameContract.EXTRA_REQUEST).putInt("version", 99);
        try (ActivityController<TetrisActivity> controller = start(version)) {
            assertEquals(GameErrorCode.UNSUPPORTED_VERSION, response(controller.get()).errorCode);
        }
        assertNull(store.load("tetris", "guest"));
    }

    private void saveGameOver() {
        TetrisLogic.State s = new TetrisLogic().exportState();
        TetrisLogic lost = TetrisLogic.fromState(new TetrisLogic.State(s.board, s.currentPiece,
                s.nextPiece, s.currentX, s.currentY, 35, true, s.bag, s.bagIndex, s.randomState));
        store.save(new GameSession("tetris", "guest", "lost-session", "classic", 1200,
                TetrisSnapshot.encode(lost), false));
    }

    @Test public void gameOverPersistsOneRealLostResultAcrossRecreation() {
        saveGameOver();
        ActivityController<TetrisActivity> controller = start(launch(LaunchMode.RESUME, "lost-session"));
        try {
            assertNull(store.load("tetris", "guest"));
            assertEquals(1, store.pendingResults().size());
            GameResult result = store.pendingResults().get(0);
            assertEquals(GameOutcome.LOST, result.outcome); assertEquals(Long.valueOf(35), result.score);
            assertEquals(1200, result.activeDurationMs); assertFalse(result.demo);
            controller = recreate(controller);
            assertNull(store.load("tetris", "guest")); assertEquals(1, store.pendingResults().size());
            controller.get().findViewById(R.id.tetris_btn_quit_game_over).performClick();
            assertEquals(ExitReason.FINISHED, response(controller.get()).reason);
            assertEquals("lost-session", response(controller.get()).sessionId);
        } finally { controller.close(); }
    }

    @Test public void replayKeepsFinishedOutboxAndCreatesFreshSession() {
        saveGameOver();
        try (ActivityController<TetrisActivity> controller = start(launch(LaunchMode.RESUME, "lost-session"))) {
            controller.get().findViewById(R.id.tetris_btn_replay).performClick();
            GameSession replay = store.load("tetris", "guest");
            assertNotNull(replay); assertNotEquals("lost-session", replay.sessionId);
            assertEquals(1, store.pendingResults().size());
            controller.get().getOnBackPressedDispatcher().onBackPressed();
            assertEquals(ExitReason.SAVED_FOR_LATER, response(controller.get()).reason);
            assertEquals(replay.sessionId, response(controller.get()).sessionId);
        }
    }

    @Test public void menuBackBeforePlayingIsCancelledWithoutSession() {
        try (ActivityController<TetrisMenuActivity> controller = Robolectric.buildActivity(
                TetrisMenuActivity.class, launch(LaunchMode.NEW_GAME, null)).setup()) {
            controller.get().getOnBackPressedDispatcher().onBackPressed();
            GameExitResponse response = GameContract.readResponse(Shadows.shadowOf(controller.get()).getResultIntent());
            assertEquals(ExitReason.CANCELLED, response.reason);
            assertNull(store.load("tetris", "guest"));
        }
    }

    @Test public void resumeMenuForwardsTheExactRequestToGame() {
        try (ActivityController<TetrisMenuActivity> controller = Robolectric.buildActivity(
                TetrisMenuActivity.class, launch(LaunchMode.RESUME, "saved-session")).setup()) {
            Intent started = Shadows.shadowOf(controller.get()).getNextStartedActivityForResult().intent;
            GameLaunchRequest forwarded = GameContract.readRequest(started);
            assertEquals(LaunchMode.RESUME, forwarded.launchMode);
            assertEquals("saved-session", forwarded.sessionId); assertEquals("guest", forwarded.playerId);
            assertEquals(TetrisActivity.class.getName(), started.getComponent().getClassName());
        }
    }
}
