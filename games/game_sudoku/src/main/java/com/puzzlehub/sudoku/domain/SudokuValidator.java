package com.puzzlehub.sudoku.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class SudokuValidator {
    private static final int SIZE = SudokuBoard.SIDE;
    public static final List<List<Integer>> units = createUnits();
    private static final List<Set<Integer>> PEERS = createPeers();

    private SudokuValidator() {}

    public static Set<Integer> peers(int index) {
        return PEERS.get(index);
    }

    public static Set<Integer> candidates(SudokuBoard board, int index) {
        if (board.get(index) != 0) {
            return Collections.emptySet();
        }
        boolean[] seen = new boolean[SIZE + 1];
        for (int peer : peers(index)) {
            seen[board.get(peer)] = true;
        }
        Set<Integer> result = new LinkedHashSet<>();
        for (int value = 1; value <= SIZE; value++) {
            if (!seen[value]) {
                result.add(value);
            }
        }
        return result;
    }

    public static boolean isValidMove(SudokuBoard board, int row, int column, int value) {
        if (row < 0 || row >= SIZE || column < 0 || column >= SIZE || value < 1 || value > SIZE) {
            return false;
        }
        for (int peer : peers(row * SIZE + column)) {
            if (board.get(peer) == value) {
                return false;
            }
        }
        return true;
    }

    public static boolean isBoardValid(SudokuBoard board) {
        for (List<Integer> unit : units) {
            boolean[] seen = new boolean[SIZE + 1];
            for (int index : unit) {
                int value = board.get(index);
                if (value == 0) {
                    continue;
                }
                if (seen[value]) {
                    return false;
                }
                seen[value] = true;
            }
        }
        return true;
    }

    public static Set<Integer> conflicts(SudokuBoard board) {
        Set<Integer> conflicts = new LinkedHashSet<>();
        for (int index = 0; index < SudokuBoard.CELL_COUNT; index++) {
            int value = board.get(index);
            if (value == 0) {
                continue;
            }
            for (int peer : peers(index)) {
                if (board.get(peer) == value) {
                    conflicts.add(index);
                    break;
                }
            }
        }
        return conflicts;
    }

    public static boolean isComplete(SudokuBoard board) {
        return !board.getCells().contains(0) && isBoardValid(board);
    }

    private static List<List<Integer>> createUnits() {
        List<List<Integer>> rows = new ArrayList<>();
        List<List<Integer>> columns = new ArrayList<>();
        List<List<Integer>> boxes = new ArrayList<>();
        for (int unit = 0; unit < SIZE; unit++) {
            List<Integer> row = new ArrayList<>();
            List<Integer> column = new ArrayList<>();
            List<Integer> box = new ArrayList<>();
            int boxRow = (unit / 3) * 3;
            int boxColumn = (unit % 3) * 3;
            for (int cell = 0; cell < SIZE; cell++) {
                row.add(unit * SIZE + cell);
                column.add(cell * SIZE + unit);
                box.add((boxRow + cell / 3) * SIZE + boxColumn + cell % 3);
            }
            rows.add(Collections.unmodifiableList(row));
            columns.add(Collections.unmodifiableList(column));
            boxes.add(Collections.unmodifiableList(box));
        }
        rows.addAll(columns);
        rows.addAll(boxes);
        return Collections.unmodifiableList(rows);
    }

    private static List<Set<Integer>> createPeers() {
        List<Set<Integer>> table = new ArrayList<>();
        for (int index = 0; index < SudokuBoard.CELL_COUNT; index++) {
            Set<Integer> peers = new LinkedHashSet<>();
            for (List<Integer> unit : units) {
                if (unit.contains(index)) {
                    peers.addAll(unit);
                }
            }
            peers.remove(index);
            table.add(Collections.unmodifiableSet(peers));
        }
        return Collections.unmodifiableList(table);
    }
}
