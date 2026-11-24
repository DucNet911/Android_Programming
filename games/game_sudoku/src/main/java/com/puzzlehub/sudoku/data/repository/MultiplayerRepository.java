package com.puzzlehub.sudoku.data.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;
import com.puzzlehub.sudoku.data.model.OnlineRoom;
import com.puzzlehub.sudoku.domain.Difficulty;
import com.puzzlehub.sudoku.domain.RoomPresence;
import com.puzzlehub.sudoku.domain.SparkRoom;
import com.puzzlehub.sudoku.domain.SparkRoomEngine;
import com.puzzlehub.sudoku.domain.SudokuGenerator;
import com.puzzlehub.sudoku.domain.SudokuSolver;
import com.puzzlehub.sudoku.domain.SudokuValidator;
import androidx.core.util.Consumer;
import androidx.core.util.Predicate;
import com.puzzlehub.sudoku.utils.SudokuCallbacks.UnaryOp;
import com.puzzlehub.sudoku.utils.SudokuTask;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/** Firebase transport for Spark rooms. SparkRoomEngine owns the game rules. */
public final class MultiplayerRepository {
    @FunctionalInterface
    public interface Subscription extends AutoCloseable {
        @Override void close();
    }

    @FunctionalInterface
    private interface SnapshotAction<T> {
        T run(DataSnapshot snapshot) throws Exception;
    }

    private final FirebaseAuth auth;
    private final FirebaseDatabase database;
    private final SharedPreferences preferences;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newCachedThreadPool();
    private final Object authLock = new Object();
    private final Object cleanupLock = new Object();
    private final SecureRandom random = new SecureRandom();
    private final Set<String> expiring = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<String> startedRooms = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<Subscription> subscriptions = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final SudokuTask<Void> clockReady = new SudokuTask<>();
    private final Subscription clockSubscription;
    private volatile long offset;
    private volatile OnlineRoom lastSyncedRoom;

    public MultiplayerRepository(FirebaseAuth auth, FirebaseDatabase database, Context context) {
        this.auth = auth;
        this.database = database;
        preferences = context.getApplicationContext().getSharedPreferences(
                "spark_room_cleanup", Context.MODE_PRIVATE);
        clockSubscription = listen(database.getReference(".info/serverTimeOffset"), snapshot -> {
            if (snapshot.getValue() instanceof Number value) {
                offset = value.longValue();
                clockReady.complete(null);
            }
        }, ignored -> {});
    }

