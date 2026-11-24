package com.puzzlehub.sudoku.data.repository;
public record Friendship(String key, SocialPlayer player, String status, boolean incoming) {
    public String getKey() { return key; }
    public SocialPlayer getPlayer() { return player; }
    public String getStatus() { return status; }
    public boolean getIncoming() { return incoming; }
}
