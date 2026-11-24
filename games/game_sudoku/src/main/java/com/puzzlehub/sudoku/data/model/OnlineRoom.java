package com.puzzlehub.sudoku.data.model;

import com.puzzlehub.sudoku.domain.SparkMove;
import com.puzzlehub.sudoku.domain.SparkPlayer;
import com.puzzlehub.sudoku.domain.SparkRoom;
import com.puzzlehub.sudoku.domain.SudokuBoard;
import java.util.ArrayList;
import java.util.List;

public record OnlineRoom(String code, String mode, String difficulty, String status,
                         SudokuBoard initial, SudokuBoard board, long revision,
                         List<OnlinePlayer> players, long startTime, String winnerId,
                         long finishedAt, long disconnectedAt, OnlineMove lastMove,
                         String hostId, String guestId) {
    public OnlineRoom {
        players = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(players));
    }

    public static OnlineRoom from(SparkRoom room, String status, long disconnectedAt) {
        List<OnlinePlayer> players = new ArrayList<>();
        for (SparkPlayer player : room.players().values()) {
            players.add(new OnlinePlayer(player.uid(), player.name(), player.board(),
                    player.revision(), room.correct(player.uid()), player.mistakes(),
                    room.score(player.uid())));
        }
        OnlineMove lastMove = null;
        if ("MOVE".equals(room.action()) && room.lastMove() != null) {
            SparkMove move = room.lastMove();
            lastMove = new OnlineMove(move.playerId(), move.index(), move.value(), move.timestamp());
        }
        return new OnlineRoom(room.code(), room.mode(), room.difficulty(), status,
                room.initial(), room.board(), room.revision(), players, room.startTime(),
                room.winnerId(), room.finishedAt(), disconnectedAt, lastMove,
                room.hostId(), room.guestId());
    }

    public OnlineRoom withStatus(String status) {
        return withPlayers(status, players);
    }

    public OnlineRoom withPlayers(String status, List<OnlinePlayer> players) {
        return new OnlineRoom(code, mode, difficulty, status, initial, board, revision,
                players, startTime, winnerId, finishedAt, disconnectedAt, lastMove, hostId, guestId);
    }

    public String getCode() { return code; }
    public String getMode() { return mode; }
    public String getDifficulty() { return difficulty; }
    public String getStatus() { return status; }
    public SudokuBoard getInitial() { return initial; }
    public SudokuBoard getBoard() { return board; }
    public long getRevision() { return revision; }
    public List<OnlinePlayer> getPlayers() { return players; }
    public long getStartTime() { return startTime; }
    public String getWinnerId() { return winnerId; }
    public long getFinishedAt() { return finishedAt; }
    public long getDisconnectedAt() { return disconnectedAt; }
    public OnlineMove getLastMove() { return lastMove; }
    public String getHostId() { return hostId; }
    public String getGuestId() { return guestId; }
}
