package com.puzzlehub.sudoku.domain;

import java.util.ArrayList;
import java.util.List;

/** Backtracking with minimum remaining values. The original board stays immutable. */
public final class SudokuSolver {
    private static final int DIGITS = 0b1111111110;

    public SudokuBoard solve(SudokuBoard board) {
        if (!SudokuValidator.isBoardValid(board)) {
            return null;
        }
        Search search = new Search(board);
        if (search.count(1) == 0) {
            return null;
        }
        List<Integer> answer = new ArrayList<>(SudokuBoard.CELL_COUNT);
        for (int digit : search.firstSolution) {
            answer.add(digit);
        }
        return new SudokuBoard(answer);
    }

    public int countSolutions(SudokuBoard board) {
        return countSolutions(board, 2);
    }

    public int countSolutions(SudokuBoard board, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("Solution limit must be positive");
        }
        if (!SudokuValidator.isBoardValid(board)) {
            return 0;
        }
        return new Search(board).count(limit);
    }

    private static final class Search {
        private final int[] cells = new int[SudokuBoard.CELL_COUNT];
        private int[] firstSolution;

        Search(SudokuBoard board) {
            for (int index = 0; index < cells.length; index++) {
                cells[index] = board.get(index);
            }
        }

        int count(int limit) {
            int target = -1;
            int options = 0;
            int fewestOptions = SudokuBoard.SIDE + 1;
            for (int index = 0; index < cells.length; index++) {
                if (cells[index] != 0) {
                    continue;
                }
                int candidates = DIGITS;
                for (int peer : SudokuValidator.peers(index)) {
                    candidates &= ~(1 << cells[peer]);
                }
                int size = Integer.bitCount(candidates);
                if (size == 0) {
                    return 0;
                }
                if (size < fewestOptions) {
                    target = index;
                    options = candidates;
                    fewestOptions = size;
                    if (size == 1) {
                        break;
                    }
                }
            }

            if (target == -1) {
                if (firstSolution == null) {
                    firstSolution = cells.clone();
                }
                return 1;
            }

            int found = 0;
            while (options != 0 && found < limit) {
                int digit = Integer.numberOfTrailingZeros(options);
                cells[target] = digit;
                found += count(limit - found);
                cells[target] = 0;
                options &= ~(1 << digit);
            }
            return found;
        }
    }
}
