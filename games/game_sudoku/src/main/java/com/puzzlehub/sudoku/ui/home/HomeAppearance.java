package com.puzzlehub.sudoku.ui.home;

import android.view.View;
import androidx.core.content.ContextCompat;
import com.puzzlehub.sudoku.game.R;
import com.puzzlehub.sudoku.game.databinding.FragmentHomeBinding;
import com.puzzlehub.sudoku.ui.widgets.FrostedGlassLayout;

/** Dynamic effects shared by every game using fragment_home. */
final class HomeAppearance {
    private HomeAppearance() {}

    static void bind(FragmentHomeBinding binding, View backdrop) {
        for (FrostedGlassLayout card : java.util.Arrays.asList(binding.brand, binding.statistics, binding.history)) {
            card.bindBackdrop(backdrop);
            card.enableLiquidMode(1);
        }
        int lavender = ContextCompat.getColor(backdrop.getContext(), R.color.glass_lavender);
        binding.statistics.setGlassTint(lavender);
        binding.history.setGlassTint(ContextCompat.getColor(backdrop.getContext(), R.color.glass_peach));
        binding.rankSpotlight.animateBadge(binding.homeRankBadge);
    }
}
