package com.puzzlehub.sudoku.ui.multiplayer;

import androidx.lifecycle.ViewModel;
import com.puzzlehub.sudoku.data.local.CompetitiveResultEntity;
import com.puzzlehub.sudoku.data.local.GameHistoryEntity;
import com.puzzlehub.sudoku.data.model.OnlinePlayer;
import com.puzzlehub.sudoku.data.model.OnlineRoom;
import com.puzzlehub.sudoku.data.repository.GameRepository;
import com.puzzlehub.sudoku.data.repository.MultiplayerRepository;
import com.puzzlehub.sudoku.domain.SudokuBoard;
import androidx.core.util.Consumer;
import androidx.core.util.Supplier;
import com.puzzlehub.sudoku.utils.AndroidTasks;
import com.puzzlehub.sudoku.utils.SudokuCallbacks.Func;
import com.puzzlehub.sudoku.utils.SudokuTask;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlow;
import kotlinx.coroutines.flow.StateFlowKt;

public final class MultiplayerViewModel extends ViewModel {
    private final MultiplayerRepository repo;
    private final GameRepository games;
    private final MutableStateFlow<MultiplayerState> mutable = StateFlowKt.MutableStateFlow(new MultiplayerState());
    private MultiplayerRepository.Subscription roomSubscription;
    private MultiplayerRepository.Subscription connectionSubscription;
    private int sessionToken;
    private boolean recorded;
    private boolean recording;
    private boolean hostOfCurrentRoom;
    private boolean hostStartedMatch;
    private boolean matchReachedPlaying;

    public MultiplayerViewModel(MultiplayerRepository repo, GameRepository games) {
        this.repo = repo;
        this.games = games;
        saveActiveRoom("");
    }

    public StateFlow<MultiplayerState> getState() {
        return mutable;
    }

    public String getUid() {
        if (repo == null) {
            return "";
        }
        return repo.getUid();
    }

    public long getNowMillis() {
        if (repo == null) {
            return System.currentTimeMillis();
        }
        return repo.nowMillis();
    }

    private MultiplayerRepository requireRepo() {
        if (repo == null) {
            throw new IllegalStateException("Phòng chơi online chưa được kết nối. Bạn vẫn có thể chơi đơn.");
        }
        return repo;
    }

    public boolean isCurrentUserHost(OnlineRoom room) {
        if (hostOfCurrentRoom) {
            return true;
        }
        String uid = getUid();
        if (!room.hostId().trim().isEmpty() && !uid.trim().isEmpty()) {
            return room.hostId().equals(uid);
        }
        if (!room.players().isEmpty() && !uid.trim().isEmpty()) {
            return room.players().get(0).uid().equals(uid);
        }
        return true;
    }

    public void create(String mode, String difficulty) {
        enterRoom(true, name -> requireRepo().createRoom(mode, difficulty, name));
    }

    public void join(String code) {
        enterRoom(false, name -> requireRepo().joinRoom(code, name));
    }

    private void enterRoom(boolean asHost,
                           Func<String, SudokuTask<String>> enter) {
        if (mutable.getValue().busy()) {
            return;
        }
        OnlineRoom current = mutable.getValue().room();
        if (current != null && ("WAITING".equals(current.status()) || "READY".equals(current.status()))) {
            showError("Hãy hủy phòng hiện tại trước khi tạo hoặc vào phòng mới.");
            return;
        }
        int token = ++sessionToken;
        if (current != null && isTerminal(current)) {
            closeSubscriptions();
            saveActiveRoom("");
            mutable.setValue(new MultiplayerState());
        }
        operation(() -> games.settings().thenCompose(settings -> enter.apply(settings.playerName)),
                code -> attach(code, asHost, token));
    }

    public void startMatch() {
        operation(() -> {
            OnlineRoom room = mutable.getValue().room();
            if (room == null) {
                throw new IllegalStateException("Không tìm thấy phòng chờ.");
            }
            if (!isCurrentUserHost(room)) {
                throw new IllegalStateException("Chỉ chủ phòng mới có thể bắt đầu trận đấu.");
            }
            if (room.players().size() < 2 && !"READY".equals(room.status())) {
                throw new IllegalStateException("Đang chờ người chơi thứ hai vào phòng.");
            }
            hostStartedMatch = true;
            matchReachedPlaying = true;
            return requireRepo().startMatch(room.code());
        }, ignored -> {
            MultiplayerState screen = mutable.getValue();
            if (screen.room() != null && !isTerminal(screen.room())) {
                mutable.setValue(screen.withRoom(screen.room().withStatus("PLAYING"), false));
            }
        });
    }

    private <T> void operation(Supplier<SudokuTask<T>> action, Consumer<T> onSuccess) {
        MultiplayerState screen = mutable.getValue();
        if (screen.busy()) {
            return;
        }
        int token = sessionToken;
        mutable.setValue(screen.withStatus(true, screen.connected(), null));
        try {
            AndroidTasks.onComplete(AndroidTasks.withTimeout(action.get(), 25000), value -> {
                if (token == sessionToken) {
                    endOperation(null);
                    onSuccess.accept(value);
                }
            }, error -> {
                if (token == sessionToken) {
                    endOperation(error);
                }
            });
        } catch (RuntimeException error) {
            endOperation(error);
        }
    }

