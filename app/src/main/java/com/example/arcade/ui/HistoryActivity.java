package com.example.arcade.ui;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.arcade.R;
import com.example.arcade.core.GameDefinition;
import com.example.arcade.catalog.GameRegistry;
import com.example.arcade.core.GameOutcome;
import com.example.arcade.core.GameResult;
import com.example.arcade.data.HistoryDb;
import com.example.arcade.data.PlayerPreferences;
import com.example.arcade.data.ResultRepository;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public final class HistoryActivity extends AppCompatActivity {
    private RecyclerView list;
    private GameRegistry registry;
    private Spinner filter;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_history); Insets.apply(this, findViewById(R.id.platform_root));
        findViewById(R.id.history_back).setOnClickListener(v -> finish());
        list = findViewById(R.id.history_list); list.setLayoutManager(new LinearLayoutManager(this));
        filter = findViewById(R.id.history_filter);
        registry = GameRegistry.discover(this);
        List<String> labels = new ArrayList<>(); labels.add(getString(R.string.all_games));
        for (GameDefinition g : registry.all()) labels.add(g.title);
        ArrayAdapter<String> choices = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        choices.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); filter.setAdapter(choices);
        filter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) { refresh(); }
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }
    @Override protected void onResume() {
        super.onResume();
        try (ResultRepository r = new ResultRepository(this)) { r.recoverPendingResults(); }
        catch (RuntimeException ex) { Toast.makeText(this, R.string.storage_error, Toast.LENGTH_LONG).show(); }
        refresh();
    }
    private void refresh() {
        int selected = filter.getSelectedItemPosition();
        String gameId = selected <= 0 ? null : registry.all().get(selected - 1).id;
        try (HistoryDb db = new HistoryDb(this)) {
            List<GameResult> items = db.list(PlayerPreferences.PLAYER_ID, gameId);
            list.setAdapter(new HistoryAdapter(items));
            findViewById(R.id.history_empty).setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
            ((TextView)findViewById(R.id.history_count)).setText(getString(R.string.history_count, items.size()));
            List<String> records = new ArrayList<>();
            for (GameDefinition g : registry.all()) for (int d = 0; d < g.difficultyCount(); d++) {
                Long bestScore = null, bestTime = null;
                for (GameResult r : items) {
                    if (r.demo || r.outcome == GameOutcome.ABANDONED || !r.gameId.equals(g.id)
                            || !java.util.Objects.equals(r.difficultyId, g.difficultyId(d))) continue;
                    if (g.ranking == GameDefinition.Ranking.LOWEST_WIN_TIME) {
                        if (r.outcome == GameOutcome.WON && (bestTime == null || r.activeDurationMs < bestTime)) bestTime = r.activeDurationMs;
                    } else if (g.ranking == GameDefinition.Ranking.HIGH_SCORE && r.score != null && (bestScore == null || r.score > bestScore)) bestScore = r.score;
                }
                String title = g.title + " / " + g.difficultyLabel(d);
                if (bestTime != null) records.add(getString(R.string.best_time, title, Formats.duration(this, bestTime)));
                if (bestScore != null) records.add(getString(R.string.best_score, title, bestScore));
            }
            String summary = records.isEmpty() ? getString(R.string.no_records) : android.text.TextUtils.join("\n", records);
            ((TextView)findViewById(R.id.history_records)).setText(summary + "\n\n" + getString(R.string.stats_note));
        } catch (RuntimeException ex) { Toast.makeText(this, R.string.storage_error, Toast.LENGTH_LONG).show(); }
    }
    private final class HistoryAdapter extends RecyclerView.Adapter<HistoryHolder> {
        private final List<GameResult> items;
        HistoryAdapter(List<GameResult> items) { this.items = items; }
        @NonNull public HistoryHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new HistoryHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false));
        }
        public void onBindViewHolder(@NonNull HistoryHolder h, int index) {
            GameResult r = items.get(index); GameDefinition g = registry.find(r.gameId);
            String title = g == null ? r.gameId : g.title;
            String difficulty = g == null ? (r.difficultyId == null ? "—" : r.difficultyId) : g.difficultyLabel(r.difficultyId);
            h.title.setText(title + " · " + Formats.outcome(HistoryActivity.this, r.outcome));
            h.value.setText(getString(R.string.result_summary, r.score == null ? getString(R.string.no_score) : getString(R.string.score_value, r.score), Formats.duration(HistoryActivity.this, r.activeDurationMs)));
            h.date.setText(difficulty + " · " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(r.finishedAtEpochMs)));
            h.demo.setText(r.demo ? R.string.demo_result : R.string.real_result);
        }
        public int getItemCount() { return items.size(); }
    }
    static final class HistoryHolder extends RecyclerView.ViewHolder {
        final TextView title, value, date, demo;
        HistoryHolder(View v) { super(v); title = v.findViewById(R.id.history_item_title); value = v.findViewById(R.id.history_item_value);
            date = v.findViewById(R.id.history_item_date); demo = v.findViewById(R.id.history_item_demo); }
    }
}
