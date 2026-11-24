package com.puzzlehub.sudoku;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.lifecycle.FlowLiveDataConversions;
import androidx.room.Room;
import com.example.arcade.core.GameSessionStore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;
import com.puzzlehub.sudoku.data.local.SudokuDatabase;
import com.puzzlehub.sudoku.data.repository.AiTutorRepository;
import com.puzzlehub.sudoku.data.repository.DemoPlayers;
import com.puzzlehub.sudoku.data.repository.GameRepository;
import com.puzzlehub.sudoku.data.repository.LocalAiTutorRepository;
import com.puzzlehub.sudoku.data.repository.MultiplayerRepository;
import com.puzzlehub.sudoku.data.repository.ProfileRepository;
import com.puzzlehub.sudoku.data.repository.ProgressRepository;
import com.puzzlehub.sudoku.data.repository.SocialPlayer;
import com.puzzlehub.sudoku.data.repository.SocialRepository;
import com.puzzlehub.sudoku.data.repository.UsernameAccounts;
import com.puzzlehub.sudoku.explore.ExploreResultRepository;
import com.puzzlehub.sudoku.ui.game.SudokuGameServices;
import com.puzzlehub.sudoku.ui.game.SudokuGameStorage;
import kotlin.coroutines.EmptyCoroutineContext;

/** Module-scoped lazy runtime container replacing a host Application subclass. */
public final class SudokuRuntime implements SudokuGameServices {
    private static final String FIREBASE_APP_NAME = "sudoku-module";
    private static volatile SudokuRuntime instance;

    private final Context appContext;
    private final AiTutorRepository tutor = new LocalAiTutorRepository();
    private final GameRepository games;
    private final DemoPlayers demoPlayers;
    private final ProfileRepository profiles;
    private final ProgressRepository progress;
    @Nullable private MultiplayerRepository multiplayer;
    @Nullable private SocialRepository social;
    @Nullable private UsernameAccounts usernameAccounts;
    private ExploreResultRepository exploreHistory;

    public static SudokuRuntime get(Context context) {
        Context app = context.getApplicationContext();
        SudokuRuntime current = instance;
        if (current != null && current.appContext == app) {
            return current;
        }
        synchronized (SudokuRuntime.class) {
            if (instance == null || instance.appContext != app) {
                instance = new SudokuRuntime(app);
            }
            return instance;
        }
    }

    private SudokuRuntime(Context context) {
        this.appContext = context;
        games = new GameRepository(
                Room.databaseBuilder(context, SudokuDatabase.class, "sudoku.db")
                        .addMigrations(SudokuDatabase.ADD_RANKING_BEST, SudokuDatabase.ADD_COMPETITIVE_WINS,
                                SudokuDatabase.RENAME_COMPETITIVE_RESULTS).build(),
                new GameSessionStore(context));
        demoPlayers = new DemoPlayers(context);
        FirebaseApp firebase = initializeFirebase(context);
        FirebaseAuth auth = null;
        if (firebase != null) {
            try {
                auth = FirebaseAuth.getInstance(firebase);
            } catch (RuntimeException error) {
                Log.w("SudokuRuntime", "Firebase Auth unavailable", error);
            }
        }
        profiles = new ProfileRepository(context, auth, demoPlayers);
        FirebaseAuth accountAuth = auth;
        games.setPlayerId(() -> rankingPlayer(accountAuth));
        Runnable observeProfile = () -> FlowLiveDataConversions.asLiveData(
                profiles.getState(), EmptyCoroutineContext.INSTANCE, 0L)
                .observeForever(profile -> {
                    String account = "";
                    if (profile.getDemo()) {
                        account = profile.getUsername();
                    }
                    games.useLocalAccount(account);
                });
        if (Looper.myLooper() == Looper.getMainLooper()) {
            observeProfile.run();
        } else {
            new Handler(Looper.getMainLooper()).post(observeProfile);
        }
        progress = new ProgressRepository(context, games.observeDeviceHistory());
        if (firebase != null && auth != null) {
            connectDatabase(context, firebase, auth);
        }
    }

    @Nullable
    private static FirebaseApp initializeFirebase(Context context) {
        try {
            for (FirebaseApp existing : FirebaseApp.getApps(context)) {
                if (FIREBASE_APP_NAME.equals(existing.getName())) {
                    return existing;
                }
            }
            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setProjectId("sudoku-6afa4")
                    .setApplicationId("1:1025959006729:android:56167d66c6509c7d091ed8")
                    .setApiKey("AIzaSyCrW7ZqY8C9-6DJgB98KdjRwdzsU-Qf7rg")
                    .setDatabaseUrl("https://sudoku-6afa4-default-rtdb.asia-southeast1.firebasedatabase.app")
                    .setStorageBucket("sudoku-6afa4.firebasestorage.app")
                    .setGcmSenderId("1025959006729")
                    .build();
            return FirebaseApp.initializeApp(context, options, FIREBASE_APP_NAME);
        } catch (RuntimeException error) {
            Log.w("SudokuRuntime", "Firebase configuration unavailable", error);
            return null;
        }
    }

    private void connectDatabase(Context context, FirebaseApp firebase, FirebaseAuth auth) {
        FirebaseDatabase database;
        try {
            database = FirebaseDatabase.getInstance(firebase);
            database.setPersistenceEnabled(false);
        } catch (RuntimeException error) {
            Log.w("SudokuRuntime", "Realtime Database configuration unavailable", error);
            return;
        }
        multiplayer = new MultiplayerRepository(auth, database, context);
        social = new SocialRepository(auth, database, games);
        usernameAccounts = new UsernameAccounts(auth, database);
        games.setOnRankingDataChanged(social::scheduleRankingSync);
    }

    private String rankingPlayer(@Nullable FirebaseAuth auth) {
        if (auth != null) {
            FirebaseUser user = auth.getCurrentUser();
            if (user != null && !user.isAnonymous()) {
                return user.getUid();
            }
        }
        SocialPlayer local = demoPlayers.current();
        if (local != null) {
            return local.getId();
        }
        return "";
    }

    @Override public SudokuGameStorage getSudokuStorage() { return games; }
    @Override public AiTutorRepository getSudokuTutor() { return tutor; }
    public AiTutorRepository getTutor() { return tutor; }
    public GameRepository getGames() { return games; }
    public ProfileRepository getProfiles() { return profiles; }
    public ProgressRepository getProgress() { return progress; }
    public DemoPlayers getDemoPlayers() { return demoPlayers; }
    @Nullable public MultiplayerRepository getMultiplayer() { return multiplayer; }
    @Nullable public SocialRepository getSocial() { return social; }
    @Nullable public UsernameAccounts getUsernameAccounts() { return usernameAccounts; }

    public synchronized ExploreResultRepository getExploreHistory() {
        if (exploreHistory == null) {
            exploreHistory = new ExploreResultRepository(appContext);
        }
        return exploreHistory;
    }
}
