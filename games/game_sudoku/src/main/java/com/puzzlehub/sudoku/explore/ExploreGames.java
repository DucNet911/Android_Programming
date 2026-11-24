package com.puzzlehub.sudoku.explore;

import android.content.Context;
import com.example.arcade.core.GameDefinition;
import com.example.arcade.core.GameDifficulty;
import com.puzzlehub.sudoku.game.R;
import com.team.bomber.BomberActivity;
import com.team.colortrail.ColorTrailActivity;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Sudoku's own internal Explore mini-games (completely independent of 2048 and Tetris). */
public final class ExploreGames {
    private ExploreGames() {}

    public static GameDefinition bomber() {
        return new GameDefinition(
                "bomber",
                "Bomber Kho Tinh Thể",
                "Đặt bom theo lượt, phá thùng và gom tinh thể trước khi thoát.",
                "◆",
                0xFF0F817B,
                BomberActivity.class,
                GameDefinition.Ranking.HIGH_SCORE,
                Collections.singletonList(new GameDifficulty("classic", "Đảo tinh thể")));
    }

    public static GameDefinition colorTrail(Context context) {
        return new GameDefinition(
                "color_trail",
                context.getString(R.string.color_trail_title),
                context.getString(R.string.color_trail_description),
                "◆",
                0xFF17766A,
                ColorTrailActivity.class,
                GameDefinition.Ranking.HIGH_SCORE,
                Arrays.asList(
                        new GameDifficulty("classic", "Ngẫu nhiên"),
                        new GameDifficulty("daily", "Đề hôm nay")));
    }

    public static List<GameDefinition> list(Context context) {
        return Arrays.asList(bomber(), colorTrail(context));
    }

    public static String resolveDifficulty(GameDefinition def, String requested) {
        if (requested == null) {
            return def.difficultyId(0);
        }
        for (int i = 0; i < def.difficultyCount(); i++) {
            if (def.difficultyId(i).equals(requested)) {
                return requested;
            }
        }
        throw new IllegalArgumentException("Unsupported difficulty: " + requested);
    }
}
