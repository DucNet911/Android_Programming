package com.puzzlehub.sudoku.data.repository;

import com.puzzlehub.sudoku.domain.Hint;
import com.puzzlehub.sudoku.domain.SudokuBoard;

public interface AiTutorRepository {
    String explainHint(Hint hint, SudokuBoard board);
}
