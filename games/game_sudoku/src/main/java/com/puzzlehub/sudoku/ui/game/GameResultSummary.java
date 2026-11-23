package com.puzzlehub.sudoku.ui.game;

public record GameResultSummary(boolean won, String title, String detail, long seconds, int score,
                                int mistakes, String primary, String secondary, boolean dismissible) {}
