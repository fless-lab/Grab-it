# Disable obfuscation (Python/Chaquopy runtime loads classes by name via reflection)
# Shrinking (unused code removal) still active
-dontobfuscate

# yt-dlp + Python runtime (Chaquopy)
-keep class com.yausername.youtubedl_android.** { *; }
-keep class org.python.** { *; }
-keep class com.chaquo.** { *; }
-dontwarn com.chaquo.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep class com.raouf.grabit.data.db.** { *; }
-dontwarn androidx.room.paging.**

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# DataStore
-keep class androidx.datastore.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { *; }

# Coil
-keep class coil.** { *; }

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# App models (used by Room/Gson)
-keep class com.raouf.grabit.domain.model.** { *; }

# Updater models (used by Gson reflection)
-keep class com.raouf.grabit.data.updater.** { *; }

# Keep Kotlin metadata for reflection
-keepattributes RuntimeVisibleAnnotations
-keep class kotlin.Metadata { *; }

# Prevent stripping of Compose
-dontwarn androidx.compose.**

# Compose Foundation (combinedClickable etc.)
-keep class androidx.compose.foundation.** { *; }
-dontwarn androidx.compose.foundation.**

# FFmpeg
-keep class com.yausername.ffmpeg.** { *; }

# Keep entire app package (prevent reflection/init surprises)
-keep class com.raouf.grabit.** { *; }
