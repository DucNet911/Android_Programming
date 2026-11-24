package com.puzzlehub.sudoku.ui.social;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.puzzlehub.sudoku.data.repository.DemoPlayers;
import com.puzzlehub.sudoku.data.repository.PlayerProfile;
import com.puzzlehub.sudoku.data.repository.ProfileRepository;
import com.puzzlehub.sudoku.domain.CompetitiveRank;
import com.puzzlehub.sudoku.data.repository.SocialRepository;
import com.puzzlehub.sudoku.utils.AndroidTasks;
import java.util.Objects;
import com.puzzlehub.sudoku.utils.SudokuTask;

public final class RankViewModel extends ViewModel {
    public record State(Long points, boolean signedIn, boolean loading, boolean unavailable) {
        public CompetitiveRank rank() {
            if (points == null) {
                return CompetitiveRank.WARRIOR;
            }
            return CompetitiveRank.fromPoints(points);
        }
    }

    private final SocialRepository social;
    private final ProfileRepository profiles;
    private final DemoPlayers localPlayers;
    private final MutableLiveData<State> state = new MutableLiveData<>(new State(null, false, false, false));
    private String loadedPlayer;
    private SudokuTask<Long> request;

    public RankViewModel(SocialRepository social, ProfileRepository profiles, DemoPlayers localPlayers) {
        this.social = social;
        this.profiles = profiles;
        this.localPlayers = localPlayers;
    }

    public LiveData<State> getState() {
        return state;
    }

    public void refresh() {
        String player = null;
        if (social != null) {
            player = social.getPlayerId();
        }
        PlayerProfile profile = profiles.getState().getValue();
        boolean signedIn = profile.getSignedIn();
        if (profile.getDemo()) {
            if (request != null) {
                request.cancel(false);
            }
            loadedPlayer = profile.getUsername();
            state.setValue(new State(localPlayers.competitivePoints(), true, false, false));
            return;
        }
        boolean samePlayer = Objects.equals(player, loadedPlayer);

        if (samePlayer && request != null && !request.isDone()) {
            return;
        }
        if (request != null) {
            request.cancel(false);
        }
        State previous = state.getValue();
        Long existingPoints = null;
        if (samePlayer && previous != null) {
            existingPoints = previous.points();
        }
        loadedPlayer = player;
        if (social == null || player == null) {
            state.setValue(new State(null, signedIn, false, signedIn));
            return;
        }

        Long cachedPoints = existingPoints;
        state.setValue(new State(cachedPoints, true, true, false));
        SudokuTask<Long> current = AndroidTasks.withTimeout(social.competitivePoints(), SocialRepository.NETWORK_TIMEOUT);
        request = current;
        AndroidTasks.onComplete(current, points -> {
            if (request == current && !current.isCancelled()) {
                state.setValue(new State(points, true, false, false));
            }
        }, error -> {
            if (request == current && !current.isCancelled()) {
                state.setValue(new State(cachedPoints, true, false, true));
            }
        });
    }
}
