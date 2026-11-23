package com.puzzlehub.sudoku.ui.game;

import com.puzzlehub.sudoku.domain.GameSession;

public interface SudokuGameStorage {
    GameSession loadGame();
    void saveGame(GameSession session);
    boolean highlightEnabled();
    String rankingPlayerId();

    default boolean restoreOnCreate() {
        return false;
    }
}
