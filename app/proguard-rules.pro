# yt-dlp
-keep class com.yausername.youtubedl_android.** { *; }
-keep class org.python.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Gson
-keepattributes Signature
-keepattributes *Annotation*
