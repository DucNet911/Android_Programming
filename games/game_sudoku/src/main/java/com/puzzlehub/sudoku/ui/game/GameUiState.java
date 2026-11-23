package com.puzzlehub.sudoku.ui.game;

import com.puzzlehub.sudoku.domain.GameSession;
import com.puzzlehub.sudoku.domain.Hint;

record GameUiState(GameSession session, int selected, boolean pencil, boolean loading, String message, Hint hint,
        String explanation, boolean explaining, boolean highlight, Integer correctCell, long moveVersion, int hintStep) {}
