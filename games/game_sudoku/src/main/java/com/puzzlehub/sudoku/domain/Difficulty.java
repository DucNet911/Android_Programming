package com.puzzlehub.sudoku.domain;

import java.util.Locale;

public enum Difficulty {
    BEGINNER("Mới chơi", 49),
    EASY("Dễ", 40),
    MEDIUM("Bình thường", 33),
    HARD("Khó", 27);

    private final String label;
    private final int targetClues;

    Difficulty(String label, int targetClues) {
        this.label = label;
        this.targetClues = targetClues;
    }

    public String getLabel() {
        return label;
    }

    public int getTargetClues() {
        return targetClues;
    }

    public String getId() {
        return name().toLowerCase(Locale.ROOT);
    }
}
