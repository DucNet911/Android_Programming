package com.puzzlehub.sudoku.domain;

public record Puzzle(SudokuBoard initial, SudokuBoard solution, Difficulty difficulty, DifficultyReport rating) {
    public SudokuBoard getInitial() { return initial; }
    public SudokuBoard getSolution() { return solution; }
    public Difficulty getDifficulty() { return difficulty; }
    public DifficultyReport getRating() { return rating; }
}
