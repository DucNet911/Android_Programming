package com.puzzlehub.sudoku.utils;

public final class SudokuCallbacks {
    private SudokuCallbacks() {}

    public interface Func<A, R> {
        R apply(A value);
    }

    public interface BiCallback<A, B> {
        void accept(A first, B second);
    }

    public interface UnaryOp<T> {
        T apply(T value);
    }

    public interface IntAction {
        void accept(int value);
    }
}
