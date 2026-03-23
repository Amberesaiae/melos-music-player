# Melos Music Player - ProGuard / R8 rules

# Keep Hilt-generated components
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ActivityComponentManager { *; }

# Keep Retrofit interfaces
-keepattributes Signature
-keepattributes *Annotation*
-keep interface com.amberesaiae.melos.core.network.** { *; }
-keep class retrofit2.** { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Keep Room entities and DAOs
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class **$$serializer { *; }

# Media3
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Coil
-dontwarn coil.**

# JAudioTagger
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**

# Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
