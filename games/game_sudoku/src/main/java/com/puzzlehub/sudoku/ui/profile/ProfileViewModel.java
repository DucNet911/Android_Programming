package com.puzzlehub.sudoku.ui.profile;

import android.app.Activity;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.puzzlehub.sudoku.data.local.GameHistoryEntity;
import com.puzzlehub.sudoku.data.repository.GameRepository;
import com.puzzlehub.sudoku.data.repository.ProfileRepository;
import com.puzzlehub.sudoku.data.repository.SocialRepository;
import com.puzzlehub.sudoku.domain.Difficulty;
import androidx.core.util.Supplier;
import com.puzzlehub.sudoku.utils.AndroidTasks;
import com.puzzlehub.sudoku.utils.SudokuTask;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlowKt;

public final class ProfileViewModel extends ViewModel {
    public record Statistics(int wins, int perfect, int streak, @Nullable Long best) {}

    private final GameRepository games;
    private final ProfileRepository profiles;
    private final SocialRepository social;
    private final Executor main = new Handler(Looper.getMainLooper())::post;
    private final MutableLiveData<Difficulty> level = new MutableLiveData<>(Difficulty.BEGINNER);
    private final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    private final MutableStateFlow<ProfileFeedback> message = StateFlowKt.MutableStateFlow(null);
    private final MediatorLiveData<Statistics> stats = new MediatorLiveData<>(new Statistics(0, 0, 0, null));
    private SudokuTask<Void> credential;
    private SudokuTask<Void> running;
    private boolean cleared;

    public ProfileViewModel(GameRepository games, ProfileRepository profiles, @Nullable SocialRepository social) {
        this.games = games;
        this.profiles = profiles;
        this.social = social;
        stats.addSource(games.observeHistory(), history -> refreshStatistics());
        stats.addSource(level, selected -> refreshStatistics());
    }

    public ProfileRepository getProfiles() { return profiles; }
    public LiveData<Difficulty> getLevel() { return level; }
    public LiveData<Boolean> getBusy() { return busy; }
    public LiveData<Statistics> getStats() { return stats; }
    public MutableStateFlow<ProfileFeedback> getMessage() { return message; }

    public void selectLevel(Difficulty difficulty) { level.setValue(difficulty); }

    private void refreshStatistics() {
        List<GameHistoryEntity> rows = games.observeHistory().getValue();
        Difficulty selected = level.getValue();
        if (rows == null || selected == null) {
            return;
        }
        List<GameHistoryEntity> records = new ArrayList<>();
        for (GameHistoryEntity item : rows) {
            if ("SOLO".equals(item.mode) && selected.name().equals(item.difficulty)) {
                records.add(item);
            }
        }
        Collections.sort(records, (a, b) -> Long.compare(a.date, b.date));

        int wins = 0;
        int perfect = 0;
        int consecutive = 0;
        int streak = 0;
        Long best = null;
        for (GameHistoryEntity item : records) {
            if ("WON".equals(item.result)) {
                wins++;
                consecutive++;
                streak = Math.max(streak, consecutive);
                if (item.mistakes == 0 && item.hints == 0) {
                    perfect++;
                }
                if (best == null || item.duration < best) {
                    best = item.duration;
                }
            } else {
                consecutive = 0;
            }
        }
        stats.setValue(new Statistics(wins, perfect, streak, best));
    }

    public void consumeMessage() { message.setValue(null); }

    public void signIn(Activity activity, String country) {
        action(() -> checkAccountCanChange().thenComposeAsync(ignored -> {
            credential = profiles.signIn(activity);
            return credential;
        }, main).thenComposeAsync(ignored -> {
            profiles.saveCountry(country);
            return saveCurrentPlayerName();
        }, main).thenComposeAsync(ignored -> publishProfile(country), main));
    }

    private SudokuTask<Void> publishProfile(String country) {
        if (social == null) {
            showSuccess("Đăng nhập Google thành công");
            return SudokuTask.completedFuture(null);
        }
        String name = profiles.getState().getValue().name();
        SudokuTask<Void> publish = AndroidTasks.withTimeout(social.publishProfile(name, country), SocialRepository.NETWORK_TIMEOUT);
        return publish.handleAsync((value, error) -> {
            if (error == null) {
                String player = social.getPlayerId();
                if (player != null) {
                    social.scheduleRankingSync(player);
                }
                showSuccess("Đăng nhập Google thành công");
            } else if (!(error instanceof CancellationException)) {
                message.setValue(new ProfileFeedback("Đã đăng nhập. Mở Bạn bè để thử đồng bộ quốc gia.", false));
            }
            return null;
        }, main);
    }

    public void signOut(Activity activity) {
        action(() -> checkAccountCanChange().thenComposeAsync(ignored -> profiles.signOut(activity), main)
                .thenCompose(ignored -> saveCurrentPlayerName())
                .thenRunAsync(() -> showSuccess("Đã đăng xuất"), main));
    }

    public void updateDetails(String name, String description) {
        action(() -> {
            profiles.updateDetails(name, description);
            return saveCurrentPlayerName();
        });
    }

    public void background(String id) {
        action(() -> {
            profiles.chooseBackground(id);
            return SudokuTask.completedFuture(null);
        });
    }

    public void importAvatar(Uri uri) {
        action(() -> profiles.importAvatar(uri).thenRunAsync(() -> showSuccess("Đã đổi ảnh đại diện"), main));
    }

    public void importCover(Uri uri) {
        action(() -> profiles.importCover(uri).thenRunAsync(() -> showSuccess("Đã đổi ảnh nền"), main));
    }

    private SudokuTask<Void> checkAccountCanChange() {
        return games.settings().thenAccept(settings -> {
            if (!settings.activeRoom.trim().isEmpty()) {
                throw new IllegalStateException("Hãy rời phòng đang chơi trước khi đổi tài khoản.");
            }
        });
    }

    private SudokuTask<Void> saveCurrentPlayerName() {
        return games.updatePlayerName(profiles.getState().getValue().name());
    }

    private void showSuccess(String text) {
        if (!cleared) {
            message.setValue(new ProfileFeedback(text, true));
        }
    }

    private void action(Supplier<SudokuTask<Void>> operation) {
        if (Boolean.TRUE.equals(busy.getValue())) {
            return;
        }
        busy.setValue(true);
        try {
            running = operation.get();
            AndroidTasks.onComplete(running, ignored -> finish(null), this::finish);
        } catch (RuntimeException error) {
            finish(error);
        }
    }

    private void finish(@Nullable Throwable error) {
        if (cleared) {
            return;
        }
        credential = null;
        running = null;
        busy.setValue(false);
        if (error != null && !(error instanceof CancellationException)
                && !(error instanceof GetCredentialCancellationException)) {
            String detail = error.getMessage();
            if (detail == null) {
                detail = "Chưa thực hiện được. Hãy thử lại.";
            }
            message.setValue(new ProfileFeedback(detail, false));
        }
    }

    @Override protected void onCleared() {
        cleared = true;
        if (credential != null) {
            credential.cancel(false);
        }
        if (running != null) {
            running.cancel(false);
        }
        super.onCleared();
    }
}
