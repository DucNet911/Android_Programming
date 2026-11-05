package com.example.arcade.core;
import android.content.Context;
/** Implement in a game library; register its public class in application manifest metadata.
 * Must have a public no-arg constructor. Keep construction cheap and side-effect free.
 */
public interface GamePlugin {
    String METADATA_PREFIX = "com.example.arcade.GAME_PLUGIN.";
    int getContractVersion();
    GameDefinition getDefinition(Context context);
}
