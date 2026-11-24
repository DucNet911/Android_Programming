package com.puzzlehub.sudoku.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Firebase room schema 2. Protocol keys and board representation are stable across app releases. */
public record SparkRoom(String code, String hostId, String guestId, String mode, String difficulty,
                        String status, SudokuBoard initial, SudokuBoard solution, SudokuBoard board,
                        Map<String, SparkPlayer> players, long createdAt, long expiresAt, long startTime,
                        long finishedAt, String winnerId, long revision, long version, long updatedAt,
                        String actorId, String action, SparkMove lastMove) {
    public SparkRoom {
        players = Collections.unmodifiableMap(new LinkedHashMap<>(players));
    }

    public String getCode() { return code; }
    public String getHostId() { return hostId; }
    public String getGuestId() { return guestId; }
    public String getMode() { return mode; }
    public String getDifficulty() { return difficulty; }
    public String getStatus() { return status; }
    public SudokuBoard getInitial() { return initial; }
    public SudokuBoard getSolution() { return solution; }
    public SudokuBoard getBoard() { return board; }
    public Map<String, SparkPlayer> getPlayers() { return players; }
    public long getCreatedAt() { return createdAt; }
    public long getExpiresAt() { return expiresAt; }
    public long getStartTime() { return startTime; }
    public long getFinishedAt() { return finishedAt; }
    public String getWinnerId() { return winnerId; }
    public long getRevision() { return revision; }
    public long getVersion() { return version; }
    public long getUpdatedAt() { return updatedAt; }
    public String getActorId() { return actorId; }
    public String getAction() { return action; }
    public SparkMove getLastMove() { return lastMove; }

    public boolean getTerminal() {
        return "FINISHED".equals(status) || "CANCELLED".equals(status);
    }

    public SudokuBoard boardFor(String uid) {
        if ("COOPERATIVE".equals(mode)) {
            return board;
        }
        return Objects.requireNonNull(players.get(uid)).board();
    }

    public int correct(String uid) {
        SudokuBoard playerBoard = boardFor(uid);
        int correct = 0;
        for (int index = 0; index < SudokuBoard.CELL_COUNT; index++) {
            if (initial.get(index) == 0 && playerBoard.get(index) == solution.get(index)) {
                correct++;
            }
        }
        return correct;
    }

    public int score(String uid) {
        int penalty = Objects.requireNonNull(players.get(uid)).mistakes() * 25;
        return Math.max(0, correct(uid) * 100 - penalty);
    }

    public Map<String, Object> encode() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schema", 2);
        root.put("code", code);
        root.put("hostId", hostId);
        root.put("guestId", guestId);
        root.put("mode", mode);
        root.put("difficulty", difficulty);
        root.put("status", status);
        root.put("initial", initial.getCells());
        root.put("solution", solution.getCells());
        root.put("board", board.getCells());
        root.put("createdAt", createdAt);
        root.put("expiresAt", expiresAt);
        root.put("startTime", startTime);
        root.put("finishedAt", finishedAt);
        root.put("winnerId", winnerId);
        root.put("revision", revision);
        root.put("version", version);
        root.put("updatedAt", updatedAt);
        root.put("actorId", actorId);
        root.put("action", action);

        Map<String, Object> participants = new LinkedHashMap<>();
        for (Map.Entry<String, SparkPlayer> item : players.entrySet()) {
            SparkPlayer player = item.getValue();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("uid", player.uid());
            entry.put("name", player.name());
            entry.put("board", player.board().getCells());
            entry.put("revision", player.revision());
            entry.put("mistakes", player.mistakes());
            participants.put(item.getKey(), entry);
        }
        root.put("players", participants);

        Map<String, Object> move = new LinkedHashMap<>();
        move.put("playerId", lastMove.playerId());
        move.put("index", lastMove.index());
        move.put("value", lastMove.value());
        move.put("timestamp", lastMove.timestamp());
        move.put("baseRevision", lastMove.baseRevision());
        root.put("lastMove", move);
        return root;
    }

    public static SparkRoom decode(Object value) {
        if (!(value instanceof Map<?, ?> root)) {
            throw new IllegalStateException("Phòng không còn tồn tại");
        }
        if (number(root, "schema") != 2L) {
            throw new IllegalArgumentException("Phiên bản phòng không còn được hỗ trợ");
        }
        if (!(root.get("players") instanceof Map<?, ?> people)) {
            throw new IllegalStateException("Thiếu người chơi");
        }
        Map<String, SparkPlayer> players = new LinkedHashMap<>();
        for (Map.Entry<?, ?> item : people.entrySet()) {
            if (!(item.getValue() instanceof Map<?, ?> player)) {
                throw new IllegalStateException("Người chơi không hợp lệ");
            }
            players.put(String.valueOf(item.getKey()), new SparkPlayer(text(player, "uid"), text(player, "name"),
                    readBoard(player, "board"), number(player, "revision"), (int) number(player, "mistakes")));
        }
        if (!(root.get("lastMove") instanceof Map<?, ?> move)) {
            throw new IllegalStateException("Thiếu nước đi");
        }
        return new SparkRoom(text(root, "code"), text(root, "hostId"), text(root, "guestId"),
                text(root, "mode"), text(root, "difficulty"), text(root, "status"),
                readBoard(root, "initial"), readBoard(root, "solution"), readBoard(root, "board"), players,
                number(root, "createdAt"), number(root, "expiresAt"), number(root, "startTime"),
                number(root, "finishedAt"), text(root, "winnerId"), number(root, "revision"),
                number(root, "version"), number(root, "updatedAt"), text(root, "actorId"), text(root, "action"),
                new SparkMove(text(move, "playerId"), (int) number(move, "index"), (int) number(move, "value"),
                        number(move, "timestamp"), number(move, "baseRevision")));
    }

    private static String text(Map<?, ?> value, String key) {
        if (value.get(key) instanceof String text) {
            return text;
        }
        throw new IllegalStateException("Dữ liệu phòng không hợp lệ");
    }

    private static long number(Map<?, ?> value, String key) {
        if (value.get(key) instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalStateException("Dữ liệu phòng không hợp lệ");
    }

    private static SudokuBoard readBoard(Map<?, ?> value, String key) {
        // The Firebase SDK may deserialize numeric children as an indexed map or a list.
        List<Integer> cells = new ArrayList<>(SudokuBoard.CELL_COUNT);
        Object raw = value.get(key);
        if (raw instanceof List<?> sequence) {
            for (Object cell : sequence) {
                cells.add(boardCell(cell));
            }
        } else if (raw instanceof Map<?, ?> indexed) {
            for (int index = 0; index < SudokuBoard.CELL_COUNT; index++) {
                cells.add(boardCell(indexed.get(String.valueOf(index))));
            }
        } else {
            throw new IllegalStateException("Bảng không hợp lệ");
        }
        return new SudokuBoard(cells);
    }

    private static int boardCell(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw new IllegalStateException("Bảng không hợp lệ");
    }

    Change change(String actor, String action, long now) {
        return new Change(this, actor, action, now);
    }

    static final class Change {
        private final SparkRoom original;
        private final String actor;
        private final String action;
        private final long now;
        final Map<String, SparkPlayer> players;
        String guestId;
        String status;
        SudokuBoard board;
        long startTime;
        long finishedAt;
        String winnerId;
        long revision;
        SparkMove lastMove;

        Change(SparkRoom room, String actor, String action, long now) {
            original = room;
            this.actor = actor;
            this.action = action;
            this.now = now;
            players = new LinkedHashMap<>(room.players);
            guestId = room.guestId;
            status = room.status;
            board = room.board;
            startTime = room.startTime;
            finishedAt = room.finishedAt;
            winnerId = room.winnerId;
            revision = room.revision;
            lastMove = room.lastMove;
        }

        SparkRoom build() {
            return new SparkRoom(original.code, original.hostId, guestId, original.mode, original.difficulty,
                    status, original.initial, original.solution, board, players, original.createdAt,
                    original.expiresAt, startTime, finishedAt, winnerId, revision, original.version + 1L,
                    now, actor, action, lastMove);
        }
    }
}
