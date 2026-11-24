package com.puzzlehub.sudoku.ui.social;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.fragment.app.Fragment;
import com.puzzlehub.sudoku.game.databinding.DialogRankTiersBinding;
import com.puzzlehub.sudoku.game.databinding.ItemRankTierBinding;
import com.puzzlehub.sudoku.domain.CompetitiveRank;
import com.puzzlehub.sudoku.ui.widgets.AppDialogs;

public final class RankTiersDialog {
    private RankTiersDialog() {}

    public static void show(Fragment fragment, Long points) {
        DialogRankTiersBinding binding = DialogRankTiersBinding.inflate(fragment.getLayoutInflater());
        CompetitiveRank current = CompetitiveRank.WARRIOR;
        if (points != null) {
            current = CompetitiveRank.fromPoints(points);
        }
        CompetitiveRank[] tiers = CompetitiveRank.values();
        for (int index = tiers.length - 1; index >= 0; index--) {
            CompetitiveRank tier = tiers[index];
            ItemRankTierBinding row = ItemRankTierBinding.inflate(
                    fragment.getLayoutInflater(), binding.tierRows, false);
            RankArt.show(row.tierBadge, tier);
            row.tierName.setText(tier.getLabel());
            row.tierPoints.setText(RankArt.points(tier.getMinimumPoints()) + " điểm");
            if (tier == current) {
                row.tierCurrent.setVisibility(View.VISIBLE);
            }
            binding.tierRows.addView(row.getRoot());
        }

        int screenHeight = fragment.getResources().getDisplayMetrics().heightPixels;
        float density = fragment.getResources().getDisplayMetrics().density;
        int height = Math.min((int) (360 * density), screenHeight / 2);
        binding.getRoot().setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, height));
        AppDialogs.content(fragment, "Các bậc Rank", binding.getRoot(), "Đóng");
    }
}
