# Keep kotlinx.serialization generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class com.mesha.mobile.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.mesha.mobile.**$$serializer { *; }

# Retrofit / OkHttp
-keepattributes Signature, Exceptions
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

# MediaPipe GenAI (Gemma local inference)
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**
