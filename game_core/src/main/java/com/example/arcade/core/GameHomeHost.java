package com.example.arcade.core;

import java.util.List;

/** Platform actions and local records used by an in-game landing screen. */
public interface GameHomeHost {
    GameDefinition game();
    GameLaunchRequest request();
    List<GameResult> history();
    void startNew(String difficulty);
    void play(boolean openBoard);
}
