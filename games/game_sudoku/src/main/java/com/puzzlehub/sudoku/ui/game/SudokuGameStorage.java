package com.puzzlehub.sudoku.ui.game;

import com.puzzlehub.sudoku.domain.GameSession;
import com.puzzlehub.sudoku.utils.SudokuTask;

public interface SudokuGameStorage {
    SudokuTask<GameSession> loadGame();
    SudokuTask<Void> saveGame(GameSession session);
    SudokuTask<Boolean> highlightEnabled();
    String rankingPlayerId();

    default boolean restoreOnCreate() {
        return false;
    }
}
