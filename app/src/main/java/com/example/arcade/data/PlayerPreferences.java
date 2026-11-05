package com.example.arcade.data;
import android.content.Context;
import android.content.SharedPreferences;
public final class PlayerPreferences {
    // Offline guest only. Add real authentication separately if required by the assignment.
    public static final String PLAYER_ID = "guest";
    private final SharedPreferences preferences;
    public PlayerPreferences(Context c) { preferences = c.getSharedPreferences("platform_preferences", Context.MODE_PRIVATE); }
    public boolean favorite(String gameId) { return preferences.getBoolean("favorite/" + gameId, false); }
    public void toggleFavorite(String gameId) { preferences.edit().putBoolean("favorite/" + gameId, !favorite(gameId)).apply(); }
}
