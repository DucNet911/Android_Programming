package com.puzzlehub.sudoku.data.model;

import androidx.annotation.Nullable;
import com.puzzlehub.sudoku.game.R;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ProfileCovers {
    public static final String DEFAULT = "aurora";
    private static final List<ProfileCover> PRESETS = Collections.unmodifiableList(Arrays.asList(
            new ProfileCover("aurora", "Cực quang", R.drawable.cover_aurora),
            new ProfileCover("forest", "Rừng sương", R.drawable.cover_forest),
            new ProfileCover("sakura", "Anh đào", R.drawable.cover_sakura),
            new ProfileCover("sunset", "Biển chiều", R.drawable.cover_sunset)));

    private ProfileCovers() {}

    public static List<ProfileCover> presets() {
        return PRESETS;
    }

    public static String canonical(String id) {
        return switch (id) {
            case "blue", "midnight" -> "aurora";
            case "teal" -> "forest";
            case "purple", "rose" -> "sakura";
            default -> id;
        };
    }

    @Nullable
    public static ProfileCover find(String id) {
        String canonical = canonical(id);
        for (ProfileCover entry : PRESETS) {
            if (entry.id().equals(canonical)) {
                return entry;
            }
        }
        return null;
    }
}
