package com.puzzlehub.sudoku.game;

import android.content.Context;
import com.example.arcade.core.GameContract;
import com.example.arcade.core.GameDefinition;
import com.example.arcade.core.GameDifficulty;
import com.example.arcade.core.GamePlugin;
import com.puzzlehub.sudoku.domain.Difficulty;
import java.util.Collections;

public final class SudokuPlugin implements GamePlugin {
    public static final String ID = "sudoku";
    public static final String MODE_STANDARD = "standard";

    public SudokuPlugin() {}

    @Override
    public int getContractVersion() {
        return GameContract.VERSION;
    }

    static boolean isSupportedDifficulty(String id) {
        if (id == null || MODE_STANDARD.equals(id)) {
            return true;
        }
        for (Difficulty difficulty : Difficulty.values()) {
            if (difficulty.getId().equals(id)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public GameDefinition getDefinition(Context context) {
        return new GameDefinition(
                ID,
                context.getString(R.string.sudoku_plugin_name),
                context.getString(R.string.sudoku_plugin_description),
                "9×9",
                0xFF6F65B7,
                SudokuActivity.class,
                GameDefinition.Ranking.LOWEST_WIN_TIME,
                Collections.singletonList(
                        new GameDifficulty(MODE_STANDARD, context.getString(R.string.sudoku_mode_standard))));
    }
}
