package com.example.arcade.ui;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.arcade.R;
import com.example.arcade.core.GameDefinition;
import com.example.arcade.catalog.GameRegistry;
import com.example.arcade.core.*;
import com.example.arcade.data.PlayerPreferences;
import com.example.arcade.data.ResultRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends AppCompatActivity {
    private GameAdapter adapter;
    private GameRegistry registry;
    private PlayerPreferences preferences;
    private GameSessionStore store;
    private EditText search;
    private boolean favoritesOnly;
    private String pendingGame;
    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK) {
                try {
                    GameExitResponse response = GameContract.readResponse(result.getData());
                    if (!response.gameId.equals(pendingGame) || !PlayerPreferences.PLAYER_ID.equals(response.playerId))
                        throw new IllegalArgumentException("Unexpected game response");
                    if (response.reason == ExitReason.FINISHED) {
                        try (ResultRepository repository = new ResultRepository(this)) { repository.accept(response.result); }
                        toast(R.string.finished_message);
                    } else if (response.reason == ExitReason.SAVED_FOR_LATER) toast(R.string.saved_message);
                    else if (response.reason == ExitReason.ERROR) {
                        if (response.errorCode == GameErrorCode.CORRUPT_SAVE || response.errorCode == GameErrorCode.SESSION_NOT_FOUND)
                            offerSaveRecovery(response.gameId);
                        else toast(R.string.error_game);
                    }
                } catch (IllegalArgumentException ex) { toast(R.string.error_response); }
                catch (RuntimeException ex) { toast(R.string.storage_error); }
            }
            pendingGame = null;
            if (adapter != null) refresh();
        });
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_main);
        Insets.apply(this, findViewById(R.id.platform_root));
        preferences = new PlayerPreferences(this); store = new GameSessionStore(this);
        registry = GameRegistry.discover(this);
        if (state != null) { favoritesOnly = state.getBoolean("favorites"); pendingGame = state.getString("pendingGame"); }
        search = findViewById(R.id.platform_search);
        RecyclerView list = findViewById(R.id.platform_games); list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new GameAdapter(preferences, store, new GameAdapter.Listener() {
            @Override public void open(GameDefinition g) { openGame(g); }
            @Override public void favorite(GameDefinition g) { preferences.toggleFavorite(g.id); refresh(); }
        });
        list.setAdapter(adapter);
        findViewById(R.id.platform_all).setOnClickListener(v -> { favoritesOnly = false; refresh(); });
        findViewById(R.id.platform_favorites).setOnClickListener(v -> { favoritesOnly = true; refresh(); });
        findViewById(R.id.platform_history).setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { refresh(); }
            public void afterTextChanged(Editable e) {}
        });
        refresh();
    }
    @Override protected void onResume() {
        super.onResume();
        try (ResultRepository r = new ResultRepository(this)) { r.recoverPendingResults(); }
        catch (RuntimeException ex) { toast(R.string.storage_error); }
        if (adapter != null) refresh();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putBoolean("favorites", favoritesOnly); out.putString("pendingGame", pendingGame); super.onSaveInstanceState(out);
    }
    private void refresh() {
        String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<GameDefinition> filtered = new ArrayList<>(); int saved = 0;
        for (GameDefinition g : registry.all()) {
            if (g.title.toLowerCase(Locale.ROOT).contains(q) && (!favoritesOnly || preferences.favorite(g.id))) filtered.add(g);
            try { if (store.load(g.id, PlayerPreferences.PLAYER_ID) != null) saved++; } catch (IllegalStateException ignored) { /* Shown on game card. */ }
        }
        adapter.submit(filtered);
        boolean noGames = registry.all().isEmpty();
        findViewById(R.id.platform_zero_state).setVisibility(noGames ? View.VISIBLE : View.GONE);
        ((TextView)findViewById(R.id.platform_empty)).setText(favoritesOnly && q.isEmpty() ? R.string.empty_favorites : R.string.empty_search);
        findViewById(R.id.platform_empty).setVisibility(!noGames && filtered.isEmpty() ? View.VISIBLE : View.GONE);
        findViewById(R.id.platform_all).setAlpha(favoritesOnly ? 0.55f : 1f);
        findViewById(R.id.platform_favorites).setAlpha(favoritesOnly ? 1f : 0.55f);
        ((TextView)findViewById(R.id.platform_stats)).setText(getString(R.string.home_stats, registry.all().size(), saved));
    }
    private void openGame(GameDefinition g) {
        if (pendingGame != null) return;
        GameSession saved;
        try { saved = store.load(g.id, PlayerPreferences.PLAYER_ID); }
        catch (IllegalStateException ex) {
            offerSaveRecovery(g.id); return;
        }
        if (GameHomeActivity.supports(this, g.id)) {
            GameLaunchRequest request = saved == null
                    ? new GameLaunchRequest(g.id, PlayerPreferences.PLAYER_ID, LaunchMode.NEW_GAME, null, null)
                    : new GameLaunchRequest(g.id, PlayerPreferences.PLAYER_ID, LaunchMode.RESUME, saved.sessionId, saved.difficultyId);
            launch(g, request);
            return;
        }
        if (saved != null) { launch(g, new GameLaunchRequest(g.id, PlayerPreferences.PLAYER_ID, LaunchMode.RESUME, saved.sessionId, saved.difficultyId)); return; }
        if (g.difficultyCount() == 1) { launchNew(g, g.difficultyId(0)); return; }
        View picker = getLayoutInflater().inflate(R.layout.game_mode_picker, null);
        ((TextView) picker.findViewById(R.id.mode_title)).setText(g.title);
        android.widget.LinearLayout choices = picker.findViewById(R.id.mode_choices);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(picker)
                .setNegativeButton(R.string.cancel, null).create();
        for (int i = 0; i < g.difficultyCount(); i++) {
            String mode = g.difficultyId(i);
            android.widget.Button button = (android.widget.Button) getLayoutInflater()
                    .inflate(R.layout.game_mode_choice, choices, false);
            button.setText(g.difficultyLabel(i));
            button.setOnClickListener(v -> {
                dialog.dismiss();
                launchNew(g, mode);
            });
            choices.addView(button);
        }
        dialog.show();
    }
    private void offerSaveRecovery(String gameId) {
        new AlertDialog.Builder(this).setTitle(R.string.corrupt_save).setMessage(R.string.corrupt_explanation)
            .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.delete_bad_save, (dialog, which) -> {
                try { store.deleteCorruptSave(gameId, PlayerPreferences.PLAYER_ID); refresh(); }
                catch (RuntimeException error) { toast(R.string.storage_error); }
            }).show();
    }
    private void launchNew(GameDefinition g, String difficulty) {
        launch(g, new GameLaunchRequest(g.id, PlayerPreferences.PLAYER_ID, LaunchMode.NEW_GAME, null, difficulty));
    }
    private void launch(GameDefinition g, GameLaunchRequest request) {
        pendingGame = g.id;
        try { launcher.launch(GameContract.putRequest(new Intent(this, GameHomeActivity.supports(this, g.id) ? GameHomeActivity.class : g.activity), request)); }
        catch (RuntimeException ex) { pendingGame = null; toast(R.string.error_open); }
    }
    private void toast(int res) { Toast.makeText(this, res, Toast.LENGTH_LONG).show(); }
}