    private void endOperation(Throwable error) {
        String message = null;
        if (error instanceof TimeoutException) {
            message = "Kết nối lâu hơn dự kiến. Vui lòng kiểm tra mạng và thử lại.";
        } else if (error != null) {
            message = error.getMessage();
            if (message == null) {
                message = "Không thể kết nối phòng chơi.";
            }
        }
        MultiplayerState screen = mutable.getValue();
        mutable.setValue(screen.withStatus(false, screen.connected(), message));
    }

    private SudokuTask<Void> saveActiveRoom(String code) {
        return games.settings().thenCompose(settings -> {
            if (code.equals(settings.activeRoom)) {
                return SudokuTask.completedFuture(null);
            }
            settings.activeRoom = code;
            return games.settings(settings);
        });
    }

    private void attach(String code, boolean asHost, int token) {
        saveActiveRoom(code);
        closeSubscriptions();
        recorded = false;
        recording = false;
        hostOfCurrentRoom = asHost;
        hostStartedMatch = false;
        matchReachedPlaying = false;
        OnlineRoom immediate = requireRepo().cachedRoom(code);
        mutable.setValue(new MultiplayerState().withRoom(immediate, immediate == null));
        roomSubscription = requireRepo().observeRoom(code, rawRoom -> {
            if (token == sessionToken) {
                receiveRoom(rawRoom);
            }
        }, error -> {
            if (token == sessionToken) {
                closeSubscriptions();
                saveActiveRoom("");
                mutable.setValue(new MultiplayerState().withStatus(false, false, error.getMessage()));
            }
        });
        connectionSubscription = requireRepo().observeConnection(connected -> {
            if (token == sessionToken) {
                receiveConnection(code, token, connected);
            }
        }, error -> {
            if (token == sessionToken) {
                showError(error.getMessage());
            }
        });
    }

    private void receiveRoom(OnlineRoom rawRoom) {
        OnlineRoom room = rawRoom;
        if (hostStartedMatch && "READY".equals(room.status())) {
            room = room.withStatus("PLAYING");
        }
        if ("PLAYING".equals(room.status()) || "PAUSED".equals(room.status())) {
            hostStartedMatch = true;
            matchReachedPlaying = true;
        }
        if ("CANCELLED".equals(room.status()) && !matchReachedPlaying) {
            closeSubscriptions();
            saveActiveRoom("");
            mutable.setValue(new MultiplayerState().withStatus(false, false, "Phòng chờ đã bị hủy."));
            return;
        }
        MultiplayerState screen = mutable.getValue();
        mutable.setValue(screen.withRoom(room, screen.busy() && screen.room() != null));
        if (isTerminal(room)) {
            record(room);
        }
    }

    private void receiveConnection(String code, int token, boolean connected) {
        MultiplayerState screen = mutable.getValue();
        mutable.setValue(screen.withStatus(screen.busy(), connected, screen.error()));
        if (!connected) {
            return;
        }
        if (hostOfCurrentRoom && !hostStartedMatch) {
            requireRepo().armDisconnect(code);
            return;
        }
        AndroidTasks.onComplete(requireRepo().markOnline(code), ignored -> {}, error -> {
            if (token != sessionToken) {
                return;
            }
            if (error instanceof TimeoutException) {
                showError("Chưa cập nhật được kết nối. Thử lại nhé.");
            } else {
                showError(error.getMessage());
            }
        });
    }

    public SudokuBoard board(OnlineRoom room) {
        if ("COOPERATIVE".equals(room.mode())) {
            return room.board();
        }
        OnlinePlayer player = player(room, getUid());
        if (player == null) {
            return room.initial();
        }
        return player.board();
    }

    private OnlinePlayer player(OnlineRoom room, String uid) {
        for (OnlinePlayer player : room.players()) {
            if (player.uid().equals(uid)) {
                return player;
            }
        }
        return null;
    }

    public void select(int index) {
        MultiplayerState screen = mutable.getValue();
        mutable.setValue(screen.withInput(index, screen.pencil(), screen.notes()));
    }

    public void pencil() {
        MultiplayerState screen = mutable.getValue();
        mutable.setValue(screen.withInput(screen.selected(), !screen.pencil(), screen.notes()));
    }

