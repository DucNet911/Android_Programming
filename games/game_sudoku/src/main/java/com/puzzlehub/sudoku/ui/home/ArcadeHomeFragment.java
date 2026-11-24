package com.puzzlehub.sudoku.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import com.example.arcade.core.GameDefinition;
import com.example.arcade.core.GameHomeHost;
import com.example.arcade.core.GameResult;
import com.example.arcade.core.LaunchMode;
import com.puzzlehub.sudoku.domain.CompetitiveRank;
import com.puzzlehub.sudoku.game.R;
import com.puzzlehub.sudoku.game.databinding.FragmentHomeBinding;
import com.puzzlehub.sudoku.game.databinding.SudokuActivityMainBinding;
import com.puzzlehub.sudoku.ui.social.RankArt;
import com.puzzlehub.sudoku.ui.widgets.AppDialogs;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Uses the Sudoku home and shell directly, with actions supplied by the current arcade game. */
public final class ArcadeHomeFragment extends Fragment {
    private SudokuActivityMainBinding shell;
    private GameHomeHost host;
    private int section;
    private OnBackPressedCallback back;

    @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                                       @Nullable Bundle state) {
        shell = SudokuActivityMainBinding.inflate(inflater, container, false);
        return shell.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        host = (GameHomeHost) requireActivity();
        shell.toolbar.setVisibility(View.GONE);
        shell.host.setPadding(0, 0, 0, dp(96));
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            shell.mainContent.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) shell.navigationCard.getLayoutParams();
            p.setMargins(dp(20) + bars.left, 0, dp(20) + bars.right, dp(12) + bars.bottom);
            shell.navigationCard.setLayoutParams(p);
            return WindowInsetsCompat.CONSUMED;
        });
        shell.navigationCard.bindBackdrop(shell.backdrop);
        shell.navigationCard.enableLiquidMode();
        shell.selectionPill.bindBackdrop(shell.backdrop);
        shell.selectionPill.enableLiquidMode();
        shell.bottomNav.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> {
            if (r-l != or-ol) positionPill(false);
        });
        shell.bottomNav.setOnTabSelected(this::show);
        back = new OnBackPressedCallback(false) {
            @Override public void handleOnBackPressed() { show(R.id.nav_home); }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), back);
        show(state == null ? R.id.nav_home : state.getInt("section", R.id.nav_home));
    }

    private void show(int target) {
        boolean animate = section != 0 && section != target;
        section = target;
        Fragment profile = getChildFragmentManager().findFragmentByTag("profile");
        if (profile != null && target != R.id.nav_settings) {
            getChildFragmentManager().beginTransaction().remove(profile).commitNow();
        }
        if (profile == null || target != R.id.nav_settings) shell.host.removeAllViews();
        back.setEnabled(target != R.id.nav_home);
        shell.bottomNav.select(target == R.id.history ? R.id.nav_home : target);
        positionPill(animate);
        if (target == R.id.nav_home) {
            FragmentHomeBinding home = FragmentHomeBinding.inflate(getLayoutInflater(), shell.host, true);
            HomeAppearance.bind(home, shell.backdrop);
            home.homeGameTitle.setText(host.game().title.toUpperCase(Locale.ROOT));
            home.homeModeBadge.setText(modeSummary().toUpperCase(Locale.ROOT));
            home.homeRankTitle.setText("Thành tích cá nhân");
            home.competitiveRank.setContentDescription("Thành tích cá nhân");
            RankArt.show(home.homeRankBadge, CompetitiveRank.WARRIOR);
            home.newGame.setOnClickListener(v -> chooseDifficulty());
            home.continueGame.setVisibility(View.VISIBLE);
            home.continueGame.setEnabled(host.request().launchMode == LaunchMode.RESUME);
            home.continueGame.setOnClickListener(v -> host.play(true));
            com.puzzlehub.sudoku.data.repository.ProfileRepository profiles =
                    com.puzzlehub.sudoku.SudokuRuntime.get(requireContext()).getProfiles();
            com.puzzlehub.sudoku.ui.profile.AvatarImages.render(home.homeAvatar,
                    profiles.getState().getValue().avatar(), profiles.avatarFile());
            home.homeAvatar.setOnClickListener(v -> show(R.id.nav_settings));
            home.competitiveRank.setOnClickListener(v -> show(R.id.nav_leaderboard));
            home.statistics.setOnClickListener(v -> show(R.id.nav_leaderboard));
            home.history.setOnClickListener(v -> show(R.id.history));
            int streak = streak(host.history());
            home.dailyFlame.display(streak, streak);
            home.dailyFlame.setContentDescription("Chuỗi chơi hiện tại: " + streak + " ngày");
            home.dailyFlame.setOnClickListener(v -> AppDialogs.message(this, "Chuỗi ngày chơi",
                    streak + " ngày liên tiếp có ván hoàn thành.", "Đóng", R.drawable.ic_notice_info));
        } else if (target == R.id.nav_settings) {
            if (profile == null) {
                getChildFragmentManager().beginTransaction().replace(R.id.host,
                        new com.puzzlehub.sudoku.ui.profile.ProfileFragment(), "profile").commitNow();
            }
        } else {
            showDetails(target);
        }
        shell.navigationCard.refreshBackdrop();
    }

    public void showHistory() { show(R.id.history); }

    public void showRankings() { show(R.id.nav_leaderboard); }

    public int currentStreak() { return streak(host.history()); }

    private String modeSummary() {
        return host.game().difficultyCount() + ("tetris".equals(host.game().id) ? " tốc độ" : " cỡ bàn");
    }

    private void chooseDifficulty() {
        GameDefinition game = host.game();
        String[] labels = new String[game.difficultyCount()];
        for (int i = 0; i < labels.length; i++) labels[i] = game.difficultyLabel(i);
        AppDialogs.gameModes(this, modeSummary(), labels, index -> host.startNew(game.difficultyId(index)));
    }

    private void positionPill(boolean animate) {
        int width = shell.bottomNav.getWidth();
        if (width == 0) return;
        int tab = section == R.id.history ? R.id.nav_home : section;
        int index = shell.bottomNav.indexOfChild(shell.bottomNav.findViewById(tab));
        float column = (float) width / shell.bottomNav.getChildCount();
        int lens = (int) (column - dp(4));
        ViewGroup.LayoutParams p = shell.selectionPill.getLayoutParams();
        if (p.width != lens) {
            p.width = lens;
            shell.selectionPill.setLayoutParams(p);
        }
        float center = column * (index + 0.5f);
        shell.selectionPill.animate().cancel();
        if (animate) shell.selectionPill.animate().translationX(center - lens / 2f).setDuration(220).start();
        else shell.selectionPill.setTranslationX(center - lens / 2f);
        shell.navigationCard.setLiquidFocus(center / width, 0);
    }

    private void showDetails(int target) {
        ScrollView scroll = new ScrollView(requireContext());
        LinearLayout rows = new LinearLayout(requireContext());
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(dp(20), dp(14), dp(20), dp(16));
        scroll.addView(rows);
        shell.host.addView(scroll);
        if (target == R.id.nav_explore) {
            line(rows, "Khám phá", true);
            line(rows, host.game().description, false);
            for (int i = 0; i < host.game().difficultyCount(); i++) line(rows, host.game().difficultyLabel(i), false);
            if ("tetris".equals(host.game().id) && host.request().launchMode != LaunchMode.RESUME) {
                android.widget.Button guide = new android.widget.Button(requireContext());
                guide.setText("Hướng dẫn và âm thanh");
                guide.setOnClickListener(v -> host.play(false));
                rows.addView(guide);
            }
            return;
        }
        List<GameResult> results = host.history();
        boolean ranked = target == R.id.nav_leaderboard;
        line(rows, ranked ? "Bảng xếp hạng" : "Lịch sử", true);
        if (ranked) {
            line(rows, "Thành tích cá nhân trên thiết bị", false);
            for (java.util.Iterator<GameResult> it = results.iterator(); it.hasNext(); )
                if (it.next().score == null) it.remove();
            Collections.sort(results, (a,b) -> Long.compare(b.score, a.score));
        }
        if (results.isEmpty()) line(rows, "Chưa có ván đã hoàn thành.", false);
        int rank = 0;
        for (GameResult result : results) {
            String date = DateFormat.getDateTimeInstance().format(new Date(result.finishedAtEpochMs));
            line(rows, (ranked ? "#" + (++rank) + "   " : "") + host.game().difficultyLabel(result.difficultyId)
                    + " · " + result.score + " điểm\n" + date, false);
        }
    }

    private void line(LinearLayout rows, String value, boolean title) {
        TextView text = new TextView(requireContext());
        text.setTextAppearance(title ? R.style.Text_Sudoku_Title : R.style.Text_Sudoku_Body);
        text.setText(value);
        text.setPadding(0, dp(8), 0, dp(10));
        rows.addView(text);
    }

    private static int streak(List<GameResult> results) {
        Calendar day = Calendar.getInstance();
        Set<Integer> days = new HashSet<>();
        for (GameResult r : results) {
            day.setTimeInMillis(r.finishedAtEpochMs);
            days.add(dayKey(day));
        }
        day.setTimeInMillis(System.currentTimeMillis());
        if (!days.contains(dayKey(day))) day.add(Calendar.DATE, -1);
        int count = 0;
        while (days.contains(dayKey(day))) {
            count++;
            day.add(Calendar.DATE, -1);
        }
        return count;
    }

    private static int dayKey(Calendar day) {
        return day.get(Calendar.YEAR) * 1000 + day.get(Calendar.DAY_OF_YEAR);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override public void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("section", section);
    }
    @Override public void onDestroyView() {
        super.onDestroyView();
        shell = null;
        host = null;
    }
}
