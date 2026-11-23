package com.puzzlehub.sudoku.ui.game;

import com.puzzlehub.sudoku.data.repository.AiTutorRepository;

public interface SudokuGameServices {
    SudokuGameStorage getSudokuStorage();
    AiTutorRepository getSudokuTutor();
}
