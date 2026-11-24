package com.puzzlehub.sudoku.ui.multiplayer;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.puzzlehub.sudoku.game.R;
import com.puzzlehub.sudoku.SudokuRuntime;
import com.puzzlehub.sudoku.data.model.OnlineMove;
import com.puzzlehub.sudoku.data.model.OnlinePlayer;
import com.puzzlehub.sudoku.data.model.OnlineRoom;
import com.puzzlehub.sudoku.domain.CompetitiveRank;
import com.puzzlehub.sudoku.domain.Difficulty;
import com.puzzlehub.sudoku.domain.SudokuBoard;
import com.puzzlehub.sudoku.game.databinding.FragmentGameBinding;
import com.puzzlehub.sudoku.ui.AppViewModelFactory;
import com.puzzlehub.sudoku.ui.game.GameResultSheet;
import com.puzzlehub.sudoku.ui.game.GameResultSummary;
import com.puzzlehub.sudoku.ui.game.NumberPad;
import com.puzzlehub.sudoku.ui.widgets.AppDialogs;
import com.puzzlehub.sudoku.ui.widgets.AppNotices;
import com.puzzlehub.sudoku.utils.AndroidTasks;
import com.puzzlehub.sudoku.utils.Format;
import java.util.Set;

public final class OnlineGameFragment extends Fragment {
    private final Handler main = new Handler(Looper.getMainLooper());
    private MultiplayerViewModel model;
    private FragmentGameBinding binding;
    private boolean endedShown;
    private boolean highlight = true;
    private BottomSheetDialog resultSheet;
    private Integer previousCorrect;
    private final Runnable clock = new Runnable() {
        @Override public void run() {
            if (binding == null) {
                return;
            }
            OnlineRoom room = model.getState().getValue().room();
            if (room != null) {
                long end = room.finishedAt();
                if (end <= 0) {
                    end = model.getNowMillis();
                }
                binding.timer.setText(Format.duration(Math.max(0, end - room.startTime()) / 1000));
            }
            main.postDelayed(this, 1000);
        }
    };

