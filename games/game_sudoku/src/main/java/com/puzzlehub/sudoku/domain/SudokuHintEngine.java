package com.puzzlehub.sudoku.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SudokuHintEngine {
    public Hint logicalHint(SudokuBoard board) {
        if (!SudokuValidator.isBoardValid(board)) {
            return null;
        }

        Map<Integer, Set<Integer>> candidates = new LinkedHashMap<>();
        for (int index = 0; index < SudokuBoard.CELL_COUNT; index++) {
            if (board.get(index) == 0) {
                candidates.put(index, SudokuValidator.candidates(board, index));
            }
        }

        for (Map.Entry<Integer, Set<Integer>> entry : candidates.entrySet()) {
            Set<Integer> values = entry.getValue();
            if (values.size() == 1) {
                int index = entry.getKey();
                int value = values.iterator().next();
                List<Integer> excluded = new ArrayList<>();
                for (int digit = 1; digit <= 9; digit++) {
                    if (digit != value) {
                        excluded.add(digit);
                    }
                }
                return hint(index, value, Strategy.NAKED_SINGLE, values, new ArrayList<>(SudokuValidator.peers(index)),
                        "Các số " + digits(excluded) + " đã bị loại bởi hàng, cột hoặc vùng 3×3. Ô chỉ còn ứng viên " + value + ".");
            }
        }

        Hint hidden = hiddenSingle(candidates);
        if (hidden != null) {
            return hidden;
        }
        return pairElimination(candidates);
    }

    public Hint nextHint(SudokuBoard board) {
        Hint logical = logicalHint(board);
        if (logical != null) {
            return logical;
        }
        int index = board.getCells().indexOf(0);
        if (index < 0) {
            return null;
        }
        SudokuBoard solution = new SudokuSolver().solve(board);
        if (solution == null) {
            return null;
        }
        return hint(index, solution.get(index), Strategy.SEARCH, SudokuValidator.candidates(board, index),
                Collections.emptyList(),
                "Chưa tìm được Single hoặc Pair. Solver dùng backtracking để kiểm tra các nhánh. Đây là bước hỗ trợ tìm kiếm, không phải suy luận Single.");
    }

    private static Hint hiddenSingle(Map<Integer, Set<Integer>> candidates) {
        for (int unitIndex = 0; unitIndex < SudokuValidator.units.size(); unitIndex++) {
            List<Integer> unit = SudokuValidator.units.get(unitIndex);
            for (int value = 1; value <= 9; value++) {
                int target = -1;
                for (int index : unit) {
                    Set<Integer> cellCandidates = candidates.get(index);
                    if (cellCandidates != null && cellCandidates.contains(value)) {
                        if (target != -1) {
                            target = -1;
                            break;
                        }
                        target = index;
                    }
                }
                if (target != -1) {
                    String name;
                    if (unitIndex < 9) {
                        name = "hàng " + (unitIndex + 1);
                    } else if (unitIndex < 18) {
                        name = "cột " + (unitIndex - 8);
                    } else {
                        name = "vùng 3×3 " + (unitIndex - 17);
                    }
                    return hint(target, value, Strategy.HIDDEN_SINGLE, candidates.get(target), unit,
                            "Trong " + name + ", chỉ ô này có thể chứa số " + value + ".");
                }
            }
        }
        return null;
    }

    private static Hint pairElimination(Map<Integer, Set<Integer>> candidates) {
        for (List<Integer> unit : SudokuValidator.units) {
            Map<Set<Integer>, List<Integer>> pairs = new LinkedHashMap<>();
            for (int index : unit) {
                Set<Integer> values = candidates.get(index);
                if (values != null && values.size() == 2) {
                    List<Integer> cells = pairs.get(values);
                if (cells == null) {
                    cells = new ArrayList<>();
                    pairs.put(values, cells);
                }
                cells.add(index);
                }
            }
            for (Map.Entry<Set<Integer>, List<Integer>> entry : pairs.entrySet()) {
                List<Integer> pairCells = entry.getValue();
                if (pairCells.size() != 2) {
                    continue;
                }
                Set<Integer> pair = entry.getKey();
                for (int index : unit) {
                    Set<Integer> values = candidates.get(index);
                    if (values == null || pairCells.contains(index)) {
                        continue;
                    }
                    Set<Integer> remaining = new LinkedHashSet<>(values);
                    remaining.removeAll(pair);
                    if (remaining.size() == 1) {
                        List<Integer> related = new ArrayList<>(pairCells);
                        related.add(index);
                        return hint(index, remaining.iterator().next(), Strategy.NAKED_PAIR, values, related,
                                "Hai ô trong cùng đơn vị chỉ có cặp " + digits(pair)
                                        + ". Loại cặp này khỏi ô đang xét để còn một ứng viên.");
                    }
                }
            }
        }
        return null;
    }

    private static Hint hint(int index, int value, Strategy strategy, Set<Integer> candidates,
            List<Integer> related, String explanation) {
        int row = index / SudokuBoard.SIDE;
        int column = index % SudokuBoard.SIDE;
        String text = "Hàng " + (row + 1) + ", cột " + (column + 1) + ": " + explanation + " Vì vậy ô này là " + value + ".";
        return new Hint(row, column, value, strategy, candidates, related, text);
    }

    private static String digits(Iterable<Integer> values) {
        List<String> text = new ArrayList<>();
        for (int value : values) {
            text.add(String.valueOf(value));
        }
        return String.join(", ", text);
    }
}
