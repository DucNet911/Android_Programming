package com.puzzlehub.sudoku.domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public final class SudokuGenerator {
    private final Random random;
    private final SudokuSolver solver = new SudokuSolver();

    public SudokuGenerator() {
        this(new Random());
    }

    public SudokuGenerator(Random random) {
        this.random = Objects.requireNonNull(random);
    }

    public SudokuBoard generateSolvedBoard() {
        List<Integer> rows = shuffledGroups();
        List<Integer> columns = shuffledGroups();
        List<Integer> digits = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9);
        Collections.shuffle(digits, random);
        List<Integer> cells = new ArrayList<>(SudokuBoard.CELL_COUNT);
        for (int row : rows) {
            for (int column : columns) {
                cells.add(digits.get((row * 3 + row / 3 + column) % 9));
            }
        }
        return new SudokuBoard(cells);
    }

    public Puzzle generatePuzzle(Difficulty difficulty) {
        Puzzle latest = null;
        SudokuDifficultyEvaluator evaluator = new SudokuDifficultyEvaluator();
        for (int attempt = 0; attempt < 16; attempt++) {
            SudokuBoard solution = generateSolvedBoard();
            SudokuBoard board = removeClues(solution, difficulty.getTargetClues());
            DifficultyReport rating = evaluator.evaluate(board);
            latest = new Puzzle(board, solution, difficulty, rating);
            if (rating.difficulty() == difficulty) {
                return latest;
            }
        }
        return Objects.requireNonNull(latest);
    }

    private SudokuBoard removeClues(SudokuBoard solution, int targetClues) {
        List<Integer> indices = new ArrayList<>();
        for (int index = 0; index < SudokuBoard.CELL_COUNT; index++) {
            indices.add(index);
        }
        Collections.shuffle(indices, random);
        SudokuBoard board = solution;
        int clues = SudokuBoard.CELL_COUNT;
        for (int index : indices) {
            if (clues <= targetClues) {
                break;
            }
            SudokuBoard candidate = board.withValue(index, 0);
            if (solver.countSolutions(candidate) == 1) {
                board = candidate;
                clues--;
            }
        }
        return board;
    }

    private List<Integer> shuffledGroups() {
        List<Integer> groups = Arrays.asList(0, 1, 2);
        Collections.shuffle(groups, random);
        List<Integer> indices = new ArrayList<>();
        for (int group : groups) {
            List<Integer> offsets = Arrays.asList(0, 1, 2);
            Collections.shuffle(offsets, random);
            for (int offset : offsets) {
                indices.add(group * 3 + offset);
            }
        }
        return indices;
    }
}
