package com.puzzlehub.sudoku.domain;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public final class SparkRoomTest {
    private static final String COMPETITIVE = "COMPETITIVE";
    private static final String COOPERATIVE = "COOPERATIVE";
    private final SudokuBoard solution = SudokuBoard.decode(
            "534678912672195348198342567859761423426853791713924856961537284287419635345286179");
    private final SudokuBoard initial = solution.withValue(0, 0).withValue(1, 0);
    private final Puzzle puzzle = new Puzzle(initial, solution, Difficulty.BEGINNER,
            new SudokuDifficultyEvaluator().evaluate(initial));

    @Test public void hostCannotJoinItsOwnWaitingRoomAsTheGuest() {
        SparkRoom waiting = waiting(COMPETITIVE);
        assertThrows(IllegalArgumentException.class, () -> SparkRoomEngine.join(waiting, "alice", "Alice", 2000));
        assertEquals("WAITING", waiting.status());
    }

    @Test public void roomRoundTripsThroughFirebaseMapsAndLongNumbers() {
        SparkRoom room = playing(COMPETITIVE);
        assertEquals(room, SparkRoom.decode(room.encode()));
        Map<String, Object> encoded = room.encode();
        Map<String, Long> indexed = new HashMap<>();
        for (int index = 0; index < SudokuBoard.CELL_COUNT; index++) {
            indexed.put(String.valueOf(index), (long) room.initial().get(index));
        }
        encoded.put("initial", indexed);
        assertEquals(room, SparkRoom.decode(encoded));
    }

    @Test public void onlyOneGuestCanJoinAndExistingMemberCanResume() {
        SparkRoom room = playing(COMPETITIVE);
        assertEquals(room, SparkRoomEngine.join(room, "alice", "Other name", 3000));
        assertThrows(IllegalArgumentException.class, () -> SparkRoomEngine.join(room, "third", "Third", 3000));
        assertEquals(2, room.players().size());
        assertEquals("bob", room.guestId());
    }

    @Test public void competitiveMovesKeepOpponentBoardAndRevisionUntouched() {
        SparkRoom room = SparkRoomEngine.move(playing(COMPETITIVE), "alice", 0, 5, 0, 3000);
        assertEquals(5, room.players().get("alice").board().get(0));
        assertEquals(initial, room.players().get("bob").board());
        assertEquals(0L, room.players().get("bob").revision());
        assertEquals(1L, room.players().get("alice").revision());
        assertEquals(100, room.score("alice"));
        assertEquals(0, room.score("bob"));
        assertEquals("PLAYING", room.status());
    }

    @Test public void exactCompletionWinsAndFinishedRoomCannotBeChanged() {
        SparkRoom first = SparkRoomEngine.move(playing(COMPETITIVE), "alice", 0, 5, 0, 3000);
        SparkRoom finished = SparkRoomEngine.move(first, "alice", 1, 3, 1, 4000);
        assertEquals("FINISHED", finished.status());
        assertEquals("alice", finished.winnerId());
        assertEquals(4000L, finished.finishedAt());
        assertThrows(IllegalArgumentException.class, () -> SparkRoomEngine.move(finished, "bob", 0, 5, 0, 5000));
        assertEquals(finished, SparkRoomEngine.leave(finished, "bob", 5000));
    }

    @Test public void cooperativeCompletionAwardsTeam() {
        SparkRoom first = SparkRoomEngine.move(playing(COOPERATIVE), "alice", 0, 5, 0, 3000);
        SparkRoom finished = SparkRoomEngine.move(first, "bob", 1, 3, 1, 4000);
        assertEquals("TEAM", finished.winnerId());
        assertEquals("FINISHED", finished.status());
    }

    private SparkRoom waiting(String mode) {
        return SparkRoomEngine.create("alice", "Alice", "ABC234", mode, Difficulty.BEGINNER, puzzle, 1000);
    }

    private SparkRoom playing(String mode) {
        return SparkRoomEngine.join(waiting(mode), "bob", "Bob", 2000);
    }
}
