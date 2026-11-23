package com.puzzlehub.sudoku.domain;

public final class SudokuDifficultyEvaluator {
    public DifficultyReport evaluate(SudokuBoard initial) {
        SudokuBoard board = initial;
        int steps = 0;
        int pairs = 0;
        SudokuHintEngine engine = new SudokuHintEngine();
        while (!SudokuValidator.isComplete(board)) {
            Hint hint = engine.logicalHint(board);
            if (hint == null) {
                break;
            }
            board = board.withValue(hint.getIndex(), hint.value());
            steps++;
            if (hint.strategy() == Strategy.NAKED_PAIR) {
                pairs++;
            }
        }
        int clues = 0;
        for (int cell : initial.getCells()) {
            if (cell != 0) {
                clues++;
            }
        }
        boolean requiresSearch = !SudokuValidator.isComplete(board);
        Difficulty difficulty;
        if (requiresSearch) {
            difficulty = Difficulty.HARD;
        } else if (clues >= 46 && pairs == 0) {
            difficulty = Difficulty.BEGINNER;
        } else if (clues >= 38 && pairs == 0) {
            difficulty = Difficulty.EASY;
        } else {
            difficulty = Difficulty.MEDIUM;
        }
        return new DifficultyReport(difficulty, clues, steps, pairs, requiresSearch);
    }
}
