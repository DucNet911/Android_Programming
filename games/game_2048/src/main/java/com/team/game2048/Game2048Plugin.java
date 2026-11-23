package com.team.game2048;

import android.content.Context;
import android.graphics.Color;
import com.example.arcade.core.*;
import java.util.Collections;

public final class Game2048Plugin implements GamePlugin {
  public static final String ID = "game_2048";

  public Game2048Plugin() {}

  @Override
  public int getContractVersion() {
    return GameContract.VERSION;
  }

  @Override
  public GameDefinition getDefinition(Context context) {
    return new GameDefinition(
        ID,
        context.getString(R.string.g2048_name),
        context.getString(R.string.g2048_description),
        "2048",
        Color.rgb(237, 194, 46),
        Game2048Activity.class,
        GameDefinition.Ranking.HIGH_SCORE,
        Collections.singletonList(
            new GameDifficulty("default", context.getString(R.string.g2048_mode))));
  }
}
