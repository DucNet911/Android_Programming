package com.team.game2048;

import android.content.Context;
import android.graphics.Color;
import com.example.arcade.core.*;
import java.util.ArrayList;
import java.util.List;

public final class Game2048Plugin implements GamePlugin {
  public static final String ID = "game_2048";

  static int boardSize(String mode) {
    if (mode == null || "default".equals(mode)) return 4;
    for (int size = 5; size <= 8; size++) if ((size + "x" + size).equals(mode)) return size;
    return 0;
  }

  public Game2048Plugin() {}

  @Override
  public int getContractVersion() {
    return GameContract.VERSION;
  }

  @Override
  public GameDefinition getDefinition(Context context) {
    List<GameDifficulty> modes = new ArrayList<>();
    for (int size = 4; size <= 8; size++) {
      modes.add(new GameDifficulty(size == 4 ? "default" : size + "x" + size, size + " × " + size));
    }
    return new GameDefinition(
        ID,
        context.getString(R.string.g2048_name),
        context.getString(R.string.g2048_description),
        "2048",
        Color.rgb(237, 194, 46),
        Game2048Activity.class,
        GameDefinition.Ranking.HIGH_SCORE,
        modes);
  }
}
