package com.puzzlehub.sudoku.ui.profile;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.puzzlehub.sudoku.game.R;
import com.puzzlehub.sudoku.SudokuRuntime;
import com.puzzlehub.sudoku.data.repository.PlayerProgress;
import com.puzzlehub.sudoku.game.databinding.FragmentHubBinding;
import com.puzzlehub.sudoku.ui.widgets.ActionCardView;
import com.puzzlehub.sudoku.ui.widgets.AppDialogs;
import com.puzzlehub.sudoku.utils.AndroidTasks;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public final class AchievementsFragment extends Fragment {
    public AchievementsFragment() { super(R.layout.fragment_hub); }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        FragmentHubBinding binding = FragmentHubBinding.bind(view);
        binding.hubTitle.setText("Thành tựu");
        SudokuRuntime app = SudokuRuntime.get(requireContext());
        AndroidTasks.observe(getViewLifecycleOwner(), app.getProgress().getState(), state -> render(binding, state));
    }

    private void render(FragmentHubBinding binding, PlayerProgress state) {
        List<AchievementUi.Badge> badges = new ArrayList<>(AchievementUi.catalog());
        java.util.Collections.sort(badges, (a, b) -> Boolean.compare(
                !state.unlocked().containsKey(a.id()),
                !state.unlocked().containsKey(b.id())));
        binding.hubSubtitle.setText(state.unlocked().size() + "/" + badges.size() + " đã mở · Từng chút một, tiến xa hơn");
        binding.hubItems.removeAllViews();
        for (AchievementUi.Badge badge : badges) {
            ActionCardView row = new ActionCardView(requireContext());
            LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, dp(86));
            layout.bottomMargin = dp(10);
            row.setLayoutParams(layout);
            Long unlocked = state.unlocked().get(badge.id());
            int tint = R.color.related;
            int accent = R.color.muted;
            String detail = AchievementUi.progress(badge.id(), state);
            if (unlocked != null) {
                tint = R.color.peach;
                accent = R.color.peach_ink;
                detail = "Đã mở ngày " + DateFormat.getDateInstance().format(new Date(unlocked));
            } else {
                row.setAlpha(0.75f);
            }
            row.configure(badge.title(), badge.detail(), badge.icon(), tint, accent, false,
                    AchievementUi.progress(badge.id(), state));
            String body = badge.detail() + "\n\n" + detail;
            row.setOnClickListener(view -> AppDialogs.message(this, badge.title(), body, "Đã hiểu", badge.icon()));
            binding.hubItems.addView(row);
        }
    }

    private int dp(int value) { return (int) (getResources().getDisplayMetrics().density * value); }
}
