package com.puzzlehub.sudoku.domain;

import androidx.annotation.Nullable;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Pattern;

/** Applies room actions to immutable snapshots without I/O. */
public final class SparkRoomEngine {
    public static final long ROOM_LIFETIME = 2 * 60 * 60 * 1000L;
    public static final long RECONNECT_MS = 60_000L;
    public static final Pattern CODE_PATTERN = Pattern.compile("[A-HJ-NP-Z2-9]{6}");

    private SparkRoomEngine() {}

    public static SparkRoom create(String uid, String name, String code, String mode,
                                   Difficulty difficulty, Puzzle puzzle, long now) {
        require(!uid.trim().isEmpty() && CODE_PATTERN.matcher(code).matches(), "Mã phòng không hợp lệ");
        require(mode.equals("COMPETITIVE") || mode.equals("COOPERATIVE"), "Kiểu chơi không hợp lệ");
        require(SudokuValidator.isComplete(puzzle.solution()), "Lời giải không hợp lệ");
        for (int index = 0; index < SudokuBoard.CELL_COUNT; index++) {
            int given = puzzle.initial().get(index);
            require(given == 0 || given == puzzle.solution().get(index), "Bảng đề bài không hợp lệ");
        }
        SparkPlayer owner = new SparkPlayer(uid, playerName(name), puzzle.initial());
        return new SparkRoom(code, uid, "", mode, difficulty.name(), "WAITING", puzzle.initial(),
                puzzle.solution(), puzzle.initial(), Collections.singletonMap(uid, owner),
                now, now + ROOM_LIFETIME, 0L, 0L, "", 0L, 0L, now, uid,
                "CREATE", new SparkMove(uid, 0, 0, now, 0L));
    }

    public static SparkRoom join(SparkRoom room, String uid, String name, long now) {
        require(now < room.expiresAt(), "Phòng đã hết hạn");
        require(!uid.equals(room.hostId()) || !room.status().equals("WAITING"),
                "Đăng nhập bằng tài khoản khác để vào phòng này");
        if (room.players().containsKey(uid)) {
            return room;
        }
        require(room.status().equals("WAITING") && room.guestId().isEmpty() && room.players().size() == 1,
                "Phòng đã đầy hoặc đã bắt đầu");
        SparkRoom.Change change = room.change(uid, "JOIN", now);
        change.guestId = uid;
        change.players.put(uid, new SparkPlayer(uid, playerName(name), room.initial()));
        change.status = "PLAYING";
        change.startTime = now;
        return change.build();
    }

    public static SparkRoom move(SparkRoom room, String uid, int index, int value, long revision, long now) {
        SparkPlayer player = room.players().get(uid);
        if (player == null) {
            throw new IllegalStateException("Bạn không thuộc phòng này");
        }
        require(room.status().equals("PLAYING") && room.players().size() == 2 && now < room.expiresAt(),
                "Trận đấu chưa sẵn sàng hoặc đã kết thúc");
        require(index >= 0 && index < SudokuBoard.CELL_COUNT && value >= 0 && value <= 9
                && room.initial().get(index) == 0, "Không được sửa ô đề bài");

        boolean cooperative = room.mode().equals("COOPERATIVE");
        long currentRevision = player.revision();
        if (cooperative) {
            currentRevision = room.revision();
        }
        require(revision == currentRevision, "Bảng đã thay đổi. Hãy thử lại trên bảng mới");
        SudokuBoard currentBoard = room.boardFor(uid);
        if (currentBoard.get(index) == value) {
            return room;
        }

        SudokuBoard nextBoard = currentBoard.withValue(index, value);
        int mistakes = player.mistakes();
        if (value != 0 && value != room.solution().get(index)) {
            mistakes++;
        }
        SparkRoom.Change change = room.change(uid, "MOVE", now);
        SudokuBoard boardForPlayer = nextBoard;
        if (cooperative) {
            change.board = nextBoard;
            change.revision++;
            boardForPlayer = player.board();
        }
        change.players.put(uid, new SparkPlayer(player.uid(), player.name(), boardForPlayer, player.revision() + 1L, mistakes));
        change.lastMove = new SparkMove(uid, index, value, now, revision);

        boolean lost = mistakes >= 3;
        boolean won = !lost && nextBoard.equals(room.solution());
        change.winnerId = "";
        change.finishedAt = 0L;
        if (lost || won) {
            change.status = "FINISHED";
            change.finishedAt = now;
            if (won) {
                if (cooperative) {
                    change.winnerId = "TEAM";
                } else {
                    change.winnerId = uid;
                }
            } else if (!cooperative) {
                String opponent = "";
                for (String id : room.players().keySet()) {
                    if (!id.equals(uid)) {
                        opponent = id;
                        break;
                    }
                }
                if (opponent.isEmpty()) {
                    throw new IllegalStateException("Không tìm thấy đối thủ");
                }
                change.winnerId = opponent;
            }
        }
        return change.build();
    }

    public static SparkRoom leave(SparkRoom room, String uid, long now) {
        require(room.players().containsKey(uid), "Bạn không thuộc phòng này");
        if (room.getTerminal()) {
            return room;
        }
        return cancel(room, uid, "LEAVE", now);
    }

    @Nullable
    public static Long disconnectedSince(SparkRoom room, Map<String, RoomPresence> presence) {
        if (!room.status().equals("PLAYING")) {
            return null;
        }
        Long earliest = null;
        for (String participant : room.players().keySet()) {
            RoomPresence member = presence.get(participant);
            if (member != null && member.online()) {
                continue;
            }
            long since = room.startTime();
            if (member != null) {
                since = Math.max(since, member.changedAt());
            }
            if (earliest == null || since < earliest) {
                earliest = since;
            }
        }
        return earliest;
    }

    public static SparkRoom expire(SparkRoom room, String uid, Map<String, RoomPresence> presence, long now) {
        require(room.players().containsKey(uid), "Bạn không thuộc phòng này");
        if (room.getTerminal()) {
            return room;
        }
        Long offlineSince = disconnectedSince(room, presence);
        boolean disconnectedTooLong = offlineSince != null && now - offlineSince >= RECONNECT_MS;
        require(now >= room.expiresAt() || disconnectedTooLong, "Chưa hết thời gian chờ kết nối");
        return cancel(room, uid, "EXPIRE", now);
    }

    private static SparkRoom cancel(SparkRoom room, String uid, String action, long now) {
        SparkRoom.Change change = room.change(uid, action, now);
        change.status = "CANCELLED";
        change.finishedAt = now;
        return change.build();
    }

    private static String playerName(String value) {
        String name = value.trim();
        if (name.trim().isEmpty()) {
            return "Người chơi";
        }
        return name.substring(0, Math.min(24, name.length()));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
