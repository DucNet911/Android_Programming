# Plugins are loaded by their manifest names. Preserve them if a future release uses R8.
-keep class * implements com.example.arcade.core.GamePlugin { public *; }
