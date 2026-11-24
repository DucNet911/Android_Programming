package com.example.arcade.ui;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.WindowCompat;
import com.example.arcade.R;
import com.example.arcade.catalog.GameRegistry;
import com.example.arcade.core.*;
import com.example.arcade.data.HistoryDb;
import java.util.List;

public final class GameHomeActivity extends AppCompatActivity implements GameHomeHost {
    private GameDefinition game;
    private GameLaunchRequest request;

    static boolean supports(Context context, String id) {
        return ("game_2048".equals(id) || "tetris".equals(id))
                && homeResources(context).getInt("arcade.home.layout") != 0;
    }

    private static Bundle homeResources(Context context) {
        try {
            Bundle metadata = context.getPackageManager().getApplicationInfo(
                    context.getPackageName(), PackageManager.GET_META_DATA).metaData;
            return metadata == null ? Bundle.EMPTY : metadata;
        } catch (PackageManager.NameNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override protected void onCreate(Bundle state) {
        Bundle resources = homeResources(this);
        setTheme(resources.getInt("arcade.home.theme", R.style.Theme_Arcade));
        getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(state);
        request = GameContract.readRequest(getIntent());
        game = GameRegistry.discover(this).find(request.gameId);
        if (game == null) { finish(); return; }
        setResult(RESULT_OK, GameContract.responseIntent(GameExitResponse.cancelled(game.id, request.playerId)));
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) {
            getWindow().setNavigationBarContrastEnforced(false);
            getWindow().setStatusBarContrastEnforced(false);
        }
        setContentView(resources.getInt("arcade.home.layout"));
    }

    @Override public GameDefinition game() { return game; }
    @Override public GameLaunchRequest request() { return request; }
    @Override public List<GameResult> history() {
        try (HistoryDb db = new HistoryDb(this)) { return db.list(request.playerId, game.id); }
    }

    @Override public void startNew(String difficulty) {
        Runnable start = () -> {
            if (request.launchMode == LaunchMode.RESUME &&
                    !new GameSessionStore(this).discard(request.gameId, request.playerId, request.sessionId)) {
                android.widget.Toast.makeText(this, "Không thể thay thế ván đã lưu.", android.widget.Toast.LENGTH_LONG).show();
                return;
            }
            request = new GameLaunchRequest(game.id, request.playerId, LaunchMode.NEW_GAME, null, difficulty);
            play(true);
        };
        if (request.launchMode != LaunchMode.RESUME) { start.run(); return; }
        new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Thay thế ván đang chơi?")
                .setMessage("Tiến trình ván hiện tại sẽ bị xóa khi bắt đầu ván mới.")
                .setNegativeButton("Giữ ván", null).setPositiveButton("Chơi ván mới", (d, w) -> start.run()).show();
    }
    @Override public void play(boolean direct) {
        Intent intent = GameContract.putRequest(new Intent(this, game.activity), request);
        intent.putExtra("open_board", direct);
        intent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
        startActivity(intent);
        finish();
    }
}
