package com.team.bomber;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.example.arcade.core.GameDefinition;
import com.example.arcade.core.GameOutcome;
import com.puzzlehub.sudoku.explore.ExploreGameActivity;
import com.puzzlehub.sudoku.explore.ExploreGames;
import com.puzzlehub.sudoku.game.R;
import com.puzzlehub.sudoku.utils.GameSounds;
import com.team.bomber.engine.BomberEngine;
import java.util.Random;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class BomberActivity extends ExploreGameActivity {
    private static final int BACKGROUND = 0xFF102238;
    private static final int INK = 0xFFF3FCFF;
    private static final int ACCENT = 0xFF6DECE0;
    private final TextView[] cells = new TextView[BomberEngine.AREA];
    private BomberEngine game;
    private TextView stats;
    private TextView feedback;
    private Button place;
    private Button wait;

    @Override
    protected GameDefinition definition() {
        return ExploreGames.bomber();
    }

    @Override
    protected void createGame(String difficultyId) {
        game = new BomberEngine(new Random().nextLong());
    }

    @Override
    protected void restoreGame(String text) {
        try {
            JSONObject snapshot = new JSONObject(text);
            if (snapshot.getInt("schema") != 1) {
                throw new IllegalArgumentException("Unsupported Bomber checkpoint");
            }
            game = new BomberEngine(readArray(snapshot.getJSONArray("tiles")),
                    readArray(snapshot.getJSONArray("bombs")), snapshot.getInt("player"),
                    snapshot.getInt("turns"), snapshot.getInt("crystals"), snapshot.getInt("score"));
        } catch (JSONException error) {
            throw new IllegalArgumentException("Invalid Bomber checkpoint", error);
        }
    }

    private static int[] readArray(JSONArray values) throws JSONException {
        if (values.length() != BomberEngine.AREA) {
            throw new IllegalArgumentException("Invalid board length");
        }
        int[] result = new int[BomberEngine.AREA];
        for (int index = 0; index < result.length; index++) {
            result[index] = values.getInt(index);
        }
        return result;
    }

    @Override
    protected String encodeGame() {
        JSONArray tiles = new JSONArray();
        JSONArray bombs = new JSONArray();
        for (int index = 0; index < BomberEngine.AREA; index++) {
            tiles.put(game.cell(index));
            bombs.put(game.fuse(index));
        }
        try {
            return new JSONObject().put("schema", 1).put("tiles", tiles).put("bombs", bombs)
                    .put("player", game.player()).put("turns", game.turns())
                    .put("crystals", game.crystals()).put("score", game.score()).toString();
        } catch (JSONException error) {
            throw new IllegalStateException("Could not save Bomber checkpoint", error);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void showGame() {
        ScrollView screen = new ScrollView(this);
        screen.setFillViewport(true);
        screen.setBackgroundColor(BACKGROUND);
        screen.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(16), dp(8), dp(16), dp(20));
        screen.addView(column);
        setContentView(screen);

        Button exit = button("‹  Khám phá", R.id.bomber_exit, 0xFF203755, INK);
        exit.setOnClickListener(view -> saveAndExit());
        column.addView(exit, new LinearLayout.LayoutParams(-2, dp(42)));
        TextView title = text("Bomber", 38, INK, true);
        title.setPadding(0, dp(8), 0, 0);
        column.addView(title);
        column.addView(text("KHO TINH THỂ", 15, ACCENT, true));
        TextView rules = text("Chạm ô kề để di chuyển. Bom nổ sau 3 lượt. Gom 3 tinh thể rồi đến cửa thoát.", 14, 0xFFBCD2E2, false);
        rules.setPadding(0, dp(8), 0, dp(9));
        column.addView(rules);

        stats = text("", 14, ACCENT, true);
        stats.setPadding(dp(12), dp(9), dp(12), dp(9));
        stats.setBackground(panel(0xFF203C56, dp(14)));
        column.addView(stats);
        buildBoard(column);

        feedback = text("Thùng có dấu ◇ giấu tinh thể.", 13, 0xFFBCD2E2, false);
        feedback.setMinHeight(dp(42));
        feedback.setPadding(0, dp(7), 0, 0);
        column.addView(feedback);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        place = button("Đặt bom", R.id.bomber_place, ACCENT, BACKGROUND);
        wait = button("Chờ 1 lượt", R.id.bomber_wait, 0xFF294561, INK);
        LinearLayout.LayoutParams placeParams = new LinearLayout.LayoutParams(0, dp(52), 1);
        placeParams.setMarginEnd(dp(8));
        controls.addView(place, placeParams);
        controls.addView(wait, new LinearLayout.LayoutParams(0, dp(52), 1));
        column.addView(controls);
        place.setOnClickListener(view -> afterAction(game.place(), "Ô này đã có bom."));
        wait.setOnClickListener(view -> afterAction(game.waitTurn(), ""));
        render();
    }

    private void buildBoard(LinearLayout parent) {
        int unit = Math.min(dp(46), (getResources().getDisplayMetrics().widthPixels - dp(34)) / BomberEngine.SIDE);
        LinearLayout board = new LinearLayout(this);
        board.setId(R.id.bomber_board);
        board.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(unit * BomberEngine.SIDE, -2);
        gridParams.gravity = Gravity.CENTER_HORIZONTAL;
        gridParams.topMargin = dp(10);
        gridParams.bottomMargin = dp(4);
        parent.addView(board, gridParams);

        for (int row = 0; row < BomberEngine.SIDE; row++) {
            LinearLayout line = new LinearLayout(this);
            board.addView(line, new LinearLayout.LayoutParams(-1, unit));
            for (int col = 0; col < BomberEngine.SIDE; col++) {
                int index = row * BomberEngine.SIDE + col;
                TextView cell = text("", 18, INK, true);
                cell.setGravity(Gravity.CENTER);
                cell.setSoundEffectsEnabled(false);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1);
                params.setMargins(dp(1), dp(1), dp(1), dp(1));
                line.addView(cell, params);
                cell.setOnClickListener(view -> afterAction(game.move(index), "Chọn một ô trống kề bên."));
                cells[index] = cell;
            }
        }
    }

    private void afterAction(boolean moved, String noMoveMessage) {
        if (!moved) {
            feedback.setText(noMoveMessage);
            return;
        }
        GameSounds.playMove(this);
        if (game.ended()) {
            GameOutcome outcome = GameOutcome.LOST;
            String message = "Hết lượt hoặc bị trúng vụ nổ.";
            if (game.won()) {
                outcome = GameOutcome.WON;
                message = "Đã thoát kho cùng tinh thể!";
            }
            feedback.setText(message);
            if (completeGame(outcome, (long) game.score())) {
                wait.setText("Chơi lại");
                wait.setOnClickListener(view -> playAgain());
            }
        } else if (!checkpointGame()) {
            return;
        } else if (game.player() == BomberEngine.DOOR) {
            feedback.setText("Cần đủ 3 tinh thể để mở cửa.");
        } else {
            feedback.setText("Thùng có dấu ◇ giấu tinh thể.");
        }
        render();
    }

    private void render() {
        stats.setText("◆ " + game.crystals() + "/3   ·   " + (BomberEngine.LIMIT - game.turns())
                + " lượt   ·   " + game.score() + " điểm");
        place.setEnabled(!game.ended() && game.fuse(game.player()) == 0);
        for (int index = 0; index < cells.length; index++) {
            drawCell(index);
        }
    }

    private void drawCell(int index) {
        TextView cell = cells[index];
        CellStyle style = switch (game.cell(index)) {
            case BomberEngine.WALL -> new CellStyle("", "Tường", 0xFF263651, 0xFF51627C);
            case BomberEngine.CRATE -> new CellStyle("▤", "Thùng", 0xFF754735, 0xFFFFB276);
            case BomberEngine.CRYSTAL_CRATE -> new CellStyle("◇", "Thùng tinh thể", 0xFF563C77, 0xFFE9AEFF);
            case BomberEngine.CRYSTAL -> new CellStyle("◆", "Tinh thể", 0xFF1F696C, ACCENT);
            case BomberEngine.EXIT -> new CellStyle("⇥", "Cửa thoát", 0xFF245E77, 0xFFA7E9FF);
            default -> new CellStyle("", "Ô trống", 0xFF213A56, INK);
        };
        if (game.flashed(index)) {
            style = new CellStyle("✦", "Vụ nổ", 0xFFAA4B39, 0xFFFFDB92);
        }
        if (game.fuse(index) > 0) {
            style = new CellStyle(String.valueOf(game.fuse(index)), "Bom còn " + game.fuse(index) + " lượt",
                    0xFF824836, 0xFFFFD67B);
        }
        if (index == game.player() && !game.flashed(index)) {
            style = new CellStyle("●", "Nhân vật", 0xFF276883, 0xFFB3FAFF);
        }
        cell.setText(style.symbol());
        cell.setTextColor(style.ink());
        cell.setBackground(panel(style.fill(), dp(7)));
        cell.setContentDescription(style.label() + ", hàng " + (index / BomberEngine.SIDE + 1)
                + ", cột " + (index % BomberEngine.SIDE + 1));
        cell.setEnabled(!game.ended());
    }

    private record CellStyle(String symbol, String label, int fill, int ink) {}

    private TextView text(String label, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return view;
    }

    private Button button(String label, int id, int fill, int ink) {
        Button button = new Button(this);
        button.setId(id);
        button.setText(label);
        button.setTextColor(ink);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setSoundEffectsEnabled(false);
        button.setBackground(panel(fill, dp(14)));
        return button;
    }

    private GradientDrawable panel(int color, int radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(radius);
        return background;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
