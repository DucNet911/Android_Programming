package com.puzzlehub.sudoku.data.repository;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class UsernameAccounts {
    private static final String DOMAIN = "@players.sudoku.example";
    private static final Pattern HANDLE = Pattern.compile("[a-z][a-z0-9_]{2,19}");
    public static final String FORMAT = "Dùng 3–20 ký tự, bắt đầu bằng chữ. Chỉ dùng a–z, số hoặc dấu _.";

    private final FirebaseAuth auth;
    private final DatabaseReference database;

    private static final class UsernameTaken extends IllegalArgumentException {
        private UsernameTaken() {
            super("Tên người chơi này đã được sử dụng.");
        }
    }

    public UsernameAccounts(FirebaseAuth auth, FirebaseDatabase database) {
        this.auth = auth;
        this.database = database.getReference();
    }

    public static String normalize(String value) {
        String handle = value.trim().toLowerCase(Locale.ROOT);
        if (handle.startsWith("@")) {
            handle = handle.substring(1);
        }
        return handle;
    }

    public static boolean isValid(String handle) {
        return HANDLE.matcher(handle).matches();
    }

    public static String usernameFromEmail(String email) {
        if (email == null || !email.endsWith(DOMAIN)) {
            return "";
        }
        String username = email.substring(0, email.length() - DOMAIN.length());
        if (isValid(username)) {
            return username;
        }
        return "";
    }

    public Task<String> authenticate(String username, String password, boolean register) {
        if (!isValid(username) || username.startsWith("demo_")) {
            return Tasks.forException(new IllegalArgumentException(FORMAT));
        }
        String email = username + DOMAIN;
        Task<AuthResult> authTask;
        if (register) {
            authTask = auth.createUserWithEmailAndPassword(email, password);
        } else {
            authTask = auth.signInWithEmailAndPassword(email, password);
        }
        return authTask.onSuccessTask(result -> {
            FirebaseUser user = result.getUser();
            if (user == null) {
                return Tasks.forException(new IllegalStateException("Chưa đăng nhập được."));
            }
            return claim(username).continueWithTask(outcome -> {
                if (outcome.isSuccessful() || !isCurrent(user.getUid())) {
                    return outcome;
                }
                if (register && outcome.getException() instanceof UsernameTaken) {
                    return user.delete().continueWithTask(deleted -> {
                        signOutIfCurrent(user.getUid());
                        return outcome;
                    });
                }
                signOutIfCurrent(user.getUid());
                return outcome;
            });
        });
    }

    public Task<String> claim(String username) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || user.isAnonymous()) {
            return Tasks.forException(new IllegalStateException("Hãy đăng nhập trước."));
        }
        if (!isValid(username) || username.startsWith("demo_")) {
            return Tasks.forException(new IllegalArgumentException(FORMAT));
        }
        String uid = user.getUid();
        DatabaseReference byOwner = database.child("player_names_by_uid").child(uid);
        DatabaseReference byName = database.child("player_names").child(username);
        return byOwner.get().onSuccessTask(existing -> {
            String assigned = existing.getValue(String.class);
            if (username.equals(assigned)) {
                return Tasks.forResult(username);
            }
            if (assigned != null) {
                return Tasks.forException(new IllegalArgumentException("Tài khoản này đã có tên @" + assigned + "."));
            }
            if (!isCurrent(uid)) {
                return Tasks.forException(new IllegalStateException("Tài khoản đã thay đổi."));
            }
            return byName.get().onSuccessTask(candidate -> {
                if (candidate.exists()) {
                    return Tasks.forException(new UsernameTaken());
                }
                if (!isCurrent(uid)) {
                    return Tasks.forException(new IllegalStateException("Tài khoản đã thay đổi."));
                }
                Map<String, Object> reservation = new HashMap<>();
                reservation.put("player_names/" + username, uid);
                reservation.put("player_names_by_uid/" + uid, username);
                return database.updateChildren(reservation).continueWithTask(write -> {
                    if (write.isSuccessful()) {
                        return Tasks.forResult(username);
                    }
                    return byName.get().onSuccessTask(current -> {
                        String owner = current.getValue(String.class);
                        if (uid.equals(owner)) {
                            return Tasks.forResult(username);
                        }
                        if (owner != null) {
                            return Tasks.forException(new UsernameTaken());
                        }
                        return Tasks.forException(new IllegalStateException("Chưa lưu được tên người chơi.", write.getException()));
                    });
                });
            });
        });
    }

    public Task<String> findUid(String username) {
        if (!isValid(username)) {
            return Tasks.forException(new IllegalArgumentException(FORMAT));
        }
        return database.child("player_names").child(username).get().onSuccessTask(record -> {
            String uid = record.getValue(String.class);
            if (uid == null) {
                return Tasks.forException(new IllegalArgumentException("Không tìm thấy tên người chơi này."));
            }
            return Tasks.forResult(uid);
        });
    }

    public Task<String> ownedUsername() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || user.isAnonymous()) {
            return Tasks.forResult("");
        }
        return database.child("player_names_by_uid").child(user.getUid()).get().onSuccessTask(record -> {
            String name = record.getValue(String.class);
            if (name == null) {
                name = "";
            }
            return Tasks.forResult(name);
        });
    }

    private boolean isCurrent(String uid) {
        FirebaseUser current = auth.getCurrentUser();
        return current != null && uid.equals(current.getUid());
    }

    private void signOutIfCurrent(String uid) {
        if (isCurrent(uid)) {
            auth.signOut();
        }
    }
}
