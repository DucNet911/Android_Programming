package com.puzzlehub.sudoku.data.repository;

import com.puzzlehub.sudoku.domain.Hint;
import com.puzzlehub.sudoku.domain.HintLesson;
import com.puzzlehub.sudoku.domain.SudokuBoard;

public final class LocalAiTutorRepository implements AiTutorRepository {
    @Override
    public String explainHint(Hint hint, SudokuBoard board) {
        return HintLesson.details(hint, board);
    }
}
