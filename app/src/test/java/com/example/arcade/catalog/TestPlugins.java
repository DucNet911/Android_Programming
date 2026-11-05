package com.example.arcade.catalog;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import com.example.arcade.core.*;
import java.util.Collections;
import org.robolectric.Shadows;
/** Test-only stubs. Not present in src/main or in the APK. No playable game is included. */
public final class TestPlugins {
    private TestPlugins() {}
    public static class ProbeActivity extends Activity {}
    public static class ProbePlugin implements GamePlugin {
        public int getContractVersion() { return GameContract.VERSION; }
        public GameDefinition getDefinition(Context context) {
            return new GameDefinition("probe","Probe", "Test metadata", "P", 0xffb9a4ff,
                    ProbeActivity.class, GameDefinition.Ranking.HIGH_SCORE,
                    Collections.singletonList(new GameDifficulty("classic","Classic")));
        }
    }
    public static class IncompatiblePlugin extends ProbePlugin {
        @Override public int getContractVersion() { return 999; }
    }
    public static class BadConstructorPlugin extends ProbePlugin {
        public BadConstructorPlugin() { throw new IllegalArgumentException("Test constructor error"); }
    }
    public static void clear(Context context) { applyMetadata(context, new Bundle()); }
    public static Bundle metadata(Class<?> type) {
        Bundle b=new Bundle(); b.putString(GamePlugin.METADATA_PREFIX+"probe", type.getName()); return b;
    }
    public static void install(Context context) { applyMetadata(context, metadata(ProbePlugin.class)); registerActivity(context, false); }
    public static void registerActivity(Context context, boolean exported) {
        ActivityInfo a=new ActivityInfo(); a.name=ProbeActivity.class.getName(); a.packageName=context.getPackageName();
        a.applicationInfo=context.getApplicationInfo(); a.enabled=true; a.exported=exported;
        a.processName=context.getApplicationInfo().processName;
        Shadows.shadowOf(context.getPackageManager()).addOrUpdateActivity(a);
    }
    public static void applyMetadata(Context context, Bundle metadata) {
        try {
            PackageInfo pkg=context.getPackageManager().getPackageInfo(context.getPackageName(),PackageManager.GET_ACTIVITIES | PackageManager.GET_META_DATA);
            pkg.applicationInfo.metaData=metadata;
            Shadows.shadowOf(context.getPackageManager()).installPackage(pkg);
        } catch (PackageManager.NameNotFoundException ex) { throw new AssertionError(ex); }
    }
}
