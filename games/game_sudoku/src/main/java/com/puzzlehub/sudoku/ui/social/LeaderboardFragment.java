package com.puzzlehub.sudoku.ui.social;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.puzzlehub.sudoku.game.R;
import com.puzzlehub.sudoku.SudokuRuntime;
import com.puzzlehub.sudoku.data.local.GameHistoryEntity;
import com.puzzlehub.sudoku.data.repository.DemoPlayers;
import com.puzzlehub.sudoku.data.repository.PlayerProfile;
import com.puzzlehub.sudoku.data.repository.RankingEntry;
import com.puzzlehub.sudoku.data.repository.SocialPlayer;
import com.puzzlehub.sudoku.data.repository.SocialRepository;
import com.puzzlehub.sudoku.game.databinding.FragmentLeaderboardBinding;
import com.puzzlehub.sudoku.domain.Difficulty;
import com.puzzlehub.sudoku.ui.MainActivity;
import com.puzzlehub.sudoku.ui.widgets.AppDialogs;
import com.puzzlehub.sudoku.ui.widgets.AppNotices;
import com.puzzlehub.sudoku.utils.AndroidTasks;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CancellationException;
import com.puzzlehub.sudoku.utils.SudokuTask;
import java.util.concurrent.TimeoutException;

public final class LeaderboardFragment extends Fragment {
    private enum Scope { FRIENDS, COUNTRY }

    private final List<SudokuTask<?>> requests = new ArrayList<>();
    private Scope scope = Scope.FRIENDS;
    private Difficulty difficulty = Difficulty.BEGINNER;
    private int generation;

    public LeaderboardFragment() { super(R.layout.fragment_leaderboard); }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        FragmentLeaderboardBinding binding = FragmentLeaderboardBinding.bind(view);
        if (saved != null) {
            scope = Scope.valueOf(saved.getString("scope", Scope.FRIENDS.name()));
            difficulty = Difficulty.valueOf(saved.getString("difficulty", Difficulty.BEGINNER.name()));
        }
        binding.leaderboardFriends.setOnClickListener(button -> {
            scope = Scope.FRIENDS;
            refresh(binding);
        });
        binding.leaderboardCountry.setOnClickListener(button -> {
            scope = Scope.COUNTRY;
            refresh(binding);
        });
        binding.leaderboardDifficulty.setOnClickListener(button -> AppDialogs.difficulty(this, selected -> {
            difficulty = selected;
            refresh(binding);
        }));
        binding.leaderboardSignIn.setOnClickListener(button -> ((MainActivity) requireActivity()).selectTab(R.id.nav_settings));
        binding.leaderboardRegion.setOnClickListener(button -> changeCountry(binding));
        binding.leaderboardRefresh.setOnClickListener(button -> refresh(binding));

