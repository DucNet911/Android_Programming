package com.puzzlehub.sudoku.ui.game;

import android.view.LayoutInflater;
import android.widget.LinearLayout;
import androidx.core.util.Consumer;
import com.puzzlehub.sudoku.domain.SudokuBoard;
import com.puzzlehub.sudoku.game.databinding.ItemNumberBinding;
import com.puzzlehub.sudoku.utils.GameSounds;
import java.util.ArrayList;
import java.util.List;

public final class NumberPad {
    private final List<ItemNumberBinding> keys = new ArrayList<>(9);

    public NumberPad(LinearLayout container, Consumer<Integer> onNumber) {
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(container.getContext());
        for (int number = 1; number <= 9; number++) {
            int digit = number;
            ItemNumberBinding item = ItemNumberBinding.inflate(inflater, container, false);
            item.number.setText(String.valueOf(digit));
            item.getRoot().setOnClickListener(view -> {
                GameSounds.playMove(view.getContext());
                onNumber.accept(digit);
            });
            container.addView(item.getRoot());
            keys.add(item);
        }
    }

    public void render(SudokuBoard board, boolean enabled) {
        int[] counts = new int[10];
        for (int index = 0; index < SudokuBoard.CELL_COUNT; index++) {
            int value = board.get(index);
            if (value >= 1 && value <= 9) {
                counts[value]++;
            }
        }
        for (int index = 0; index < keys.size(); index++) {
            int digit = index + 1;
            int remaining = Math.max(0, 9 - counts[digit]);
            ItemNumberBinding item = keys.get(index);
            item.remaining.setText(String.valueOf(remaining));
            boolean available = enabled && remaining > 0;
            item.getRoot().setEnabled(available);
            if (remaining == 0) {
                item.getRoot().setAlpha(0.34f);
            } else {
                item.getRoot().setAlpha(1f);
            }
            item.getRoot().setContentDescription("Số " + digit + ", còn " + remaining + " ô");
        }
    }
}
