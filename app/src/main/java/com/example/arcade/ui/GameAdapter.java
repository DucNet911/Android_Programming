package com.example.arcade.ui;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.annotation.NonNull;
import com.example.arcade.R;
import com.example.arcade.core.GameDefinition;
import com.example.arcade.core.GameSessionStore;
import com.example.arcade.data.PlayerPreferences;
import java.util.ArrayList;
import java.util.List;
final class GameAdapter extends RecyclerView.Adapter<GameAdapter.Holder> {
    interface Listener { void open(GameDefinition game); void favorite(GameDefinition game); }
    private final List<GameDefinition> items = new ArrayList<>();
    private final PlayerPreferences preferences;
    private final GameSessionStore store;
    private final Listener listener;
    GameAdapter(PlayerPreferences preferences, GameSessionStore store, Listener listener) {
        this.preferences = preferences; this.store = store; this.listener = listener;
    }
    @android.annotation.SuppressLint("NotifyDataSetChanged")
    void submit(List<GameDefinition> games) { items.clear(); items.addAll(games); notifyDataSetChanged(); }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_game, parent, false));
    }
    @Override public void onBindViewHolder(@NonNull Holder h, int position) {
        GameDefinition g = items.get(position);
        h.badge.setText(R.string.ready_badge);
        h.title.setText(g.title); h.description.setText(g.description); h.symbol.setText(g.symbol);
        h.symbol.setTextColor(g.accentColor);
        boolean fav = preferences.favorite(g.id); h.favorite.setText(fav ? "★" : "☆");
        h.favorite.setContentDescription(h.itemView.getContext().getString(fav ? R.string.favorite_remove : R.string.favorite_add, h.title.getText()));
        try {
            boolean saved = store.load(g.id, PlayerPreferences.PLAYER_ID) != null;
            h.status.setText(saved ? R.string.saved_status : R.string.ready_status);
            h.play.setText(saved ? R.string.resume_real : R.string.play_real);
        } catch (IllegalStateException ex) { h.status.setText(R.string.corrupt_save); h.play.setText(R.string.recover); }
        h.play.setOnClickListener(v -> listener.open(g));
        h.favorite.setOnClickListener(v -> listener.favorite(g));
    }
    @Override public int getItemCount() { return items.size(); }
    static final class Holder extends RecyclerView.ViewHolder {
        final TextView title, description, symbol, status, badge;
        final Button favorite, play;
        Holder(View v) { super(v); badge = v.findViewById(R.id.card_badge); title = v.findViewById(R.id.card_title); description = v.findViewById(R.id.card_description);
            symbol = v.findViewById(R.id.card_symbol); status = v.findViewById(R.id.card_status);
            favorite = v.findViewById(R.id.card_favorite); play = v.findViewById(R.id.card_play); }
    }
}
