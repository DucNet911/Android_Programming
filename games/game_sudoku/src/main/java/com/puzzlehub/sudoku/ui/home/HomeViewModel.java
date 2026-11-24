package com.puzzlehub.sudoku.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.puzzlehub.sudoku.data.repository.GameRepository;

public final class HomeViewModel extends ViewModel {
    private final GameRepository games;
    private final MutableLiveData<Boolean> canContinue = new MutableLiveData<>(false);

    public HomeViewModel(GameRepository games) {
        this.games = games;
    }

    public LiveData<Boolean> getCanContinue() { return canContinue; }

    public void refresh() {
        games.loadGame().whenComplete((session, error) -> canContinue.postValue(error == null && session != null));
    }
}
