package com.example.arcade.catalog;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import com.example.arcade.core.GameContract;
import com.example.arcade.core.GameDefinition;
import com.example.arcade.core.GamePlugin;
import java.util.*;

/** Finds only modules already compiled into this application. No network or APK loading. */
public final class GameRegistry {
    private static final String TAG = "ArcadeRegistry";
    private final List<GameDefinition> games;
    private final List<String> errors;
    private GameRegistry(List<GameDefinition> games, List<String> errors) {
        this.games = Collections.unmodifiableList(new ArrayList<>(games));
        this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
    }
    public static GameRegistry discover(Context context) {
        try {
            Bundle metadata = context.getPackageManager().getApplicationInfo(
                    context.getPackageName(), PackageManager.GET_META_DATA).metaData;
            return fromMetadata(context, metadata);
        } catch (PackageManager.NameNotFoundException ex) {
            throw new IllegalStateException("Host application not found", ex);
        }
    }
    // Package-private seam for tests. Tests do not package modules in the production APK.
    static GameRegistry fromMetadata(Context context, Bundle metadata) {
        List<GameDefinition> accepted = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Set<String> duplicates = new HashSet<>();
        List<String> keys = metadata == null ? new ArrayList<>() : new ArrayList<>(metadata.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            if (!key.startsWith(GamePlugin.METADATA_PREFIX)) continue;
            try {
                String className = metadata.getString(key);
                if (className == null || className.trim().isEmpty()) throw new IllegalArgumentException("Missing plugin class");
                Class<?> type = Class.forName(className, false, context.getClassLoader());
                if (!GamePlugin.class.isAssignableFrom(type)) throw new IllegalArgumentException("Class does not implement GamePlugin");
                GamePlugin plugin = (GamePlugin) type.getConstructor().newInstance();
                if (plugin.getContractVersion() != GameContract.VERSION) throw new IllegalArgumentException("Unsupported contract version");
                GameDefinition definition = Objects.requireNonNull(plugin.getDefinition(context), "Null definition");
                if (!seen.add(definition.id)) {
                    duplicates.add(definition.id);
                    throw new IllegalArgumentException("Duplicate game ID: " + definition.id);
                }
                if (!key.equals(GamePlugin.METADATA_PREFIX + definition.id))
                    throw new IllegalArgumentException("Metadata suffix must equal game ID");
                ActivityInfo info = context.getPackageManager().getActivityInfo(
                        new ComponentName(context, definition.activity), 0);
                if (info.exported || !info.enabled || !info.applicationInfo.enabled
                        || !context.getPackageName().equals(info.packageName))
                    throw new IllegalArgumentException("Game Activity must be enabled, internal and exported=false");
                String hostProcess = context.getApplicationInfo().processName;
                if (hostProcess == null || hostProcess.isEmpty()) hostProcess = context.getPackageName();
                if (info.processName != null && !info.processName.equals(hostProcess))
                    throw new IllegalArgumentException("Game must use the host app process");
                accepted.add(definition);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError | PackageManager.NameNotFoundException ex) {
                String message = key + ": " + ex.getClass().getSimpleName() + " - " + ex.getMessage();
                errors.add(message); Log.e(TAG, message, ex);
            }
        }
        // Do not silently choose one implementation of a duplicate ID.
        for (Iterator<GameDefinition> it = accepted.iterator(); it.hasNext();) {
            if (duplicates.contains(it.next().id)) it.remove();
        }
        Collections.sort(accepted, (a, b) -> {
            int order = a.title.compareToIgnoreCase(b.title);
            return order == 0 ? a.id.compareTo(b.id) : order;
        });
        return new GameRegistry(accepted, errors);
    }
    public List<GameDefinition> all() { return games; }
    public List<String> errors() { return errors; }
    public GameDefinition find(String id) {
        for (GameDefinition g : games) if (g.id.equals(id)) return g;
        return null;
    }
}
