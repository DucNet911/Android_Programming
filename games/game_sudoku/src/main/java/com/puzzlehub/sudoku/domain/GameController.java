package com.puzzlehub.sudoku.domain;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Undo restores board markings while retaining penalties and elapsed play time. */
public final class GameController {
    public static final int MAX_MISTAKES = 3;
    private static final int UNDO_LIMIT = 100;
    private final ArrayDeque<GameSession> undo = new ArrayDeque<>();
    private GameSession session;

    public GameController(GameSession initial) {
        session = initial;
        if (initial.mistakes() >= MAX_MISTAKES
                && (initial.status() == GameStatus.PLAYING || initial.status() == GameStatus.PAUSED)) {
            session = initial.withStatus(GameStatus.LOST);
        }
    }

    public GameSession getSession() {
        return session;
    }

    public boolean enter(int index, int value) {
        return enter(index, value, false);
    }

    public boolean enter(int index, int value, boolean pencil) {
        if (session.status() != GameStatus.PLAYING || index < 0 || index >= SudokuBoard.CELL_COUNT
                || value < 0 || value > 9 || session.puzzle().initial().get(index) != 0) {
            return false;
        }
        if (pencil && value != 0) {
            return toggleNote(index, value);
        }

        int oldValue = session.board().get(index);
        if (oldValue == value) {
            Set<Integer> marks = session.notes().get(index);
            if (value == 0 && marks != null && !marks.isEmpty()) {
                remember();
                Map<Integer, Set<Integer>> notes = new LinkedHashMap<>(session.notes());
                notes.remove(index);
                replaceBoard(session.board(), notes, session.hinted(), session.mistakes(), session.status());
                return true;
            }
            return false;
        }

        remember();
        SudokuBoard board = session.board().withValue(index, value);
        Map<Integer, Set<Integer>> notes = cleanNotes(index, value);
        Set<Integer> hinted = new LinkedHashSet<>(session.hinted());
        hinted.remove(index);
        int mistakes = session.mistakes();
        if (value != 0 && value != session.puzzle().solution().get(index)) {
            mistakes++;
        }
        GameStatus status = GameStatus.PLAYING;
        if (mistakes >= MAX_MISTAKES) {
            status = GameStatus.LOST;
        } else if (SudokuValidator.isComplete(board)) {
            status = GameStatus.WON;
        }
        replaceBoard(board, notes, hinted, mistakes, status);
        return true;
    }

    public void applyHint(Hint hint) {
        if (hint.getIndex() < 0 || hint.getIndex() >= SudokuBoard.CELL_COUNT
                || hint.value() != session.puzzle().solution().get(hint.getIndex())) {
            return;
        }
        if (enter(hint.getIndex(), hint.value())) {
            Set<Integer> hinted = new LinkedHashSet<>(session.hinted());
            hinted.add(hint.getIndex());
            session = session.update(session.board(), session.notes(), hinted, session.elapsedSeconds(),
                    session.mistakes(), session.hintCount() + 1, session.status());
        }
    }

    public boolean undo() {
        if (undo.isEmpty() || session.status() != GameStatus.PLAYING) {
            return false;
        }
        GameSession old = undo.removeLast();
        replaceBoard(old.board(), old.notes(), old.hinted(), session.mistakes(), session.status());
        return true;
    }

    public void tick() {
        addElapsedSeconds(1);
    }

    public void addElapsedSeconds(long seconds) {
        if (session.status() == GameStatus.PLAYING && seconds > 0) {
            session = session.update(session.board(), session.notes(), session.hinted(),
                    session.elapsedSeconds() + seconds, session.mistakes(), session.hintCount(), session.status());
        }
    }

    public void pause(boolean paused) {
        if (session.status() == GameStatus.PLAYING && paused) {
            session = session.withStatus(GameStatus.PAUSED);
        } else if (session.status() == GameStatus.PAUSED && !paused) {
            session = session.withStatus(GameStatus.PLAYING);
        }
    }

    public void abandon() {
        session = session.withStatus(GameStatus.ABANDONED);
    }

    private boolean toggleNote(int index, int value) {
        if (session.board().get(index) != 0) {
            return false;
        }
        remember();
        Map<Integer, Set<Integer>> notes = new LinkedHashMap<>(session.notes());
        Set<Integer> previous = notes.get(index);
        Set<Integer> cellNotes = new LinkedHashSet<>(previous == null ? Collections.emptySet() : previous);
        if (!cellNotes.remove(value)) {
            cellNotes.add(value);
        }
        notes.put(index, cellNotes);
        replaceBoard(session.board(), notes, session.hinted(), session.mistakes(), session.status());
        return true;
    }

    private Map<Integer, Set<Integer>> cleanNotes(int index, int value) {
        Map<Integer, Set<Integer>> notes = new LinkedHashMap<>(session.notes());
        notes.remove(index);
        if (value == 0) {
            return notes;
        }
        for (int peer : SudokuValidator.peers(index)) {
            Set<Integer> previous = notes.get(peer);
            if (previous != null && previous.contains(value)) {
                Set<Integer> remaining = new LinkedHashSet<>(previous);
                remaining.remove(value);
                notes.put(peer, remaining);
            }
        }
        return notes;
    }

    private void replaceBoard(SudokuBoard board, Map<Integer, Set<Integer>> notes,
            Set<Integer> hinted, int mistakes, GameStatus status) {
        session = session.update(board, notes, hinted, session.elapsedSeconds(), mistakes, session.hintCount(), status);
    }

    private void remember() {
        if (undo.size() >= UNDO_LIMIT) {
            undo.removeFirst();
        }
        undo.addLast(session);
    }
}
