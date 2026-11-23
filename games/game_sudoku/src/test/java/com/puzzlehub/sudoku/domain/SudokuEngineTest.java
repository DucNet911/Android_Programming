package com.puzzlehub.sudoku.domain;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import static org.junit.Assert.*;

public final class SudokuEngineTest {
    private final SudokuBoard initial = SudokuBoard.decode(
            "530070000600195000098000060800060003400803001700020006060000280000419005000080079");
    private final SudokuBoard solved = SudokuBoard.decode(
            "534678912672195348198342567859761423426853791713924856961537284287419635345286179");
    private final SudokuSolver solver = new SudokuSolver();

    @Test public void boardCopiesSourceAndRejectsInvalidValues() {
        List<Integer> source = new ArrayList<>(Collections.nCopies(81, 0));
        SudokuBoard board = new SudokuBoard(source);
        source.set(0, 9);
        assertEquals(0, board.get(0));
        assertEquals(SudokuBoard.empty(), board);
        assertThrows(IllegalArgumentException.class, () -> new SudokuBoard(Collections.nCopies(80, 0)));
        assertThrows(IllegalArgumentException.class, () -> new SudokuBoard(Collections.nCopies(81, 10)));
    }

    @Test public void validatorChecksBoardAndMoves() {
        assertTrue(SudokuValidator.isBoardValid(initial));
        assertFalse(SudokuValidator.isComplete(initial));
        assertTrue(SudokuValidator.isComplete(solved));
        assertTrue(SudokuValidator.isValidMove(initial, 0, 2, 4));
        assertFalse(SudokuValidator.isValidMove(initial, 0, 2, 5));
        assertFalse(SudokuValidator.isValidMove(initial, -1, 0, 4));
        assertFalse(SudokuValidator.isBoardValid(initial.withValue(2, 5)));
    }

    @Test public void solverFindsKnownUniqueSolutionWithoutMutation() {
        assertEquals(solved, solver.solve(initial));
        assertEquals(1, solver.countSolutions(initial));
        assertEquals(30, clueCount(initial));
    }

    @Test public void countsZeroOneAndMultipleSolutions() {
        assertEquals(0, solver.countSolutions(initial.withValue(2, 5)));
        assertEquals(1, solver.countSolutions(solved));
        assertEquals(2, solver.countSolutions(SudokuBoard.empty()));
        assertNull(solver.solve(initial.withValue(2, 5)));
    }

    @Test public void locallyValidButImpossibleBoardReturnsZero() {
        SudokuBoard impossible = initial.withValue(2, 1);
        assertTrue(SudokuValidator.isBoardValid(impossible));
        assertEquals(0, solver.countSolutions(impossible));
    }

    @Test public void generatedBoardsHaveOneSolutionForAllDifficulties() {
        for (Difficulty difficulty : Difficulty.values()) {
            for (int seed = 0; seed < 3; seed++) {
                Puzzle puzzle = new SudokuGenerator(new Random(seed + difficulty.ordinal() * 100))
                        .generatePuzzle(difficulty);
                assertTrue(SudokuValidator.isComplete(puzzle.solution()));
                assertEquals(1, solver.countSolutions(puzzle.initial()));
                assertEquals(puzzle.solution(), solver.solve(puzzle.initial()));
                assertTrue(clueCount(puzzle.initial()) <= Math.max(45, difficulty.getTargetClues()));
            }
        }
    }

    @Test public void beginnerProvidesMoreCluesAndFourLevelsKeepSavedNames() {
        String[] labels = java.util.Arrays.stream(Difficulty.values()).map(Difficulty::getLabel).toArray(String[]::new);
        assertArrayEquals(new String[]{"Mới chơi", "Dễ", "Bình thường", "Khó"}, labels);
        Puzzle puzzle = new SudokuGenerator(new Random(31)).generatePuzzle(Difficulty.BEGINNER);
        assertEquals(49, clueCount(puzzle.initial()));
        assertEquals(Difficulty.BEGINNER, puzzle.rating().difficulty());
        assertEquals(1, solver.countSolutions(puzzle.initial()));
        assertEquals(Difficulty.MEDIUM, Difficulty.valueOf("MEDIUM"));
    }

    @Test public void hintIsSoundAndStructured() {
        SudokuBoard board = initial;
        SudokuHintEngine engine = new SudokuHintEngine();
        for (int i = 0; i < 51; i++) {
            Hint hint = engine.nextHint(board);
            assertNotNull(hint);
            assertEquals(solved.get(hint.getIndex()), hint.value());
            assertFalse(hint.explanation().isBlank());
            board = board.withValue(hint.getIndex(), hint.value());
        }
        assertEquals(solved, board);
        assertNull(engine.nextHint(board));
        assertNull(engine.nextHint(initial.withValue(2, 5)));
    }

