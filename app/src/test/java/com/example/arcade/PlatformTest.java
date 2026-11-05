package com.example.arcade;
import android.content.Context;
import android.widget.EditText;
import androidx.recyclerview.widget.RecyclerView;
import com.example.arcade.catalog.GameRegistry;
import com.example.arcade.core.*;
import com.example.arcade.data.*;
import com.example.arcade.ui.MainActivity;
import com.example.arcade.ui.HistoryActivity;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class PlatformTest {
    private Context context;
    @Before public void setup() {
        context = RuntimeEnvironment.getApplication(); context.deleteDatabase("arcade_history.db");
        context.getSharedPreferences("arcade_sessions_v1", 0).edit().clear().commit();
        context.getSharedPreferences("platform_preferences", 0).edit().clear().commit();
    }
    private GameResult result(String game, String player, String session, Long score) {
        return new GameResult(game, player, session, "easy", GameOutcome.WON, score, 3000, 100, false);
    }
    @Test public void historyDeduplicatesAndPreservesNullScore() {
        try (HistoryDb db = new HistoryDb(context)) {
            GameResult r = result("sample_time", "guest", "s1", null); db.insertOnce(r); db.insertOnce(r);
            db.insertOnce(result("sample_time", "other", "s1", null));
            assertEquals(1, db.list("guest", null).size()); assertNull(db.list("guest", null).get(0).score);
            assertTrue(db.list("guest", "sample_score").isEmpty());
        }
    }
    @Test public void pendingResultSurvivesMissingActivityCallback() {
        GameSessionStore store = new GameSessionStore(context);
        store.save(new GameSession("sample_time", "guest", "s1", "easy", 10, "{}", false));
        store.finish(result("sample_time", "guest", "s1", null));
        try (ResultRepository repository = new ResultRepository(context)) { repository.recoverPendingResults(); repository.recoverPendingResults(); }
        try (HistoryDb db = new HistoryDb(context)) { assertEquals(1, db.list("guest", null).size()); }
        assertTrue(store.pendingResults().isEmpty());
    }
    @Test public void crashAfterInsertBeforeAckDoesNotDuplicateHistory() {
        GameSessionStore store = new GameSessionStore(context);
        store.save(new GameSession("sample_time", "guest", "s1", "easy", 10, "{}", false));
        GameResult r=result("sample_time", "guest", "s1", null); store.finish(r);
        try (HistoryDb db = new HistoryDb(context)) { db.insertOnce(r); }
        try (ResultRepository repository = new ResultRepository(context)) { repository.recoverPendingResults(); }
        try (HistoryDb db = new HistoryDb(context)) { assertEquals(1, db.list("guest", null).size()); }
    }
    @Test public void homeSupportsEmptyCatalog() {
        com.example.arcade.catalog.TestPlugins.clear(context);
        try (org.robolectric.android.controller.ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity main=controller.get(); RecyclerView list=main.findViewById(R.id.platform_games);
            assertEquals(0,list.getAdapter().getItemCount());
            assertEquals(android.view.View.VISIBLE, main.findViewById(R.id.platform_zero_state).getVisibility());
            assertTrue(((android.widget.TextView)main.findViewById(R.id.platform_stats)).getText().toString().startsWith("0 game"));
            ((EditText)main.findViewById(R.id.platform_search)).setText("anything");
            main.findViewById(R.id.platform_favorites).performClick(); assertEquals(0,list.getAdapter().getItemCount());
            assertEquals(android.view.View.VISIBLE, main.findViewById(R.id.platform_zero_state).getVisibility());
        }
    }
    @Test public void registeredGameCanBeSearchedFavoritedAndLaunched() {
        com.example.arcade.catalog.TestPlugins.install(context);
        try (org.robolectric.android.controller.ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity main=controller.get(); RecyclerView list=main.findViewById(R.id.platform_games);
            assertEquals(1,list.getAdapter().getItemCount());
            assertEquals(android.view.View.GONE, main.findViewById(R.id.platform_zero_state).getVisibility());
            ((EditText)main.findViewById(R.id.platform_search)).setText("NO MATCH"); assertEquals(0,list.getAdapter().getItemCount());
            ((EditText)main.findViewById(R.id.platform_search)).setText("probe"); assertEquals(1,list.getAdapter().getItemCount());
            main.findViewById(R.id.platform_favorites).performClick(); assertEquals(0,list.getAdapter().getItemCount());
            new PlayerPreferences(main).toggleFavorite("probe"); main.findViewById(R.id.platform_favorites).performClick();
            assertEquals(1,list.getAdapter().getItemCount());
            RecyclerView.Adapter adapter=list.getAdapter(); RecyclerView.ViewHolder holder=adapter.createViewHolder(list,0);
            adapter.bindViewHolder(holder,0); holder.itemView.findViewById(R.id.card_play).performClick();
            android.content.Intent intent=org.robolectric.Shadows.shadowOf(main).getNextStartedActivityForResult().intent;
            assertEquals("probe",GameContract.readRequest(intent).gameId);
            assertEquals(LaunchMode.NEW_GAME,GameContract.readRequest(intent).launchMode);
        }
    }
    @Test public void suppliedModulesDeclareValidPlugins() {
        // Validates the real merged Manifest, including future games added by teammates.
        GameRegistry registry = GameRegistry.discover(context);
        assertTrue(registry.errors().toString(), registry.errors().isEmpty());
        String expected = System.getProperty("arcade.expectedGameId");
        if (expected != null) assertNotNull("Game is not registered: " + expected, registry.find(expected));
    }
    @Test public void oldDemoDataIsRemovedButRealDataSurvives() {
        GameSessionStore store = new GameSessionStore(context);
        store.save(new GameSession("old_demo","guest","d1","classic",0,"{}",true));
        store.save(new GameSession("real_game","guest","r1","classic",0,"{}",false));
        try (HistoryDb db=new HistoryDb(context)) {
            db.insertOnce(new GameResult("old_demo","guest","d2","classic",GameOutcome.WON,4L,0,100,true));
            db.insertOnce(result("real_game","guest","r2",4L));
        }
        try (ResultRepository repository=new ResultRepository(context)) { repository.recoverPendingResults(); }
        assertNull(store.load("old_demo","guest")); assertNotNull(store.load("real_game","guest"));
        try (HistoryDb db=new HistoryDb(context)) { assertEquals(1,db.list("guest",null).size()); }
    }
    @Test public void historyScreenInflatesWithResult() {
        try (HistoryDb db = new HistoryDb(context)) { db.insertOnce(result("sample_time","guest","s1",null)); }
        try (org.robolectric.android.controller.ActivityController<HistoryActivity> controller = Robolectric.buildActivity(HistoryActivity.class).setup()) {
            RecyclerView list=controller.get().findViewById(R.id.history_list); assertEquals(1,list.getAdapter().getItemCount());
        }
    }
}
