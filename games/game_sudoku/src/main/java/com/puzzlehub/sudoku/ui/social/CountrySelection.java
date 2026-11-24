package com.puzzlehub.sudoku.ui.social;

import com.puzzlehub.sudoku.SudokuRuntime;
import com.puzzlehub.sudoku.data.repository.PlayerProfile;
import com.puzzlehub.sudoku.data.repository.SocialRepository;

public final class CountrySelection {
    private CountrySelection() {}

    public static boolean save(SudokuRuntime app, String country) {
        PlayerProfile profile = app.getProfiles().getState().getValue();
        if (country.equals(profile.getCountry())) {
            return false;
        }
        app.getProfiles().saveCountry(country);
        SocialRepository social = app.getSocial();
        if (!profile.getDemo() && social != null) {
            social.scheduleProfileUpdate(profile.getName(), country);
        }
        return true;
    }
}
