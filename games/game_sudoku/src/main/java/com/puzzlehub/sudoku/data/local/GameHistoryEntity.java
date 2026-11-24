package com.puzzlehub.sudoku.data.local;
import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
@Entity(tableName = "game_history")
public class GameHistoryEntity {
    @PrimaryKey @NonNull public String id = "";
    public long date;
    @NonNull public String difficulty = "EASY";
    @NonNull public String mode = "SOLO";
    public long duration;
    public int mistakes;
    @NonNull public String result = "WON";
    public int score;
    public int hints;
}
