package com.puzzlehub.sudoku.data.repository;

import android.util.Log;
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;
import com.puzzlehub.sudoku.data.local.CompetitiveResultEntity;
import com.puzzlehub.sudoku.data.local.RankingBestEntity;
import com.puzzlehub.sudoku.domain.CompetitiveRank;
import com.puzzlehub.sudoku.domain.Difficulty;
import com.puzzlehub.sudoku.utils.SudokuTask;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;

public final class SocialRepository {
    public static final String PENDING = "pending";
    public static final String ACCEPTED = "accepted";
    public static final long NETWORK_TIMEOUT = 10_000L;
    public static final Set<String> COUNTRIES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(Locale.getISOCountries())));
    private static final Pattern PLAYER_ID = Pattern.compile("[A-Za-z0-9_-]{1,128}");
    private static final long MAX_DURATION = 86_400_000L;
    private static final String TAG = "SudokuSocial";

    private record ProfileChange(String player, String name, String country) {}
    private record RankResult(String key, long startedAt, boolean won) {}
    private record Best(String player, long duration) {}
    private record Link(String key, String other, String status, boolean incoming) {}

    private final FirebaseAuth auth;
    private final GameRepository games;
    private final DatabaseReference root;
    private final DatabaseReference profiles;
    private final DatabaseReference links;
    private final DatabaseReference scores;
    private final DatabaseReference legacyWins;
    private final DatabaseReference results;
    private final DatabaseReference rooms;
    private final Executor writes = RepositoryIo.serial("sudoku-social");
    private ProfileChange pending;
    private SudokuTask<Void> profileUpdates = SudokuTask.completedFuture(null);
    private String pendingSync;
    private boolean syncing;

    public SocialRepository(FirebaseAuth auth, FirebaseDatabase database, GameRepository games) {
        this.auth = auth;
        this.games = games;
        root = database.getReference();
        profiles = root.child("social_profiles");
        links = root.child("social_links");
        scores = root.child("social_scores");
        legacyWins = root.child("competitive_wins");
        results = root.child("competitive_results");
        rooms = root.child("sparkRooms");
        auth.addAuthStateListener(ignored -> syncSignedInPlayer());
        database.getReference(".info/connected").addValueEventListener(new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snapshot) {
                if (Boolean.TRUE.equals(snapshot.getValue(Boolean.class))) {
                    flushProfile();
                    syncSignedInPlayer();
                }
            }
            @Override public void onCancelled(DatabaseError error) { Log.d(TAG, "Connection listener cancelled"); }
        });
    }

    @Nullable public String getPlayerId() {
        FirebaseUser user = auth.getCurrentUser();
        if (user != null && !user.isAnonymous()) {
            return user.getUid();
        }
        return null;
    }

    private void syncSignedInPlayer() {
        String player = getPlayerId();
        if (player != null) {
            scheduleRankingSync(player);
        }
    }

    public synchronized void scheduleRankingSync(String player) {
        if (!player.equals(getPlayerId())) {
            return;
        }
        pendingSync = player;
        if (!syncing) {
            syncing = true;
            writes.execute(this::flushRanking);
        }
    }

    private void flushRanking() {
        while (true) {
            String player;
            synchronized (this) {
                player = pendingSync;
                pendingSync = null;
                if (player == null) {
                    syncing = false;
                    return;
                }
            }
            try {
                if (player.equals(getPlayerId())) {
                    publishCompetitiveResults(player);
                    publishBests(player);
                }
            } catch (Exception error) {
                Log.w(TAG, "Ranking sync deferred", error);
            }
        }
    }

    public synchronized void scheduleProfileUpdate(String name, String country) {
        String player = getPlayerId();
        if (player != null) {
            pending = new ProfileChange(player, name, country);
            flushProfile();
        }
    }

    private synchronized void flushProfile() {
        if (pending != null && profileUpdates.isDone()) {
            profileUpdates = new SudokuTask<>();
            writes.execute(this::writePendingProfiles);
        }
    }

    private void writePendingProfiles() {
        while (true) {
            ProfileChange change;
            synchronized (this) {
                change = pending;
                if (change == null || !change.player().equals(getPlayerId())) {
                    pending = null;
                    profileUpdates.complete(null);
                    return;
                }
            }
            try {
                publishProfileFor(change);
                synchronized (this) {
                    if (change.equals(pending)) {
                        pending = null;
                    }
                }
                scheduleRankingSync(change.player());
            } catch (Exception error) {
                Log.w(TAG, "Profile sync deferred", error);
                synchronized (this) {
                    if (change.equals(pending)) {
                        profileUpdates.complete(null);
                        return;
                    }
                }
            }
        }
    }

    public synchronized SudokuTask<Void> awaitProfileUpdates() {
        return profileUpdates.thenApply(ignored -> null);
    }

    public SudokuTask<Void> publishProfile(String name, String country) {
        String player = getPlayerId();
        return RepositoryIo.submit(writes, () -> {
            checkPlayer(player);
            publishProfileFor(new ProfileChange(player, name, country));
            return null;
        });
    }

    private void publishProfileFor(ProfileChange change) throws Exception {
        String player = change.player();
        checkPlayer(player);
        checkCountry(change.country());
        String name = change.name().trim();
        name = name.substring(0, Math.min(name.length(), 24));
        if (name.trim().isEmpty()) {
            name = "Người chơi";
        }
        Map<String, Object> updates = new HashMap<>();
        Map<String, String> details = new HashMap<>();
        details.put("name", name);
        details.put("country", change.country());
        updates.put("social_profiles/" + player, details);
        SocialPlayer previous = player(player);
        if (previous != null && !previous.country().equals(change.country())) {
            Map<Difficulty, Task<DataSnapshot>> records = new EnumMap<>(Difficulty.class);
            for (Difficulty difficulty : Difficulty.values()) {
                records.put(difficulty, scores.child(difficulty.getId()).child(player).get());
            }
            for (var record : records.entrySet()) {
                if (await(record.getValue()).exists()) {
                    updates.put("social_scores/" + record.getKey().getId() + "/" + player + "/country", change.country());
                }
            }
        }
        checkPlayer(player);
        await(root.updateChildren(updates));
    }

    public SudokuTask<List<Friendship>> friends() {
        String player = getPlayerId();
        return RepositoryIo.submit(RepositoryIo.pool(), () -> friendsOf(player));
    }

    private List<Friendship> friendsOf(String player) throws Exception {
        checkPlayer(player);
        Task<DataSnapshot> sent = links.orderByChild("from").equalTo(player).get();
        Task<DataSnapshot> received = links.orderByChild("to").equalTo(player).get();
        List<Link> relations = new ArrayList<>();
        readLinks(await(sent), player, relations);
        readLinks(await(received), player, relations);
        Map<Link, Task<DataSnapshot>> people = new LinkedHashMap<>();
        for (Link link : relations) {
            people.put(link, profiles.child(link.other()).get());
        }
        List<Friendship> friends = new ArrayList<>();
        for (var entry : people.entrySet()) {
            Link link = entry.getKey();
            SocialPlayer other = readPlayer(link.other(), await(entry.getValue()));
            if (other != null) {
                friends.add(new Friendship(link.key(), other, link.status(), link.incoming()));
            }
        }
        Collections.sort(friends, (a, b) -> {
            int cmp = Integer.compare(friendOrder(a), friendOrder(b));
            if (cmp != 0) {
                return cmp;
            }
            return a.player().name().toLowerCase(Locale.ROOT)
                    .compareTo(b.player().name().toLowerCase(Locale.ROOT));
        });
        return friends;
    }

    private static void readLinks(DataSnapshot data, String player, List<Link> output) {
        for (DataSnapshot row : data.getChildren()) {
            String from = text(row, "from");
            String to = text(row, "to");
            String status = text(row, "status");
            if (from == null || to == null || status == null || row.getKey() == null) {
                continue;
            }
            boolean incoming = player.equals(to);
            String other = to;
            if (incoming) {
                other = from;
            }
            output.add(new Link(row.getKey(), other, status, incoming));
        }
    }

    private static int friendOrder(Friendship relation) {
        if (PENDING.equals(relation.status()) && relation.incoming()) {
            return 0;
        }
        if (ACCEPTED.equals(relation.status())) {
            return 1;
        }
        return 2;
    }

    public SudokuTask<Void> requestFriend(String code) {
        String player = getPlayerId();
        return RepositoryIo.submit(writes, () -> {
            checkPlayer(player);
            String target = code.trim();
            if (!PLAYER_ID.matcher(target).matches() || target.equals(player)) {
                throw new IllegalArgumentException("Mã bạn bè không hợp lệ.");
            }
            if (player(target) == null) {
                throw new IllegalArgumentException("Không tìm thấy người chơi có mã này.");
            }
            DatabaseReference outgoing = links.child(player + "~" + target);
            DatabaseReference incoming = links.child(target + "~" + player);
            if (await(outgoing.get()).exists() || await(incoming.get()).exists()) {
                throw new IllegalStateException("Hai bạn đã có kết nối hoặc lời mời đang chờ.");
            }
            checkPlayer(player);
            Map<String, String> request = new HashMap<>();
            request.put("from", player);
            request.put("to", target);
            request.put("status", PENDING);
            await(outgoing.setValue(request));
            return null;
        });
    }

    public SudokuTask<Void> acceptFriend(String key) { return updateFriend(key, true); }
    public SudokuTask<Void> removeFriend(String key) { return updateFriend(key, false); }

    private SudokuTask<Void> updateFriend(String key, boolean accept) {
        String player = getPlayerId();
        return RepositoryIo.submit(writes, () -> {
            checkPlayer(player);
            DatabaseReference link = links.child(key);
            if (accept) {
                await(link.child("status").setValue(ACCEPTED));
            } else {
                await(link.removeValue());
            }
            return null;
        });
    }

    public SudokuTask<List<RankingEntry>> countryRanking(String country, Difficulty difficulty) {
        String player = getPlayerId();
        return RepositoryIo.submit(RepositoryIo.pool(), () -> {
            checkPlayer(player);
            checkCountry(country);
            DataSnapshot rows = await(scores.child(difficulty.getId()).orderByChild("country").equalTo(country).get());
            List<Best> ordered = new ArrayList<>();
            for (DataSnapshot row : rows.getChildren()) {
                Long duration = row.child("durationMs").getValue(Long.class);
                if (row.getKey() != null && duration != null) {
                    ordered.add(new Best(row.getKey(), duration));
                }
            }
            Collections.sort(ordered, (a, b) -> {
                int cmp = Long.compare(a.duration(), b.duration());
                return cmp != 0 ? cmp : a.player().compareTo(b.player());
            });
            List<RankingEntry> ranking = new ArrayList<>();
            for (int first = 0; first < ordered.size() && ranking.size() < 50; first += 20) {
                Map<Best, Task<DataSnapshot>> batch = new LinkedHashMap<>();
                for (Best best : ordered.subList(first, Math.min(first + 20, ordered.size()))) {
                    batch.put(best, profiles.child(best.player()).get());
                }
                for (var entry : batch.entrySet()) {
                    Best best = entry.getKey();
                    SocialPlayer candidate = readPlayer(best.player(), await(entry.getValue()));
                    if (candidate != null && country.equals(candidate.country())) {
                        ranking.add(new RankingEntry(candidate, best.duration()));
                        if (ranking.size() == 50) {
                            break;
                        }
                    }
                }
            }
            return ranking;
        });
    }

    public SudokuTask<List<RankingEntry>> friendRanking(Difficulty difficulty) {
        String player = getPlayerId();
        return RepositoryIo.submit(RepositoryIo.pool(), () -> {
            List<SocialPlayer> participants = new ArrayList<>();
            for (Friendship relation : friendsOf(player)) {
                if (ACCEPTED.equals(relation.status())) {
                    participants.add(relation.player());
                }
            }
            SocialPlayer own = player(player);
            if (own != null) {
                participants.add(own);
            }
            Map<SocialPlayer, Task<DataSnapshot>> pendingScores = new LinkedHashMap<>();
            for (SocialPlayer candidate : participants) {
                pendingScores.put(candidate, scores.child(difficulty.getId()).child(candidate.id()).get());
            }
            List<RankingEntry> ranking = new ArrayList<>();
            for (var score : pendingScores.entrySet()) {
                Long duration = await(score.getValue()).child("durationMs").getValue(Long.class);
                if (duration != null) {
                    ranking.add(new RankingEntry(score.getKey(), duration));
                }
            }
            Collections.sort(ranking, (a, b) -> {
                int cmp = Long.compare(a.durationMs(), b.durationMs());
                return cmp != 0 ? cmp : a.player().id().compareTo(b.player().id());
            });
            return ranking;
        });
    }

    public SudokuTask<Long> competitivePoints() {
        String player = getPlayerId();
        return RepositoryIo.submit(writes, () -> {
            checkPlayer(player);
            publishCompetitiveResults(player);
            checkPlayer(player);
            Task<DataSnapshot> previous = legacyWins.child(player).get();
            Task<DataSnapshot> current = results.child(player).get();
            Map<String, RankResult> merged = new HashMap<>();
            readRank(await(previous), true, merged);
            readRank(await(current), false, merged);
            checkPlayer(player);
            List<RankResult> ordered = new ArrayList<>(merged.values());
            Collections.sort(ordered, (a, b) -> {
                int cmp = Long.compare(a.startedAt(), b.startedAt());
                return cmp != 0 ? cmp : a.key().compareTo(b.key());
            });
            long points = 0;
            for (RankResult result : ordered) {
                points = CompetitiveRank.pointsAfterMatch(points, result.won());
            }
            return points;
        });
    }

    private static void readRank(DataSnapshot snapshot, boolean legacy, Map<String, RankResult> results) {
        for (DataSnapshot row : snapshot.getChildren()) {
            String key = Objects.requireNonNull(row.getKey());
            long time = Objects.requireNonNull(row.child("startedAt").getValue(Long.class));
            boolean won = legacy || Objects.requireNonNull(row.child("won").getValue(Boolean.class));
            results.put(key, new RankResult(key, time, won));
        }
    }

    private void publishCompetitiveResults(String player) throws Exception {
        for (CompetitiveResultEntity result : RepositoryIo.await(games.pendingCompetitiveResults(player), NETWORK_TIMEOUT)) {
            if (!player.equals(getPlayerId())) {
                return;
            }
            DatabaseReference claim = results.child(player).child(result.claimKey());
            try {
                if (await(claim.get()).exists() || publishFinishedMatch(result)) {
                    RepositoryIo.await(games.acknowledgeCompetitiveResult(result), NETWORK_TIMEOUT);
                }
            } catch (DatabaseException error) {
                if (player.equals(getPlayerId()) && await(claim.get()).exists()) {
                    RepositoryIo.await(games.acknowledgeCompetitiveResult(result), NETWORK_TIMEOUT);
                } else {
                    Log.w(TAG, "Competitive result awaiting verification", error);
                }
            }
        }
    }

    private boolean publishFinishedMatch(CompetitiveResultEntity result) throws Exception {
        DataSnapshot room = await(rooms.child(result.roomCode).get());
        Long started = room.child("startTime").getValue(Long.class);
        if (!"COMPETITIVE".equals(text(room, "mode")) || !"FINISHED".equals(text(room, "status"))
                || started == null || started != result.startedAt) {
            return false;
        }
        String host = text(room, "hostId");
        String guest = text(room, "guestId");
        String winner = text(room, "winnerId");
        if (host == null || guest == null || host.trim().isEmpty() || guest.trim().isEmpty() || host.equals(guest)
                || (!host.equals(winner) && !guest.equals(winner))
                || (!host.equals(result.playerId) && !guest.equals(result.playerId))
                || !result.playerId.equals(getPlayerId())) {
            return false;
        }
        Map<String, Object> updates = new HashMap<>();
        for (String participant : new String[]{host, guest}) {
            Map<String, Object> row = new HashMap<>();
            row.put("roomCode", result.roomCode);
            row.put("startedAt", result.startedAt);
            row.put("won", participant.equals(winner));
            updates.put("competitive_results/" + participant + "/" + result.claimKey(), row);
        }
        await(root.updateChildren(updates));
        return true;
    }

    public SudokuTask<Void> syncRanking() {
        String player = getPlayerId();
        return RepositoryIo.submit(writes, () -> {
            checkPlayer(player);
            publishBests(player);
            return null;
        });
    }

    private void publishBests(String player) throws Exception {
        List<RankingBestEntity> bests = RepositoryIo.await(games.rankingBests(player), NETWORK_TIMEOUT);
        if (bests.isEmpty() || !player.equals(getPlayerId())) {
            return;
        }
        SocialPlayer profile = player(player);
        if (profile == null) {
            return;
        }
        for (RankingBestEntity best : bests) {
            if (!player.equals(getPlayerId())) {
                return;
            }
            if (best.durationMs >= 1000 && best.durationMs <= MAX_DURATION) {
                submitPersonalBest(scores.child(best.difficulty).child(player), best.durationMs, profile.country());
            }
        }
    }

    private void submitPersonalBest(DatabaseReference reference, long durationMs, String country) throws Exception {
        await(reference.get());
        SudokuTask<Void> submitted = new SudokuTask<>();
        reference.runTransaction(new Transaction.Handler() {
            @Override public Transaction.Result doTransaction(MutableData data) {
                Long best = data.child("durationMs").getValue(Long.class);
                if (best != null && best <= durationMs) {
                    return Transaction.abort();
                }
                Map<String, Object> update = new HashMap<>();
                update.put("durationMs", durationMs);
                update.put("country", country);
                data.setValue(update);
                return Transaction.success(data);
            }

            @Override public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
                if (error != null) {
                    submitted.completeExceptionally(error.toException());
                } else {
                    submitted.complete(null);
                }
            }
        }, false);
        RepositoryIo.await(submitted, NETWORK_TIMEOUT);
    }

    @Nullable private SocialPlayer player(String id) throws Exception {
        return readPlayer(id, await(profiles.child(id).get()));
    }

    @Nullable private static SocialPlayer readPlayer(String id, DataSnapshot snapshot) {
        String name = text(snapshot, "name");
        String country = text(snapshot, "country");
        if (name == null || country == null) {
            return null;
        }
        return new SocialPlayer(id, name, country);
    }

    private static String text(DataSnapshot snapshot, String key) {
        return snapshot.child(key).getValue(String.class);
    }

    private void checkPlayer(@Nullable String expected) {
        if (expected == null) {
            throw new IllegalStateException("Hãy đăng nhập Google để tham gia.");
        }
        if (!expected.equals(getPlayerId())) {
            throw new IllegalStateException("Tài khoản đã thay đổi.");
        }
    }

    private static void checkCountry(String country) {
        if (!COUNTRIES.contains(country)) {
            throw new IllegalArgumentException("Hãy chọn một quốc gia.");
        }
    }

    private static <T> T await(Task<T> work) throws Exception {
        return RepositoryIo.await(work, NETWORK_TIMEOUT);
    }
}
