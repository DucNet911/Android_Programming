package com.team.game2048.data;

import com.team.game2048.engine.Game2048Engine;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class SnapshotCodec {
  private SnapshotCodec() {}

  public static String encode(Game2048Engine engine) {
    try {
      JSONObject o = new JSONObject();
      JSONArray board = new JSONArray();
      for (long v : engine.board()) board.put(v);
      return o.put("schema", 1)
          .put("board", board)
          .put("score", engine.score())
          .put("won", engine.hasWon())
          .put("randomState", engine.randomState())
          .toString();
    } catch (JSONException e) {
      throw new IllegalStateException(e);
    }
  }

  public static Game2048Engine decode(String snapshot) {
    try {
      JSONObject o = new JSONObject(snapshot);
      if (integer(o.get("schema")) != 1) throw new IllegalArgumentException("Unsupported schema");
      JSONArray a = o.getJSONArray("board");
      if (a.length() != 16) throw new IllegalArgumentException("Invalid board length");
      long[] board = new long[16];
      for (int i = 0; i < 16; i++) board[i] = integer(a.get(i));
      Object won = o.get("won");
      if (!(won instanceof Boolean)) throw new IllegalArgumentException("Invalid flag");
      return new Game2048Engine(
          board, integer(o.get("score")), (Boolean) won, integer(o.get("randomState")));
    } catch (JSONException e) {
      throw new IllegalArgumentException("Corrupt snapshot", e);
    }
  }

  private static long integer(Object v) {
    if (!(v instanceof Integer) && !(v instanceof Long))
      throw new IllegalArgumentException("Invalid integer");
    return ((Number) v).longValue();
  }
}
