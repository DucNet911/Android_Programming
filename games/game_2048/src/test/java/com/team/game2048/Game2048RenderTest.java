package com.team.game2048;

import static org.junit.Assert.*;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import com.example.arcade.core.GameContract;
import com.example.arcade.core.GameLaunchRequest;
import com.example.arcade.core.LaunchMode;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class Game2048RenderTest {
  @Test
  public void rendersBoardAndControls() throws Exception {
    Intent intent =
        GameContract.putRequest(
            new Intent(RuntimeEnvironment.getApplication(), Game2048Activity.class),
            new GameLaunchRequest("game_2048", "render", LaunchMode.NEW_GAME, null, null));
    try (var controller = Robolectric.buildActivity(Game2048Activity.class, intent).setup()) {
      View root = controller.get().findViewById(R.id.g2048_root);
      root.measure(
          View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
      root.layout(0, 0, 360, 800);
      View board = root.findViewById(R.id.g2048_board);
      assertTrue(board.getWidth() > 0);
      assertEquals(board.getWidth(), board.getHeight());
      Bitmap bitmap = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888);
      root.draw(new Canvas(bitmap));
      File output = new File("build/reports/game2048-preview.png");
      output.getParentFile().mkdirs();
      try (FileOutputStream stream = new FileOutputStream(output)) {
        assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
      }
    }
  }
}
