package com.puzzlehub.sudoku.ui.home;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.puzzlehub.sudoku.game.R;
import com.puzzlehub.sudoku.SudokuRuntime;
import com.puzzlehub.sudoku.game.databinding.FragmentHomeBinding;
import com.puzzlehub.sudoku.ui.AppViewModelFactory;
import com.puzzlehub.sudoku.ui.MainActivity;
import com.puzzlehub.sudoku.ui.game.GameFragment;
import com.puzzlehub.sudoku.ui.history.HistoryFragment;
import com.puzzlehub.sudoku.ui.profile.AvatarImages;
import com.puzzlehub.sudoku.ui.social.CompetitiveRankFragment;
import com.puzzlehub.sudoku.ui.social.RankArt;
import com.puzzlehub.sudoku.ui.social.RankViewModel;
import com.puzzlehub.sudoku.ui.widgets.AppDialogs;
import com.puzzlehub.sudoku.ui.widgets.FrostedGlassLayout;
import com.puzzlehub.sudoku.utils.AndroidTasks;
import java.util.List;

public final class HomeFragment extends Fragment {
    public HomeFragment() {
        super(R.layout.fragment_home);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        FragmentHomeBinding binding = FragmentHomeBinding.bind(view);
        MainActivity activity = (MainActivity) requireActivity();
        SudokuRuntime app = SudokuRuntime.get(activity);
        HomeViewModel model = new ViewModelProvider(this, new AppViewModelFactory(app))
                .get(HomeViewModel.class);

        for (FrostedGlassLayout card : java.util.Arrays.asList(binding.brand, binding.statistics, binding.history)) {
            card.bindBackdrop(activity.getGlassBackdrop());
            card.enableLiquidMode(1);
        }
        int lavender = ContextCompat.getColor(requireContext(), R.color.glass_lavender);
        binding.statistics.setGlassTint(lavender);
        binding.history.setGlassTint(ContextCompat.getColor(requireContext(), R.color.glass_peach));
        RankViewModel rank = new ViewModelProvider(activity, new AppViewModelFactory(app)).get(RankViewModel.class);
        rank.getState().observe(getViewLifecycleOwner(), state -> RankArt.show(binding.homeRankBadge, state.rank()));
        AndroidTasks.observe(getViewLifecycleOwner(), app.getProfiles().getState(), profile -> rank.refresh());
        binding.rankSpotlight.animateBadge(binding.homeRankBadge);

        binding.dailyFlame.setOnClickListener(button -> activity.showDailyStreak());
        binding.newGame.setOnClickListener(button -> AppDialogs.difficulty(this, difficulty -> {
            activity.open(GameFragment.newGame(difficulty));

        }));
        binding.continueGame.setOnClickListener(button -> activity.open(new GameFragment()));
        binding.competitiveRank.setOnClickListener(button -> activity.open(new CompetitiveRankFragment()));
        binding.homeAvatar.setOnClickListener(button -> activity.selectTab(R.id.nav_settings));
        binding.statistics.setOnClickListener(button -> activity.selectTab(R.id.nav_leaderboard));
        binding.history.setOnClickListener(button -> activity.open(new HistoryFragment()));

        model.refresh();
        model.getCanContinue().observe(getViewLifecycleOwner(), canContinue -> {
            if (canContinue) {
                binding.continueGame.setVisibility(View.VISIBLE);
            } else {
                binding.continueGame.setVisibility(View.GONE);
            }
        });
        AndroidTasks.observe(getViewLifecycleOwner(), app.getProfiles().getState(), profile ->
                AvatarImages.render(binding.homeAvatar, profile.getAvatar(), app.getProfiles().avatarFile()));
        AndroidTasks.observe(getViewLifecycleOwner(), app.getProgress().getState(), progress ->
                binding.dailyFlame.display(progress.getDaily().getCurrent(), progress.getDaily().getBest()));
    }
}
