package com.puzzlehub.sudoku.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record GameSession(String id, Puzzle puzzle, SudokuBoard board, Map<Integer, Set<Integer>> notes,
        Set<Integer> hinted, long elapsedSeconds, int mistakes, int hintCount, GameStatus status,
        long createdAt, String rankingPlayerId) {

    public GameSession {
        Objects.requireNonNull(id);
        Objects.requireNonNull(puzzle);
        Objects.requireNonNull(board);
        Objects.requireNonNull(status);
        Objects.requireNonNull(rankingPlayerId);
        if (elapsedSeconds < 0 || mistakes < 0 || hintCount < 0) {
            throw new IllegalArgumentException("Invalid Sudoku session counters");
        }
        Map<Integer, Set<Integer>> notesCopy = new LinkedHashMap<>();
        for (Map.Entry<Integer, Set<Integer>> entry : notes.entrySet()) {
            notesCopy.put(entry.getKey(), Collections.unmodifiableSet(new LinkedHashSet<>(entry.getValue())));
        }
        notes = Collections.unmodifiableMap(notesCopy);
        hinted = Collections.unmodifiableSet(new LinkedHashSet<>(hinted));
    }

    public GameSession(Puzzle puzzle) {
        this(puzzle, "");
    }

    public GameSession(Puzzle puzzle, String rankingPlayerId) {
        this(UUID.randomUUID().toString(), puzzle, puzzle.initial(), Collections.emptyMap(), Collections.emptySet(),
                0, 0, 0, GameStatus.PLAYING, System.currentTimeMillis(), rankingPlayerId);
    }

    public GameSession withStatus(GameStatus nextStatus) {
        return update(board, notes, hinted, elapsedSeconds, mistakes, hintCount, nextStatus);
    }

    GameSession update(SudokuBoard nextBoard, Map<Integer, Set<Integer>> nextNotes, Set<Integer> nextHinted,
            long nextElapsed, int nextMistakes, int nextHintCount, GameStatus nextStatus) {
        return new GameSession(id, puzzle, nextBoard, nextNotes, nextHinted,
                nextElapsed, nextMistakes, nextHintCount, nextStatus, createdAt, rankingPlayerId);
    }

    public int getScore() {
        int correct = 0;
        for (int index = 0; index < SudokuBoard.CELL_COUNT; index++) {
            if (puzzle.initial().get(index) == 0 && board.get(index) == puzzle.solution().get(index)) {
                correct++;
            }
        }
        return (int) Math.max(0, correct * 100L - mistakes * 25L - hintCount * 50L);
    }

    public String getId() { return id; }
    public Puzzle getPuzzle() { return puzzle; }
    public SudokuBoard getBoard() { return board; }
    public Map<Integer, Set<Integer>> getNotes() { return notes; }
    public Set<Integer> getHinted() { return hinted; }
    public long getElapsedSeconds() { return elapsedSeconds; }
    public int getMistakes() { return mistakes; }
    public int getHintCount() { return hintCount; }
    public GameStatus getStatus() { return status; }
    public long getCreatedAt() { return createdAt; }
    public String getRankingPlayerId() { return rankingPlayerId; }
}
