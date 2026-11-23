package com.puzzlehub.sudoku.domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public final class HintLesson {
    private HintLesson() {}

    public static String details(Hint hint, SudokuBoard board) {
        int row = hint.row();
        int column = hint.column();
        int box = (row / 3) * 3 + column / 3;
        String rowValues = numbers(board, SudokuValidator.units.get(row));
        String columnValues = numbers(board, SudokuValidator.units.get(9 + column));
        String boxValues = numbers(board, SudokuValidator.units.get(18 + box));
        String candidates = join(new TreeSet<>(SudokuValidator.candidates(board, hint.getIndex())));
        return "Hàng " + (row + 1) + ": " + rowValues
                + "\nCột " + (column + 1) + ": " + columnValues
                + "\nVùng 3×3: " + boxValues
                + "\nCòn có thể thử: " + candidates + "\n\n" + hint.explanation();
    }

    public static List<HintLessonStep> steps(Hint hint) {
        String location = "hàng " + (hint.row() + 1) + ", cột " + (hint.column() + 1);
        Set<Integer> related = new LinkedHashSet<>(hint.related());
        if (related.isEmpty()) {
            related.addAll(SudokuValidator.peers(hint.getIndex()));
        }
        String middle;
        switch (hint.strategy()) {
            case NAKED_SINGLE:
                middle = "Loại các số đã có ở hàng, cột và vùng. Ô này chỉ còn một ứng viên.";
                break;
            case HIDDEN_SINGLE:
                middle = "Nhìn các ô trống trong nhóm đang sáng. Chỉ một ô trong nhóm nhận được số cần tìm.";
                break;
            case NAKED_PAIR:
                middle = "Hai ô cùng nhóm giữ đúng một cặp số. Các ô khác không thể dùng hai số đó.";
                break;
            case SEARCH:
                middle = "Chưa tìm được bước Single hoặc Pair. Solver kiểm tra các nhánh; bước này dùng tìm kiếm.";
                break;
            default:
                throw new IllegalArgumentException("Unknown tutor strategy");
        }
        return Arrays.asList(
                new HintLessonStep("1 · Nhìn vùng liên quan",
                        "Quan sát ô xanh ở " + location + " và các ô đang sáng. Bạn thử đoán trước nhé.",
                        related, false, false),
                new HintLessonStep("2 · Thu hẹp lựa chọn", middle, related, true, false),
                new HintLessonStep("3 · Kiểm tra đáp án", hint.explanation(), related, false, true));
    }

    private static String numbers(SudokuBoard board, List<Integer> indices) {
        Set<Integer> values = new TreeSet<>();
        for (int index : indices) {
            int value = board.get(index);
            if (value != 0) {
                values.add(value);
            }
        }
        if (values.isEmpty()) {
            return "chưa có số";
        }
        return join(values);
    }

    private static String join(Set<Integer> values) {
        List<String> digits = new ArrayList<>();
        for (int value : values) {
            digits.add(String.valueOf(value));
        }
        return String.join(", ", digits);
    }
}
