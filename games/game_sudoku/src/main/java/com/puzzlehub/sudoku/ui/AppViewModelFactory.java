package com.puzzlehub.sudoku.ui;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.puzzlehub.sudoku.SudokuRuntime;
import com.puzzlehub.sudoku.ui.game.GameViewModel;
import com.puzzlehub.sudoku.ui.history.HistoryViewModel;
import com.puzzlehub.sudoku.ui.home.HomeViewModel;
import com.puzzlehub.sudoku.ui.multiplayer.MultiplayerViewModel;
import com.puzzlehub.sudoku.ui.profile.ProfileViewModel;
import com.puzzlehub.sudoku.ui.settings.SettingsViewModel;
import com.puzzlehub.sudoku.ui.social.RankViewModel;

public final class AppViewModelFactory implements ViewModelProvider.Factory {
    private final SudokuRuntime app;

    public AppViewModelFactory(SudokuRuntime app) {
        this.app = app;
    }

    @NonNull @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass == GameViewModel.class) {
            return modelClass.cast(new GameViewModel(app.getGames(), app.getTutor()));
        }
        if (modelClass == MultiplayerViewModel.class) {
            return modelClass.cast(new MultiplayerViewModel(app.getMultiplayer(), app.getGames()));
        }
        if (modelClass == HomeViewModel.class) {
            return modelClass.cast(new HomeViewModel(app.getGames()));
        }
        if (modelClass == SettingsViewModel.class) {
            return modelClass.cast(new SettingsViewModel(app.getGames()));
        }
        if (modelClass == ProfileViewModel.class) {
            return modelClass.cast(new ProfileViewModel(app.getGames(), app.getProfiles(), app.getSocial()));
        }
        if (modelClass == RankViewModel.class) {
            return modelClass.cast(new RankViewModel(app.getSocial(), app.getProfiles(), app.getDemoPlayers()));
        }
        if (modelClass == HistoryViewModel.class) {
            return modelClass.cast(new HistoryViewModel(app.getGames()));
        }
        throw new IllegalArgumentException("Unknown ViewModel: " + modelClass);
    }
}
