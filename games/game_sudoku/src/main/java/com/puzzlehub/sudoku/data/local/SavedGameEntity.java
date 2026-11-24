package com.puzzlehub.sudoku.data.local;
import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
@Entity(tableName = "saved_game")
public class SavedGameEntity {
    @PrimaryKey public int slot = 1;
    @NonNull public String payload = "";
}
