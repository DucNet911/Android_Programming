package com.puzzlehub.sudoku.data.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.FlowLiveDataConversions;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import com.puzzlehub.sudoku.data.local.GameHistoryEntity;
import com.puzzlehub.sudoku.domain.Achievement;
import com.puzzlehub.sudoku.domain.DailyStreak;
import com.puzzlehub.sudoku.domain.Difficulty;
import com.puzzlehub.sudoku.utils.LocalDay;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.flow.Flow;
import kotlinx.coroutines.flow.FlowKt;
import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlow;
import kotlinx.coroutines.flow.StateFlowKt;

/** Device-local check-ins and awards. Repeated visits or Room emissions never award twice. */
public final class ProgressRepository {
    private final SharedPreferences preferences;
    private final LiveData<List<GameHistoryEntity>> history;
    private final Observer<List<GameHistoryEntity>> historyObserver = this::summarize;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<Difficulty, Integer> wins = new EnumMap<>(Difficulty.class);
    private final Map<Achievement, Long> unlocked = new EnumMap<>(Achievement.class);
    private final Set<Achievement> pending = EnumSet.noneOf(Achievement.class);
    private final MutableStateFlow<PlayerProgress> mutable;
    private final StateFlow<PlayerProgress> state;
    private DailyStreak daily;
    private int perfectWins;
    private volatile boolean closed;

    public ProgressRepository(Context context, LiveData<List<GameHistoryEntity>> history) {
        this(context, history, "player_progress");
    }

    public ProgressRepository(Context context, Flow<List<GameHistoryEntity>> history, String preferencesName) {
        this(context, FlowLiveDataConversions.asLiveData(history, EmptyCoroutineContext.INSTANCE, 0L), preferencesName);
    }

    private ProgressRepository(Context context, LiveData<List<GameHistoryEntity>> history, String preferencesName) {
        this.history = history;
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE);
        Long lastDay = preferences.getLong("lastDay", Long.MIN_VALUE);
        if (lastDay == Long.MIN_VALUE) {
            lastDay = null;
        }
        daily = new DailyStreak(lastDay, preferences.getInt("days", 0), preferences.getInt("best", 0));
        Set<String> storedPending = preferences.getStringSet("pending", Collections.emptySet());
        for (Achievement badge : Achievement.values()) {
            long time = preferences.getLong("earned_" + badge.name(), 0L);
            if (time > 0L) {
                unlocked.put(badge, time);
            }
            if (storedPending != null && storedPending.contains(badge.name())) {
                pending.add(badge);
            }
        }
        mutable = StateFlowKt.MutableStateFlow(snapshot());
        state = FlowKt.asStateFlow(mutable);
        onMain(() -> {
            if (!closed) {
                history.observeForever(historyObserver);
            }
        });
    }

    public StateFlow<PlayerProgress> getState() { return state; }

    public void visit() { visit(LocalDay.now()); }

    public synchronized void visit(LocalDay day) {
        daily = daily.visit(day);
        updateAwards();
    }

    public synchronized void acknowledge(java.util.Collection<Achievement> badges) {
        pending.removeAll(badges);
        persistAndNotify();
    }

    private synchronized void summarize(List<GameHistoryEntity> records) {
        wins.clear();
        perfectWins = 0;
        for (GameHistoryEntity record : records) {
            if (!"SOLO".equals(record.mode) || !"WON".equals(record.result)) {
                continue;
            }
            try {
                Difficulty level = Difficulty.valueOf(record.difficulty);
                Integer count = wins.get(level);
                wins.put(level, count == null ? 1 : count + 1);
            } catch (IllegalArgumentException ignored) {
                // Older devices may have a difficulty this release does not show.
            }
            if (record.mistakes == 0 && record.hints == 0) {
                perfectWins++;
            }
        }
        updateAwards();
    }

    private void updateAwards() {
        for (Achievement badge : Achievement.earned(daily.best(), wins, perfectWins)) {
            if (!unlocked.containsKey(badge)) {
                unlocked.put(badge, System.currentTimeMillis());
                pending.add(badge);
            }
        }
        persistAndNotify();
    }

    private PlayerProgress snapshot() {
        return new PlayerProgress(daily, Collections.unmodifiableMap(new EnumMap<>(wins)), perfectWins,
                Collections.unmodifiableMap(new EnumMap<>(unlocked)), Collections.unmodifiableSet(new HashSet<>(pending)));
    }

    private void persistAndNotify() {
        long lastDay = Long.MIN_VALUE;
        if (daily.lastDay() != null) {
            lastDay = daily.lastDay();
        }
        SharedPreferences.Editor edit = preferences.edit().putLong("lastDay", lastDay)
                .putInt("days", daily.current()).putInt("best", daily.best());
        for (Map.Entry<Achievement, Long> entry : unlocked.entrySet()) {
            edit.putLong("earned_" + entry.getKey().name(), entry.getValue());
        }
        Set<String> keys = new HashSet<>();
        for (Achievement badge : pending) {
            keys.add(badge.name());
        }
        edit.putStringSet("pending", keys).apply();
        mutable.setValue(snapshot());
    }

    public void close() {
        closed = true;
        onMain(() -> history.removeObserver(historyObserver));
    }

    private void onMain(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
        } else {
            main.post(action);
        }
    }
}
