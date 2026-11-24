package com.puzzlehub.sudoku.data.repository;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.CancellationSignal;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.ClearCredentialException;
import androidx.credentials.exceptions.GetCredentialException;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.puzzlehub.sudoku.data.model.ProfileCovers;
import com.puzzlehub.sudoku.game.R;
import com.puzzlehub.sudoku.utils.SudokuTask;
import com.puzzlehub.sudoku.utils.SudokuTaskException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import kotlinx.coroutines.flow.FlowKt;
import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlow;
import kotlinx.coroutines.flow.StateFlowKt;

/** Profile choices and imported images are stored under the active account on this device. */
public final class ProfileRepository {
    public static final List<String> AVATARS = Collections.unmodifiableList(Arrays.asList("cat", "fox", "panda", "robot"));
    private static final String DEFAULT_NAME = "Người chơi";
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private final Context context;
    private final FirebaseAuth auth;
    private final DemoPlayers demoPlayers;
    private final SharedPreferences preferences;
    private final MutableStateFlow<PlayerProfile> mutable;
    private final StateFlow<PlayerProfile> state;

    public ProfileRepository(Context context, @Nullable FirebaseAuth auth) {
        this(context, auth, new DemoPlayers(context));
    }

    public ProfileRepository(Context context, @Nullable FirebaseAuth auth, DemoPlayers demoPlayers) {
        this.context = context;
        this.auth = auth;
        this.demoPlayers = demoPlayers;
        preferences = context.getSharedPreferences("player_profiles", Context.MODE_PRIVATE);
        mutable = StateFlowKt.MutableStateFlow(load());
        state = FlowKt.asStateFlow(mutable);
        if (auth != null) {
            auth.addAuthStateListener(ignored -> refreshProfile());
        }
    }

    public StateFlow<PlayerProfile> getState() { return state; }

    public void refreshProfile() {
        mutable.setValue(load());
    }

    public void saveUsername(String username) {
        if (!UsernameAccounts.isValid(username)) {
            throw new IllegalArgumentException(UsernameAccounts.FORMAT);
        }
        write("username", username);
    }

    public void saveCountry(String country) {
        if (!Arrays.asList(Locale.getISOCountries()).contains(country)) {
            throw new IllegalArgumentException("Quốc gia không hợp lệ");
        }
        write("country", country);
    }

    public void rename(String value) {
        write("name", playerName(value));
    }

    public void updateDetails(String name, String description) {
        String account = scope();
        preferences.edit().putString(account + "_name", playerName(name))
                .putString(account + "_description", limit(description.trim(), 80)).apply();
        refreshProfile();
    }

    public void chooseAvatar(String avatar) {
        if (!AVATARS.contains(avatar)) {
            throw new IllegalArgumentException("Avatar không hợp lệ");
        }
        write("avatar", avatar);
    }

    public void chooseBackground(String value) {
        String background = ProfileCovers.canonical(value);
        if (ProfileCovers.find(background) == null && !("custom".equals(background) && coverFile() != null)) {
            throw new IllegalArgumentException("Nền không hợp lệ");
        }
        write("background", background);
    }

    @Nullable public File avatarFile() { return existing(photo(scope())); }
    @Nullable public File coverFile() { return existing(cover(scope())); }

    public SudokuTask<Void> importAvatar(Uri uri) {
        String account = scope();
        return importImage(uri, photo(account), account, "avatar", 512, 512);
    }

    public SudokuTask<Void> importCover(Uri uri) {
        String account = scope();
        return importImage(uri, cover(account), account, "background", 1024, 384);
    }

    private SudokuTask<Void> importImage(Uri uri, File destination, String account,
                                               String preference, int width, int height) {
        return SudokuTask.runAsync(() -> {
            try {
                ProfileImageStore.importImage(context, uri, destination, width, height);
            } catch (IOException error) {
                throw new SudokuTaskException(error);
            }
            preferences.edit().putString(account + "_" + preference, "custom").apply();
            if (account.equals(scope())) {
                refreshProfile();
            }
        });
    }

