package com.puzzlehub.sudoku.data.local;

import com.puzzlehub.sudoku.domain.Difficulty;
import com.puzzlehub.sudoku.domain.GameSession;
import com.puzzlehub.sudoku.domain.GameStatus;
import com.puzzlehub.sudoku.domain.Puzzle;
import com.puzzlehub.sudoku.domain.SudokuBoard;
import com.puzzlehub.sudoku.domain.SudokuDifficultyEvaluator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.json.JSONException;
import org.json.JSONObject;

public final class SessionCodec {
    private static final int SCHEMA = 1;

    private SessionCodec() {}

    public static String encode(GameSession session) {
        try {
            JSONObject data = new JSONObject();
            data.put("schema", SCHEMA);
            data.put("id", session.id());
            data.put("initial", session.puzzle().initial().encode());
            data.put("solution", session.puzzle().solution().encode());
            data.put("difficulty", session.puzzle().difficulty().name());
            data.put("board", session.board().encode());
            data.put("elapsed", session.elapsedSeconds());
            data.put("mistakes", session.mistakes());
            data.put("hints", session.hintCount());
            data.put("status", session.status().name());
            data.put("created", session.createdAt());
            data.put("rankingPlayer", session.rankingPlayerId());

            StringBuilder hinted = new StringBuilder();
            for (int index : session.hinted()) {
                if (hinted.length() > 0) {
                    hinted.append(',');
                }
                hinted.append(index);
            }
            data.put("hinted", hinted.toString());

            JSONObject notes = new JSONObject();
            for (Map.Entry<Integer, Set<Integer>> cell : session.notes().entrySet()) {
                StringBuilder values = new StringBuilder();
                for (int number : new TreeSet<>(cell.getValue())) {
                    values.append(number);
                }
                notes.put(String.valueOf(cell.getKey()), values.toString());
            }
            data.put("notes", notes);
            return data.toString();
        } catch (JSONException error) {
            throw new IllegalStateException("Could not encode Sudoku session", error);
        }
    }

    public static GameSession decode(String snapshot) {
        try {
            JSONObject data = new JSONObject(snapshot);
            if (data.has("schema") && data.getInt("schema") != SCHEMA) {
                throw new IllegalArgumentException("Unsupported Sudoku snapshot version");
            }
            SudokuBoard initial = SudokuBoard.decode(data.getString("initial"));
            SudokuBoard solution = SudokuBoard.decode(data.getString("solution"));
            Puzzle puzzle = new Puzzle(initial, solution, Difficulty.valueOf(data.getString("difficulty")),
                    new SudokuDifficultyEvaluator().evaluate(initial));
            return new GameSession(data.getString("id"), puzzle, SudokuBoard.decode(data.getString("board")),
                    readNotes(data.getJSONObject("notes")), readHinted(data.optString("hinted")),
                    data.getLong("elapsed"), data.getInt("mistakes"), data.getInt("hints"),
                    GameStatus.valueOf(data.getString("status")), data.getLong("created"),
                    data.optString("rankingPlayer"));
        } catch (JSONException error) {
            throw new IllegalArgumentException("Invalid Sudoku snapshot", error);
        }
    }

    private static Map<Integer, Set<Integer>> readNotes(JSONObject data) throws JSONException {
        Map<Integer, Set<Integer>> notes = new LinkedHashMap<>();
        Iterator<String> keys = data.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            String values = data.getString(key);
            Set<Integer> digits = new LinkedHashSet<>();
            for (int index = 0; index < values.length(); index++) {
                char digit = values.charAt(index);
                if (digit < '1' || digit > '9') {
                    throw new IllegalArgumentException("Invalid pencil mark");
                }
                digits.add(digit - '0');
            }
            notes.put(cellIndex(key), digits);
        }
        return notes;
    }

    private static Set<Integer> readHinted(String text) {
        Set<Integer> hinted = new LinkedHashSet<>();
        if (!text.isEmpty()) {
            for (String value : text.split(",")) {
                hinted.add(cellIndex(value));
            }
        }
        return hinted;
    }

    private static int cellIndex(String value) {
        int index = Integer.parseInt(value);
        if (index < 0 || index >= SudokuBoard.CELL_COUNT) {
            throw new IllegalArgumentException("Invalid Sudoku cell index");
        }
        return index;
    }
}
