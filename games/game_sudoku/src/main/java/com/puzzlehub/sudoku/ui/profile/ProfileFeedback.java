package com.puzzlehub.sudoku.ui.profile;

public record ProfileFeedback(String text, boolean successful) {
    public String getText() { return text; }
    public boolean getSuccessful() { return successful; }
}
