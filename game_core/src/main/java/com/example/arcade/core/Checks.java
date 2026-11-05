package com.example.arcade.core;
final class Checks {
    private Checks() {}
    static String text(String value, String field) {
        if (value == null || value.trim().isEmpty() || value.length() > 128)
            throw new IllegalArgumentException(field + " must contain 1..128 characters");
        return value;
    }
    static long nonNegative(long value, String field) {
        if (value < 0) throw new IllegalArgumentException(field + " must be non-negative");
        return value;
    }
}
