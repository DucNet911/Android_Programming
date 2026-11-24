package com.puzzlehub.sudoku.data.model;

public record ProfileCover(String id, String label, int image) {
    public String getId() { return id; }
    public String getLabel() { return label; }
    public int getImage() { return image; }
}
