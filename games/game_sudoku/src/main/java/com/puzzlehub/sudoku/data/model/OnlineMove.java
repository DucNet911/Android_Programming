package com.puzzlehub.sudoku.data.model;

public record OnlineMove(String playerId, int index, int value, long timestamp) {
    public String getPlayerId() { return playerId; }
    public int getIndex() { return index; }
    public int getValue() { return value; }
    public long getTimestamp() { return timestamp; }
}
