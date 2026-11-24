package com.puzzlehub.sudoku.data.local;
import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
@Entity(tableName = "settings")
public class SettingsEntity {
    @PrimaryKey public int id = 1;
    @NonNull public String activeRoom = "";
    public boolean darkMode = false;
    public boolean highlight = true;
    @NonNull public String playerName = "Người chơi";
}
