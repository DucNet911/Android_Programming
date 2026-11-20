package com.team.tetris;

import com.team.tetris.data.TetrisSnapshot;
import com.team.tetris.engine.TetrisLogic;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk = 28)
public class TetrisSnapshotTest {
    @Test public void roundTripPreservesBoardPiecesBagAndFutureRandomness() {
        TetrisLogic original = new TetrisLogic();
        original.moveLeft(); original.moveDown(); original.Rotate90Clockwise();
        TetrisLogic restored = TetrisSnapshot.decode(TetrisSnapshot.encode(original));
        for (int turn = 0; turn < 40; turn++) {
            assertEquals(TetrisSnapshot.encode(original), TetrisSnapshot.encode(restored));
            for (int i = 0; i < 12; i++) { original.moveLeft(); restored.moveLeft(); }
            for (int i = 0; i < (turn * 3) % 8; i++) { original.moveRight(); restored.moveRight(); }
            original.Rotate90Clockwise(); restored.Rotate90Clockwise();
            original.hardDrop(); restored.hardDrop();
        }
        assertEquals(TetrisSnapshot.encode(original), TetrisSnapshot.encode(restored));
    }

    @Test public void exportingStateDoesNotExposeLiveBoard() {
        TetrisLogic logic = new TetrisLogic();
        TetrisLogic.State state = logic.exportState();
        state.board[19][0] = 7;
        assertEquals(0, logic.getBoard()[19][0]);
    }

    @Test(expected = IllegalArgumentException.class) public void unsupportedSchemaIsRejected() throws Exception {
        JSONObject json = new JSONObject(TetrisSnapshot.encode(new TetrisLogic()));
        TetrisSnapshot.decode(json.put("schema", 2).toString());
    }

    @Test(expected = IllegalArgumentException.class) public void invalidBoardIsRejected() throws Exception {
        JSONObject json = new JSONObject(TetrisSnapshot.encode(new TetrisLogic()));
        json.getJSONArray("board").getJSONArray(0).put(0, 8);
        TetrisSnapshot.decode(json.toString());
    }

    @Test(expected = IllegalArgumentException.class) public void duplicateBagIsRejected() throws Exception {
        JSONObject json = new JSONObject(TetrisSnapshot.encode(new TetrisLogic()));
        json.getJSONArray("bag").put(1, json.getJSONArray("bag").getInt(0));
        TetrisSnapshot.decode(json.toString());
    }

    @Test(expected = IllegalArgumentException.class) public void invalidPieceIsRejected() throws Exception {
        JSONObject json = new JSONObject(TetrisSnapshot.encode(new TetrisLogic()));
        json.getJSONArray("next").getJSONArray(0).put(0, 7);
        // Make the entire first row invalid, regardless of the randomly chosen piece.
        json.getJSONArray("next").getJSONArray(0).put(1, 6);
        TetrisSnapshot.decode(json.toString());
    }
}
