package com.team.tetris.ui;

import com.team.tetris.R;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.example.arcade.core.GameContract;
import com.example.arcade.core.GameExitResponse;
import com.example.arcade.core.GameLaunchRequest;
import com.example.arcade.core.LaunchMode;
import com.team.tetris.TetrisPlugin;
import com.team.tetris.data.TetrisRequests;

public class TetrisMenuActivity extends AppCompatActivity {
    private MediaPlayer bgmPlayer;
    private GameLaunchRequest request;
    private boolean gameLaunched;
    private final ActivityResultLauncher<Intent> gameLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                setResult(result.getResultCode(), result.getData());
                finish();
            });

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    private void hideSystemUI() {
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try { request = TetrisRequests.read(getIntent()); }
        catch (RuntimeException ex) {
            setResult(RESULT_OK, TetrisRequests.invalidResponse(getIntent()));
            finish(); return;
        }
        gameLaunched = savedInstanceState != null && savedInstanceState.getBoolean("gameLaunched");
        setResult(RESULT_OK, GameContract.responseIntent(GameExitResponse.cancelled(TetrisPlugin.ID, request.playerId)));
        setContentView(R.layout.tetris_main_menu);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { finish(); }
        });
        if (request.launchMode == LaunchMode.RESUME) {
            if (!gameLaunched) openGame();
            return;
        }

        android.content.SharedPreferences prefs = getSharedPreferences("tetris_settings", MODE_PRIVATE);
        float bgmVolume = prefs.getFloat("bgm_volume", 1.0f);

        bgmPlayer = MediaPlayer.create(this, R.raw.tetris_main_menu);
        if (bgmPlayer != null) {
            bgmPlayer.setVolume(bgmVolume, bgmVolume);
            bgmPlayer.setLooping(true);
            bgmPlayer.start();
        }

        ImageView imgLogo = findViewById(R.id.tetris_img_logo);
        FrameLayout btnPlay = findViewById(R.id.tetris_btn_play);
        FrameLayout btnTutorial = findViewById(R.id.tetris_btn_tutorial);
        FrameLayout tutorialLayer = findViewById(R.id.tetris_tutorial_layer);
        ImageView btnCloseTutorial = findViewById(R.id.tetris_btn_close_tutorial);

        Animation pulseAnim = AnimationUtils.loadAnimation(this, R.anim.tetris_pulse);
        imgLogo.startAnimation(pulseAnim);

        Animation floatUpAnim = AnimationUtils.loadAnimation(this, R.anim.tetris_float_up);
        btnPlay.startAnimation(floatUpAnim);
        
        Animation floatUpAnim2 = AnimationUtils.loadAnimation(this, R.anim.tetris_float_up);
        floatUpAnim2.setStartOffset(200);
        btnTutorial.startAnimation(floatUpAnim2);

        btnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                openGame();
                            }
                        }).start();
                    }
                }).start();
            }
        });

        btnTutorial.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                        tutorialLayer.setVisibility(View.VISIBLE);
                    }
                }).start();
            }
        });

        btnCloseTutorial.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(100).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                        tutorialLayer.setVisibility(View.GONE);
                    }
                }).start();
            }
        });
        ImageView btnSound = findViewById(R.id.tetris_btn_sound);
        FrameLayout soundMenuLayer = findViewById(R.id.tetris_sound_menu_layer);
        ImageView btnCloseSoundMenu = findViewById(R.id.tetris_btn_close_sound_menu);
        android.widget.SeekBar seekBarBgm = findViewById(R.id.tetris_seek_bar_bgm);


        seekBarBgm.setProgress((int) (bgmVolume * 100));


        btnSound.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.animate().scaleX(1.2f).scaleY(1.2f).setDuration(100).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                        soundMenuLayer.setVisibility(View.VISIBLE);
                    }
                }).start();
            }
        });

        btnCloseSoundMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(100).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                        soundMenuLayer.setVisibility(View.GONE);
                    }
                }).start();
            }
        });

        seekBarBgm.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                float newVolume = progress / 100f;
                if (bgmPlayer != null) {
                    bgmPlayer.setVolume(newVolume, newVolume);
                }
                prefs.edit().putFloat("bgm_volume", newVolume).apply();
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });



    }

    @Override
    protected void onPause() {
        super.onPause();
        if (bgmPlayer != null && bgmPlayer.isPlaying()) {
            bgmPlayer.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bgmPlayer != null) {
            bgmPlayer.start();
            android.content.SharedPreferences prefs = getSharedPreferences("tetris_settings", MODE_PRIVATE);
            float bgmVolume = prefs.getFloat("bgm_volume", 1.0f);
            bgmPlayer.setVolume(bgmVolume, bgmVolume);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("gameLaunched", gameLaunched);
        super.onSaveInstanceState(state);
    }

    private void openGame() {
        if (gameLaunched) return;
        gameLaunched = true;
        gameLauncher.launch(GameContract.putRequest(new Intent(this, TetrisActivity.class), request));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bgmPlayer != null) {
            bgmPlayer.release();
            bgmPlayer = null;
        }
    }
}
