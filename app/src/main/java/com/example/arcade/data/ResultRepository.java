package com.example.arcade.data;
import android.content.Context;
import com.example.arcade.core.GameResult;
import com.example.arcade.core.GameSessionStore;
public final class ResultRepository implements AutoCloseable {
    private final HistoryDb db;
    private final GameSessionStore store;
    public ResultRepository(Context context) { db = new HistoryDb(context); store = new GameSessionStore(context); }
    public void recoverPendingResults() {
        store.clearDemoData();
        db.deleteDemoResults();
        for (GameResult r : store.pendingResults()) accept(r);
    }
    public void accept(GameResult r) {
        if (r.demo) { store.acknowledge(r); return; }
        db.insertOnce(r); // Persist before acknowledging. Retry after a crash is harmless.
        store.acknowledge(r);
    }
    @Override public void close() { db.close(); }
}
