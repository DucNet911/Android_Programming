package com.team.game2048;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.example.arcade.core.*;
import com.team.game2048.data.SnapshotCodec;
import com.team.game2048.engine.Game2048Engine;
import com.team.game2048.ui.BoardView;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.UUID;

/** Owns the platform contract; rules and animation never write platform data. */
public final class Game2048Activity extends AppCompatActivity {
  private GameLaunchRequest request;
  private GameSessionStore store;
  private Game2048Engine engine;
  private String sessionId;
  private long duration, started = -1, best;
  private GameResult completed;
  private BoardView board;
  private TextView scoreView, bestView, status;
  private Button exitButton;
  private SharedPreferences prefs;
  private boolean resumed, blocked, leaving;
  private AlertDialog dialog;
  private Runnable retry;

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    try {
      request = GameContract.readRequest(getIntent());
    } catch (RuntimeException e) {
      // Recover only valid identity fields so the platform can route the error.
      Bundle payload =
          getIntent() == null ? null : getIntent().getBundleExtra(GameContract.EXTRA_REQUEST);
      String game =
          payload == null ? Game2048Plugin.ID : payload.getString("game", Game2048Plugin.ID);
      String player = payload == null ? "guest" : payload.getString("player", "guest");
      if (game == null || game.trim().isEmpty() || game.length() > 128) game = Game2048Plugin.ID;
      if (player == null || player.trim().isEmpty() || player.length() > 128) player = "guest";
      GameErrorCode code =
          payload != null && payload.getInt("version", -1) != GameContract.VERSION
              ? GameErrorCode.UNSUPPORTED_VERSION
              : GameErrorCode.INVALID_REQUEST;
      setResult(RESULT_OK, GameContract.responseIntent(GameExitResponse.error(game, player, code)));
      finish();
      return;
    }
    if (!Game2048Plugin.ID.equals(request.gameId)
        || Game2048Plugin.boardSize(request.difficultyId) == 0) {
      fail(GameErrorCode.INVALID_REQUEST);
      return;
    }
    store = new GameSessionStore(this);
    prefs = getSharedPreferences("g2048_records", MODE_PRIVATE);
    try {
      best = prefs.getLong(request.playerId, 0);
    } catch (ClassCastException e) {
      best = 0;
    }
    GameSession saved;
    try {
      saved = store.load(Game2048Plugin.ID, request.playerId);
    } catch (RuntimeException e) {
      fail(GameErrorCode.CORRUPT_SAVE);
      return;
    }
    if (state != null && state.containsKey("session")) {
      String expected = state.getString("session");
      // A result may already have been acknowledged by the platform after process death.
      if (state.getBoolean("completed")) {
        try {
          sessionId = expected;
          engine = SnapshotCodec.decode(state.getString("snapshot"));
          duration = state.getLong("duration");
          completed =
              new GameResult(
                  Game2048Plugin.ID,
                  request.playerId,
                  sessionId,
                  mode(),
                  GameOutcome.valueOf(state.getString("outcome")),
                  engine.score(),
                  duration,
                  state.getLong("finishedAt"),
                  false);
        } catch (RuntimeException e) {
          fail(GameErrorCode.CORRUPT_SAVE);
          return;
        }
      } else if (saved != null && saved.sessionId.equals(expected)) {
        if (!restore(saved)) return;
        duration = Math.max(duration, state.getLong("duration"));
      } else {
        // Covers finish committed just before the process was killed.
        try {
          for (GameResult result : store.pendingResults())
            if (result.gameId.equals(Game2048Plugin.ID)
                && result.playerId.equals(request.playerId)
                && result.sessionId.equals(expected)) completed = result;
          if (completed != null) {
            setResult(RESULT_OK, GameContract.responseIntent(GameExitResponse.finished(completed)));
            finish();
            return;
          }
        } catch (RuntimeException e) {
          fail(GameErrorCode.STORAGE_ERROR);
          return;
        }
        fail(GameErrorCode.SESSION_NOT_FOUND);
        return;
      }
    } else if (request.launchMode == LaunchMode.RESUME) {
      if (saved == null || !saved.sessionId.equals(request.sessionId)) {
        fail(GameErrorCode.SESSION_NOT_FOUND);
        return;
      }
      if (!restore(saved)) return;
    } else {
      if (saved != null) {
        fail(GameErrorCode.SESSION_EXISTS);
        return;
      }
      createSession();
      try {
        checkpoint();
      } catch (RuntimeException e) {
        fail(GameErrorCode.STORAGE_ERROR);
        return;
      }
    }
    setContentView(R.layout.g2048_activity);
    View root = findViewById(R.id.g2048_root);
    WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    WindowCompat.getInsetsController(getWindow(), root).setAppearanceLightStatusBars(true);
    WindowCompat.getInsetsController(getWindow(), root).setAppearanceLightNavigationBars(true);
    ViewCompat.setOnApplyWindowInsetsListener(
        root,
        (v, insets) -> {
          androidx.core.graphics.Insets i =
              insets.getInsets(
                  WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
          v.setPadding(i.left, i.top, i.right, i.bottom);
          return insets;
        });
    ViewCompat.requestApplyInsets(root);
    board = findViewById(R.id.g2048_board);
    scoreView = findViewById(R.id.g2048_score);
    bestView = findViewById(R.id.g2048_best);
    status = findViewById(R.id.g2048_status);
    exitButton = findViewById(R.id.g2048_exit);
    board.show(engine.board());
    board.setListener(this::move);
    findViewById(R.id.g2048_left).setOnClickListener(v -> move(Game2048Engine.Direction.LEFT));
    findViewById(R.id.g2048_right).setOnClickListener(v -> move(Game2048Engine.Direction.RIGHT));
    findViewById(R.id.g2048_up).setOnClickListener(v -> move(Game2048Engine.Direction.UP));
    findViewById(R.id.g2048_down).setOnClickListener(v -> move(Game2048Engine.Direction.DOWN));
    findViewById(R.id.g2048_help).setOnClickListener(v -> help());
    findViewById(R.id.g2048_new).setOnClickListener(v -> confirmRestart());
    exitButton.setOnClickListener(v -> exitGame());
    getOnBackPressedDispatcher()
        .addCallback(
            this,
            new OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                exitGame();
              }
            });
    render();
    if (completed == null && engine.isGameOver()) finishGame(false);
  }

  private boolean restore(GameSession saved) {
    if (!mode().equals(saved.difficultyId) || saved.demo) {
      fail(GameErrorCode.CORRUPT_SAVE);
      return false;
    }
    try {
      engine = SnapshotCodec.decode(saved.snapshot);
    } catch (RuntimeException e) {
      fail(GameErrorCode.CORRUPT_SAVE);
      return false;
    }
    sessionId = saved.sessionId;
    duration = saved.activeDurationMs;
    return true;
  }

  private void createSession() {
    sessionId = UUID.randomUUID().toString();
    engine = new Game2048Engine(System.nanoTime(), Game2048Plugin.boardSize(mode()));
    duration = 0;
    started = -1;
    completed = null;
  }

  private void fail(GameErrorCode code) {
    leaving = true;
    setResult(
        RESULT_OK,
        GameContract.responseIntent(
            GameExitResponse.error(request.gameId, request.playerId, code)));
    finish();
  }

  private long elapsed() {
    return duration + (started < 0 ? 0 : SystemClock.elapsedRealtime() - started);
  }

  private void pauseClock() {
    duration = elapsed();
    started = -1;
  }

  private void startClock() {
    if (resumed
        && !blocked
        && !leaving
        && completed == null
        && engine != null
        && !engine.isGameOver()
        && started < 0) started = SystemClock.elapsedRealtime();
  }

  private GameSession session() {
    return new GameSession(
        Game2048Plugin.ID,
        request.playerId,
        sessionId,
        mode(),
        elapsed(),
        SnapshotCodec.encode(engine),
        false);
  }

  private void checkpoint() {
    if (completed == null) store.save(session());
  }

  private void persistBest() {
    best = Math.max(best, engine.score());
    if (!prefs.edit().putLong(request.playerId, best).commit())
      throw new IllegalStateException("Best score write failed");
  }

  private String mode() {
    return request.difficultyId == null ? "default" : request.difficultyId;
  }

  private void move(Game2048Engine.Direction direction) {
    if (blocked || leaving || completed != null || board.isAnimating() || !resumed) return;
    Game2048Engine.Move movement = engine.move(direction);
    if (!movement.changed) return;
    board.animateMove(engine.board(), movement);
    render();
    if (movement.firstWin) {
      status.setText(R.string.g2048_win);
      board.celebrate();
    }
    if (engine.isGameOver()) {
      pauseClock();
      finishGame(false);
    } else {
      try {
        checkpoint();
        persistBest();
        render();
      } catch (RuntimeException e) {
        storageFailure(
            () -> {
              checkpoint();
              persistBest();
              render();
            });
      }
    }
  }

  private void render() {
    best = Math.max(best, engine.score());
    scoreView.setText(
        NumberFormat.getIntegerInstance(new Locale("vi", "VN")).format(engine.score()));
    bestView.setText(NumberFormat.getIntegerInstance(new Locale("vi", "VN")).format(best));
    if (completed != null || engine.isGameOver())
      status.setText(getString(R.string.g2048_over, engine.score()));
    else if (engine.hasWon()) status.setText(R.string.g2048_win);
    else status.setText("");
    exitButton.setText(completed == null ? R.string.g2048_exit : R.string.g2048_finished_exit);
  }

  private void commitFinish(boolean abandoning) {
    if (completed != null) return;
    pauseClock();
    checkpoint();
    persistBest();
    GameOutcome outcome =
        engine.hasWon() ? GameOutcome.WON : abandoning ? GameOutcome.ABANDONED : GameOutcome.LOST;
    GameResult result =
        new GameResult(
            Game2048Plugin.ID,
            request.playerId,
            sessionId,
            mode(),
            outcome,
            engine.score(),
            duration,
            System.currentTimeMillis(),
            false);
    store.finish(result);
    completed = result;
  }

  private void finishGame(boolean abandoning) {
    try {
      commitFinish(abandoning);
      render();
    } catch (RuntimeException e) {
      storageFailure(
          () -> {
            commitFinish(abandoning);
            render();
          });
    }
  }

  private void exitGame() {
    if (blocked || leaving || engine == null) return;
    pauseClock();
    board.settle();
    try {
      exitPersisted();
    } catch (RuntimeException e) {
      storageFailure(this::exitPersisted);
    }
  }

  private void exitPersisted() {
    if (completed == null && engine.isGameOver()) commitFinish(false);
    if (completed == null) {
      checkpoint();
      persistBest();
      setResult(RESULT_OK, GameContract.responseIntent(GameExitResponse.saved(session())));
    } else setResult(RESULT_OK, GameContract.responseIntent(GameExitResponse.finished(completed)));
    leaving = true;
    finish();
  }

  private void help() {
    if (blocked || leaving) return;
    pauseClock();
    blocked = true;
    board.settle();
    dialog =
        new AlertDialog.Builder(this)
            .setTitle(R.string.g2048_help)
            .setMessage(R.string.g2048_rules)
            .setPositiveButton(R.string.g2048_close, null)
            .create();
    dialog.setOnDismissListener(
        d -> {
          dialog = null;
          if (retry != null) {
            if (resumed) showStorageError();
          } else {
            blocked = false;
            startClock();
          }
        });
    dialog.show();
  }

  private void confirmRestart() {
    if (blocked || leaving) return;
    if (completed != null) {
      restart();
      return;
    }
    pauseClock();
    blocked = true;
    board.settle();
    dialog =
        new AlertDialog.Builder(this)
            .setTitle(R.string.g2048_restart_title)
            .setMessage(R.string.g2048_restart_message)
            .setNegativeButton(R.string.g2048_cancel, null)
            .setPositiveButton(
                R.string.g2048_new,
                (d, w) -> {
                  dialog.setOnDismissListener(null);
                  dialog = null;
                  blocked = false;
                  restart();
                })
            .create();
    dialog.setOnDismissListener(
        d -> {
          dialog = null;
          if (retry != null) {
            if (resumed) showStorageError();
          } else {
            blocked = false;
            startClock();
          }
        });
    dialog.show();
  }

  private void restart() {
    try {
      commitFinish(true);
    } catch (RuntimeException e) {
      storageFailure(this::restartOrThrow);
      return;
    }
    startNewPersisted();
  }

  private void restartOrThrow() {
    commitFinish(true);
    startNewPersisted();
  }

  private void startNewPersisted() {
    createSession();
    board.pauseEffects();
    board.show(engine.board());
    render();
    try {
      checkpoint();
      startClock();
    } catch (RuntimeException e) {
      storageFailure(
          () -> {
            checkpoint();
            startClock();
          });
    }
  }

  private void storageFailure(Runnable operation) {
    pauseClock();
    blocked = true;
    retry = operation;
    if (resumed) showStorageError();
  }

  private void showStorageError() {
    if (dialog != null || isFinishing()) return;
    dialog =
        new AlertDialog.Builder(this)
            .setMessage(R.string.g2048_storage)
            .setCancelable(false)
            .setPositiveButton(R.string.g2048_retry, null)
            .create();
    dialog.show();
    dialog
        .getButton(AlertDialog.BUTTON_POSITIVE)
        .setOnClickListener(
            v -> {
              Runnable action = retry;
              retry = null;
              dialog.dismiss();
              dialog = null;
              try {
                action.run();
                if (retry == null) {
                  blocked = false;
                  startClock();
                }
              } catch (RuntimeException e) {
                storageFailure(action);
              }
            });
  }

  @Override
  protected void onResume() {
    super.onResume();
    resumed = true;
    if (retry != null) showStorageError();
    else startClock();
  }

  @Override
  protected void onPause() {
    resumed = false;
    pauseClock();
    if (board != null) board.pauseEffects();
    if (engine != null && completed == null && !leaving) {
      try {
        checkpoint();
        persistBest();
      } catch (RuntimeException e) {
        if (retry == null)
          storageFailure(
              () -> {
                checkpoint();
                persistBest();
              });
      }
    }
    super.onPause();
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    if (engine != null) {
      out.putString("session", sessionId);
      out.putString("snapshot", SnapshotCodec.encode(engine));
      out.putLong("duration", elapsed());
      out.putBoolean("completed", completed != null);
      if (completed != null) {
        out.putString("outcome", completed.outcome.name());
        out.putLong("finishedAt", completed.finishedAtEpochMs);
      }
    }
    super.onSaveInstanceState(out);
  }

  @Override
  protected void onDestroy() {
    if (dialog != null) {
      dialog.setOnDismissListener(null);
      dialog.dismiss();
      dialog = null;
    }
    super.onDestroy();
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    Game2048Engine.Direction d = null;
    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) d = Game2048Engine.Direction.LEFT;
    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) d = Game2048Engine.Direction.RIGHT;
    if (keyCode == KeyEvent.KEYCODE_DPAD_UP) d = Game2048Engine.Direction.UP;
    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) d = Game2048Engine.Direction.DOWN;
    if (d != null && board != null) {
      move(d);
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }
}
