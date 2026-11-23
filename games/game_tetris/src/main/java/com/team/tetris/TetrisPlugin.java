package com.team.tetris;

import android.content.Context;
import android.graphics.Color;
import com.example.arcade.core.GameContract;
import com.example.arcade.core.GameDefinition;
import com.example.arcade.core.GameDifficulty;
import com.example.arcade.core.GamePlugin;
import com.team.tetris.ui.TetrisMenuActivity;
import java.util.Collections;

public final class TetrisPlugin implements GamePlugin {
    public static final String ID = "tetris";
    public static final String CLASSIC = "classic";

    public TetrisPlugin() {}

    @Override public int getContractVersion() { return GameContract.VERSION; }

    @Override public GameDefinition getDefinition(Context context) {
        return new GameDefinition(ID, context.getString(R.string.tetris_app_name),
                context.getString(R.string.tetris_description), "T", Color.rgb(93, 213, 255),
                TetrisMenuActivity.class, GameDefinition.Ranking.HIGH_SCORE,
                Collections.singletonList(new GameDifficulty(CLASSIC,
                        context.getString(R.string.tetris_mode_classic))));
    }
}