    @Test public void hintsRemainCorrectOnGeneratedPuzzles() {
        SudokuHintEngine engine = new SudokuHintEngine();
        for (int seed = 0; seed <= 5; seed++) {
            Puzzle puzzle = new SudokuGenerator(new Random(seed)).generatePuzzle(Difficulty.HARD);
            SudokuBoard board = puzzle.initial();
            while (!SudokuValidator.isComplete(board)) {
                Hint hint = engine.nextHint(board);
                assertNotNull(hint);
                assertEquals(puzzle.solution().get(hint.getIndex()), hint.value());
                board = board.withValue(hint.getIndex(), hint.value());
            }
        }
    }

    @Test public void eraseClearsNotesAndUndoRestoresThem() {
        GameController game = new GameController(new GameSession(puzzle()));
        game.enter(2, 4, true);
        assertTrue(game.enter(2, 0));
        assertTrue(game.getSession().notes().getOrDefault(2, Collections.emptySet()).isEmpty());
        game.undo();
        assertEquals(Set.of(4), game.getSession().notes().get(2));
    }

    @Test public void notesUndoErasePauseAndCompletion() {
        GameController game = new GameController(new GameSession(puzzle()));
        assertFalse(game.enter(0, 2));
        assertTrue(game.enter(2, 4, true));
        assertEquals(Set.of(4), game.getSession().notes().get(2));
        game.enter(2, 5);
        assertEquals(1, game.getSession().mistakes());
        assertTrue(game.undo());
        assertEquals(0, game.getSession().board().get(2));
        assertEquals(1, game.getSession().mistakes());
        game.enter(2, 4);
        game.enter(2, 0);
        assertEquals(0, game.getSession().board().get(2));
        game.pause(true);
        game.tick();
        assertEquals(0L, game.getSession().elapsedSeconds());
        assertFalse(game.enter(2, 4));
        game.pause(false);
        game.tick();
        assertEquals(1L, game.getSession().elapsedSeconds());
        for (int cell = 0; cell < 81; cell++) {
            if (initial.get(cell) == 0) {
                game.enter(cell, solved.get(cell));
            }
        }
        assertEquals(GameStatus.WON, game.getSession().status());
    }

    @Test public void thirdMistakeEndsGameAndCannotBeUndoneOrResumed() {
        GameController game = new GameController(new GameSession(puzzle()));
        game.enter(2, 1, true);
        assertEquals(0, game.getSession().mistakes());
        for (int value = 1; value <= 3; value++) {
            assertTrue(game.enter(2, value));
            assertEquals(value, game.getSession().mistakes());
            GameStatus expected = GameStatus.PLAYING;
            if (value == 3) {
                expected = GameStatus.LOST;
            }
            assertEquals(expected, game.getSession().status());
        }
        GameSession ended = game.getSession();
        assertFalse(game.undo());
        assertFalse(game.enter(2, 4));
        assertFalse(game.enter(2, 0));
        game.pause(true);
        game.pause(false);
        game.tick();
        game.applyHint(new Hint(0, 2, 4, Strategy.NAKED_SINGLE, Set.of(4), Collections.emptyList(), "test"));
        assertEquals(ended, game.getSession());
    }

    @Test public void mistakesSurviveUndoAndOldSavedGamesRespectLimit() {
        Puzzle puzzle = puzzle();
        GameController game = new GameController(new GameSession(puzzle));
        for (int attempt = 0; attempt < 2; attempt++) {
            game.enter(2, 1);
            assertTrue(game.undo());
        }
        game.enter(2, 1);
        assertEquals(GameStatus.LOST, game.getSession().status());
        GameController restored = new GameController(new GameSession("old", puzzle, puzzle.initial(),
                Collections.emptyMap(), Collections.emptySet(), 0, 7, 0, GameStatus.PAUSED, 1, ""));
        assertEquals(GameStatus.LOST, restored.getSession().status());
        restored.pause(false);
        assertFalse(restored.enter(2, 4));
    }

    private Puzzle puzzle() {
        return new Puzzle(initial, solved, Difficulty.EASY, new SudokuDifficultyEvaluator().evaluate(initial));
    }

    private long clueCount(SudokuBoard board) {
        return board.getCells().stream().filter(value -> value != 0).count();
    }
}
