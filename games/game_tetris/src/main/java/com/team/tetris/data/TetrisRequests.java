package com.team.tetris.data;

import android.content.Intent;
import android.os.Bundle;
import com.example.arcade.core.GameContract;
import com.example.arcade.core.GameErrorCode;
import com.example.arcade.core.GameExitResponse;
import com.example.arcade.core.GameLaunchRequest;
import com.team.tetris.TetrisPlugin;

/** Validate platform input before creating or modifying a session. */
public final class TetrisRequests {
    private TetrisRequests() {}

    public static GameLaunchRequest read(Intent intent) {
        GameLaunchRequest request = GameContract.readRequest(intent);
        if (!TetrisPlugin.ID.equals(request.gameId)
                || TetrisPlugin.tickMillis(request.difficultyId) == 0) {
            throw new IllegalArgumentException("Unsupported game or difficulty");
        }
        return request;
    }

    public static Intent invalidResponse(Intent intent) {
        Bundle payload = intent == null ? null : intent.getBundleExtra(GameContract.EXTRA_REQUEST);
        String player = payload == null ? null : payload.getString("player");
        if (player == null || player.trim().isEmpty() || player.length() > 128) player = "guest";
        GameErrorCode code = payload != null && payload.getInt("version", -1) != GameContract.VERSION
                ? GameErrorCode.UNSUPPORTED_VERSION : GameErrorCode.INVALID_REQUEST;
        return GameContract.responseIntent(GameExitResponse.error(TetrisPlugin.ID, player, code));
    }
}
