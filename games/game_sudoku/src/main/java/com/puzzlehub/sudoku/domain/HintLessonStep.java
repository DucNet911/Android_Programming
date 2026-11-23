package com.puzzlehub.sudoku.domain;

import java.util.Set;

public record HintLessonStep(String title, String text, Set<Integer> highlighted,
        boolean showCandidates, boolean revealAnswer) {
    public String getTitle() { return title; }
    public String getText() { return text; }
    public Set<Integer> getHighlighted() { return highlighted; }
    public boolean getShowCandidates() { return showCandidates; }
    public boolean getRevealAnswer() { return revealAnswer; }
}
