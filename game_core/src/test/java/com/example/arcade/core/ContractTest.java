package com.example.arcade.core;
import android.content.Intent;
import android.os.Bundle;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class ContractTest {
    @Test public void requestRoundTrip() {
        GameLaunchRequest r = new GameLaunchRequest("sudoku", "guest", LaunchMode.RESUME, "s1", "hard");
        GameLaunchRequest decoded = GameContract.readRequest(GameContract.putRequest(new Intent(), r));
        assertEquals("hard", decoded.difficultyId); assertEquals("s1", decoded.sessionId); assertEquals(LaunchMode.RESUME, decoded.launchMode);
    }
    @Test public void nullScoreRemainsNull() {
        GameResult r = new GameResult("sudoku", "guest", "s1", "easy", GameOutcome.WON, null, 40, 100, true);
        GameExitResponse decoded = GameContract.readResponse(GameContract.responseIntent(GameExitResponse.finished(r)));
        assertNull(decoded.result.score); assertTrue(decoded.result.demo); assertEquals(ExitReason.FINISHED, decoded.reason);
    }
    @Test public void numericScoreRemainsNumeric() {
        GameResult r = new GameResult("2048", "guest", "s1", "classic", GameOutcome.LOST, 2048L, 40, 100, false);
        assertEquals(Long.valueOf(2048), GameContract.readResponse(GameContract.responseIntent(GameExitResponse.finished(r))).result.score);
    }
    @Test public void saveIsNotFinalResult() {
        GameSession s = new GameSession("tetris", "guest", "s1", "classic", 42, "{}", true);
        GameExitResponse decoded = GameContract.readResponse(GameContract.responseIntent(GameExitResponse.saved(s)));
        assertEquals(ExitReason.SAVED_FOR_LATER, decoded.reason); assertNull(decoded.result); assertEquals("s1", decoded.sessionId);
    }
    @Test(expected=IllegalArgumentException.class) public void rejectWrongVersion() {
        Intent i = GameContract.putRequest(new Intent(), new GameLaunchRequest("2048", "guest", LaunchMode.NEW_GAME, null, null));
        Bundle b = i.getBundleExtra(GameContract.EXTRA_REQUEST); b.putInt("version", 999); i.putExtra(GameContract.EXTRA_REQUEST, b);
        GameContract.readRequest(i);
    }
    @Test(expected=IllegalArgumentException.class) public void rejectMismatchedResultIdentity() {
        GameResult r = new GameResult("2048", "guest", "s1", "classic", GameOutcome.WON, 8L, 1, 100, false);
        Intent i = GameContract.responseIntent(GameExitResponse.finished(r));
        Bundle b = i.getBundleExtra(GameContract.EXTRA_RESPONSE); b.putString("game", "sudoku"); i.putExtra(GameContract.EXTRA_RESPONSE,b);
        GameContract.readResponse(i);
    }
    @Test(expected=IllegalArgumentException.class) public void rejectEmptyIntent() { GameContract.readRequest(new Intent()); }
}