        app().getGames().observeHistory().observe(getViewLifecycleOwner(), records -> {
            PlayerProfile profile = app().getProfiles().getState().getValue();
            if (profile.demo()) {
                showLocalRanking(binding, profile, countryOf(profile), records);
            }
        });
        refresh(binding);
    }

    @Override public void onSaveInstanceState(@NonNull Bundle state) {
        state.putString("scope", scope.name());
        state.putString("difficulty", difficulty.name());
        super.onSaveInstanceState(state);
    }

    private void refresh(FragmentLeaderboardBinding binding) {
        cancelRequests();
        binding.leaderboardRows.removeAllViews();
        binding.leaderboardFriends.setChecked(scope == Scope.FRIENDS);
        binding.leaderboardCountry.setChecked(scope == Scope.COUNTRY);
        binding.leaderboardDifficulty.setText("Sudoku · " + difficulty.getLabel() + "  ▾");

        SudokuRuntime app = app();
        SocialRepository social = app.getSocial();
        PlayerProfile profile = app.getProfiles().getState().getValue();
        String player = null;
        if (social != null) {
            player = social.getPlayerId();
        }
        String country = countryOf(profile);
        setVisible(binding.leaderboardSignIn, !profile.signedIn() && scope == Scope.FRIENDS);
        setVisible(binding.leaderboardActions, player != null || scope == Scope.COUNTRY);
        binding.leaderboardRegion.setText(CountryPicker.name(country) + "  ▾");
        binding.leaderboardRegion.setIconResource(CountryPicker.flag(country));
        setVisible(binding.leaderboardLoading, false);

        if (profile.demo()) {
            List<GameHistoryEntity> records = app.getGames().observeHistory().getValue();
            if (records != null) {
                showLocalRanking(binding, profile, country, records);
            }
            return;
        }
        if (scope == Scope.COUNTRY && (player == null || profile.country().trim().isEmpty())) {
            showSamples(binding, country, difficulty);
            return;
        }
        if (social == null || player == null) {
            String message = "Đăng nhập để so thời gian giải Sudoku với bạn bè hoặc người chơi cùng quốc gia.";
            if (profile.signedIn()) {
                message = "Chưa kết nối được bảng xếp hạng.";
            }
            binding.leaderboardStatus.setText(message);
            return;
        }
        if (profile.country().trim().isEmpty()) {
            binding.leaderboardStatus.setText("Chọn quốc gia để tạo hồ sơ bảng xếp hạng.");
            return;
        }
        loadOnline(binding, social, profile.name(), player, country);
    }

    private void loadOnline(FragmentLeaderboardBinding binding, SocialRepository social, String name, String player,
                            String country) {
        int ticket = generation;
        Scope requestedScope = scope;
        Difficulty requestedLevel = difficulty;
        binding.leaderboardStatus.setText("");
        setVisible(binding.leaderboardLoading, true);
        social.scheduleProfileUpdate(name, country);

        SudokuTask<List<RankingEntry>> ranking = social.awaitProfileUpdates()
                .thenCompose(ignored -> social.syncRanking())
                .thenCompose(ignored -> {
                    if (requestedScope == Scope.COUNTRY) {
                        return social.countryRanking(country, requestedLevel);
                    }
                    return social.friendRanking(requestedLevel);
                });
        SudokuTask<List<RankingEntry>> load = AndroidTasks.withTimeout(ranking, SocialRepository.NETWORK_TIMEOUT);
        requests.add(load);
        AndroidTasks.onComplete(load, rows -> {
            if (ticket != generation) {
                return;
            }
            setVisible(binding.leaderboardLoading, false);
            if (requestedScope == Scope.COUNTRY && rows.isEmpty()) {
                showSamples(binding, country, requestedLevel);
            } else {
                showRows(binding, rows, player, requestedScope);
            }
        }, error -> {
            if (ticket != generation) {
                return;
            }
            setVisible(binding.leaderboardLoading, false);
            if (error instanceof TimeoutException) {
                binding.leaderboardStatus.setText("Kết nối quá lâu. Bấm Làm mới để thử lại.");
            } else if (!(error instanceof CancellationException)) {
                binding.leaderboardStatus.setText("Chưa tải được bảng xếp hạng. Bấm Làm mới để thử lại.");
            }
        });
    }

    private void cancelRequests() {
        generation++;
        List<SudokuTask<?>> pending = new ArrayList<>(requests);
        requests.clear();
        for (SudokuTask<?> future : pending) {
            future.cancel(false);
        }
    }

    private void changeCountry(FragmentLeaderboardBinding binding) {
        SudokuRuntime app = app();
        PlayerProfile profile = app.getProfiles().getState().getValue();
        CountryPicker.show(this, profile.country(), "Lưu quốc gia", country -> {
            if (CountrySelection.save(app, country)) {
                AppNotices.success(binding.getRoot(), "Đã lưu quốc gia");
                refresh(binding);
            }
        });
    }

    private void showLocalRanking(FragmentLeaderboardBinding binding, PlayerProfile profile,
                                  String country, List<GameHistoryEntity> records) {
        List<RankingEntry> samples;
        if (scope == Scope.COUNTRY) {
            samples = DemoPlayers.countryRanking(country, difficulty);
        } else {
            samples = app().getDemoPlayers().friendRanking(difficulty);
        }
        List<RankingEntry> rows = new ArrayList<>();
        for (RankingEntry sample : samples) {
            if (!sample.getPlayer().getId().equals(profile.username())) {
                rows.add(sample);
            }
        }
        Long best = null;
        for (GameHistoryEntity record : records) {
            if (DemoPlayers.ownsHistory(profile.username(), record.id) && "SOLO".equals(record.mode)
                    && difficulty.name().equals(record.difficulty) && "WON".equals(record.result)
                    && record.duration > 0L && (best == null || record.duration < best)) {
                best = record.duration;
            }
        }
        if (best != null) {
            rows.add(new RankingEntry(new SocialPlayer(profile.username(), profile.name(), country), best * 1000L));
        }
        java.util.Collections.sort(rows, (a, b) -> {
            int cmp = Long.compare(a.getDurationMs(), b.getDurationMs());
            return cmp != 0 ? cmp : a.getPlayer().getId().compareTo(b.getPlayer().getId());
        });
        showRows(binding, rows, profile.username(), scope);
        if (scope == Scope.COUNTRY) {
            binding.leaderboardStatus.setText("Thành tích tại " + CountryPicker.name(country));
        }
    }

    private void showSamples(FragmentLeaderboardBinding binding, String country, Difficulty level) {
        showRows(binding, DemoPlayers.countryRanking(country, level), null, Scope.COUNTRY);
        binding.leaderboardStatus.setText("Dữ liệu mẫu · " + CountryPicker.name(country));
        binding.leaderboardRegion.setText(CountryPicker.name(country) + "  ▾");
    }

    private void showRows(FragmentLeaderboardBinding binding, List<RankingEntry> rows, @Nullable String own, Scope scope) {
        binding.leaderboardRows.removeAllViews();
        String message = "Thời gian giải nhanh nhất của bạn và bạn bè.";
        if (rows.isEmpty()) {
            message = "Hoàn thành một ván ở độ khó này khi đã đăng nhập để lên bảng.";
        } else if (scope == Scope.COUNTRY) {
            message = "Top 50 thời gian giải nhanh nhất trong quốc gia của bạn.";
        }
        binding.leaderboardStatus.setText(message);
        for (int index = 0; index < rows.size(); index++) {
            RankingEntry entry = rows.get(index);
            binding.leaderboardRows.addView(LeaderboardRows.create(requireContext(), index + 1, entry,
                    entry.getPlayer().getId().equals(own)));
        }
    }

    private static String countryOf(PlayerProfile profile) {
        if (profile.country().trim().isEmpty()) {
            return "VN";
        }
        return profile.country();
    }

    private static void setVisible(View view, boolean visible) {
        if (visible) {
            view.setVisibility(View.VISIBLE);
        } else {
            view.setVisibility(View.GONE);
        }
    }

    private SudokuRuntime app() { return SudokuRuntime.get(requireContext()); }

    @Override public void onDestroyView() {
        cancelRequests();
        super.onDestroyView();
    }
}
