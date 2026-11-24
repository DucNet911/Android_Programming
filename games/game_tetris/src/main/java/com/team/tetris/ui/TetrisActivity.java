package com.team.tetris.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.example.arcade.core.*;
import com.team.tetris.R;
import com.team.tetris.TetrisPlugin;
import com.team.tetris.data.TetrisRequests;
import com.team.tetris.data.TetrisSnapshot;
import com.team.tetris.engine.TetrisLogic;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class TetrisActivity extends AppCompatActivity {
    private String mode() {
        return request.difficultyId == null ? TetrisPlugin.CLASSIC : request.difficultyId;
    }
    private static final String SETTINGS = "tetris_settings";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Integer> musicBag = new ArrayList<>();
    private GameSessionStore store;
    private GameLaunchRequest request;
    private String sessionId;
    private TetrisLogic gameLogic;
    private GameResult completedResult;
    private long activeDurationMs;
    private long activeSince = -1;
    private boolean resumed, isPaused, exiting;
    private TetrisView gameView;
    private NextPieceView nextPieceView;
    private TextView scoreView;
    private View pausedMenu, soundMenu, gameOverLayer;
    private MediaPlayer bgmPlayer;
    private SoundPool soundPool;
    private int soundClear, soundDrop, soundRotate;
    private float sfxVolume;

    private final Runnable gameLoop = new Runnable() {
        @Override public void run() {
            if (!canPlay()) return;
            gameLogic.moveDown();
            afterMove();
            if (canPlay()) handler.postDelayed(this, TetrisPlugin.tickMillis(mode()));
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        try { request = TetrisRequests.read(getIntent()); }
        catch (RuntimeException ex) {
            exiting = true; setResult(RESULT_OK, TetrisRequests.invalidResponse(getIntent()));
            finish(); return;
        }
        store = new GameSessionStore(this);
        if (!openSession(state)) return;
        setContentView(R.layout.tetris_activity_main);
        gameView = findViewById(R.id.tetris_game_view);
        nextPieceView = findViewById(R.id.tetris_next_piece_view);
        scoreView = findViewById(R.id.tetris_tv_score);
        pausedMenu = findViewById(R.id.tetris_img_paused_menu);
        soundMenu = findViewById(R.id.tetris_sound_menu_layer);
        gameOverLayer = findViewById(R.id.tetris_game_over_layer);
        bindEngine();
        setupAudio();
        setupControls();
        pausedMenu.setVisibility(isPaused && !gameLogic.isGameOver() ? View.VISIBLE : View.GONE);
        if (gameLogic.isGameOver()) completeGame();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (soundMenu.getVisibility() == View.VISIBLE) soundMenu.setVisibility(View.GONE);
                else saveAndExit();
            }
        });
    }

    private boolean openSession(Bundle state) {
        try {
            if (state != null && state.containsKey("completed")) {
                Intent response = state.getParcelable("completed");
                completedResult = GameContract.readResponse(response).result;
                sessionId = state.getString("sessionId");
                if (completedResult == null || !TetrisPlugin.ID.equals(completedResult.gameId)
                        || !request.playerId.equals(completedResult.playerId)
                        || !completedResult.sessionId.equals(sessionId)) {
                    exitError(GameErrorCode.CORRUPT_SAVE); return false;
                }
                gameLogic = TetrisSnapshot.decode(state.getString("snapshot"));
                activeDurationMs = completedResult.activeDurationMs;
                setResult(RESULT_OK, response);
                return true;
            }
            GameSession saved;
            try { saved = store.load(TetrisPlugin.ID, request.playerId); }
            catch (RuntimeException ex) { exitError(GameErrorCode.CORRUPT_SAVE); return false; }
            String expectedId = state != null ? state.getString("sessionId")
                    : request.launchMode == LaunchMode.RESUME ? request.sessionId : null;
            if (expectedId != null) {
                if (saved == null || !expectedId.equals(saved.sessionId)) {
                    exitError(GameErrorCode.SESSION_NOT_FOUND); return false;
                }
                if (!mode().equals(saved.difficultyId) || saved.demo) {
                    exitError(GameErrorCode.CORRUPT_SAVE); return false;
                }
                sessionId = saved.sessionId;
                activeDurationMs = saved.activeDurationMs;
                gameLogic = TetrisSnapshot.decode(saved.snapshot);
                isPaused = state != null && state.getBoolean("paused");
            } else {
                if (saved != null) { exitError(GameErrorCode.SESSION_EXISTS); return false; }
                sessionId = UUID.randomUUID().toString();
                gameLogic = new TetrisLogic();
                if (!saveCheckpoint()) return false;
            }
            return true;
        } catch (IllegalArgumentException ex) { exitError(GameErrorCode.CORRUPT_SAVE); return false; }
        catch (IllegalStateException ex) {
            exitError(ex.getMessage() != null && ex.getMessage().startsWith("Corrupt")
                    ? GameErrorCode.CORRUPT_SAVE : GameErrorCode.STORAGE_ERROR);
            return false;
        }
    }

    private void bindEngine() {
        gameLogic.setOnLineClearListener((row, col, block) -> {
            gameView.spawnExplosion(row, col, block); playSound(soundClear);
        });
        gameView.setGameLogic(gameLogic);
        nextPieceView.setGameLogic(gameLogic);
        redraw();
    }

    private void setupControls() {
        findViewById(R.id.tetris_pause).setOnClickListener(v -> {
            if (gameLogic.isGameOver()) return;
            isPaused = true; stopPlaying(); saveCheckpoint();
            pausedMenu.setVisibility(View.VISIBLE);
        });
        findViewById(R.id.tetris_btn_resume).setOnClickListener(v -> {
            isPaused = false; pausedMenu.setVisibility(View.GONE); startPlaying();
        });
        findViewById(R.id.tetris_btn_quit).setOnClickListener(v -> saveAndExit());
        findViewById(R.id.tetris_btn_quit_game_over).setOnClickListener(v -> saveAndExit());
        findViewById(R.id.tetris_btn_sound).setOnClickListener(v -> soundMenu.setVisibility(View.VISIBLE));
        findViewById(R.id.tetris_btn_close_sound_menu).setOnClickListener(v -> soundMenu.setVisibility(View.GONE));
        findViewById(R.id.tetris_btn_replay).setOnClickListener(v -> replay());
        SharedPreferences prefs = getSharedPreferences(SETTINGS, MODE_PRIVATE);
        configureVolume(R.id.tetris_seek_bar_bgm, "bgm_volume", prefs, true);
        configureVolume(R.id.tetris_seek_bar_sfx, "sfx_volume", prefs, false);
    }

    private void configureVolume(int viewId, String key, SharedPreferences prefs, boolean music) {
        SeekBar seekBar = findViewById(viewId);
        seekBar.setProgress((int) (prefs.getFloat(key, 1f) * 100));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                float volume = progress / 100f;
                prefs.edit().putFloat(key, volume).apply();
                if (music && bgmPlayer != null) bgmPlayer.setVolume(volume, volume);
                if (!music) sfxVolume = volume;
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
    }

    private void setupAudio() {
        sfxVolume = getSharedPreferences(SETTINGS, MODE_PRIVATE).getFloat("sfx_volume", 1f);
        soundPool = new SoundPool.Builder().setMaxStreams(5).setAudioAttributes(
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        soundClear = soundPool.load(this, R.raw.tetris_clear, 1);
        soundDrop = soundPool.load(this, R.raw.tetris_drop, 1);
        soundRotate = soundPool.load(this, R.raw.tetris_rotate, 1);
    }

    private void playNextSong() {
        if (bgmPlayer != null) { bgmPlayer.release(); bgmPlayer = null; }
        if (!canPlay()) return;
        if (musicBag.isEmpty()) {
            Collections.addAll(musicBag, R.raw.tetris_bgm, R.raw.tetris_a_beautiful_song,
                    R.raw.tetris_gerudo_valley, R.raw.tetris_city_ruins);
            Collections.shuffle(musicBag);
        }
        bgmPlayer = MediaPlayer.create(this, musicBag.remove(0));
        if (bgmPlayer != null) {
            float volume = getSharedPreferences(SETTINGS, MODE_PRIVATE).getFloat("bgm_volume", 1f);
            bgmPlayer.setVolume(volume, volume);
            bgmPlayer.setOnCompletionListener(player -> playNextSong());
            bgmPlayer.start();
        }
    }

    private void playSound(int sound) {
        if (soundPool != null && sound != 0 && resumed && !isPaused)
            soundPool.play(sound, sfxVolume, sfxVolume, 0, 0, 1f);
    }

    private boolean canPlay() {
        return resumed && !isPaused && !exiting && gameLogic != null
                && !gameLogic.isGameOver() && completedResult == null;
    }

    private void startPlaying() {
        if (!canPlay()) return;
        if (activeSince < 0) activeSince = SystemClock.elapsedRealtime();
        handler.removeCallbacks(gameLoop);
        handler.postDelayed(gameLoop, TetrisPlugin.tickMillis(mode()));
        gameView.setAnimationRunning(true);
        if (bgmPlayer == null) playNextSong();
        else if (!bgmPlayer.isPlaying()) bgmPlayer.start();
    }

    private void stopPlaying() {
        handler.removeCallbacks(gameLoop);
        if (activeSince >= 0) {
            activeDurationMs += Math.max(0, SystemClock.elapsedRealtime() - activeSince);
            activeSince = -1;
        }
        if (gameView != null) gameView.setAnimationRunning(false);
        if (bgmPlayer != null && bgmPlayer.isPlaying()) bgmPlayer.pause();
        if (soundPool != null) soundPool.autoPause();
    }

    private long duration() {
        return activeDurationMs + (activeSince < 0 ? 0 : Math.max(0, SystemClock.elapsedRealtime() - activeSince));
    }

    private GameSession checkpoint() {
        return new GameSession(TetrisPlugin.ID, request.playerId, sessionId, mode(),
                duration(), TetrisSnapshot.encode(gameLogic), false);
    }

    private boolean saveCheckpoint() {
        if (exiting || completedResult != null || gameLogic == null) return false;
        try { store.save(checkpoint()); return true; }
        catch (RuntimeException ex) { exitError(GameErrorCode.STORAGE_ERROR); return false; }
    }

    private void afterMove() {
        redraw();
        if (gameLogic.isGameOver()) completeGame();
        else saveCheckpoint();
    }

    private void redraw() {
        scoreView.setText(String.valueOf(gameLogic.getScore()));
        gameView.invalidate(); nextPieceView.invalidate();
    }

    private void completeGame() {
        stopPlaying();
        if (completedResult == null) {
            GameResult result = new GameResult(TetrisPlugin.ID, request.playerId, sessionId,
                    mode(), GameOutcome.LOST, (long) gameLogic.getScore(),
                    activeDurationMs, System.currentTimeMillis(), false);
            try { store.finish(result); }
            catch (RuntimeException ex) { exitError(GameErrorCode.STORAGE_ERROR); return; }
            completedResult = result;
        }
        setResult(RESULT_OK, GameContract.responseIntent(GameExitResponse.finished(completedResult)));
        ((TextView) findViewById(R.id.tetris_tv_final_score)).setText(String.valueOf(gameLogic.getScore()));
        if (gameOverLayer.getVisibility() != View.VISIBLE) {
            gameOverLayer.setVisibility(View.VISIBLE);
            gameOverLayer.startAnimation(AnimationUtils.loadAnimation(this, R.anim.tetris_slide_down));
        }
    }

    private void replay() {
        if (completedResult == null || exiting) return;
        sessionId = UUID.randomUUID().toString();
        gameLogic = new TetrisLogic();
        completedResult = null; activeDurationMs = 0; isPaused = false;
        if (!saveCheckpoint()) return;
        gameOverLayer.clearAnimation(); gameOverLayer.setVisibility(View.GONE);
        pausedMenu.setVisibility(View.GONE); soundMenu.setVisibility(View.GONE);
        bindEngine(); startPlaying();
    }

    private void saveAndExit() {
        if (exiting) return;
        stopPlaying();
        if (completedResult == null) {
            if (!saveCheckpoint()) return;
            setResult(RESULT_OK, GameContract.responseIntent(GameExitResponse.saved(checkpoint())));
        } else {
            setResult(RESULT_OK, GameContract.responseIntent(GameExitResponse.finished(completedResult)));
        }
        exiting = true; finish();
    }

    private void exitError(GameErrorCode code) {
        exiting = true; stopPlaying();
        setResult(RESULT_OK, GameContract.responseIntent(GameExitResponse.error(
                TetrisPlugin.ID, request.playerId, code)));
        if (code == GameErrorCode.STORAGE_ERROR)
            Toast.makeText(this, R.string.tetris_storage_error, Toast.LENGTH_LONG).show();
        finish();
    }

    @Override protected void onResume() {
        super.onResume(); resumed = true; startPlaying();
    }

    @Override protected void onPause() {
        resumed = false; stopPlaying();
        if (!exiting && completedResult == null && gameLogic != null) {
            isPaused = true;
            if (pausedMenu != null) pausedMenu.setVisibility(View.VISIBLE);
            saveCheckpoint();
        }
        super.onPause();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        if (gameLogic != null && !exiting) {
            state.putString("sessionId", sessionId); state.putBoolean("paused", isPaused);
            if (completedResult != null) {
                state.putParcelable("completed", GameContract.responseIntent(GameExitResponse.finished(completedResult)));
                state.putString("snapshot", TetrisSnapshot.encode(gameLogic));
            } else saveCheckpoint();
        }
        super.onSaveInstanceState(state);
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused) {
            WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            controller.hide(WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    @Override protected void onDestroy() {
        stopPlaying();
        if (soundPool != null) { soundPool.release(); soundPool = null; }
        if (bgmPlayer != null) { bgmPlayer.release(); bgmPlayer = null; }
        super.onDestroy();
    }

    private float startX, startY;
    private long startTime;
    private boolean dragging, hardDropped;

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!canPlay()) return true;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startX = event.getX(); startY = event.getY();
                startTime = SystemClock.elapsedRealtime(); dragging = false; hardDropped = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - startX, dy = event.getY() - startY;
                boolean changed = false;
                if (Math.abs(dx) > 50) {
                    dragging = true;
                    if (dx > 0) gameLogic.moveRight(); else gameLogic.moveLeft();
                    startX = event.getX(); startY = event.getY(); changed = true;
                }
                if (dy > 120 && !hardDropped) {
                    dragging = true; hardDropped = true; gameLogic.hardDrop();
                    playSound(soundDrop); startY = event.getY(); changed = true;
                }
                if (changed) afterMove();
                return true;
            case MotionEvent.ACTION_UP:
                if (!dragging && !hardDropped && SystemClock.elapsedRealtime() - startTime < 200
                        && Math.abs(event.getX() - startX) < 15 && Math.abs(event.getY() - startY) < 15) {
                    gameLogic.Rotate90Clockwise(); playSound(soundRotate); afterMove();
                }
                hardDropped = false; return true;
            case MotionEvent.ACTION_CANCEL:
                hardDropped = false; return true;
            default: return super.onTouchEvent(event);
        }
    }
}
