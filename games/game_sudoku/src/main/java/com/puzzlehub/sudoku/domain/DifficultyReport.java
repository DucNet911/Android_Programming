package com.puzzlehub.sudoku.domain;

public record DifficultyReport(Difficulty difficulty, int clues, int logicSteps, int pairs, boolean requiresSearch) {
    public Difficulty getDifficulty() { return difficulty; }
    public int getClues() { return clues; }
    public int getLogicSteps() { return logicSteps; }
    public int getPairs() { return pairs; }
    public boolean getRequiresSearch() { return requiresSearch; }
}
