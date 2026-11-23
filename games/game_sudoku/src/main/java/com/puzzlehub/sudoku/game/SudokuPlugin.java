package com.puzzlehub.sudoku.game;

import android.content.Context;
import com.example.arcade.core.GameContract;
import com.example.arcade.core.GameDefinition;
import com.example.arcade.core.GameDifficulty;
import com.example.arcade.core.GamePlugin;
import com.puzzlehub.sudoku.domain.Difficulty;
import java.util.ArrayList;
import java.util.List;

public final class SudokuPlugin implements GamePlugin {
    public static final String ID = "sudoku";

    public SudokuPlugin() {}

    @Override
    public int getContractVersion() {
        return GameContract.VERSION;
    }

    static Difficulty resolveDifficulty(String id) {
        if (id == null) {
            return Difficulty.values()[0];
        }
        for (Difficulty difficulty : Difficulty.values()) {
            if (difficulty.getId().equals(id)) {
                return difficulty;
            }
        }
        throw new IllegalArgumentException("Unsupported Sudoku difficulty");
    }

    @Override
    public GameDefinition getDefinition(Context context) {
        List<GameDifficulty> difficulties = new ArrayList<>();
        for (Difficulty difficulty : Difficulty.values()) {
            difficulties.add(new GameDifficulty(difficulty.getId(), difficulty.getLabel()));
        }
        return new GameDefinition(ID, context.getString(R.string.sudoku_plugin_name),
                context.getString(R.string.sudoku_plugin_description), "9×9", 0xFF6F65B7,
                SudokuActivity.class, GameDefinition.Ranking.LOWEST_WIN_TIME, difficulties);
    }
}
