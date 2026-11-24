package com.puzzlehub.sudoku.data.repository;
public record SocialPlayer(String id, String name, String country) {
    public String getId() { return id; }
    public String getName() { return name; }
    public String getCountry() { return country; }
}
