package com.puzzlehub.sudoku.domain;

import org.junit.Test;
import java.util.Collections;
import static org.junit.Assert.*;

public final class HintLessonTest {
    private static final String SOLVED = "534678912672195348198342567859761423426853791713924856961537284287419635345286179";

    @Test public void lessonDoesNotRevealAnswerUntilLastStep() {
        SudokuBoard solved = SudokuBoard.decode(SOLVED);
        Hint hint = new SudokuHintEngine().nextHint(solved.withValue(0, 0));
        var steps = HintLesson.steps(hint);
        assertEquals(3, steps.size());
        assertFalse(steps.get(0).revealAnswer());
        assertFalse(steps.get(1).revealAnswer());
        assertTrue(steps.get(1).showCandidates());
        assertTrue(steps.get(2).revealAnswer());
        assertEquals(hint.explanation(), steps.get(2).text());
    }

    @Test public void searchStepExplicitlyExplainsItsLimitsAndHighlightsPeers() {
        Hint hint = new Hint(0, 0, 5, Strategy.SEARCH, java.util.Set.of(1, 5, 9),
                Collections.emptyList(), "Tìm kiếm đã kiểm tra kết quả 5");
        var steps = HintLesson.steps(hint);
        assertTrue(steps.get(1).text().contains("tìm kiếm"));
        assertEquals(20, steps.get(0).highlighted().size());
        assertFalse(steps.get(0).highlighted().contains(0));
    }

    @Test public void detailedExplanationUsesActualBoardWithoutChangingIt() {
        SudokuBoard board = SudokuBoard.decode("0" + SOLVED.substring(1));
        String before = board.encode();
        Hint hint = new SudokuHintEngine().nextHint(board);
        String details = HintLesson.details(hint, board);
        assertTrue(details.contains("Hàng 1: 1, 2, 3, 4, 6, 7, 8, 9"));
        assertTrue(details.contains("Còn có thể thử: 5"));
        assertTrue(details.endsWith(hint.explanation()));
        assertEquals(before, board.encode());
    }
}
