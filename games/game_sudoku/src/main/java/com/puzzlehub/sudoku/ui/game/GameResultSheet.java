package com.puzzlehub.sudoku.ui.game;

import android.graphics.Color;
import android.view.View;
import android.view.Window;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.puzzlehub.sudoku.game.R;
import com.puzzlehub.sudoku.game.databinding.SheetResultBinding;
import com.puzzlehub.sudoku.utils.Format;

public final class GameResultSheet {
    private GameResultSheet() {}

    public static BottomSheetDialog show(Fragment fragment, GameResultSummary result,
                                         Runnable onPrimary, Runnable onSecondary) {
        SheetResultBinding binding = SheetResultBinding.inflate(fragment.getLayoutInflater());
        BottomSheetDialog dialog = new BottomSheetDialog(fragment.requireContext());
        int icon = R.drawable.ic_result_retry;
        if (result.won()) {
            icon = R.drawable.ic_result_win;
        }
        binding.resultIcon.setImageResource(icon);
        binding.resultTitle.setText(result.title());
        binding.resultDetail.setText(result.detail());
        binding.timeValue.setText(Format.duration(result.seconds()));
        binding.scoreValue.setText(String.valueOf(result.score()));
        binding.mistakesValue.setText(result.mistakes() + "/3");
        binding.playAgain.setText(result.primary());
        binding.goHome.setText(result.secondary());
        binding.playAgain.setOnClickListener(view -> { dialog.dismiss(); onPrimary.run(); });
        binding.goHome.setOnClickListener(view -> { dialog.dismiss(); onSecondary.run(); });
        dialog.setContentView(binding.getRoot());
        dialog.setCancelable(result.dismissible());
        dialog.setOnShowListener(ignored -> {
            View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                sheet.setBackgroundColor(Color.TRANSPARENT);
            }
            dialog.getBehavior().setDraggable(result.dismissible());
            dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
            Window window = dialog.getWindow();
            if (window != null) {
                window.setDimAmount(0.28f);
            }
            binding.resultCard.setAlpha(0f);
            binding.resultCard.setTranslationY(12f * fragment.getResources().getDisplayMetrics().density);
            binding.resultCard.animate().alpha(1f).translationY(0f).setDuration(260L).start();
        });
        dialog.show();
        return dialog;
    }
}