    public SudokuTask<Void> signIn(Activity activity) {
        SudokuTask<Void> result = new SudokuTask<>();
        if (auth == null) {
            result.completeExceptionally(new IllegalStateException(
                    "Đăng nhập Google chưa được kết nối. Bạn vẫn có thể chơi trên thiết bị."));
            return result;
        }
        SudokuTask<String> token = requestGoogleIdToken(activity);
        result.whenComplete((value, error) -> {
            if (result.isCancelled()) {
                token.cancel(false);
            }
        });
        token.whenComplete((value, error) -> {
            if (error != null) {
                result.completeExceptionally(error);
            } else if (!result.isCancelled()) {
                AuthCredential credential = GoogleAuthProvider.getCredential(value, null);
                authenticate(credential).addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        refreshProfile();
                        result.complete(null);
                    } else {
                        reportTaskFailure(result, task);
                    }
                });
            }
        });
        return result;
    }

    private SudokuTask<String> requestGoogleIdToken(Activity activity) {
        SudokuTask<String> result = new SudokuTask<>();
        String client = context.getString(R.string.sudoku_default_web_client_id);
        if (client.trim().isEmpty()) {
            result.completeExceptionally(new IllegalStateException(
                    "Đăng nhập Google chưa được kết nối. Bạn vẫn có thể chọn avatar và chơi trên thiết bị."));
            return result;
        }
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(new GetSignInWithGoogleOption.Builder(client).build()).build();
        CancellationSignal cancellation = new CancellationSignal();
        result.whenComplete((token, error) -> {
            if (result.isCancelled()) {
                cancellation.cancel();
            }
        });
        Executor main = ContextCompat.getMainExecutor(activity);
        CredentialManager.create(activity).getCredentialAsync(activity, request, cancellation, main,
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override public void onResult(GetCredentialResponse response) {
                        try {
                            Credential credential = response.getCredential();
                            if (!(credential instanceof CustomCredential) ||
                                    !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
                                throw new IllegalStateException("Không nhận được tài khoản Google.");
                            }
                            result.complete(GoogleIdTokenCredential.createFrom(credential.getData()).getIdToken());
                        } catch (Exception error) {
                            result.completeExceptionally(error);
                        }
                    }

                    @Override public void onError(GetCredentialException error) {
                        result.completeExceptionally(error);
                    }
                });
        return result;
    }

    private Task<AuthResult> authenticate(AuthCredential credential) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || !user.isAnonymous()) {
            return auth.signInWithCredential(credential);
        }
        return user.linkWithCredential(credential).continueWithTask(result -> {
            if (!result.isSuccessful() && !(result.getException() instanceof FirebaseAuthUserCollisionException)) {
                Exception error = result.getException();
                if (error != null) {
                    throw error;
                }
                throw new IllegalStateException("Không kết nối được tài khoản Google.");
            }
            return auth.signInWithCredential(credential);
        });
    }

    public SudokuTask<Void> signOut(Activity activity) {
        demoPlayers.signOut();
        if (auth != null) {
            auth.signOut();
        }
        refreshProfile();
        SudokuTask<Void> result = new SudokuTask<>();
        try {
            CredentialManager.create(activity).clearCredentialStateAsync(new ClearCredentialStateRequest(),
                    null, ContextCompat.getMainExecutor(activity), new CredentialManagerCallback<Void, ClearCredentialException>() {
                        @Override public void onResult(Void value) { result.complete(null); }
                        @Override public void onError(ClearCredentialException error) { result.complete(null); }
                    });
        } catch (RuntimeException error) {
            result.complete(null);
        }
        return result;
    }

    private static void reportTaskFailure(SudokuTask<Void> future, Task<?> task) {
        Exception error = task.getException();
        if (error == null) {
            error = new IllegalStateException("Không kết nối được tài khoản Google.");
        }
        future.completeExceptionally(error);
    }

    private PlayerProfile load() {
        FirebaseUser user = signedInUser();
        SocialPlayer demo = null;
        if (user == null) {
            demo = demoPlayers.current();
        }
        String account = scope(user, demo);
        String email = "";
        String name = DEFAULT_NAME;
        String handle = "";
        String country = "";
        if (user != null) {
            email = orDefault(user.getEmail(), "");
            handle = UsernameAccounts.usernameFromEmail(email);
            if (!handle.trim().isEmpty()) {
                name = handle;
            }
            name = orDefault(user.getDisplayName(), name);
        } else if (demo != null) {
            name = demo.getName();
            handle = demo.getId();
            country = demo.getCountry();
        }
        String avatar = choice(account, "avatar", "cat");
        if ("custom".equals(avatar) && !photo(account).exists()) {
            avatar = "cat";
        }
        String background = ProfileCovers.canonical(choice(account, "background", ProfileCovers.DEFAULT));
        if (ProfileCovers.find(background) == null && !("custom".equals(background) && cover(account).exists())) {
            background = ProfileCovers.DEFAULT;
        }

        if (demo == null) {
            handle = choice(account, "username", handle);
        }
        return new PlayerProfile(choice(account, "name", name), avatar, email, user != null || demo != null,
                choice(account, "description", "Hồ sơ trên thiết bị"), background,
                choice(account, "country", country), handle, demo != null);
    }

    private String scope() {
        FirebaseUser user = signedInUser();
        SocialPlayer demo = null;
        if (user == null) {
            demo = demoPlayers.current();
        }
        return scope(user, demo);
    }

    private String scope(@Nullable FirebaseUser user, @Nullable SocialPlayer demo) {
        if (user != null) {
            try {
                byte[] digest = MessageDigest.getInstance("SHA-256").digest(user.getUid().getBytes(StandardCharsets.UTF_8));
                char[] name = new char[digest.length * 2];
                for (int index = 0; index < digest.length; index++) {
                    int sample = digest[index] & 255;
                    name[2 * index] = HEX[sample >>> 4];
                    name[2 * index + 1] = HEX[sample & 15];
                }
                return new String(name);
            } catch (NoSuchAlgorithmException error) {
                throw new IllegalStateException("Unable to hash the profile ID", error);
            }
        }
        if (demo != null) {
            return demo.getId();
        }
        return "guest";
    }

    @Nullable private FirebaseUser signedInUser() {
        if (auth != null) {
            FirebaseUser user = auth.getCurrentUser();
            if (user != null && !user.isAnonymous()) {
                return user;
            }
        }
        return null;
    }

    private void write(String key, String value) {
        preferences.edit().putString(scope() + "_" + key, value).apply();
        refreshProfile();
    }

    private String choice(String account, String key, String defaultValue) {
        return orDefault(preferences.getString(account + "_" + key, defaultValue), defaultValue);
    }

    private File photo(String account) { return new File(context.getFilesDir(), "avatars/" + account + ".jpg"); }
    private File cover(String account) { return new File(context.getFilesDir(), "covers/" + account + ".jpg"); }

    @Nullable private static File existing(File file) {
        if (file.exists()) {
            return file;
        }
        return null;
    }

    private static String playerName(String value) {
        String name = limit(value.trim(), 24);
        if (name.trim().isEmpty()) {
            return DEFAULT_NAME;
        }
        return name;
    }

    private static String limit(String value, int length) {
        return value.substring(0, Math.min(value.length(), length));
    }

    private static String orDefault(@Nullable String value, String defaultValue) {
        if (value != null) {
            return value;
        }
        return defaultValue;
    }
}
