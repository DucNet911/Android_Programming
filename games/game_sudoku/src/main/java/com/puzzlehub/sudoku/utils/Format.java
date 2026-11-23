package com.puzzlehub.sudoku.utils;

import java.util.Locale;

public final class Format {
    private Format() {}

    public static String duration(long seconds) {
        if (seconds >= 3600) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
        }
        return String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60);
    }

    public static String modeLabel(String mode) {
        return switch (mode) {
            case "COMPETITIVE" -> "Đối kháng 1v1";
            case "COOPERATIVE" -> "Cùng giải";
            default -> "Chơi đơn";
        };
    }

    public static String resultLabel(String result) {
        return switch (result) {
            case "WON" -> "Hoàn thành";
            case "LOST" -> "Thua";
            default -> "Bỏ dở";
        };
    }
}
