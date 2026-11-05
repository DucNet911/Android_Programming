package com.example.arcade.core;
public final class GameDifficulty {
    public final String id, label;
    public GameDifficulty(String id, String label) {
        this.id = Checks.text(id, "difficultyId");
        this.label = Checks.text(label, "difficultyLabel");
    }
}
