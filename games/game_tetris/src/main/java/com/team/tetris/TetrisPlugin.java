package com.team.tetris;

import android.content.Context;
import android.graphics.Color;
import com.example.arcade.core.GameContract;
import com.example.arcade.core.GameDefinition;
import com.example.arcade.core.GameDifficulty;
import com.example.arcade.core.GamePlugin;
import com.team.tetris.ui.TetrisMenuActivity;
import java.util.Arrays;

public final class TetrisPlugin implements GamePlugin {
    public static final String ID = "tetris";
    public static final String CLASSIC = "classic";

    public static int tickMillis(String mode) {
        if (mode == null || CLASSIC.equals(mode)) return 500;
        if ("slow".equals(mode)) return 800;
        if ("fast".equals(mode)) return 250;
        return 0;
    }

    public TetrisPlugin() {}

    @Override public int getContractVersion() { return GameContract.VERSION; }

    @Override public GameDefinition getDefinition(Context context) {
        return new GameDefinition(ID, context.getString(R.string.tetris_app_name),
                context.getString(R.string.tetris_description), "T", Color.rgb(93, 213, 255),
                TetrisMenuActivity.class, GameDefinition.Ranking.HIGH_SCORE,
                Arrays.asList(new GameDifficulty("slow", "Dễ · Chậm"),
                        new GameDifficulty(CLASSIC, "Vừa · Tiêu chuẩn"), new GameDifficulty("fast", "Khó · Nhanh")));
    }
}
