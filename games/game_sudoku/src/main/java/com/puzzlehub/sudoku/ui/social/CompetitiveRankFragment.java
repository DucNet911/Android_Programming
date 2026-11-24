package com.puzzlehub.sudoku.ui.social;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.puzzlehub.sudoku.game.R;
import com.puzzlehub.sudoku.SudokuRuntime;
import com.puzzlehub.sudoku.game.databinding.FragmentCompetitiveRankBinding;
import com.puzzlehub.sudoku.domain.CompetitiveRank;
import com.puzzlehub.sudoku.ui.AppViewModelFactory;
import com.puzzlehub.sudoku.ui.MainActivity;
import com.puzzlehub.sudoku.ui.multiplayer.LobbyFragment;

public final class CompetitiveRankFragment extends Fragment {
    private RankViewModel model;

    public CompetitiveRankFragment() {
        super(R.layout.fragment_competitive_rank);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        FragmentCompetitiveRankBinding binding = FragmentCompetitiveRankBinding.bind(view);
        MainActivity activity = (MainActivity) requireActivity();
        model = new ViewModelProvider(activity, new AppViewModelFactory(SudokuRuntime.get(activity)))
                .get(RankViewModel.class);
        binding.rankBack.setOnClickListener(button -> activity.getOnBackPressedDispatcher().onBackPressed());
        binding.rankDetailSpotlight.hideRings();
        binding.rankDetailSpotlight.animateBadge(binding.rankBadge);
        binding.rankBadge.setOnClickListener(button -> activity.open(new LobbyFragment()));
        binding.openRankLobby.setOnClickListener(button -> binding.rankBadge.performClick());
        binding.rankSignIn.setOnClickListener(button -> activity.selectTab(R.id.nav_settings));
        binding.rankRefresh.setOnClickListener(button -> model.refresh());
        model.getState().observe(getViewLifecycleOwner(), state -> render(binding, state));
    }

    @Override
    public void onResume() {
        super.onResume();
        model.refresh();
    }

    private void render(FragmentCompetitiveRankBinding binding, RankViewModel.State state) {
        Long points = state.points();
        if (points == null && !state.signedIn()) {
            points = 0L;
        }
        CompetitiveRank rank = state.rank();
        CompetitiveRank next = null;
        if (points != null) {
            next = rank.getNext();
            binding.currentRankPoints.setText(RankArt.points(points) + " điểm");
        }
        RankArt.show(binding.rankBadge, rank);
        binding.currentRank.setText(rank.getLabel());
        visible(binding.currentRankPoints, points != null);
        visible(binding.rankProgress, next != null);
        visible(binding.rankProgressLabel, points != null);
        if (points != null && next != null) {
            binding.rankProgress.setMax(next.getMinimumPoints() - rank.getMinimumPoints());
            binding.rankProgress.setProgress((int) (points - rank.getMinimumPoints()));
            binding.rankProgressLabel.setText("Còn " + RankArt.points(next.getMinimumPoints() - points)
                    + " điểm để lên " + next.getLabel());
        } else if (points != null) {
            binding.rankProgressLabel.setText("Đã đạt bậc cao nhất");
        }

        visible(binding.rankSignIn, !state.signedIn());
        visible(binding.rankRefresh, state.unavailable());
        String status = "";
        if (!state.signedIn()) {
            status = "Đăng nhập để xem điểm Rank cá nhân.";
        } else if (state.loading() && points == null) {
            status = "Đang tải điểm Rank…";
        } else if (state.unavailable()) {
            status = "Chưa cập nhật được điểm Rank.";
        }
        binding.rankStatus.setText(status);
        visible(binding.rankStatus, !status.isEmpty());
    }

    private void visible(View view, boolean show) {
        if (show) {
            view.setVisibility(View.VISIBLE);
        } else {
            view.setVisibility(View.GONE);
        }
    }
}
