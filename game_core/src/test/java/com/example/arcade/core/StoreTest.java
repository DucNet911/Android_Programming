package com.example.arcade.core;
import android.content.Context;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class StoreTest {
    private GameSessionStore store;
    @Before public void setup() {
        RuntimeEnvironment.getApplication().getSharedPreferences("arcade_sessions_v1", Context.MODE_PRIVATE).edit().clear().commit();
        store = new GameSessionStore(RuntimeEnvironment.getApplication());
    }
    private GameSession session(String game, String player, String id) {
        return new GameSession(game, player, id, "classic", 42, "{\"steps\":3}", true);
    }
    private GameResult result(String game, String player, String id) {
        return new GameResult(game, player, id, "classic", GameOutcome.LOST, 88L, 60, 100, true);
    }
    @Test public void isolatesPlayersAndGames() {
        store.save(session("2048", "alice", "s1")); store.save(session("2048", "bob", "s2")); store.save(session("tetris", "alice", "s3"));
        assertEquals("s1", store.load("2048", "alice").sessionId); assertEquals("s2", store.load("2048", "bob").sessionId);
        assertEquals("s3", store.load("tetris", "alice").sessionId); assertNull(store.load("sudoku", "alice"));
    }
    @Test(expected=IllegalStateException.class) public void refusesToOverwriteAnotherSession() {
        store.save(session("2048", "guest", "s1")); store.save(session("2048", "guest", "s2"));
    }
    @Test public void finishPersistsResultBeforeAcknowledgement() {
        store.save(session("2048", "guest", "s1")); GameResult r = result("2048", "guest", "s1"); store.finish(r);
        assertNull(store.load("2048", "guest"));
        GameSessionStore reopened = new GameSessionStore(RuntimeEnvironment.getApplication());
        assertEquals(1, reopened.pendingResults().size()); assertEquals(Long.valueOf(88), reopened.pendingResults().get(0).score);
        reopened.acknowledge(r); assertTrue(store.pendingResults().isEmpty());
    }
    @Test public void duplicateFinishIsIdempotent() {
        store.save(session("2048", "guest", "s1")); GameResult r = result("2048", "guest", "s1"); store.finish(r); store.finish(r);
        assertEquals(1, store.pendingResults().size());
    }
    @Test(expected=IllegalStateException.class) public void wrongResultCannotDeleteCurrentSave() {
        store.save(session("2048", "guest", "s1"));
        try { store.finish(result("2048", "guest", "s2")); }
        finally { assertEquals("s1", store.load("2048", "guest").sessionId); }
    }
    @Test(expected=IllegalStateException.class) public void corruptSaveIsReported() {
        RuntimeEnvironment.getApplication().getSharedPreferences("arcade_sessions_v1", Context.MODE_PRIVATE).edit().putString("save/2048/guest", "broken").commit();
        store.load("2048", "guest");
    }
}
