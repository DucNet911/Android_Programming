package com.puzzlehub.sudoku.ui.history;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import com.puzzlehub.sudoku.data.local.GameHistoryEntity;
import com.puzzlehub.sudoku.data.repository.GameRepository;
import java.util.List;

public final class HistoryViewModel extends ViewModel {
    private final LiveData<List<GameHistoryEntity>> history;

    public HistoryViewModel(GameRepository games) {
        history = games.observeHistory();
    }

    public LiveData<List<GameHistoryEntity>> getHistory() {
        return history;
    }
}
