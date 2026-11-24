package com.puzzlehub.sudoku.ui.multiplayer;

import com.puzzlehub.sudoku.data.model.OnlineRoom;
import java.util.Map;
import java.util.Set;

public record MultiplayerState(OnlineRoom room, boolean busy, boolean connected, String error,
                               int selected, boolean pencil, Map<Integer, Set<Integer>> notes) {
    public MultiplayerState() {
        this(null, false, false, null, -1, false, java.util.Collections.emptyMap());
    }

    public OnlineRoom getRoom() { return room; }
    public boolean getBusy() { return busy; }
    public boolean getConnected() { return connected; }
    public String getError() { return error; }
    public int getSelected() { return selected; }
    public boolean getPencil() { return pencil; }
    public Map<Integer, Set<Integer>> getNotes() { return notes; }

    public MultiplayerState withRoom(OnlineRoom value, boolean busy) {
        return new MultiplayerState(value, busy, connected, error, selected, pencil, notes);
    }

    MultiplayerState withStatus(boolean busy, boolean connected, String error) {
        return new MultiplayerState(room, busy, connected, error, selected, pencil, notes);
    }

    MultiplayerState withInput(int selected, boolean pencil, Map<Integer, Set<Integer>> notes) {
        return new MultiplayerState(room, busy, connected, error, selected, pencil,
                java.util.Collections.unmodifiableMap(new java.util.HashMap<>(notes)));
    }
}
