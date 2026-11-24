package com.puzzlehub.sudoku.ui.multiplayer;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.puzzlehub.sudoku.game.R;
import com.puzzlehub.sudoku.SudokuRuntime;
import com.puzzlehub.sudoku.data.model.OnlineRoom;
import com.puzzlehub.sudoku.game.databinding.DialogJoinRoomBinding;
import com.puzzlehub.sudoku.game.databinding.FragmentLobbyBinding;
import com.puzzlehub.sudoku.domain.CompetitiveRank;
import com.puzzlehub.sudoku.domain.Difficulty;
import com.puzzlehub.sudoku.domain.SparkRoomEngine;
import com.puzzlehub.sudoku.ui.AppViewModelFactory;
import com.puzzlehub.sudoku.ui.MainActivity;
import com.puzzlehub.sudoku.ui.social.RankArt;
import com.puzzlehub.sudoku.ui.social.RankTiersDialog;
import com.puzzlehub.sudoku.ui.social.RankViewModel;
import com.puzzlehub.sudoku.ui.widgets.AppDialogs;
import com.puzzlehub.sudoku.ui.widgets.AppNotices;
import com.puzzlehub.sudoku.utils.AndroidTasks;

import java.util.Arrays;
import java.util.Locale;


public final class LobbyFragment extends Fragment {
    private FragmentLobbyBinding binding;
    private MultiplayerViewModel model;
    private RankViewModel rankModel;
    private Difficulty difficulty = Difficulty.BEGINNER;
    private String mode = "COMPETITIVE";
    private String openedMatch = "";
    private RoomCodeDialog.WaitingRoomPopup waitingPopup;
    private Dialog joinDialog;
    private DialogJoinRoomBinding joinForm;

    public LobbyFragment() {
        super(R.layout.fragment_lobby);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        FragmentLobbyBinding views = FragmentLobbyBinding.bind(view);
        binding = views;
        MainActivity activity = (MainActivity) requireActivity();
        ViewModelProvider provider = new ViewModelProvider(
                activity, new AppViewModelFactory(SudokuRuntime.get(activity)));
        model = provider.get(MultiplayerViewModel.class);
        rankModel = provider.get(RankViewModel.class);
        restore(saved);
        setupActions(views);
        AndroidTasks.observe(getViewLifecycleOwner(), model.getState(), state -> render(views, state));
        rankModel.getState().observe(getViewLifecycleOwner(), state -> renderRank(views, state));
        if (saved != null && saved.getBoolean("joining")) {
            showJoinDialog(saved.getString("joinDraft", ""));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        rankModel.refresh();
    }

    private void restore(Bundle saved) {
        if (saved == null) {
            return;
        }
        String difficultyName = saved.getString("difficulty", Difficulty.BEGINNER.name());
        for (Difficulty level : Difficulty.values()) {
            if (level.name().equals(difficultyName)) {
                difficulty = level;
                break;
            }
        }
        mode = saved.getString("mode", "COMPETITIVE");
        openedMatch = saved.getString("match", "");
    }

    private void setupActions(FragmentLobbyBinding views) {
        if (mode.equals("COOPERATIVE")) {
            views.mode.check(R.id.cooperative);
        } else {
            views.mode.check(R.id.competitive);
        }
        views.mode.addOnButtonCheckedListener((group, checkedId, checked) -> {
            if (checked) {
                if (checkedId == R.id.cooperative) {
                    mode = "COOPERATIVE";
                } else {
                    mode = "COMPETITIVE";
                }
            }
        });
        renderDifficulty(views);
        views.difficulty.setOnClickListener(button -> AppDialogs.difficulty(this, level -> {
            difficulty = level;
            renderDifficulty(views);
        }));
        views.lobbyBack.setOnClickListener(button -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        views.createRoom.setOnClickListener(button -> createRoom());
        views.openJoinRoom.setOnClickListener(button -> showJoinDialog(""));
        views.lobbyRankName.setOnClickListener(button -> {
            RankViewModel.State state = rankModel.getState().getValue();
            Long points = null;
            if (state != null) {
                points = state.points();
            }
            RankTiersDialog.show(this, points);
        });
    }

    private void renderDifficulty(FragmentLobbyBinding views) {
        views.difficulty.setText(difficulty.getLabel());
        int icon = switch (difficulty) {
            case BEGINNER -> R.drawable.ic_level_beginner;
            case EASY -> R.drawable.ic_level_easy;
            case MEDIUM -> R.drawable.ic_level_medium;
            case HARD -> R.drawable.ic_level_hard;
        };
        views.difficulty.setIconResource(icon);
    }

    private void showJoinDialog(String draft) {
        if (!canUseRoom("Hãy hủy phòng đang chờ trước khi vào phòng khác.")
                || (joinDialog != null && joinDialog.isShowing())) {
            return;
        }
        DialogJoinRoomBinding form = DialogJoinRoomBinding.inflate(getLayoutInflater());
        InputFilter[] filters = form.roomCode.getFilters();
        InputFilter[] capitals = Arrays.copyOf(filters, filters.length + 1);
        capitals[filters.length] = new InputFilter.AllCaps();
        form.roomCode.setFilters(capitals);
        form.roomCode.setText(draft);
        form.roomCode.setSelection(form.roomCode.length());
        form.roomCode.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable text) {
                form.roomCodeBox.setError(null);
            }
        });

