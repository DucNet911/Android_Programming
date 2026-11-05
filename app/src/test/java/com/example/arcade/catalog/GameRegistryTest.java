package com.example.arcade.catalog;
import android.content.Context;
import android.os.Bundle;
import com.example.arcade.core.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class GameRegistryTest {
    private Context context;
    @Before public void setup() { context=RuntimeEnvironment.getApplication(); }
    @Test public void noMetadataMeansZeroGames() {
        assertTrue(GameRegistry.fromMetadata(context,null).all().isEmpty());
        assertTrue(GameRegistry.fromMetadata(context,new Bundle()).all().isEmpty());
    }
    @Test public void registeredLibraryIsDiscovered() {
        TestPlugins.install(context); GameRegistry registry=GameRegistry.discover(context);
        assertTrue(registry.errors().toString(),registry.errors().isEmpty()); assertEquals(1,registry.all().size());
        assertEquals("Probe",registry.find("probe").title);
    }
    @Test public void unrelatedMetadataIsIgnored() {
        Bundle b=new Bundle();b.putString("analytics.provider", "not.a.RealClass");
        assertTrue(GameRegistry.fromMetadata(context,b).errors().isEmpty());
    }
    @Test public void incompatibleContractIsRejected() {
        GameRegistry r=GameRegistry.fromMetadata(context,TestPlugins.metadata(TestPlugins.IncompatiblePlugin.class));
        assertTrue(r.all().isEmpty());assertEquals(1,r.errors().size());
    }
    @Test public void missingPluginIsRejectedWithoutCrashingHome() {
        Bundle b=new Bundle();b.putString(GamePlugin.METADATA_PREFIX+"missing","not.a.RealPlugin");
        GameRegistry r=GameRegistry.fromMetadata(context,b);assertTrue(r.all().isEmpty());assertEquals(1,r.errors().size());
    }
    @Test public void missingActivityIsRejected() {
        GameRegistry r=GameRegistry.fromMetadata(context,TestPlugins.metadata(TestPlugins.ProbePlugin.class));
        assertTrue(r.all().isEmpty());assertEquals(1,r.errors().size());
    }
    @Test public void exportedActivityIsRejected() {
        TestPlugins.registerActivity(context,true);
        GameRegistry r=GameRegistry.fromMetadata(context,TestPlugins.metadata(TestPlugins.ProbePlugin.class));
        assertTrue(r.all().isEmpty());assertEquals(1,r.errors().size());
    }
    @Test public void duplicateIdentityIsNotSilentlySelected() {
        TestPlugins.registerActivity(context,false);Bundle b=TestPlugins.metadata(TestPlugins.ProbePlugin.class);
        b.putString(GamePlugin.METADATA_PREFIX+"z_duplicate",TestPlugins.ProbePlugin.class.getName());
        GameRegistry r=GameRegistry.fromMetadata(context,b);assertTrue(r.all().isEmpty());assertFalse(r.errors().isEmpty());
    }
    @Test public void constructorErrorIsContained() {
        GameRegistry r=GameRegistry.fromMetadata(context,TestPlugins.metadata(TestPlugins.BadConstructorPlugin.class));
        assertTrue(r.all().isEmpty());assertEquals(1,r.errors().size());
    }
    @Test(expected=UnsupportedOperationException.class) public void catalogIsReadOnly() {
        GameRegistry.fromMetadata(context,null).all().clear();
    }
}