    public void move(int value) {
        MultiplayerState screen = mutable.getValue();
        OnlineRoom room = screen.room();
        int index = screen.selected();
        if (room == null || index < 0 || room.initial().get(index) != 0
                || !"PLAYING".equals(room.status()) || !screen.connected()) {
            return;
        }
        if (screen.pencil() && value != 0) {
            if (board(room).get(index) == 0) {
                Set<Integer> existingNotes = screen.notes().get(index);
                Set<Integer> notes = existingNotes == null ? new HashSet<>() : new HashSet<>(existingNotes);
                if (!notes.remove(value)) {
                    notes.add(value);
                }
                updateNotes(index, Collections.unmodifiableSet(new HashSet<>(notes)));
            }
            return;
        }
        long revision;
        if ("COOPERATIVE".equals(room.mode())) {
            revision = room.revision();
        } else {
            OnlinePlayer player = player(room, getUid());
            if (player == null) {
                return;
            }
            revision = player.revision();
        }
        operation(() -> requireRepo().sendMove(room.code(), index, value, revision),
                ignored -> updateNotes(index, Collections.emptySet()));
    }

    private void updateNotes(int index, Set<Integer> values) {
        MultiplayerState screen = mutable.getValue();
        Map<Integer, Set<Integer>> notes = new HashMap<>(screen.notes());
        if (values.isEmpty()) {
            notes.remove(index);
        } else {
            notes.put(index, values);
        }
        mutable.setValue(screen.withInput(screen.selected(), screen.pencil(), notes));
    }

    public void leave() {
        leave(() -> {});
    }

    public void leave(Runnable onLeft) {
        OnlineRoom current = mutable.getValue().room();
        boolean wasPlaying = matchReachedPlaying;
        sessionToken++;
        closeSubscriptions();
        hostOfCurrentRoom = false;
        hostStartedMatch = false;
        matchReachedPlaying = false;
        mutable.setValue(new MultiplayerState());
        onLeft.run();
        saveActiveRoom("");
        if (current != null && repo != null) {
            repo.leaveRoomInBackground(current.code());
            if (!recorded && wasPlaying && current.startTime() > 0) {
                if ("FINISHED".equals(current.status())) {
                    record(current);
                } else {
                    record(new OnlineRoom(current.code(), current.mode(), current.difficulty(), "CANCELLED",
                            current.initial(), current.board(), current.revision(), current.players(),
                            current.startTime(), current.winnerId(), getNowMillis(),
                            current.disconnectedAt(), current.lastMove(), current.hostId(), current.guestId()));
                }
            }
        }
    }

    private void closeSubscriptions() {
        if (roomSubscription != null) {
            roomSubscription.close();
            roomSubscription = null;
        }
        if (connectionSubscription != null) {
            connectionSubscription.close();
            connectionSubscription = null;
        }
    }

    @Override
    protected void onCleared() {
        sessionToken++;
        closeSubscriptions();
        OnlineRoom room = mutable.getValue().room();
        if (room != null && repo != null && !isTerminal(room)) {
            repo.leaveRoomInBackground(room.code());
        }
    }

    public void clearError() {
        showError(null);
    }

    private void showError(String message) {
        MultiplayerState screen = mutable.getValue();
        mutable.setValue(screen.withStatus(screen.busy(), screen.connected(), message));
    }

    private boolean isTerminal(OnlineRoom room) {
        return "FINISHED".equals(room.status()) || "CANCELLED".equals(room.status());
    }

    public boolean affectsCompetitiveRank(OnlineRoom room) {
        String uid = getUid();
        return !uid.trim().isEmpty() && uid.equals(games.rankingPlayerId())
                && "FINISHED".equals(room.status()) && "COMPETITIVE".equals(room.mode())
                && player(room, uid) != null && player(room, room.winnerId()) != null
                && room.startTime() > 0 && room.finishedAt() >= room.startTime();
    }

    private void record(OnlineRoom room) {
        if (recorded || recording) {
            return;
        }
        if (room.startTime() <= 0) {
            recorded = true;
            return;
        }
        OnlinePlayer me = player(room, getUid());
        if (me == null) {
            return;
        }
        GameHistoryEntity history = new GameHistoryEntity();
        history.id = "online-" + room.code() + "-" + room.startTime() + "-" + me.uid();
        history.date = room.startTime();
        history.difficulty = room.difficulty();
        history.mode = room.mode();
        history.duration = Math.max(0, room.finishedAt() - room.startTime()) / 1000;
        history.mistakes = me.mistakes();
        history.score = me.score();
        if ("CANCELLED".equals(room.status())) {
            history.result = "ABANDONED";
        } else if ("TEAM".equals(room.winnerId()) || me.uid().equals(room.winnerId())) {
            history.result = "WON";
        } else {
            history.result = "LOST";
        }
        CompetitiveResultEntity rankResult = null;
        if (affectsCompetitiveRank(room)) {
            rankResult = new CompetitiveResultEntity(me.uid(), room.code(), room.startTime(), room.finishedAt());
        }
        int token = sessionToken;
        recording = true;
        AndroidTasks.onComplete(games.saveMultiplayer(history, rankResult), ignored -> {
            if (token == sessionToken) {
                recorded = true;
                recording = false;
            }
        }, error -> {
            if (token == sessionToken) {
                recording = false;
            }
        });
    }
}
