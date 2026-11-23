package com.puzzlehub.sudoku.ui.game;

import android.view.LayoutInflater;
import android.widget.LinearLayout;
import androidx.core.util.Consumer;
import com.puzzlehub.sudoku.domain.SudokuBoard;
import com.puzzlehub.sudoku.game.databinding.ItemNumberBinding;

public final class NumberPad {
    private final ItemNumberBinding[] keys = new ItemNumberBinding[9];

    public NumberPad(LinearLayout container, Consumer<Integer> onNumber) {
        LayoutInflater inflater = LayoutInflater.from(container.getContext());
        for (int index = 0; index < keys.length; index++) {
            int number = index + 1;
            ItemNumberBinding binding = ItemNumberBinding.inflate(inflater, container, false);
            binding.number.setText(String.valueOf(number));
            binding.getRoot().setContentDescription("Nhập số " + number);
            binding.getRoot().setOnClickListener(view -> onNumber.accept(number));
            container.addView(binding.getRoot());
            keys[index] = binding;
        }
    }

    public void render(SudokuBoard board, boolean enabled) {
        int[] counts = new int[10];
        for (int value : board.getCells()) {
            counts[value]++;
        }
        float alpha = 0.45f;
        if (enabled) {
            alpha = 1f;
        }
        for (int index = 0; index < keys.length; index++) {
            ItemNumberBinding key = keys[index];
            key.remaining.setText(String.valueOf(Math.max(0, 9 - counts[index + 1])));
            key.getRoot().setEnabled(enabled);
            key.getRoot().setAlpha(alpha);
        }
    }
}