        Dialog dialog = AppDialogs.form(this, "Nhập mã phòng", form.getRoot(),
                "Vào phòng", "Hủy", () -> submitCode(form));
        joinForm = form;
        joinDialog = dialog;
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        form.roomCode.setOnEditorActionListener((field, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_GO) {
                return false;
            }
            if (submitCode(form)) {
                dialog.dismiss();
            }
            return true;
        });
    }

    private boolean submitCode(DialogJoinRoomBinding form) {
        String code = String.valueOf(form.roomCode.getText()).trim().toUpperCase(Locale.ROOT);
        if (!SparkRoomEngine.CODE_PATTERN.matcher(code).matches()) {
            form.roomCodeBox.setError("Mã phòng cần 6 ký tự hợp lệ");
            return false;
        }
        if (!canUseRoom("Hãy hủy phòng đang chờ trước khi vào phòng khác.")) {
            return false;
        }
        openedMatch = "";
        model.join(code);
        return true;
    }

    private void createRoom() {
        if (canUseRoom("Hãy hủy phòng đang chờ trước khi tạo phòng mới.")) {
            openedMatch = "";
            model.create(mode, difficulty.name());
        }
    }

    private boolean canUseRoom(String waitingMessage) {
        MultiplayerState state = model.getState().getValue();
        if (state.getBusy()) {
            return false;
        }
        if (isWaiting(state.getRoom())) {
            AppNotices.show(requireView(), waitingMessage, true, false);
            return false;
        }
        return true;
    }

    private boolean isWaiting(OnlineRoom room) {
        return room != null && (room.getStatus().equals("WAITING") || room.getStatus().equals("READY"));
    }

    private void renderRank(FragmentLobbyBinding views, RankViewModel.State state) {
        Long points = state.points();
        CompetitiveRank rank = state.rank();
        String detail;
        if (points != null) {
            detail = RankArt.points(points) + " điểm Rank";
        } else if (state.loading()) {
            detail = "Đang tải điểm Rank…";
        } else if (state.signedIn()) {
            detail = "Chưa cập nhật được điểm Rank";
        } else {
            detail = "Đăng nhập để tích điểm Rank";
        }
        RankArt.show(views.lobbyRankBadge, rank);
        views.lobbyRankName.setText(rank.getLabel() + "  ›");
        views.lobbyRankName.setContentDescription(rank.getLabel() + ", xem các bậc Rank");
        views.lobbyRankPoints.setText(detail);
    }

    private void render(FragmentLobbyBinding views, MultiplayerState state) {
        OnlineRoom room = state.getRoom();
        boolean waiting = isWaiting(room);
        boolean canUse = !state.getBusy() && !waiting;
        if (waiting) {
            views.lobbyScroll.setAlpha(0.18f);
        } else {
            views.lobbyScroll.setAlpha(1f);
        }
        if (state.getBusy() && !waiting) {
            views.loading.setVisibility(View.VISIBLE);
        } else {
            views.loading.setVisibility(View.GONE);
        }
        views.createRoom.setEnabled(canUse);
        views.openJoinRoom.setEnabled(canUse);
        views.lobbyRankName.setEnabled(!waiting);
        views.competitive.setEnabled(canUse);
        views.cooperative.setEnabled(canUse);
        views.difficulty.setEnabled(canUse);

        if (state.getError() != null) {
            AppNotices.show(views.getRoot(), state.getError(), true, false);
            model.clearError();
        }
        if (room == null) {
            dismissWaitingPopup();
            openedMatch = "";
            return;
        }
        if (waiting) {
            if (joinDialog != null) {
                joinDialog.dismiss();
            }
            showWaitingPopup(views, room, state.getBusy());
        } else {
            dismissWaitingPopup();
            openMatchWhenReady(room);
        }
    }

    private void showWaitingPopup(FragmentLobbyBinding views, OnlineRoom room, boolean busy) {
        boolean host = model.isCurrentUserHost(room);
        if (waitingPopup == null || !waitingPopup.isShowing()) {
            waitingPopup = RoomCodeDialog.showInOverlay(this, views.waitingOverlay, room, host, busy,
                    this::copyRoomCode, model::startMatch, this::cancelWaitingRoom);
        } else {
            waitingPopup.render(room, host, busy);
        }
    }

    private void dismissWaitingPopup() {
        if (waitingPopup != null) {
            waitingPopup.dismissQuietly();
            waitingPopup = null;
        }
        if (binding != null) {
            binding.waitingOverlay.setVisibility(View.GONE);
            binding.lobbyScroll.setAlpha(1f);
        }
    }

    private void cancelWaitingRoom() {
        dismissWaitingPopup();
        model.leave(() -> {
            openedMatch = "";
            if (binding != null) {
                AppNotices.show(binding.getRoot(), "Đã hủy phòng chờ", false, true);
            }
        });
    }

    private void openMatchWhenReady(OnlineRoom room) {
        String status = room.getStatus();
        boolean ready = status.equals("PLAYING") || status.equals("PAUSED") || status.equals("FINISHED");
        if (ready && !openedMatch.equals(room.getCode())) {
            openedMatch = room.getCode();
            ((MainActivity) requireActivity()).openOnlineMatch(room.getCode());
        }
    }

    private void copyRoomCode(String code) {
        ClipboardManager clipboard = requireContext().getSystemService(ClipboardManager.class);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Mã phòng Sudoku", code));
        }
        if (binding != null) {
            AppNotices.success(binding.getRoot(), "Đã chép mã phòng");
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString("match", openedMatch);
        outState.putString("mode", mode);
        outState.putString("difficulty", difficulty.name());
        if (joinDialog != null && joinDialog.isShowing() && joinForm != null) {
            outState.putBoolean("joining", true);
            outState.putString("joinDraft", String.valueOf(joinForm.roomCode.getText()));
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroyView() {
        dismissWaitingPopup();
        if (joinDialog != null) {
            joinDialog.dismiss();
        }
        joinDialog = null;
        joinForm = null;
        binding = null;
        super.onDestroyView();
    }
}
