package com.puzzlehub.sudoku.domain;

import java.util.List;
import java.util.Set;

public record Hint(int row, int column, int value, Strategy strategy,
        Set<Integer> candidates, List<Integer> related, String explanation) {
    public int getRow() { return row; }
    public int getColumn() { return column; }
    public int getValue() { return value; }
    public Strategy getStrategy() { return strategy; }
    public Set<Integer> getCandidates() { return candidates; }
    public List<Integer> getRelated() { return related; }
    public String getExplanation() { return explanation; }
    public int getIndex() { return row * SudokuBoard.SIDE + column; }
}
