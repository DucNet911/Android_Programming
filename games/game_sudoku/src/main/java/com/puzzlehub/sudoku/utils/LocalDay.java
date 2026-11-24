package com.puzzlehub.sudoku.utils;

import java.util.Calendar;

/** API 23-compatible calendar day represented as epoch day since 1970-01-01. */
public final class LocalDay {
    private final long epochDay;

    private LocalDay(long epochDay) {
        this.epochDay = epochDay;
    }

    public static LocalDay now() {
        Calendar cal = Calendar.getInstance();
        return of(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
    }

    public static LocalDay of(int year, int month, int day) {
        long y = year;
        long m = month;
        y -= m <= 2 ? 1 : 0;
        long era = (y >= 0 ? y : y - 399) / 400;
        long yoe = y - era * 400;
        long doy = (153 * (m + (m > 2 ? -3 : 9)) + 2) / 5 + day - 1;
        long doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
        return new LocalDay(era * 146097 + doe - 719468);
    }

    public static LocalDay ofEpochDay(long epochDay) {
        return new LocalDay(epochDay);
    }

    public long toEpochDay() {
        return epochDay;
    }

    public LocalDay plusDays(long days) {
        return new LocalDay(epochDay + days);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LocalDay && ((LocalDay) other).epochDay == epochDay;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(epochDay);
    }
}
