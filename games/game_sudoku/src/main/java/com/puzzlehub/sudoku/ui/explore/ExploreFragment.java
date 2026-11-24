package com.puzzlehub.sudoku.ui.explore;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import com.example.arcade.core.ExitReason;
import com.example.arcade.core.GameContract;
import com.example.arcade.core.GameDefinition;
import com.example.arcade.core.GameErrorCode;
import com.example.arcade.core.GameExitResponse;
import com.example.arcade.core.GameLaunchRequest;
import com.example.arcade.core.GameOutcome;
import com.example.arcade.core.GameResult;
import com.example.arcade.core.GameSession;
import com.example.arcade.core.LaunchMode;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.puzzlehub.sudoku.SudokuRuntime;
import com.puzzlehub.sudoku.explore.ExploreGames;
import com.puzzlehub.sudoku.explore.ExploreResultRepository;
import com.puzzlehub.sudoku.explore.ExploreSessionStore;
import com.puzzlehub.sudoku.game.R;
import com.puzzlehub.sudoku.game.databinding.FragmentHubBinding;
import com.puzzlehub.sudoku.ui.MainActivity;
import com.puzzlehub.sudoku.ui.widgets.ActionCardView;
import com.puzzlehub.sudoku.ui.widgets.AppDialogs;
import com.puzzlehub.sudoku.ui.widgets.AppNotices;
import com.puzzlehub.sudoku.utils.AndroidTasks;
import com.puzzlehub.sudoku.utils.Format;
import com.puzzlehub.sudoku.utils.SudokuTask;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ExploreFragment extends Fragment {
    private final Map<GameDefinition, ActionCardView> cards = new LinkedHashMap<>();
    private final ActivityResultLauncher<Intent> gameLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> gameReturned(result.getData()));
    private int recordsVersion;
    private AlertDialog difficultyDialog;
    private String launchedGame;
    private String launchedPlayer;

    public ExploreFragment() { super(R.layout.fragment_hub); }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        FragmentHubBinding binding = FragmentHubBinding.bind(view);
        binding.hubTitle.setText("Khám phá");
        binding.hubSubtitle.setText("Một chút tò mò, thêm một ván vui");
        if (state != null) {
            launchedGame = state.getString("launchedGame");
            launchedPlayer = state.getString("launchedPlayer");
        }
        MainActivity activity = (MainActivity) requireActivity();
        ActionCardView guide = addCard(binding, 80);
        guide.setId(R.id.guide);
        guide.configure("Sudoku cho người mới", "Hướng dẫn bằng hình · vuốt để học", R.drawable.ic_guide_book,
                R.color.related, R.color.primary);
        guide.setOnClickListener(button -> activity.open(new SudokuGuideFragment()));
        for (GameDefinition game : ExploreGames.list(requireContext())) {
            ActionCardView card = addCard(binding, 88);
            bindGame(card, game, null);
            card.setOnClickListener(button -> chooseGame(game));
            cards.put(game, card);
        }
    }

    private void gameReturned(@Nullable Intent data) {
        String gameId = launchedGame;
        String player = launchedPlayer;
        launchedGame = null;
        launchedPlayer = null;
        GameExitResponse response = null;
        try {
            response = GameContract.readResponse(data);
        } catch (RuntimeException ignored) {
            // A child game may close without a result.
        }
        if (response != null && response.reason == ExitReason.ERROR && isAdded()) {
            GameDefinition definition = null;
            for (GameDefinition game : ExploreGames.list(requireContext())) {
                if (game.id.equals(gameId)) {
                    definition = game;
                    break;
                }
            }
            if (response.errorCode == GameErrorCode.CORRUPT_SAVE && definition != null && player != null) {
                offerCorruptSaveRemoval(definition, player);
            } else {
                notifyError("Chưa mở hoặc lưu được ván. Thử lại nhé.");
            }
        }
        refreshRecords();
    }

    @Override public void onResume() {
        super.onResume();
        refreshRecords();
    }

    private void refreshRecords() {
        if (getView() == null || cards.isEmpty()) {
            return;
        }
        int ticket = ++recordsVersion;
        ExploreResultRepository repository = app().getExploreHistory();
        ExploreSessionStore store = new ExploreSessionStore(requireContext());
        String player = playerId();
        GameDefinition[] definitions = cards.keySet().toArray(new GameDefinition[0]);
        SudokuTask<Map<GameDefinition, String>> work = SudokuTask.supplyAsync(() -> {
            repository.recoverPending(player);
            Map<GameDefinition, String> descriptions = new LinkedHashMap<>();
            for (GameDefinition game : definitions) {
                GameSession checkpoint = null;
                try {
                    checkpoint = store.load(game.id, player);
                } catch (ExploreSessionStore.Failure ignored) {
                    // Opening this game will provide the corrupt-checkpoint action.
                }
                Long best = repository.record(game, player);
                String trailing = null;
                if (checkpoint != null) {
                    trailing = "Tiếp tục ›";
                } else if (best != null) {
                    if (game.ranking == GameDefinition.Ranking.HIGH_SCORE) {
                        trailing = best + " điểm";
                    } else {
                        trailing = Format.duration(best / 1000L);
                    }
                }
                descriptions.put(game, trailing);
            }
            return descriptions;
        });
        AndroidTasks.onComplete(work, records -> {
            if (ticket != recordsVersion || getView() == null || !player.equals(playerId())) {
                return;
            }
            for (Map.Entry<GameDefinition, String> record : records.entrySet()) {
                GameDefinition game = record.getKey();
                ActionCardView card = cards.get(game);
                if (card != null) {
                    bindGame(card, game, record.getValue());
                }
            }
        }, error -> Log.w("SudokuExplore", "Could not read game records", error));
    }

    private void bindGame(ActionCardView card, GameDefinition game, @Nullable String trailing) {
        card.configureGame(game.title, game.description, game.symbol, game.accentColor, trailing);
    }

    private void chooseGame(GameDefinition game) {
        if (difficultyDialog != null || launchedGame != null) {
            return;
        }
        String player = playerId();
        GameSession checkpoint;
        try {
            checkpoint = new ExploreSessionStore(requireContext()).load(game.id, player);
        } catch (ExploreSessionStore.Failure error) {
            offerCorruptSaveRemoval(game, player);
            return;
        }
        if (checkpoint == null) {
            chooseDifficulty(game, player);
        } else {
            AppDialogs.message(this, game.title, "Bạn có một ván đang chơi.", "Tiếp tục", "Bỏ ván",
                    () -> launchGame(game, player, checkpoint, null),
                    () -> confirmDiscard(game, player, checkpoint));
        }
    }

    private void chooseDifficulty(GameDefinition game, String player) {
        if (game.difficultyCount() == 1) {
            launchGame(game, player, null, game.difficultyId(0));
            return;
        }
        String[] labels = new String[game.difficultyCount()];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = game.difficultyLabel(i);
        }
        difficultyDialog = new MaterialAlertDialogBuilder(requireContext()).setTitle(game.title)
                .setItems(labels, (dialog, index) -> launchGame(game, player, null, game.difficultyId(index)))
                .setNegativeButton("Hủy", null).create();
        difficultyDialog.setOnDismissListener(dialog -> difficultyDialog = null);
        difficultyDialog.show();
    }

    private void launchGame(GameDefinition game, String player, @Nullable GameSession checkpoint, @Nullable String difficulty) {
        if (!playerId().equals(player) || launchedGame != null) {
            return;
        }
        GameLaunchRequest request;
        if (checkpoint == null) {
            request = new GameLaunchRequest(game.id, player, LaunchMode.NEW_GAME, null, difficulty);
        } else {
            request = new GameLaunchRequest(game.id, player, LaunchMode.RESUME,
                    checkpoint.sessionId, checkpoint.difficultyId);
        }
        launchedGame = game.id;
        launchedPlayer = player;
        gameLauncher.launch(GameContract.putRequest(new Intent(requireContext(), game.activity), request));
    }

    private void confirmDiscard(GameDefinition game, String player, GameSession session) {
        AppDialogs.message(this, "Bỏ ván đang chơi?", "Tiến trình hiện tại sẽ kết thúc.", "Bỏ ván", "Giữ lại", () -> {
            try {
                GameResult result = new GameResult(game.id, player, session.sessionId,
                        session.difficultyId, GameOutcome.ABANDONED, null,
                        session.activeDurationMs, System.currentTimeMillis(), false);
                new ExploreSessionStore(requireContext()).finish(result);
                chooseDifficulty(game, player);
            } catch (RuntimeException error) {
                notifyError("Chưa bỏ được ván.");
            }
        }, null);
    }

    private void offerCorruptSaveRemoval(GameDefinition game, String player) {
        if (getView() == null || !playerId().equals(player)) {
            return;
        }
        AppDialogs.message(this, game.title, "Ván lưu bị lỗi. Xóa bản lưu để bắt đầu lại?", "Xóa ván lưu", "Hủy", () -> {
            try {
                new ExploreSessionStore(requireContext()).removeCorruptCheckpoint(game.id, player);
                chooseDifficulty(game, player);
            } catch (RuntimeException error) {
                notifyError("Chưa xóa được ván lưu.");
            }
        }, null);
    }

    private void notifyError(String message) {
        View view = getView();
        if (view != null) {
            AppNotices.show(view, message, true, false);
        }
    }

    private String playerId() {
        String player = app().getGames().rankingPlayerId();
        if (player.trim().isEmpty()) {
            return "guest";
        }
        return player;
    }

    private SudokuRuntime app() { return SudokuRuntime.get(requireContext()); }

    private ActionCardView addCard(FragmentHubBinding binding, int height) {
        ActionCardView card = new ActionCardView(requireContext());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(height));
        params.bottomMargin = dp(12);
        card.setLayoutParams(params);
        binding.hubItems.addView(card);
        return card;
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density); }

    @Override public void onSaveInstanceState(@NonNull Bundle state) {
        state.putString("launchedGame", launchedGame);
        state.putString("launchedPlayer", launchedPlayer);
        super.onSaveInstanceState(state);
    }

    @Override public void onDestroyView() {
        recordsVersion++;
        if (difficultyDialog != null) {
            difficultyDialog.dismiss();
        }
        cards.clear();
        super.onDestroyView();
    }
}
