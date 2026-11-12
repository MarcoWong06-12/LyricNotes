# LyricNotes Release R8 / ProGuard Rules

# Preserve line numbers and source filenames for readable crash stacktraces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Preserve Kotlin metadata & annotations
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep Manifest-declared Android components and Application class
-keep public class com.linernotes.app.LinerNotesApp
-keep public class com.linernotes.app.MainActivity
-keep public class com.linernotes.app.core.floating.FloatingLyricsService
-keep public class com.linernotes.app.core.floating.FloatingLyricsTileService
-keep public class com.linernotes.app.core.playback.MediaPlaybackSyncService
-keep public class com.linernotes.app.core.playback.SpotifyBroadcastReceiver
-keep public class com.linernotes.app.core.translation.BatchTranslationService
-keep public class com.linernotes.app.core.media.CompanionPlaybackService

# Keep Hilt EntryPoints and generated components
-keep @dagger.hilt.EntryPoint interface * { *; }
-keep @dagger.hilt.InstallIn interface * { *; }
-keep class com.linernotes.app.core.preference.AiPreferencesEntryPoint { *; }

# Keep Room Entities and DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# Keep Domain Models used in collections/persistence/debugging
-keep class com.linernotes.app.domain.model.** { *; }
-keep class com.linernotes.app.core.playback.TrackPlaybackState { *; }
-keep class com.linernotes.app.core.playback.PlaybackClockState { *; }
-keep class com.linernotes.app.core.playback.TrackMetadataState { *; }

# OkHttp / Conscrypt / Coroutines optional platform warnings
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn retrofit2.**

# Keep Kotlin Serialization if pulled by transitive artifacts
-keepattributes RuntimeVisibleAnnotations
-keepclassmembers class * {
    @kotlinx.serialization.* <fields>;
    @kotlinx.serialization.* <methods>;
}

# Strip debug logs in Release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
