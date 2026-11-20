package com.team.tetris.data;

import com.team.tetris.engine.TetrisLogic;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class TetrisSnapshot {
    private static final int SCHEMA = 1;
    private TetrisSnapshot() {}

    public static String encode(TetrisLogic logic) {
        TetrisLogic.State state = logic.exportState();
        try {
            return new JSONObject().put("schema", SCHEMA).put("board", matrix(state.board))
                    .put("current", matrix(state.currentPiece)).put("next", matrix(state.nextPiece))
                    .put("x", state.currentX).put("y", state.currentY).put("score", state.score)
                    .put("gameOver", state.gameOver).put("bag", new JSONArray(state.bag))
                    .put("bagIndex", state.bagIndex).put("randomState", state.randomState).toString();
        } catch (JSONException ex) { throw new IllegalStateException("Cannot encode Tetris", ex); }
    }

    public static TetrisLogic decode(String snapshot) {
        try {
            JSONObject json = new JSONObject(snapshot);
            if (json.getInt("schema") != SCHEMA) throw new IllegalArgumentException("Unsupported Tetris schema");
            JSONArray bag = json.getJSONArray("bag");
            if (bag.length() != 7) throw new IllegalArgumentException("Invalid bag size");
            int[] pieces = new int[7];
            for (int i = 0; i < pieces.length; i++) pieces[i] = bag.getInt(i);
            return TetrisLogic.fromState(new TetrisLogic.State(readMatrix(json.getJSONArray("board"), 20, 10),
                    readPiece(json.getJSONArray("current")), readPiece(json.getJSONArray("next")),
                    json.getInt("x"), json.getInt("y"), json.getInt("score"), json.getBoolean("gameOver"),
                    pieces, json.getInt("bagIndex"), json.getLong("randomState")));
        } catch (JSONException | NullPointerException ex) { throw new IllegalArgumentException("Corrupt Tetris snapshot", ex); }
    }

    private static JSONArray matrix(int[][] rows) throws JSONException {
        JSONArray result = new JSONArray();
        for (int[] row : rows) result.put(new JSONArray(row));
        return result;
    }

    private static int[][] readPiece(JSONArray array) throws JSONException {
        int size = array.length();
        if (size < 2 || size > 4) throw new IllegalArgumentException("Invalid tetromino size");
        return readMatrix(array, size, size);
    }

    private static int[][] readMatrix(JSONArray array, int rows, int cols) throws JSONException {
        if (array.length() != rows) throw new IllegalArgumentException("Invalid row count");
        int[][] result = new int[rows][cols];
        for (int r = 0; r < rows; r++) {
            JSONArray row = array.getJSONArray(r);
            if (row.length() != cols) throw new IllegalArgumentException("Invalid column count");
            for (int c = 0; c < cols; c++) result[r][c] = row.getInt(c);
        }
        return result;
    }
}
