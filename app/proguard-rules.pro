# Keep app models
-keep class com.aether.app.models.** { *; }
-keep class com.aether.app.network.** { *; }
-keep class com.aether.app.data.** { *; }

# Gson
-keep class com.google.gson.** { *; }
-keepattributes Signature, *Annotation*
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { *; }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}

# OkHttp
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# AndroidX
-keep class androidx.** { *; }
-keep interface androidx.** { *; }

# Material
-keep class com.google.android.material.** { *; }

# ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Markwon
-keep class io.noties.markwon.** { *; }
-dontwarn io.noties.markwon.**

-dontwarn **
-dontnote **
-dontoptimize
# R8 full mode compatible
-keepattributes EnclosingMethod, InnerClasses, Signature
