package com.puzzlehub.sudoku.ui.game;

import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.puzzlehub.sudoku.domain.Difficulty;
import com.puzzlehub.sudoku.domain.GameController;
import com.puzzlehub.sudoku.domain.GameSession;
import com.puzzlehub.sudoku.domain.GameStatus;
import com.puzzlehub.sudoku.domain.Hint;
import com.puzzlehub.sudoku.domain.HintLesson;
import com.puzzlehub.sudoku.domain.HintLessonStep;
import com.puzzlehub.sudoku.domain.SudokuBoard;
import com.puzzlehub.sudoku.game.R;
import com.puzzlehub.sudoku.game.SudokuActivity;
import com.puzzlehub.sudoku.game.databinding.FragmentGameBinding;
import com.puzzlehub.sudoku.game.databinding.SheetTutorBinding;
import com.puzzlehub.sudoku.ui.widgets.AppDialogs;
import com.puzzlehub.sudoku.ui.widgets.AppNotices;
import com.puzzlehub.sudoku.utils.Format;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class GameFragment extends Fragment {
    private GameViewModel model;
    private FragmentGameBinding binding;
    private NumberPad pad;
    private BottomSheetDialog tutorSheet;
    private SheetTutorBinding tutorBinding;
    private BottomSheetDialog resultSheet;
    private boolean endedShown;
    private long correctUntil;
    private long renderedMoveVersion = -1L;

    public GameFragment() {
        super(R.layout.fragment_game);
    }

    public static GameFragment newGame(Difficulty difficulty) {
        GameFragment fragment = new GameFragment();
        Bundle arguments = new Bundle();
        arguments.putString("difficulty", difficulty.name());
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding = FragmentGameBinding.bind(view);
        endedShown = false;
        SudokuGameServices services;
        if (requireActivity() instanceof SudokuGameServices) {
            services = (SudokuGameServices) requireActivity();
        } else {
            services = (SudokuGameServices) requireActivity().getApplication();
        }
        model = new ViewModelProvider(this, new ViewModelProvider.Factory() {
            @NonNull
            @Override
            public <T extends ViewModel> T create(@NonNull Class<T> type) {
                return Objects.requireNonNull(type.cast(new GameViewModel(services.getSudokuStorage(), services.getSudokuTutor())));
            }
        }).get(GameViewModel.class);
        pad = new NumberPad(binding.numberKeys, model::enter);
        binding.board.setOnCellSelected(model::select);
        binding.notes.setOnClickListener(button -> model.togglePencil());
        binding.erase.setOnClickListener(button -> model.enter(0));
        binding.undo.setOnClickListener(button -> model.undo());
        binding.hint.setOnClickListener(button -> model.requestHint());
        binding.pause.setOnClickListener(button -> {
            model.pause(true);
            showPause();
        });
        binding.pauseOverlay.setOnClickListener(button -> showPause());
        Difficulty difficulty = null;
        Bundle arguments = getArguments();
        if (arguments != null && arguments.containsKey("difficulty")) {
            difficulty = Difficulty.valueOf(arguments.getString("difficulty"));
        }
        model.start(difficulty);
        model.state().observe(getViewLifecycleOwner(), this::render);
    }

    @Override
    public void onStart() {
        super.onStart();
        model.setForeground(true);
    }

    @Override
    public void onStop() {
        model.setForeground(false);
        model.pause(true);
        super.onStop();
    }

    public void saveBeforeExit() {
        if (model != null) {
            model.save();
        }
    }

    private void render(GameUiState state) {
        visible(binding.loading, state.loading());
        if (state.message() != null) {
            AppNotices.show(binding.getRoot(), state.message(), false, false);
            model.clearMessage();
        }
        GameSession session = state.session();
        if (session == null) {
            return;
        }

        Set<Integer> wrong = new HashSet<>();
        int total = 0;
        int correct = 0;
        for (int index = 0; index < SudokuBoard.CELL_COUNT; index++) {
            int value = session.board().get(index);
            int solution = session.puzzle().solution().get(index);
            if (value != 0 && value != solution) {
                wrong.add(index);
            }
            if (session.puzzle().initial().get(index) == 0) {
                total++;
                if (value == solution) {
                    correct++;
                }
            }
        }

        binding.board.render(session.board(), session.puzzle().initial(), session.notes(), state.selected(),
                session.hinted(), wrong, state.highlight());
        boolean playing = session.status() == GameStatus.PLAYING;
        binding.board.setEnabled(playing);
        pad.render(session.board(), playing);
        visible(binding.pauseOverlay, session.status() == GameStatus.PAUSED);
        for (View button : new View[]{binding.undo, binding.erase, binding.notes, binding.hint, binding.pause}) {
            button.setEnabled(playing);
        }
        if (state.pencil()) {
            binding.notes.setAlpha(1f);
            binding.notes.setText("Chì · BẬT");
        } else {
            binding.notes.setAlpha(0.7f);
            binding.notes.setText(R.string.notes);
        }
        binding.score.setText(session.getScore() + " điểm");
        binding.timer.setText(Format.duration(session.elapsedSeconds()));
        binding.mistakes.setText("Lỗi " + session.mistakes() + "/" + GameController.MAX_MISTAKES);
        int mistakeColor = R.color.ink;
        if (session.mistakes() > 0) {
            mistakeColor = R.color.error;
        }
        binding.mistakes.setTextColor(ContextCompat.getColor(requireContext(), mistakeColor));
        binding.progress.setText("✓ " + correct + "/" + total);
        binding.difficulty.setText(session.puzzle().difficulty().getLabel());
        renderFeedback(state, playing);

        String footnote = "Sai 3 lần là thua";
        if (session.status() == GameStatus.LOST) {
            footnote = "Đã hết lượt · 3/3 lỗi";
        } else if (session.status() == GameStatus.WON) {
            footnote = "Hoàn thành!";
        }
        binding.footnote.setText(footnote);

        if (state.hint() != null && tutorSheet == null) {
            showTutor(state.hint());
        }
        if (tutorBinding != null && state.hint() != null) {
            renderTutor(state, session);
        }
        boolean terminal = session.status() == GameStatus.WON || session.status() == GameStatus.LOST;
        if (terminal && !endedShown) {
            endedShown = true;
            showResult(session);
        }
    }

    private void renderFeedback(GameUiState state, boolean playing) {
        if (renderedMoveVersion != state.moveVersion()) {
            correctUntil = 0;
            renderedMoveVersion = state.moveVersion();
        }
        if (state.correctCell() != null) {
            correctUntil = SystemClock.uptimeMillis() + 1400;
            binding.board.showCorrectFeedback(state.correctCell());
            model.consumeCorrectCell();
        }
        int accent;
        if (playing && SystemClock.uptimeMillis() < correctUntil) {
            binding.banner.setText("✓ Chính xác");
            binding.banner.setLetterSpacing(0);
            accent = R.color.hint;
        } else {
            binding.banner.setText("CHƠI ĐƠN");
            binding.banner.setLetterSpacing(0.1f);
            accent = R.color.primary;
        }
        binding.banner.setTextColor(ContextCompat.getColor(requireContext(), accent));
    }

    private void renderTutor(GameUiState state, GameSession session) {
        Hint hint = state.hint();
        HintLessonStep step = HintLesson.steps(hint).get(state.hintStep());
        tutorBinding.lessonBoard.setBoard(session.board());
        tutorBinding.lessonBoard.setTarget(hint.getIndex());
        tutorBinding.lessonBoard.setHighlighted(step.highlighted());
        String description = "Ô hàng " + (hint.row() + 1) + ", cột " + (hint.column() + 1) + ". ";
        if (step.revealAnswer()) {
            tutorBinding.lessonBoard.setAnswer(hint.value());
            tutorBinding.explanation.setText(state.explanation());
            description += "Đáp án " + hint.value();
        } else {
            tutorBinding.lessonBoard.setAnswer(null);
            tutorBinding.explanation.setText(step.text());
            description += "Các ô liên quan được tô sáng";
        }
        if (step.showCandidates()) {
            tutorBinding.lessonBoard.setCandidates(hint.candidates());
        } else {
            tutorBinding.lessonBoard.setCandidates(Collections.emptySet());
        }
        tutorBinding.lessonBoard.setContentDescription(description);
        tutorBinding.lessonStep.setText(step.title());
        tutorBinding.explain.setEnabled(!state.explaining());
        if (state.hintStep() < 2) {
            tutorBinding.explain.setText("Xem bước tiếp");
        } else if (state.explaining()) {
            tutorBinding.explain.setText("Đang diễn giải…");
        } else {
            tutorBinding.explain.setText("Diễn giải thêm");
        }
        tutorBinding.lessonPrevious.setEnabled(state.hintStep() > 0);
        visible(tutorBinding.apply, step.revealAnswer());
    }

    private void showResult(GameSession session) {
        if (tutorSheet != null) {
            tutorSheet.dismiss();
        }
        boolean won = session.status() == GameStatus.WON;
        String title;
        String detail;
        String primary;
        if (won) {
            title = "Hoàn thành!";
            detail = "Bạn đã giải xong bảng Sudoku";
            primary = "Chơi tiếp";
        } else {
            title = "Thử lại nhé";
            detail = "Đã đủ 3 lỗi · Bắt đầu ván mới";
            primary = "Ván mới";
        }
        GameResultSummary result = new GameResultSummary(won, title, detail, session.elapsedSeconds(),
                session.getScore(), session.mistakes(), primary, "Về sảnh", false);
        resultSheet = GameResultSheet.show(this, result, () -> {
            resultSheet = null;
            endedShown = false;
            correctUntil = 0;
            model.restart();

        }, () -> {
            leaveGame();

        });
    }

    private void showTutor(Hint hint) {
        SheetTutorBinding sheet = SheetTutorBinding.inflate(getLayoutInflater());
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        tutorBinding = sheet;
        tutorSheet = dialog;
        switch (hint.strategy()) {
            case NAKED_SINGLE:
                sheet.strategy.setText("Ô chỉ còn một số");
                break;
            case HIDDEN_SINGLE:
                sheet.strategy.setText("Số chỉ có một chỗ");
                break;
            case NAKED_PAIR:
                sheet.strategy.setText("Loại bằng cặp số");
                break;
            case SEARCH:
                sheet.strategy.setText("Hỗ trợ bằng tìm kiếm");
                break;
        }
        sheet.explain.setOnClickListener(view -> {
            if (model.hintStep() < 2) {
                model.changeHintStep(1);
            } else {
                model.explainHint();
            }
        });
        sheet.lessonPrevious.setOnClickListener(view -> model.changeHintStep(-1));
        sheet.apply.setOnClickListener(view -> {
            model.applyHint();
            dialog.dismiss();
        });
        sheet.close.setOnClickListener(view -> dialog.dismiss());
        dialog.setContentView(sheet.getRoot());
        dialog.setOnDismissListener(ignored -> {
            tutorSheet = null;
            tutorBinding = null;
            model.clearHint();
        });
        dialog.show();
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    private void leaveGame() {
        if (getActivity() instanceof SudokuActivity) {
            ((SudokuActivity) getActivity()).finishGame();
        } else {
            getParentFragmentManager().popBackStack();
        }
    }

    private void showPause() {
        AppDialogs.message(this, getString(R.string.pause), "Bảng đã được ẩn và đồng hồ đã dừng.",
                getString(R.string.resume), "Lưu và về", R.drawable.ic_pause, false, () -> {
                    model.pause(false);

                }, () -> {
                    model.save();
                    leaveGame();

                }, false);
    }

    private static void visible(View view, boolean visible) {
        if (visible) {
            view.setVisibility(View.VISIBLE);
        } else {
            view.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        if (resultSheet != null) {
            resultSheet.dismiss();
            resultSheet = null;
        }
        if (tutorSheet != null) {
            tutorSheet.dismiss();
            tutorSheet = null;
        }
        tutorBinding = null;
        pad = null;
        binding = null;
        super.onDestroyView();
    }
}
