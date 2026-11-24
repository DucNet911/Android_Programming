package com.puzzlehub.sudoku.data.model;

import com.puzzlehub.sudoku.domain.SudokuBoard;

public record OnlinePlayer(String uid, String name, SudokuBoard board, long revision,
                           int correct, int mistakes, int score) {
    public String getUid() { return uid; }
    public String getName() { return name; }
    public SudokuBoard getBoard() { return board; }
    public long getRevision() { return revision; }
    public int getCorrect() { return correct; }
    public int getMistakes() { return mistakes; }
    public int getScore() { return score; }
}
