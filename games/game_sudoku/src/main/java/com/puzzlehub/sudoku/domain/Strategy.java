package com.puzzlehub.sudoku.domain;

public enum Strategy {
    NAKED_SINGLE("Naked Single"),
    HIDDEN_SINGLE("Hidden Single"),
    NAKED_PAIR("Naked Pair → Single"),
    SEARCH("Suy luận bằng tìm kiếm");

    private final String label;

    Strategy(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
