package com.example.arcade.core;
import android.app.Activity;
import java.util.*;
/** Pure game metadata supplied by each module. Does not contain rules or board state. */
public final class GameDefinition {
    public enum Ranking { HIGH_SCORE, LOWEST_WIN_TIME, NONE }
    public final String id, title, description, symbol;
    public final int accentColor;
    public final Class<? extends Activity> activity;
    public final Ranking ranking;
    private final List<GameDifficulty> difficulties;
    public GameDefinition(String id, String title, String description, String symbol, int accentColor,
            Class<? extends Activity> activity, Ranking ranking, List<GameDifficulty> difficulties) {
        this.id = Checks.text(id, "gameId");
        if (!id.matches("[a-z0-9][a-z0-9_]{0,63}")) throw new IllegalArgumentException("Invalid game ID");
        this.title = Checks.text(title, "title");
        this.description = Checks.text(description, "description");
        this.symbol = Checks.text(symbol, "symbol");
        this.accentColor = accentColor;
        this.activity = Objects.requireNonNull(activity, "activity");
        this.ranking = Objects.requireNonNull(ranking, "ranking");
        if (difficulties == null || difficulties.isEmpty()) throw new IllegalArgumentException("At least one mode is required");
        List<GameDifficulty> copy = new ArrayList<>(difficulties);
        Set<String> ids = new HashSet<>();
        for (GameDifficulty d : copy) {
            if (d == null || !ids.add(d.id)) throw new IllegalArgumentException("Null or duplicate difficulty");
        }
        this.difficulties = Collections.unmodifiableList(copy);
    }
    public int difficultyCount() { return difficulties.size(); }
    public String difficultyId(int index) { return difficulties.get(index).id; }
    public String difficultyLabel(int index) { return difficulties.get(index).label; }
    public String difficultyLabel(String id) {
        for (GameDifficulty d : difficulties) if (d.id.equals(id)) return d.label;
        return id == null ? "—" : id;
    }
}
