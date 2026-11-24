package com.puzzlehub.sudoku.domain;

public record SparkPlayer(String uid, String name, SudokuBoard board, long revision, int mistakes) {
    public SparkPlayer(String uid, String name, SudokuBoard board) {
        this(uid, name, board, 0L, 0);
    }

    public String getUid() { return uid; }
    public String getName() { return name; }
    public SudokuBoard getBoard() { return board; }
    public long getRevision() { return revision; }
    public int getMistakes() { return mistakes; }
}
