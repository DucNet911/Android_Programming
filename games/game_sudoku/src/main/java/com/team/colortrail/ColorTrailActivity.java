package com.team.colortrail;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.example.arcade.core.GameDefinition;
import com.example.arcade.core.GameOutcome;
import com.puzzlehub.sudoku.explore.ExploreGameActivity;
import com.puzzlehub.sudoku.explore.ExploreGames;
import com.puzzlehub.sudoku.game.R;
import com.puzzlehub.sudoku.utils.GameSounds;
import com.puzzlehub.sudoku.utils.LocalDay;
import com.team.colortrail.engine.ColorTrailEngine;
import java.util.Random;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class ColorTrailActivity extends ExploreGameActivity {
    private static final int SIDE = ColorTrailEngine.SIDE;
    private static final int[] TILE_COLORS = {0xFFB4E5DB, 0xFFFBD39D, 0xFFDDD1F4, 0xFFC6E5A6};
    private static final String[] COLOR_NAMES = {"xanh ngọc", "cam", "tím", "xanh lá"};
    private final TextView[] tiles = new TextView[ColorTrailEngine.SIZE];
    private ColorTrailEngine game;
    private TextView stats;
    private TextView feedback;
    private Button hintButton;
    private int suggested = -1;

    @Override
    protected GameDefinition definition() {
        return ExploreGames.colorTrail(this);
    }

    @Override
    protected void createGame(String difficultyId) {
        long seed = new Random().nextLong();
        if ("daily".equals(difficultyId)) {
            seed = LocalDay.now().toEpochDay();
        }
        game = new ColorTrailEngine(seed);
    }

    @Override
    protected void restoreGame(String text) {
        try {
            JSONObject snapshot = new JSONObject(text);
            if (snapshot.getInt("schema") != 1) {
                throw new IllegalArgumentException("Unknown save format");
            }
            JSONArray cells = snapshot.getJSONArray("path");
            int[] values = new int[cells.length()];
            for (int index = 0; index < values.length; index++) {
                values[index] = cells.getInt(index);
            }
            game = ColorTrailEngine.restore(snapshot.getLong("seed"), values);
            if (game.ended()) {
                throw new IllegalArgumentException("Completed games have a result");
            }
        } catch (JSONException error) {
            throw new IllegalArgumentException("Invalid save", error);
        }
    }

    @Override
    protected String encodeGame() {
        try {
            JSONObject snapshot = new JSONObject();
            snapshot.put("schema", 1);
            snapshot.put("seed", game.seed());
            JSONArray cells = new JSONArray();
            for (int cell : game.path()) {
                cells.put(cell);
            }
            snapshot.put("path", cells);
            return snapshot.toString();
        } catch (JSONException error) {
            throw new IllegalStateException("Could not save game", error);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void showGame() {
        suggested = -1;
        setContentView(R.layout.color_trail_activity);
        findViewById(R.id.color_trail_root).setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        stats = findViewById(R.id.color_trail_stats);
        feedback = findViewById(R.id.color_trail_feedback);
        hintButton = findViewById(R.id.color_trail_hint_button);
        findViewById(R.id.color_trail_exit_button).setOnClickListener(view -> saveAndExit());
        hintButton.setOnClickListener(view -> showHint());
        LinearLayout grid = findViewById(R.id.color_trail_board);
        for (int row = 0; row < SIDE; row++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            grid.addView(rowLayout);
            for (int column = 0; column < SIDE; column++) {
                int index = row * SIDE + column;
                TextView tile = new TextView(this);
                tile.setGravity(Gravity.CENTER);
                tile.setTextSize(19);
                tile.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                tile.setTextColor(getColor(R.color.color_trail_ink));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(54), 1);
                params.setMargins(dp(3), dp(3), dp(3), dp(3));
                rowLayout.addView(tile, params);
                tile.setSoundEffectsEnabled(false);
                tile.setOnClickListener(view -> play(index));
                tiles[index] = tile;
            }
        }
        render();
    }

    private void play(int index) {
        if (game.ended()) {
            return;
        }
        int previousCrystals = game.collected();
        if (!game.move(index)) {
            if (index == ColorTrailEngine.EXIT && game.collected() < ColorTrailEngine.REQUIRED_CRYSTALS) {
                feedback.setText("Cần ít nhất 3 tinh thể để mở đích.");
            } else {
                feedback.setText("Chọn ô kề cạnh khác màu mà bạn chưa đi qua.");
            }
            return;
        }
        GameSounds.playMove(this);
        if (game.collected() > previousCrystals) {
            feedback.setText("Nhặt được tinh thể · Giữ nguyên năng lượng");
        } else {
            feedback.setText("");
        }
        suggested = -1;
        if (game.ended()) {
            boolean won = game.won();
            GameOutcome outcome;
            if (won) {
                outcome = GameOutcome.WON;
                feedback.setText("Bạn đã tới đích!");
            } else {
                outcome = GameOutcome.LOST;
                feedback.setText("Hết đường đi. Thử một lối khác ở ván sau.");
            }
            Long score = null;
            if (won) {
                score = (long) game.score();
            }
            if (completeGame(outcome, score)) {
                hintButton.setText("Chơi lại");
                hintButton.setOnClickListener(view -> playAgain());
            }
        } else if (!checkpointGame()) {
            return;
        }
        render();
    }

    private void showHint() {
        suggested = game.suggestMove();
        if (suggested < 0) {
            feedback.setText("Đường hiện tại chưa có lối tới đích.");
        } else {
            String location = "hàng " + (suggested / SIDE + 1) + ", cột " + (suggested % SIDE + 1);
            feedback.setText("Đi tới " + location + ".");
        }
        render();
    }

    private void render() {
        stats.setText("Tinh thể " + game.collected() + "/" + ColorTrailEngine.REQUIRED_CRYSTALS
                + " · Năng lượng " + game.energy() + "\n" + game.score() + " điểm");
        boolean ended = game.ended();
        for (int index = 0; index < tiles.length; index++) {
            TextView tile = tiles[index];
            int color = game.colorAt(index);
            String marker = "";
            String description = COLOR_NAMES[color];
            if (game.position() == index) {
                marker = "●";
                description += ", vị trí hiện tại";
            } else if (index == ColorTrailEngine.EXIT) {
                marker = "⚑";
                description += ", đích";
            } else if (game.crystalAt(index)) {
                marker = "◆";
                description += ", có tinh thể";
            } else if (game.visited(index)) {
                marker = "·";
                description += ", đã đi qua";
            }
            tile.setText(marker);
            int fill = TILE_COLORS[color];
            if (game.visited(index) && game.position() != index) {
                fill = 0xFFE2ECE8;
            }
            GradientDrawable background = new GradientDrawable();
            background.setColor(fill);
            background.setCornerRadius(dp(15));
            if (index == suggested) {
                background.setStroke(dp(3), getColor(R.color.color_trail_accent));
                description += ", gợi ý";
            } else {
                background.setStroke(dp(1), Color.WHITE);
            }
            tile.setBackground(background);
            tile.setContentDescription(description + ", hàng " + (index / SIDE + 1) + ", cột " + (index % SIDE + 1));
            tile.setEnabled(!ended);
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
