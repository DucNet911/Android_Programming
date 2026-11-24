package com.puzzlehub.sudoku.ui.settings;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.puzzlehub.sudoku.data.local.SettingsEntity;
import com.puzzlehub.sudoku.data.repository.GameRepository;
import com.puzzlehub.sudoku.utils.AndroidTasks;

public final class SettingsViewModel extends ViewModel {
    public record State(SettingsEntity settings, boolean saving, boolean saved, String error) {}

    private final GameRepository games;
    private final MutableLiveData<State> state = new MutableLiveData<>(new State(null, false, false, null));

    public SettingsViewModel(GameRepository games) {
        this.games = games;
        AndroidTasks.onComplete(games.settings(),
                value -> state.setValue(new State(value, false, false, null)),
                error -> state.setValue(new State(null, false, false, "Không tải được cài đặt.")));
    }

    public LiveData<State> getState() {
        return state;
    }

    public void save(boolean dark, boolean highlight) {
        State current = state.getValue();
        if (current == null || current.settings() == null || current.saving()) {
            return;
        }
        state.setValue(new State(current.settings(), true, false, null));
        AndroidTasks.onComplete(games.saveDisplaySettings(dark, highlight),
                value -> state.setValue(new State(value, false, true, null)),
                error -> state.setValue(new State(current.settings(), false, false, "Không lưu được cài đặt.")));
    }

    public void consumed() {
        State current = state.getValue();
        if (current != null) {
            state.setValue(new State(current.settings(), current.saving(), false, null));
        }
    }
}
