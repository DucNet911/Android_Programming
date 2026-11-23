package com.puzzlehub.sudoku.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable 9 by 9 board. Zero represents an empty cell. */
public final class SudokuBoard {
    public static final int SIDE = 9;
    public static final int CELL_COUNT = SIDE * SIDE;
    private final List<Integer> cells;

    public SudokuBoard(List<Integer> cells) {
        if (cells.size() != CELL_COUNT) {
            throw new IllegalArgumentException("A Sudoku board has 81 cells");
        }
        List<Integer> copy = new ArrayList<>(cells);
        for (Integer value : copy) {
            if (value == null || value < 0 || value > SIDE) {
                throw new IllegalArgumentException("Cell values must be between 0 and 9");
            }
        }
        this.cells = Collections.unmodifiableList(copy);
    }

    public List<Integer> getCells() {
        return cells;
    }

    public int get(int index) {
        return cells.get(index);
    }

    public int get(int row, int column) {
        return get(row * SIDE + column);
    }

    public SudokuBoard withValue(int index, int value) {
        List<Integer> updated = new ArrayList<>(cells);
        updated.set(index, value);
        return new SudokuBoard(updated);
    }

    public String encode() {
        StringBuilder text = new StringBuilder(CELL_COUNT);
        for (int cell : cells) {
            text.append(cell);
        }
        return text.toString();
    }

    public static SudokuBoard empty() {
        return new SudokuBoard(Collections.nCopies(CELL_COUNT, 0));
    }

    public static SudokuBoard decode(String text) {
        List<Integer> values = new ArrayList<>(text.length());
        for (int index = 0; index < text.length(); index++) {
            char digit = text.charAt(index);
            if (digit < '0' || digit > '9') {
                throw new IllegalArgumentException("Invalid board digit");
            }
            values.add(digit - '0');
        }
        return new SudokuBoard(values);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SudokuBoard && cells.equals(((SudokuBoard) other).cells);
    }

    @Override
    public int hashCode() {
        return cells.hashCode();
    }

    @Override
    public String toString() {
        return encode();
    }
}
