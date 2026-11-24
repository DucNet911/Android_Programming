package com.puzzlehub.sudoku.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;

public final class GameSounds {
    private static final String ENABLED = "enabled";

    private GameSounds() {}

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("sudoku_audio", Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(Context context) {
        return preferences(context).getBoolean(ENABLED, true);
    }

    public static void setEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(ENABLED, enabled).apply();
    }

    public static void playMove(Context context) {
        if (!isEnabled(context)) {
            return;
        }
        AudioManager audio = context.getSystemService(AudioManager.class);
        if (audio != null) {
            audio.playSoundEffect(AudioManager.FX_KEY_CLICK, 0.45f);
        }
    }
}
