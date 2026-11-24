package com.puzzlehub.sudoku.domain;

public record RoomPresence(boolean online, long changedAt) {
    public boolean getOnline() { return online; }
}
