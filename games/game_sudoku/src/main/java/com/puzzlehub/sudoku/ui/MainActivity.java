package com.puzzlehub.sudoku.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.OneShotPreDrawListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Lifecycle;
import com.puzzlehub.sudoku.SudokuRuntime;
import com.puzzlehub.sudoku.data.repository.AiTutorRepository;
import com.puzzlehub.sudoku.data.repository.ProgressRepository;
import com.puzzlehub.sudoku.domain.Achievement;
import com.puzzlehub.sudoku.domain.DailyStreak;
import com.puzzlehub.sudoku.game.R;
import com.puzzlehub.sudoku.game.databinding.SudokuActivityMainBinding;
import com.puzzlehub.sudoku.ui.explore.ExploreFragment;
import com.puzzlehub.sudoku.ui.explore.SudokuGuideFragment;
import com.puzzlehub.sudoku.ui.game.GameFragment;
import com.puzzlehub.sudoku.ui.game.SudokuGameServices;
import com.puzzlehub.sudoku.ui.game.SudokuGameStorage;
import com.puzzlehub.sudoku.ui.home.HomeFragment;
import com.puzzlehub.sudoku.ui.multiplayer.LobbyFragment;
import com.puzzlehub.sudoku.ui.multiplayer.OnlineGameFragment;
import com.puzzlehub.sudoku.ui.profile.AchievementUi;
import com.puzzlehub.sudoku.ui.profile.ProfileFragment;
import com.puzzlehub.sudoku.ui.settings.SettingsFragment;
import com.puzzlehub.sudoku.ui.social.CompetitiveRankFragment;
import com.puzzlehub.sudoku.ui.social.LeaderboardFragment;
import com.puzzlehub.sudoku.ui.widgets.AppDialogs;
import com.puzzlehub.sudoku.utils.AndroidTasks;
import com.puzzlehub.sudoku.utils.SudokuTask;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements SudokuGameServices {
    private final List<Integer> tabs = Arrays.asList(R.id.nav_home, R.id.nav_explore,
            R.id.nav_leaderboard, R.id.nav_settings);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable checkIn = new Runnable() {
        @Override public void run() {
            runtime().getProgress().visit();
            main.postDelayed(this, 60_000L);
        }
    };
    private SudokuActivityMainBinding binding;
    private int selectedTab = R.id.nav_home;
    private ValueAnimator indicatorAnimator;
    private Fragment pendingSlide;
    private Dialog achievementDialog;

    public View getGlassBackdrop() { return binding.backdrop; }

    @Override protected void onCreate(@Nullable Bundle saved) {
        Bundle navigation = null;
        if (saved != null && saved.getInt("navigationVersion") == 2) {
            navigation = saved;
        }
        super.onCreate(navigation);
        if (!onInitializeHost(saved)) {
            return;
        }
        configureWindow();

        binding = SudokuActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        if (navigation != null && tabs.contains(navigation.getInt("tab"))) {
            selectedTab = navigation.getInt("tab");
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            binding.mainContent.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) binding.navigationCard.getLayoutParams();
            params.leftMargin = dp(20) + bars.left;
            params.rightMargin = dp(20) + bars.right;
            params.bottomMargin = dp(12) + bars.bottom;
            binding.navigationCard.setLayoutParams(params);
            return WindowInsetsCompat.CONSUMED;
        });

        binding.navigationCard.bindBackdrop(binding.backdrop);
        binding.navigationCard.enableLiquidMode();
        binding.selectionPill.bindBackdrop(binding.backdrop);
        binding.selectionPill.enableLiquidMode();
        binding.bottomNav.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View view, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                view.removeOnLayoutChangeListener(this);
                moveSelection(false);
            }
        });
        binding.toolbar.setNavigationOnClickListener(view -> getOnBackPressedDispatcher().onBackPressed());
        configureNavigation();
        if (navigation == null) {
            selectTab(selectedTab);
        } else {
            binding.bottomNav.select(selectedTab);
        }
        AndroidTasks.onComplete(runtime().getGames().settings(), settings -> {
            if (!isDestroyed()) {
                int mode = AppCompatDelegate.MODE_NIGHT_NO;
                if (settings.darkMode) {
                    mode = AppCompatDelegate.MODE_NIGHT_YES;
                }
                if (getDelegate().getLocalNightMode() != mode) {
                    getDelegate().setLocalNightMode(mode);
                }
            }
        }, error -> Log.w("SudokuSettings", "Unable to read display settings", error));
        AndroidTasks.observe(this, runtime().getProgress().getState(), state -> maybeCelebrate());
        updateChrome();
    }

    protected boolean onInitializeHost(@Nullable Bundle saved) {
        return true;
    }

    protected void onExitHost() {
        finish();
    }

    @SuppressWarnings("deprecation")
    private void configureWindow() {
        Window window = getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 28) {
            window.setNavigationBarDividerColor(Color.TRANSPARENT);
        }
        if (Build.VERSION.SDK_INT >= 29) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
    }

    private void configureNavigation() {
        FragmentManager fragments = getSupportFragmentManager();
        fragments.addOnBackStackChangedListener(this::updateChrome);
        fragments.registerFragmentLifecycleCallbacks(new FragmentManager.FragmentLifecycleCallbacks() {
            @Override public void onFragmentViewCreated(@NonNull FragmentManager manager, @NonNull Fragment fragment,
                                                        @NonNull View view, @Nullable Bundle saved) {
                OneShotPreDrawListener.add(view, () -> {
                    if (fragment == pendingSlide) {
                        pendingSlide = null;
                        moveSelection(true);
                        refreshTabGlass(700L);
                    }
                });
            }

            @Override public void onFragmentResumed(@NonNull FragmentManager manager, @NonNull Fragment fragment) {
                updateChrome();
                binding.navigationCard.refreshBackdrop(680L);
                binding.getRoot().post(MainActivity.this::maybeCelebrate);
            }
        }, false);
        binding.bottomNav.setOnTabSelected(id -> {
            if (id != selectedTab || fragments.getBackStackEntryCount() > 0) {
                selectTab(id);
            }
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                Fragment active = fragments.findFragmentById(R.id.host);
                if (active instanceof GameFragment) {
                    ((GameFragment) active).saveBeforeExit();
                }
                if (fragments.getBackStackEntryCount() > 0) {
                    fragments.popBackStack();
                } else if (selectedTab != R.id.nav_home) {
                    selectTab(R.id.nav_home);
                } else {
                    onExitHost();
                }
            }
        });
    }

    @Override protected void onStart() {
        super.onStart();
        if (binding != null) {
            checkIn.run();
        }
    }

    @Override protected void onStop() {
        main.removeCallbacks(checkIn);
        super.onStop();
    }

    public void showDailyStreak() {
        if (binding == null) {
            return;
        }
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.host);
        if (fragment == null) {
            return;
        }
        DailyStreak daily = runtime().getProgress().getState().getValue().daily();
        String detail = daily.current() + " ngày liên tiếp · Tốt nhất " + daily.best()
                + " ngày\n\nHôm nay đã ghi nhận. Mở Sudoku mỗi ngày để giữ chuỗi; không cần thắng ván hay đăng nhập Google.";
        AppDialogs.message(fragment, "Giữ lửa mỗi ngày", detail, "Đã hiểu", R.drawable.ic_level_hard);
    }

    private void maybeCelebrate() {
        if (binding == null) {
            return;
        }
        FragmentManager fragments = getSupportFragmentManager();
        if (isDestroyed() || !getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)
                || fragments.isStateSaved() || fragments.getBackStackEntryCount() > 0
                || (achievementDialog != null && achievementDialog.isShowing())) {
            return;
        }
        Fragment fragment = fragments.findFragmentById(R.id.host);
        if (fragment == null || !fragment.isAdded() || fragment.getView() == null) {
            return;
        }
        ProgressRepository progress = runtime().getProgress();
        Set<Achievement> pending = progress.getState().getValue().pending();
        List<Achievement> acknowledged = new ArrayList<>();
        List<String> descriptions = new ArrayList<>();
        for (AchievementUi.Badge badge : AchievementUi.catalog()) {
            if (pending.contains(badge.id())) {
                acknowledged.add(badge.id());
                descriptions.add(badge.title() + "\n" + badge.detail());
            }
        }
        if (acknowledged.isEmpty()) {
            return;
        }
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < descriptions.size(); i++) {
            if (i > 0) {
                body.append("\n\n");
            }
            body.append(descriptions.get(i));
        }
        Dialog dialog = AppDialogs.message(fragment, "Mở khóa thành tựu!", body.toString(),
                "Hay quá", null, R.drawable.ic_result_win, false, () -> progress.acknowledge(acknowledged), null, true);
        achievementDialog = dialog;
        if (dialog.getWindow() != null) {
            dialog.getWindow().getDecorView().addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View view) {}
                @Override public void onViewDetachedFromWindow(View view) {
                    view.removeOnAttachStateChangeListener(this);
                    if (achievementDialog == dialog) {
                        achievementDialog = null;
                    }
                    if (binding != null) {
                        binding.getRoot().post(MainActivity.this::maybeCelebrate);
                    }
                }
            });
        }
    }

    @Override protected void onPostResume() {
        super.onPostResume();
        if (binding == null) {
            return;
        }
        binding.getRoot().post(this::maybeCelebrate);
        SudokuRuntime app = runtime();
        String player = app.getGames().rankingPlayerId();
        if (player.trim().isEmpty()) {
            player = "guest";
        }
        String account = player;
        SudokuTask.runAsync(() -> {
            try {
                app.getExploreHistory().recoverPending(account);
            } catch (RuntimeException error) {
                Log.w("SudokuExplore", "Pending results will be retried", error);
            }
        });
    }

    public void selectTab(int id) {
        FragmentManager fragments = getSupportFragmentManager();
        int previous = selectedTab;
        boolean slide = fragments.findFragmentById(R.id.host) != null && previous != id;
        Fragment screen;
        if (id == R.id.nav_explore) {
            screen = new ExploreFragment();
        } else if (id == R.id.nav_leaderboard) {
            screen = new LeaderboardFragment();
        } else if (id == R.id.nav_settings) {
            screen = new ProfileFragment();
        } else {
            screen = new HomeFragment();
        }
        fragments.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        selectedTab = id;
        binding.bottomNav.select(id);
        pendingSlide = null;
        FragmentTransaction transaction = fragments.beginTransaction().setReorderingAllowed(true);
        if (slide) {
            pendingSlide = screen;
            if (tabs.indexOf(id) > tabs.indexOf(previous)) {
                transaction.setCustomAnimations(R.anim.page_enter_right, R.anim.page_exit_left);
            } else {
                transaction.setCustomAnimations(R.anim.page_enter_left, R.anim.page_exit_right);
            }
        } else {
            moveSelection(false);
        }
        transaction.replace(R.id.host, screen).commit();
        refreshTabGlass(700L);
    }

    private void moveSelection(boolean animate) {
        int width = binding.bottomNav.getWidth();
        if (width == 0) {
            return;
        }
        float column = (float) width / tabs.size();
        int index = Math.max(0, tabs.indexOf(selectedTab));
        int lensWidth = (int) (column - 4 * getResources().getDisplayMetrics().density);
        ViewGroup.LayoutParams params = binding.selectionPill.getLayoutParams();
        if (params.width != lensWidth) {
            params.width = lensWidth;
            binding.selectionPill.setLayoutParams(params);
        }
        float target = column * (index + 0.5f) - lensWidth / 2f;
        if (indicatorAnimator != null) {
            indicatorAnimator.cancel();
        }
        if (!animate || (Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled())) {
            binding.selectionPill.setTranslationX(target);
            tabFocus(lensWidth, 0f);
            return;
        }
        float start = binding.selectionPill.getTranslationX();
        binding.slideParticles.slide(start + lensWidth / 2f, target + lensWidth / 2f);
        indicatorAnimator = ValueAnimator.ofFloat(start, target);
        indicatorAnimator.setDuration(600L);
        indicatorAnimator.setInterpolator(new PathInterpolator(0.32f, 0f, 0.2f, 1f));
        indicatorAnimator.addUpdateListener(frame -> {
            binding.selectionPill.setTranslationX((float) frame.getAnimatedValue());
            binding.slideParticles.setProgress((float) frame.getCurrentPlayTime() / frame.getDuration());
            float wave = (float) Math.sin(Math.PI * frame.getAnimatedFraction());
            binding.selectionPill.setScaleX(1f + 0.16f * wave);
            binding.selectionPill.setScaleY(1f - 0.05f * wave);
            tabFocus(lensWidth, wave);
        });
        indicatorAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(Animator animation) { cancelled = true; }
            @Override public void onAnimationEnd(Animator animation) {
                if (!cancelled) {
                    binding.selectionPill.setTranslationX(target);
                    binding.slideParticles.finish();
                }
                binding.selectionPill.setScaleX(1f);
                binding.selectionPill.setScaleY(1f);
                tabFocus(lensWidth, 0f);
            }
        });
        indicatorAnimator.start();
    }

    private void tabFocus(int lensWidth, float motion) {
        binding.navigationCard.setLiquidFocus((binding.selectionPill.getTranslationX() + lensWidth / 2f)
                / binding.bottomNav.getWidth(), motion);
    }

    public void open(Fragment fragment) {
        pushScreen(fragment, null);
    }

    public void openOnlineMatch(String roomCode) {
        FragmentManager fragments = getSupportFragmentManager();
        if (fragments.findFragmentById(R.id.host) instanceof OnlineGameFragment) {
            return;
        }
        String tag = "online-match-" + roomCode.toUpperCase(Locale.ROOT);
        if (fragments.findFragmentByTag(tag) != null) {
            fragments.popBackStackImmediate(tag, 0);
            return;
        }
        pushScreen(new OnlineGameFragment(), tag);
    }

    private void pushScreen(Fragment fragment, @Nullable String tag) {
        getSupportFragmentManager().beginTransaction().setReorderingAllowed(true)
                .setCustomAnimations(R.anim.tab_enter_right, R.anim.tab_exit_left, R.anim.tab_enter_left, R.anim.tab_exit_right)
                .replace(R.id.host, fragment, tag).addToBackStack(tag).commit();
    }

    private void updateChrome() {
        if (binding == null) {
            return;
        }
        FragmentManager fragments = getSupportFragmentManager();
        Fragment fragment = fragments.findFragmentById(R.id.host);
        boolean playing = fragment instanceof GameFragment || fragment instanceof OnlineGameFragment
                || fragment instanceof SudokuGuideFragment;
        boolean customHeader = fragment instanceof LobbyFragment || fragment instanceof CompetitiveRankFragment
                || fragment instanceof SettingsFragment;
        if (fragments.getBackStackEntryCount() > 0 && !customHeader) {
            binding.toolbar.setVisibility(View.VISIBLE);
            binding.toolbar.setNavigationIcon(ContextCompat.getDrawable(this, R.drawable.ic_back));
        } else {
            binding.toolbar.setVisibility(View.GONE);
            binding.toolbar.setNavigationIcon(null);
        }
        int bottomSpace = 0;
        if (playing) {
            binding.navigationCard.setVisibility(View.GONE);
        } else {
            bottomSpace = dp(96);
            binding.navigationCard.setVisibility(View.VISIBLE);
            binding.navigationCard.refreshBackdrop();
        }
        if (binding.host.getPaddingBottom() != bottomSpace) {
            binding.host.setPadding(0, 0, 0, bottomSpace);
        }
    }

    private void refreshTabGlass(long duration) {
        binding.navigationCard.refreshBackdrop(duration);
        binding.selectionPill.refreshBackdrop(duration);
    }

    protected SudokuRuntime runtime() { return SudokuRuntime.get(this); }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density); }

    @Override public SudokuGameStorage getSudokuStorage() { return runtime().getSudokuStorage(); }
    @Override public AiTutorRepository getSudokuTutor() { return runtime().getSudokuTutor(); }

    @Override protected void onSaveInstanceState(@NonNull Bundle state) {
        state.putInt("navigationVersion", 2);
        state.putInt("tab", selectedTab);
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy() {
        if (indicatorAnimator != null) {
            indicatorAnimator.cancel();
        }
        super.onDestroy();
    }
}
