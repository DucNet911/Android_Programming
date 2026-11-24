package com.puzzlehub.sudoku.domain;

public record SparkMove(String playerId, int index, int value, long timestamp, long baseRevision) {
    public String getPlayerId() { return playerId; }
    public int getIndex() { return index; }
    public int getValue() { return value; }
    public long getTimestamp() { return timestamp; }
    public long getBaseRevision() { return baseRevision; }
}