    public OnlineGameFragment() {
        super(R.layout.fragment_game);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedState) {
        SudokuRuntime app = SudokuRuntime.get(requireContext());
        model = new ViewModelProvider(requireActivity(), new AppViewModelFactory(app)).get(MultiplayerViewModel.class);
        FragmentGameBinding views = FragmentGameBinding.bind(view);
        binding = views;
        NumberPad pad = new NumberPad(views.numberKeys, model::move);
        endedShown = savedState != null && savedState.getBoolean("ended");
        views.board.setOnCellSelected(model::select);
        views.erase.setOnClickListener(ignored -> model.move(0));
        views.notes.setOnClickListener(ignored -> model.pencil());
        views.undo.setEnabled(false);
        views.hint.setEnabled(false);
        views.pause.setVisibility(View.GONE);
        views.footnote.setText("Sai 3 lần là thua");
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override public void handleOnBackPressed() { confirmLeave(); }
                });
        getViewLifecycleOwner().getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override public void onStart(@NonNull LifecycleOwner owner) { clock.run(); }
            @Override public void onStop(@NonNull LifecycleOwner owner) { main.removeCallbacks(clock); }
        });
        AndroidTasks.onComplete(app.getGames().settings(), settings -> {
            if (binding == views) {
                highlight = settings.highlight;
                render(views, pad, model.getState().getValue());
            }
        }, ignored -> {});
        AndroidTasks.observe(getViewLifecycleOwner(), model.getState(), state -> render(views, pad, state));
    }

    private void render(FragmentGameBinding views, NumberPad pad, MultiplayerState state) {
        if (state.busy()) {
            views.loading.setVisibility(View.VISIBLE);
        } else {
            views.loading.setVisibility(View.GONE);
        }
        if (state.error() != null) {
            AppNotices.show(views.getRoot(), state.error(), true, false);
            model.clearError();
        }
        OnlineRoom room = state.room();
        if (room == null) {
            return;
        }
        if ("CANCELLED".equals(room.status()) && !endedShown) {
            endedShown = true;
            View root = requireActivity().findViewById(R.id.root);
            model.leave(() -> {
                popBack();
                if (root != null) {
                    AppNotices.show(root, "Người chơi kia đã rời phòng, trận đấu đã hủy.", true, false);
                }
            });
            return;
        }
        OnlinePlayer me = null;
        OnlinePlayer opponent = null;
        for (OnlinePlayer player : room.players()) {
            if (player.uid().equals(model.getUid())) {
                me = player;
            } else if (opponent == null) {
                opponent = player;
            }
        }
        if (me == null) {
            return;
        }
        SudokuBoard board = model.board(room);
        views.board.render(board, room.initial(), state.notes(), state.selected(),
                java.util.Collections.emptySet(), java.util.Collections.emptySet(), highlight);
        OnlineMove move = room.lastMove();
        if (previousCorrect != null && me.correct() > previousCorrect && move != null
                && ("COOPERATIVE".equals(room.mode()) || move.playerId().equals(model.getUid()))) {
            views.board.showCorrectFeedback(move.index());
        }
        previousCorrect = me.correct();
        boolean enabled = "PLAYING".equals(room.status()) && state.connected() && !state.busy();
        views.board.setEnabled(enabled);
        pad.render(board, enabled);
        views.erase.setEnabled(enabled);
        views.notes.setEnabled(enabled);
        views.score.setText(me.score() + " điểm");
        views.mistakes.setText("Lỗi " + me.mistakes() + "/3");
        views.difficulty.setText(Difficulty.valueOf(room.difficulty()).getLabel());
        if (state.pencil()) {
            views.notes.setText("Viết chì · BẬT");
        } else {
            views.notes.setText(R.string.notes);
        }
        int total = 0;
        for (int index = 0; index < 81; index++) {
            if (room.initial().get(index) == 0) {
                total++;
            }
        }
        views.progress.setText("✓ " + me.correct() + "/" + total);
        if (!state.connected()) {
            views.banner.setText("Bạn mất kết nối · Đang kết nối lại…");
        } else if ("PAUSED".equals(room.status())) {
            views.banner.setText("Có người mất kết nối · Chờ tối đa 60 giây");
        } else if ("FINISHED".equals(room.status())) {
            views.banner.setText("Trận đấu đã kết thúc");
        } else {
            String opponentName = "Bạn chơi";
            int opponentCorrect = 0;
            int opponentScore = 0;
            int opponentMistakes = 0;
            if (opponent != null) {
                opponentName = opponent.name();
                opponentCorrect = opponent.correct();
                opponentScore = opponent.score();
                opponentMistakes = opponent.mistakes();
            }
            views.banner.setText("Phòng " + room.code() + " · " + Format.modeLabel(room.mode()) + "\n"
                    + opponentName + ": ✓ " + opponentCorrect + "/" + total + " ô · "
                    + opponentScore + " điểm · Lỗi " + opponentMistakes + "/3");
        }
        if ("FINISHED".equals(room.status()) && !endedShown) {
            endedShown = true;
            showResult(room, me);
        }
    }

    private void showResult(OnlineRoom room, OnlinePlayer me) {
        boolean won = "TEAM".equals(room.winnerId()) || room.winnerId().equals(model.getUid());
        boolean anyMaxMistakes = false;
        for (OnlinePlayer p : room.players()) {
            if (p.mistakes() >= 3) {
                anyMaxMistakes = true;
                break;
            }
        }
        String title;
        if ("TEAM".equals(room.winnerId())) {
            title = "Cùng nhau hoàn thành!";
        } else if ("COOPERATIVE".equals(room.mode()) && anyMaxMistakes) {
            title = "Hết lượt · Đội đã thua";
        } else if (me.mistakes() >= 3) {
            title = "Bạn thua · Đã mắc 3 lỗi";
        } else if (won) {
            title = "Bạn thắng!";
        } else {
            title = "Bạn chơi đã hoàn thành trước";
        }
        String detail;
        if (!model.affectsCompetitiveRank(room)) {
            detail = "Kết quả đã lưu vào lịch sử";
        } else if (won) {
            detail = "+" + CompetitiveRank.POINTS_PER_WIN + " điểm rank khi đồng bộ";
        } else {
            detail = "Trừ tối đa " + CompetitiveRank.POINTS_PER_LOSS + " điểm rank khi đồng bộ";
        }
        long seconds = Math.max(0, room.finishedAt() - room.startTime()) / 1000;
        resultSheet = GameResultSheet.show(this, new GameResultSummary(won, title, detail, seconds,
                me.score(), me.mistakes(), "Rời phòng", "Xem bảng", true),
                () -> model.leave(this::popBack), () -> resultSheet = null);
    }

    private void confirmLeave() {
        AppDialogs.message(this, getString(R.string.leave_room), "Rời phòng sẽ hủy trận chưa kết thúc.",
                getString(R.string.leave_room), getString(R.string.close), () -> model.leave(this::popBack), null);
    }

    private void popBack() {
        if (isAdded()) {
            getParentFragmentManager().popBackStack();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle state) {
        state.putBoolean("ended", endedShown);
        super.onSaveInstanceState(state);
    }

    @Override
    public void onDestroyView() {
        main.removeCallbacks(clock);
        if (resultSheet != null) {
            resultSheet.dismiss();
            resultSheet = null;
        }
        previousCorrect = null;
        binding = null;
        super.onDestroyView();
    }
}
