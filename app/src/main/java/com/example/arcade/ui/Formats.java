package com.example.arcade.ui;
import android.content.Context;
import com.example.arcade.R;
import com.example.arcade.core.GameOutcome;
public final class Formats {
    private Formats() {}
    public static String duration(Context c, long ms) { long s = ms / 1000; return c.getString(R.string.duration_value, s / 60, s % 60); }
    public static String outcome(Context c, GameOutcome outcome) {
        return c.getString(outcome == GameOutcome.WON ? R.string.outcome_won : outcome == GameOutcome.LOST ? R.string.outcome_lost : R.string.outcome_abandoned);
    }
}