    public String getUid() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            return "";
        }
        return user.getUid();
    }

    public long nowMillis() {
        return System.currentTimeMillis() + offset;
    }

    public SudokuTask<Void> authenticate() {
        return RepositoryIo.submit(io, () -> {
            ensureAuthenticated();
            return null;
        });
    }

    private void ensureAuthenticated() throws Exception {
        synchronized (authLock) {
            if (auth.getCurrentUser() == null) {
                RepositoryIo.await(auth.signInAnonymously(), 12000);
            }
        }
    }

    private void ready() throws Exception {
        ensureAuthenticated();
        awaitConnection(12000);
        try {
            RepositoryIo.await(clockReady, 2000);
        } catch (TimeoutException ignored) {
            // Device time is the fallback until Firebase publishes the offset.
        }
    }

    private DatabaseReference roomRef(String code) {
        return database.getReference("sparkRooms").child(code);
    }

    private DatabaseReference presenceRef(String code) {
        return database.getReference("sparkPresence").child(code);
    }

    private String checkedCode(String code) {
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (!SparkRoomEngine.CODE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Mã phòng cần 6 ký tự hợp lệ");
        }
        return normalized;
    }

    public SudokuTask<String> createRoom(String mode, String difficulty, String name) {
        return RepositoryIo.submit(io, () -> {
            ready();
            Difficulty level = Difficulty.valueOf(difficulty);
            var puzzle = new SudokuGenerator().generatePuzzle(level);
            for (int attempt = 0; attempt < 8; attempt++) {
                String code = newRoomCode();
                SparkRoom room = SparkRoomEngine.create(getUid(), name, code, mode, level,
                        puzzle, nowMillis());
                try {
                    RepositoryIo.await(roomRef(code).setValue(room.encode()), 10000);
                } catch (DatabaseException error) {
                    if (attempt == 7) {
                        throw new IllegalStateException(
                                "Không tạo được phòng. Kiểm tra kết nối hoặc Rules Spark trong Realtime Database", error);
                    }
                    continue;
                }
                lastSyncedRoom = OnlineRoom.from(room, "WAITING", 0);
                updatePresenceQuietly(code, false);
                rememberOwned(code, room.createdAt());
                io.execute(this::cleanupOwned);
                return code;
            }
            throw new IllegalStateException("Chưa tạo được mã phòng. Thử lại nhé");
        });
    }

    private String newRoomCode() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder code = new StringBuilder(6);
        for (int index = 0; index < 6; index++) {
            code.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return code.toString();
    }

    public SudokuTask<String> joinRoom(String code, String name) {
        return RepositoryIo.submit(io, () -> {
            String normalized = checkedCode(code);
            ready();
            DatabaseReference ref = roomRef(normalized);
            withSnapshot(ref, value -> true, 10000,
                    "Không tìm thấy phòng đang chờ hoặc phòng đã đầy", snapshot -> {
                        if (!snapshot.exists()) {
                            throw new IllegalStateException("Không tìm thấy phòng này");
                        }
                        SparkRoom room = SparkRoom.decode(snapshot.getValue());
                        if (getUid().equals(room.hostId()) && "WAITING".equals(room.status())) {
                            throw new IllegalStateException("Đăng nhập bằng tài khoản khác để vào phòng này");
                        }
                        validatePuzzle(room);
                        SparkRoom joined = SparkRoomEngine.join(room, getUid(), name, nowMillis());
                        try {
                            RepositoryIo.await(ref.setValue(joined.encode()), 10000);
                        } catch (DatabaseException error) {
                            throw new IllegalStateException("Phòng đã đầy hoặc đã bắt đầu", error);
                        }
                        lastSyncedRoom = OnlineRoom.from(joined, "READY", 0);
                        return null;
                    });
            updatePresenceQuietly(normalized, true);
            return normalized;
        });
    }

    public void leaveRoomInBackground(String code) {
        leaveRoom(code).exceptionally(error -> null);
    }

    public OnlineRoom cachedRoom(String code) {
        OnlineRoom room = lastSyncedRoom;
        if (room != null && room.code().equalsIgnoreCase(code)) {
            return room;
        }
        return null;
    }

    public SudokuTask<Void> startMatch(String code) {
        return RepositoryIo.submit(io, () -> {
            String normalized = checkedCode(code);
            startedRooms.add(normalized);
            ready();
            updatePresence(normalized, true, 10000);
            return null;
        });
    }

    public SudokuTask<OnlineRoom> fetchActiveRoom(String code) {
        return RepositoryIo.submit(io, () -> {
            try {
                String normalized = checkedCode(code);
                ensureAuthenticated();
                DataSnapshot snapshot = withSnapshot(roomRef(normalized), value -> true,
                        6000, null, value -> value);
                if (!snapshot.exists()) {
                    return null;
                }
                SparkRoom room = SparkRoom.decode(snapshot.getValue());
                if (room.getTerminal() || nowMillis() >= room.expiresAt()
                        || !room.players().containsKey(getUid())) {
                    return null;
                }
                return OnlineRoom.from(room, room.status(), 0);
            } catch (Exception ignored) {
                return null;
            }
        });
    }

    private void validatePuzzle(SparkRoom room) {
        boolean hasEmptyCell = false;
        for (int index = 0; index < 81; index++) {
            int given = room.initial().get(index);
            if (given == 0) {
                hasEmptyCell = true;
            } else if (given != room.solution().get(index)) {
                throw new IllegalStateException("Đề trong phòng không hợp lệ");
            }
        }
        SudokuSolver solver = new SudokuSolver();
        if (!hasEmptyCell || !SudokuValidator.isComplete(room.solution())
                || solver.countSolutions(room.initial(), 2) != 1
                || !room.solution().equals(solver.solve(room.initial()))) {
            throw new IllegalStateException("Đề trong phòng không hợp lệ");
        }
    }

    public SudokuTask<Void> sendMove(String code, int index, int value, long revision) {
        return RepositoryIo.submit(io, () -> {
            ready();
            mutate(code, room -> SparkRoomEngine.move(room, getUid(), index, value, revision, nowMillis()));
            return null;
        });
    }

    public SudokuTask<Void> leaveRoom(String code) {
        return RepositoryIo.submit(io, () -> {
            String normalized = checkedCode(code);
            ready();
            markOffline(normalized);
            mutate(normalized, room -> SparkRoomEngine.leave(room, getUid(), nowMillis()));
            return null;
        });
    }

    public SudokuTask<Void> finishGame(String code) {
        return RepositoryIo.submit(io, () -> {
            if (!"FINISHED".equals(SparkRoom.decode(snapshot(roomRef(code)).getValue()).status())) {
                throw new IllegalStateException("Trận chưa hoàn thành");
            }
            return null;
        });
    }

    public Subscription observeRoom(String code, Consumer<OnlineRoom> onRoom, Consumer<Exception> onError) {
        RoomObserver observer = new RoomObserver(code, onRoom, onError);
        subscriptions.add(observer);
        observer.start();
        return observer;
    }

    private final class RoomObserver implements Subscription {
        private final String code;
        private final Consumer<OnlineRoom> onRoom;
        private final Consumer<Exception> onError;
        private final Runnable tick = this::tick;
        private Subscription roomSubscription;
        private Subscription presenceSubscription;
        private DataSnapshot roomSnapshot;
        private DataSnapshot presenceSnapshot;
        private OnlineRoom previous;
        private boolean closed;

        RoomObserver(String code, Consumer<OnlineRoom> onRoom, Consumer<Exception> onError) {
            this.code = code;
            this.onRoom = onRoom;
            this.onError = onError;
        }

        void start() {
            main.post(() -> {
                if (closed) {
                    return;
                }
                roomSubscription = listen(roomRef(code), value -> {
                    roomSnapshot = value;
                    publish();
                }, this::fail);
                presenceSubscription = listen(presenceRef(code), value -> {
                    presenceSnapshot = value;
                    publish();
                }, this::fail);
                main.postDelayed(tick, 1000);
            });
        }

        private void tick() {
            publish();
            if (!closed) {
                main.postDelayed(tick, 1000);
            }
        }

        private void publish() {
            if (closed || roomSnapshot == null || presenceSnapshot == null) {
                return;
            }
            if (!roomSnapshot.exists()) {
                fail(new IllegalStateException("Phòng đã được dọn hoặc không còn tồn tại"));
                return;
            }
            OnlineRoom current;
            try {
                SparkRoom room = SparkRoom.decode(roomSnapshot.getValue());
                Map<String, RoomPresence> online = readPresence(presenceSnapshot);
                boolean started = hasMatchStarted(room, online);
                boolean dropped = !started && waitingDropped(room, online);
                Long offlineSince = null;
                if (started) {
                    offlineSince = SparkRoomEngine.disconnectedSince(room, online);
                }
                expireWhenNeeded(room, dropped, offlineSince);
                String status = room.status();
                long disconnectedAt = 0;
                if ("PLAYING".equals(status)) {
                    if (dropped) {
                        status = "CANCELLED";
                    } else if (!started) {
                        status = "READY";
                    } else if (offlineSince != null) {
                        status = "PAUSED";
                        disconnectedAt = offlineSince;
                    }
                }
                current = OnlineRoom.from(room, status, disconnectedAt);
            } catch (RuntimeException error) {
                fail(error);
                return;
            }
            if (!current.equals(previous)) {
                previous = current;
                onRoom.accept(current);
            }
        }

        private void fail(Exception error) {
            if (!closed) {
                closeOnMain();
                onError.accept(error);
            }
        }

        @Override
        public void close() {
            if (Looper.myLooper() == main.getLooper()) {
                closeOnMain();
            } else {
                main.post(this::closeOnMain);
            }
        }

        private void closeOnMain() {
            closed = true;
            subscriptions.remove(this);
            main.removeCallbacks(tick);
            if (roomSubscription != null) {
                roomSubscription.close();
            }
            if (presenceSubscription != null) {
                presenceSubscription.close();
            }
        }
    }

    private Map<String, RoomPresence> readPresence(DataSnapshot snapshot) {
        Map<String, RoomPresence> entries = new HashMap<>();
        for (DataSnapshot child : snapshot.getChildren()) {
            boolean online = Boolean.TRUE.equals(child.child("online").getValue());
            long changedAt = 0;
            if (child.child("changedAt").getValue() instanceof Number value) {
                changedAt = value.longValue();
            }
            entries.put(child.getKey(), new RoomPresence(online, changedAt));
        }
        return entries;
    }

    private boolean hasMatchStarted(SparkRoom room, Map<String, RoomPresence> online) {
        if (!"PLAYING".equals(room.status())) {
            return false;
        }
        RoomPresence host = online.get(room.hostId());
        return (host != null && host.online()) || startedRooms.contains(room.code())
                || "MOVE".equals(room.action()) || room.version() > 1;
    }

    private boolean waitingDropped(SparkRoom room, Map<String, RoomPresence> online) {
        RoomPresence host = online.get(room.hostId());
        RoomPresence guest = online.get(room.guestId());
        return (host != null && !host.online()) || (guest != null && !guest.online());
    }

    private void expireWhenNeeded(SparkRoom room, boolean dropped, Long offlineSince) {
        if (room.getTerminal()) {
            return;
        }
        long now = nowMillis();
        boolean cancelWaiting = dropped && room.players().containsKey(getUid());
        boolean timedOut = offlineSince != null && now - offlineSince >= SparkRoomEngine.RECONNECT_MS;
        if (!cancelWaiting && !timedOut && now < room.expiresAt()) {
            return;
        }
        if (!expiring.add(room.code())) {
            return;
        }
        RepositoryIo.submit(io, () -> {
            try {
                if (cancelWaiting) {
                    mutate(room.code(), current -> SparkRoomEngine.leave(current, getUid(), nowMillis()));
                } else {
                    Map<String, RoomPresence> online = readPresence(snapshot(presenceRef(room.code())));
                    mutate(room.code(), current -> SparkRoomEngine.expire(current, getUid(), online, nowMillis()));
                }
            } finally {
                expiring.remove(room.code());
            }
            return null;
        });
    }

    public Subscription observeConnection(Consumer<Boolean> onConnection, Consumer<Exception> onError) {
        AtomicReference<Boolean> previous = new AtomicReference<>();
        return listen(database.getReference(".info/connected"), snapshot -> {
            boolean connected = Boolean.TRUE.equals(snapshot.getValue());
            if (!Boolean.valueOf(connected).equals(previous.getAndSet(connected))) {
                onConnection.accept(connected);
            }
        }, onError);
    }

    private void awaitConnection(long timeout) throws Exception {
        withSnapshot(database.getReference(".info/connected"),
                value -> Boolean.TRUE.equals(value.getValue()), timeout, null, value -> value);
    }

    public SudokuTask<Void> armDisconnect(String code) {
        return RepositoryIo.submit(io, () -> {
            updatePresence(code, false, 6000);
            return null;
        });
    }

    public SudokuTask<Void> markOnline(String code) {
        return RepositoryIo.submit(io, () -> {
            updatePresence(code, true, 10000);
            return null;
        });
    }

    private void updatePresence(String code, boolean online, long timeout) throws Exception {
        long deadline = android.os.SystemClock.elapsedRealtime() + timeout;
        awaitConnection(timeout);
        DatabaseReference ref = presenceRef(code).child(getUid());
        RepositoryIo.await(ref.onDisconnect().setValue(presence(false)),
                Math.max(1, deadline - android.os.SystemClock.elapsedRealtime()));
        if (online) {
            RepositoryIo.await(ref.setValue(presence(true)),
                    Math.max(1, deadline - android.os.SystemClock.elapsedRealtime()));
        }
    }

    private void updatePresenceQuietly(String code, boolean online) {
        try {
            if (online) {
                updatePresence(code, true, 10000);
            } else {
                updatePresence(code, false, 6000);
            }
        } catch (Exception ignored) {
            // The connection listener retries on reconnect.
        }
    }

    public void markOffline(String code) {
        String uid = getUid();
        if (!uid.trim().isEmpty()) {
            presenceRef(code).child(uid).setValue(presence(false));
        }
    }

    private Map<String, Object> presence(boolean online) {
        Map<String, Object> map = new HashMap<>();
        map.put("online", online);
        map.put("changedAt", ServerValue.TIMESTAMP);
        return map;
    }

    private DataSnapshot snapshot(DatabaseReference ref) throws Exception {
        return withSnapshot(ref, value -> true, 10000, null, value -> value);
    }

    private <T> T withSnapshot(DatabaseReference ref, Predicate<DataSnapshot> ready,
            long timeout, String permissionMessage, SnapshotAction<T> action) throws Exception {
        SudokuTask<DataSnapshot> first = new SudokuTask<>();
        try (Subscription subscription = listen(ref, value -> {
            if (ready.test(value)) {
                first.complete(value);
            }
        }, error -> {
            if (permissionMessage != null) {
                first.completeExceptionally(new IllegalStateException(permissionMessage, error));
            } else {
                first.completeExceptionally(error);
            }
        })) {
            return action.run(RepositoryIo.await(first, timeout));
        }
    }

    private Subscription listen(DatabaseReference ref, Consumer<DataSnapshot> onData,
            Consumer<Exception> onError) {
        ValueEventListener listener = new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snapshot) { onData.accept(snapshot); }
            @Override public void onCancelled(DatabaseError error) { onError.accept(error.toException()); }
        };
        ref.addValueEventListener(listener);
        return () -> ref.removeEventListener(listener);
    }

    private void mutate(String code, UnaryOp<SparkRoom> change) throws Exception {
        DatabaseReference ref = roomRef(code);
        withSnapshot(ref, value -> true, 10000, null, first -> {
            DataSnapshot current = first;
            for (int attempt = 0; attempt < 3; attempt++) {
                if (!current.exists()) {
                    throw new IllegalStateException("Phòng không còn tồn tại");
                }
                SparkRoom room = SparkRoom.decode(current.getValue());
                SparkRoom next = change.apply(room);
                if (next.equals(room)) {
                    return null;
                }
                try {
                    RepositoryIo.await(ref.setValue(next.encode()), 8000);
                    return null;
                } catch (DatabaseException error) {
                    if (attempt == 2) {
                        transact(ref, change);
                        return null;
                    }
                    Thread.sleep(80);
                    current = snapshot(ref);
                }
            }
            return null;
        });
    }

    private void transact(DatabaseReference ref, UnaryOp<SparkRoom> change) throws Exception {
        SudokuTask<Void> committed = new SudokuTask<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        ref.runTransaction(new Transaction.Handler() {
            @Override
            public Transaction.Result doTransaction(MutableData current) {
                if (committed.isDone()) {
                    return Transaction.abort();
                }
                if (current.getValue() == null) {
                    return Transaction.success(current);
                }
                try {
                    SparkRoom room = SparkRoom.decode(current.getValue());
                    SparkRoom next = change.apply(room);
                    if (next.equals(room)) {
                        return Transaction.abort();
                    }
                    current.setValue(next.encode());
                    return Transaction.success(current);
                } catch (Exception error) {
                    failure.set(error);
                    return Transaction.abort();
                }
            }

            @Override
            public void onComplete(DatabaseError error, boolean success, DataSnapshot snapshot) {
                if (failure.get() != null) {
                    committed.completeExceptionally(failure.get());
                } else if (error != null) {
                    committed.completeExceptionally(error.toException());
                } else {
                    committed.complete(null);
                }
            }
        }, false);
        try {
            RepositoryIo.await(committed, 15000);
        } finally {
            committed.cancel(false);
        }
    }

    private String ownedKey() {
        return "owned_" + getUid() + "_" + database.getReference().toString().hashCode();
    }

    private void rememberOwned(String code, long createdAt) {
        synchronized (cleanupLock) {
            String key = ownedKey();
            Set<String> entries = new HashSet<>(preferences.getStringSet(key, Collections.emptySet()));
            entries.add(code + ":" + createdAt);
            preferences.edit().putStringSet(key, entries).apply();
        }
    }

    private void cleanupOwned() {
        String owner = getUid();
        String key = ownedKey();
        Set<String> entries = new HashSet<>(preferences.getStringSet(key, Collections.emptySet()));
        Set<String> removed = new HashSet<>();
        int attempts = 0;
        for (String entry : entries) {
            int separator = entry.indexOf(':');
            if (separator < 0) {
                continue;
            }
            try {
                long created = Long.parseLong(entry.substring(separator + 1));
                if (nowMillis() - created <= SparkRoomEngine.ROOM_LIFETIME) {
                    continue;
                }
                if (attempts++ >= 3) {
                    break;
                }
                String code = entry.substring(0, separator);
                DataSnapshot snapshot = snapshot(roomRef(code));
                if (snapshot.exists()) {
                    SparkRoom room = SparkRoom.decode(snapshot.getValue());
                    if (!owner.equals(room.hostId()) || nowMillis() < room.expiresAt()) {
                        continue;
                    }
                    RepositoryIo.await(presenceRef(code).removeValue(), 10000);
                    RepositoryIo.await(roomRef(code).removeValue(), 10000);
                }
                removed.add(entry);
            } catch (Exception ignored) {
                // Rooms that could not be removed will be retried after the next create.
            }
        }
        synchronized (cleanupLock) {
            Set<String> current = new HashSet<>(preferences.getStringSet(key, Collections.emptySet()));
            current.removeAll(removed);
            preferences.edit().putStringSet(key, current).apply();
        }
    }

    public void close() {
        clockSubscription.close();
        for (Subscription subscription : subscriptions) {
            subscription.close();
        }
        io.shutdownNow();
    }
}
