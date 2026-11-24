package com.puzzlehub.sudoku.ui.explore;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.puzzlehub.sudoku.game.R;
import com.puzzlehub.sudoku.game.databinding.FragmentSudokuGuideBinding;
import com.puzzlehub.sudoku.domain.Difficulty;
import com.puzzlehub.sudoku.domain.SudokuBoard;
import com.puzzlehub.sudoku.ui.MainActivity;
import com.puzzlehub.sudoku.ui.game.GameFragment;
import com.puzzlehub.sudoku.ui.widgets.LessonBoardView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class SudokuGuideFragment extends Fragment {
    private static final String[] TITLES = {"01  Mỗi hàng đủ 1–9", "02  Mỗi cột cũng vậy", "03  Mỗi vùng 3×3 đủ 1–9",
            "04  Chỉ thiếu một số", "05  Chơi theo nhịp của bạn"};
    private static final String[] CAPTIONS = {"Không lặp số trong hàng đang sáng", "Không lặp số trong cột đang sáng",
            "Chín ô nhỏ, chín số khác nhau", "Hàng này còn thiếu gì? Chạm số bên dưới",
            "Ghi chú khi chưa chắc · sai 3 lần là thua"};
    private static final String[] BOARD_HINTS = {"Hàng đầu tiên tô xanh", "Cột thứ năm tô xanh", "Vùng ở giữa tô xanh",
            "Hàng đầu: ô trống, 3, 4, 6, 7, 8, 9, 1, 2", "Ô có ba số ghi chú nhỏ 2, 5, 7"};
    private static final SudokuBoard SOLVED = SudokuBoard.decode(
            "534678912672195348198342567859761423426853791713924856961537284287419635345286179");
    private int page;

    public SudokuGuideFragment() { super(R.layout.fragment_sudoku_guide); }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        FragmentSudokuGuideBinding binding = FragmentSudokuGuideBinding.bind(view);
        if (saved != null) {
            page = saved.getInt("page");
        }
        LinearLayoutManager layout = new LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false);
        binding.guidePager.setLayoutManager(layout);
        PagerSnapHelper snap = new PagerSnapHelper();
        snap.attachToRecyclerView(binding.guidePager);
        binding.guidePager.setAdapter(new Pages());
        binding.guidePager.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrollStateChanged(@NonNull RecyclerView recycler, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    View centered = snap.findSnapView(layout);
                    if (centered != null) {
                        update(binding, layout.getPosition(centered));
                    }
                }
            }
        });
        binding.guidePrevious.setOnClickListener(button -> binding.guidePager.smoothScrollToPosition(Math.max(0, page - 1)));
        binding.guideNext.setOnClickListener(button -> {
            if (page == TITLES.length - 1) {
                ((MainActivity) requireActivity()).open(GameFragment.newGame(Difficulty.BEGINNER));
            } else {
                binding.guidePager.smoothScrollToPosition(page + 1);
            }
        });
        binding.guidePager.scrollToPosition(page);
        update(binding, page);
    }

    private void update(FragmentSudokuGuideBinding binding, int index) {
        page = Math.max(0, Math.min(TITLES.length - 1, index));
        StringBuilder dots = new StringBuilder();
        for (int position = 0; position < TITLES.length; position++) {
            if (position != 0) {
                dots.append("  ");
            }
            if (position == page) {
                dots.append("●");
            } else {
                dots.append("○");
            }
        }
        binding.guideDots.setText(dots);
        binding.guidePrevious.setEnabled(page > 0);
        if (page == TITLES.length - 1) {
            binding.guideNext.setText("Chơi thử");
        } else {
            binding.guideNext.setText("Tiếp");
        }
        binding.guidePager.setContentDescription("Hướng dẫn " + (page + 1) + "/" + TITLES.length + ". Vuốt sang trang kế tiếp");
    }

    private final class Pages extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override public int getItemCount() { return TITLES.length; }

        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout panel = new LinearLayout(parent.getContext());
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setGravity(Gravity.CENTER_VERTICAL);
            panel.setLayoutParams(new RecyclerView.LayoutParams(-1, -1));
            return new RecyclerView.ViewHolder(panel) {};
        }

        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            LinearLayout panel = (LinearLayout) holder.itemView;
            panel.removeAllViews();
            panel.setPadding(dp(2), 0, dp(2), 0);
            panel.addView(label(TITLES[position], 19f, true));

            LessonBoardView diagram = new LessonBoardView(requireContext());
            diagram.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
            diagram.setBoard(boardFor(position));
            diagram.setHighlighted(highlightedCells(position));
            if (position >= 3) {
                diagram.setTarget(0);
            }
            if (position == 4) {
                diagram.setCandidates(new HashSet<>(Arrays.asList(2, 5, 7)));
            }
            diagram.setContentDescription(CAPTIONS[position] + ". " + BOARD_HINTS[position]);
            panel.addView(diagram);

            TextView caption = label(CAPTIONS[position], 14f, false);
            panel.addView(caption);
            if (position == 3) {
                panel.addView(choices(diagram, caption));
            }
        }
    }

    private SudokuBoard boardFor(int page) {
        if (page == 3) {
            return SOLVED.withValue(0, 0);
        }
        ArrayList<Integer> values = new ArrayList<>(SudokuBoard.CELL_COUNT);
        for (int index = 0; index < SudokuBoard.CELL_COUNT; index++) {
            boolean given;
            if (page == 4) {
                given = index != 0 && index % 3 == 0;
            } else {
                given = index * 7 % 11 < 4;
            }
            int value = 0;
            if (given) {
                value = SOLVED.get(index);
            }
            values.add(value);
        }
        return new SudokuBoard(values);
    }

    private Set<Integer> highlightedCells(int position) {
        if (position == 2) {
            return new HashSet<>(Arrays.asList(30, 31, 32, 39, 40, 41, 48, 49, 50));
        }
        Set<Integer> result = new HashSet<>();
        for (int index = 0; index < 9; index++) {
            if (position == 1) {
                result.add(index * 9 + 4);
            } else {
                result.add(index);
            }
        }
        return result;
    }

    private LinearLayout choices(LessonBoardView diagram, TextView caption) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setGravity(Gravity.CENTER);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int number : new int[]{3, 5, 8}) {
            MaterialButton choice = new MaterialButton(requireContext());
            choice.setText(String.valueOf(number));
            choice.setContentDescription("Thử số " + number);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(72), dp(52));
            params.setMargins(dp(4), 0, dp(4), 0);
            choice.setLayoutParams(params);
            choice.setOnClickListener(view -> {
                if (number == 5) {
                    diagram.setAnswer(5);
                    caption.setText("Đúng rồi! Hàng này đã đủ 1–9");
                    caption.setTextColor(color(R.color.hint));
                } else {
                    caption.setText("Số " + number + " đã có trong hàng. Thử lại nhé");
                    caption.setTextColor(color(R.color.error));
                }
            });
            row.addView(choice);
        }
        return row;
    }

    private TextView label(String text, float size, boolean bold) {
        TextView label = new TextView(requireContext());
        label.setText(text);
        label.setTextSize(size);
        label.setGravity(Gravity.CENTER);
        label.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        label.setPadding(0, dp(6), 0, dp(6));
        if (bold) {
            label.setTypeface(Typeface.DEFAULT_BOLD);
            label.setTextColor(color(R.color.ink));
        } else {
            label.setTextColor(color(R.color.muted));
        }
        return label;
    }

    @Override public void onSaveInstanceState(@NonNull Bundle state) {
        state.putInt("page", page);
        super.onSaveInstanceState(state);
    }

    private int color(int resource) { return ContextCompat.getColor(requireContext(), resource); }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density); }
}
