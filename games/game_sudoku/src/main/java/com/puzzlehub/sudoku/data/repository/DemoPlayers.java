package com.puzzlehub.sudoku.data.repository;

import android.content.Context;
import android.content.SharedPreferences;
import com.puzzlehub.sudoku.data.local.GameHistoryEntity;
import com.puzzlehub.sudoku.domain.CompetitiveRank;
import com.puzzlehub.sudoku.domain.Difficulty;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class DemoPlayers {
    private static final String PREVIEW_PASSWORD = "SudokuDemo#10";
    private static final String HISTORY_PREFIX = "local-account:";
    private static final long HISTORY_REFERENCE = 1795107600000L;
    private static final List<SocialPlayer> PLAYERS = buildPlayers();

    private static List<SocialPlayer> buildPlayers() {
        List<SocialPlayer> players = new ArrayList<>(Arrays.asList(
            new SocialPlayer("demo_linh", "Linh Sudoku", "VN"),
            new SocialPlayer("demo_minh", "Minh Sudoku", "VN"),
            new SocialPlayer("demo_an", "An Nhiên", "VN"),
            new SocialPlayer("demo_hana", "Hana Sato", "JP"),
            new SocialPlayer("demo_aki", "Aki Mori", "JP"),
            new SocialPlayer("demo_yuna", "Yuna Kim", "KR"),
            new SocialPlayer("demo_jin", "Jin Park", "KR"),
            new SocialPlayer("demo_ava", "Ava Nguyen", "US"),
            new SocialPlayer("demo_lee", "Tyler Lee", "US"),
            new SocialPlayer("demo_mei", "Mei Tan", "SG"),
            new SocialPlayer("demo_isaac", "Isaac Lim", "SG")));
        addCountry(players, "VN", "Bảo Ngọc", "Hoàng Nam", "Mai Phương", "Gia Hân", "Hải Đăng", "Thu Vy",
                "Quang Vinh", "Minh Anh", "Khánh Linh", "Ngọc Hà", "Tuấn Khang", "Thanh Trúc", "Đức Long");
        addCountry(players, "JP", "Ren Ito", "Yui Tanaka", "Riku Watanabe", "Mio Suzuki", "Sora Abe", "Kaito Ono",
                "Nana Kato", "Yuto Saito", "Rei Nakamura", "Aoi Takahashi", "Nagi Yamada", "Rin Ueda");
        addCountry(players, "KR", "Seo Jun", "Ji Won", "Ha Rin", "Do Yun", "Min Seo", "Ye Jin", "Su Bin",
                "Jae Won", "Yu Ri", "Hye Jin", "Eun Ji", "Joon Ho");
        addCountry(players, "US", "Chloe Park", "Mason Reed", "Jade Miller", "Noah Hayes", "Emma Brooks",
                "Sofia Cruz", "Liam Chen", "Isla Moore", "Aiden Green", "Ivy Palmer", "Zoe Hall", "Ethan Kim");
        addCountry(players, "SG", "Wei Lim", "Zara Koh", "Ray Ong", "Siti Aminah", "Anil Raj", "June Teo",
                "Maya Goh", "Ben Ng", "Dina Rahman", "Chloe Yeo", "Aaron Soh", "Mira Lee");
        return Collections.unmodifiableList(players);
    }

    private static void addCountry(List<SocialPlayer> players, String country, String... names) {
        for (int index = 0; index < names.length; index++) {
            String id = "demo_" + country.toLowerCase(java.util.Locale.ROOT) + "_" + (index + 1);
            players.add(new SocialPlayer(id, names[index], country));
        }
    }
    private final SharedPreferences preferences;

    public DemoPlayers(Context context) {
        preferences = context.getSharedPreferences("arcade_demo_players", Context.MODE_PRIVATE);
        String account = preferences.getString("current", "");
        if (canSignIn(account)) {
            seedFriends(account);
        }
    }

    public SocialPlayer current() {
        return find(preferences.getString("current", ""));
    }

    public static SocialPlayer find(String username) {
        for (SocialPlayer player : PLAYERS) {
            if (player.getId().equals(username)) {
                return player;
            }
        }
        return null;
    }

    public boolean signIn(String username, String password) {
        if (!canSignIn(username) || !PREVIEW_PASSWORD.equals(password)) {
            return false;
        }
        seedFriends(username);
        preferences.edit().putString("current", username).apply();
        return true;
    }

    public void signOut() {
        preferences.edit().remove("current").apply();
    }

    public static boolean isHistoryId(String id) {
        return id.startsWith(HISTORY_PREFIX);
    }

    public static String historyId(String account, String record) {
        return HISTORY_PREFIX + account + ":" + record;
    }

    public static boolean ownsHistory(String account, String id) {
        return id.startsWith(historyId(account, ""));
    }

    public static boolean canSignIn(String username) {
        return "demo_linh".equals(username) || "demo_minh".equals(username);
    }

    public static List<GameHistoryEntity> recordedHistory(String account) {
        if (!canSignIn(account)) {
            return Collections.emptyList();
        }
        List<GameHistoryEntity> records = new ArrayList<>();
        for (Difficulty difficulty : Difficulty.values()) {
            long best = sampleResult(find(account), difficulty).getDurationMs() / 1000L;
            records.add(record(account, records.size(), difficulty, "SOLO", best + 84L, true, 2));
            records.add(record(account, records.size(), difficulty, "SOLO", best + 31L, true, 1));
            records.add(record(account, records.size(), difficulty, "SOLO", best + 180L, false, 3));
            records.add(record(account, records.size(), difficulty, "SOLO", best, true, 0));
        }
        int matches = 84;
        if ("demo_minh".equals(account)) {
            matches = 79;
        }
        Difficulty[] levels = Difficulty.values();
        for (int index = 0; index < matches; index++) {
            Difficulty difficulty = levels[index % levels.length];
            boolean won = (index + 1) % 10 != 0;
            long seconds = 104L + difficulty.ordinal() * 82L + (index * 23L % 95L);
            records.add(record(account, records.size(), difficulty, "COMPETITIVE", seconds, won, index % 3));
        }
        return records;
    }

    public long competitivePoints() {
        SocialPlayer player = current();
        if (player == null) {
            return 0L;
        }
        long points = 0L;
        for (GameHistoryEntity row : recordedHistory(player.getId())) {
            if ("COMPETITIVE".equals(row.mode)) {
                points = CompetitiveRank.pointsAfterMatch(points, "WON".equals(row.result));
            }
        }
        return points;
    }

    private static GameHistoryEntity record(String account, int index, Difficulty difficulty, String mode,
                                            long seconds, boolean won, int mistakes) {
        GameHistoryEntity game = new GameHistoryEntity();
        game.id = historyId(account, "initial-" + index);
        game.date = HISTORY_REFERENCE - TimeUnit.HOURS.toMillis(8L * (102 - index));
        game.difficulty = difficulty.name();
        game.mode = mode;
        game.duration = seconds;
        game.mistakes = mistakes;
        game.result = "LOST";
        if (won) {
            game.result = "WON";
            game.score = Math.max(100, 1000 - (int) seconds - mistakes * 50);
        }
        return game;
    }

    private void seedFriends(String account) {
        String key = "friends-v2-" + account;
        String expandedKey = "friends-v3-" + account;
        if (preferences.getBoolean(expandedKey, false)) {
            return;
        }
        SharedPreferences.Editor changes = preferences.edit();
        if (!preferences.getBoolean(key, false)) {
            if ("demo_linh".equals(account)) {
                seedRelation(changes, "demo_an", account, SocialRepository.ACCEPTED);
                seedRelation(changes, account, "demo_hana", SocialRepository.ACCEPTED);
                seedRelation(changes, "demo_yuna", account, SocialRepository.PENDING);
            } else {
                seedRelation(changes, account, "demo_ava", SocialRepository.ACCEPTED);
                seedRelation(changes, "demo_mei", account, SocialRepository.ACCEPTED);
                seedRelation(changes, "demo_jin", account, SocialRepository.PENDING);
            }
            changes.putBoolean(key, true);
        }
        String[] contacts;
        String incoming;
        String outgoing;
        if ("demo_linh".equals(account)) {
            contacts = new String[]{"demo_vn_1", "demo_vn_3", "demo_vn_6", "demo_aki", "demo_jp_4",
                    "demo_kr_2", "demo_us_3", "demo_ava", "demo_mei", "demo_sg_4"};
            incoming = "demo_vn_8";
            outgoing = "demo_us_5";
        } else {
            contacts = new String[]{"demo_vn_2", "demo_vn_5", "demo_hana", "demo_jp_7", "demo_yuna",
                    "demo_kr_5", "demo_lee", "demo_us_4", "demo_isaac", "demo_sg_2"};
            incoming = "demo_sg_6";
            outgoing = "demo_jp_8";
        }
        for (String contact : contacts) {
            seedRelation(changes, account, contact, SocialRepository.ACCEPTED);
        }
        seedRelation(changes, incoming, account, SocialRepository.PENDING);
        seedRelation(changes, account, outgoing, SocialRepository.PENDING);
        changes.putBoolean(expandedKey, true).apply();
    }

    private void seedRelation(SharedPreferences.Editor changes, String from, String to, String status) {
        String key = from + "~" + to;
        if (!preferences.contains(key) && !preferences.contains(to + "~" + from)) {
            changes.putString(key, status);
        }
    }

    public static List<RankingEntry> countryRanking(String country, Difficulty difficulty) {
        List<RankingEntry> entries = new ArrayList<>();
        long best = 49_000L + difficulty.ordinal() * 31_000L;
        for (SocialPlayer player : PLAYERS) {
            if (player.getCountry().equals(country)) {
                int place = entries.size();
                entries.add(new RankingEntry(player, best + place * 8_000L + (place * place % 19) * 1_000L));
            }
        }
        Collections.sort(entries, (a, b) -> Long.compare(a.getDurationMs(), b.getDurationMs()));
        return entries;
    }

    public List<RankingEntry> friendRanking(Difficulty difficulty) {
        List<RankingEntry> result = new ArrayList<>();
        SocialPlayer me = current();
        if (me == null) {
            return result;
        }
        result.add(sampleResult(me, difficulty));
        for (Friendship relation : friends()) {
            if (SocialRepository.ACCEPTED.equals(relation.getStatus())) {
                result.add(sampleResult(relation.getPlayer(), difficulty));
            }
        }
        Collections.sort(result, (a, b) -> Long.compare(a.getDurationMs(), b.getDurationMs()));
        return result;
    }

    private static RankingEntry sampleResult(SocialPlayer player, Difficulty difficulty) {
        for (RankingEntry entry : countryRanking(player.getCountry(), difficulty)) {
            if (entry.getPlayer().getId().equals(player.getId())) {
                return entry;
            }
        }
        throw new IllegalArgumentException("Unknown sample player");
    }

    public List<Friendship> friends() {
        SocialPlayer me = current();
        List<Friendship> result = new ArrayList<>();
        if (me == null) {
            return result;
        }
        for (SocialPlayer player : PLAYERS) {
            if (me.getId().equals(player.getId())) {
                continue;
            }
            String incoming = player.getId() + "~" + me.getId();
            String outgoing = me.getId() + "~" + player.getId();
            String status = preferences.getString(incoming, null);
            if (status != null) {
                result.add(new Friendship(incoming, player, status, true));
            } else {
                status = preferences.getString(outgoing, null);
                if (status != null) {
                    result.add(new Friendship(outgoing, player, status, false));
                }
            }
        }
        return result;
    }

    public boolean invite(String username) {
        SocialPlayer me = current();
        SocialPlayer friend = find(username);
        if (me == null || friend == null || me.getId().equals(username)) {
            return false;
        }
        String outgoing = me.getId() + "~" + username;
        String incoming = username + "~" + me.getId();
        if (preferences.contains(outgoing) || preferences.contains(incoming)) {
            return false;
        }
        preferences.edit().putString(outgoing, "pending").apply();
        return true;
    }

    public void updateFriend(String key, boolean accept) {
        for (Friendship relation : friends()) {
            if (!relation.getKey().equals(key)) {
                continue;
            }
            if (accept && relation.getIncoming() && relation.getStatus().equals("pending")) {
                preferences.edit().putString(key, "accepted").apply();
            } else if (!accept) {
                preferences.edit().remove(key).apply();
            }
            return;
        }
    }
}
