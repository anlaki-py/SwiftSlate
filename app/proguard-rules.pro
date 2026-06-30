# SwiftSlate ProGuard Rules

# Keep Hilt generated components
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Keep the accessibility service (instantiated by Android framework via reflection)
-keep class com.musheer360.swiftslate.service.AssistantService { <init>(); }

# Keep enum values used in JSON serialization via CommandType.valueOf()
-keepclassmembers enum com.musheer360.swiftslate.model.CommandType {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Room entities
-keep class com.musheer360.swiftslate.data.local.** { *; }

# Keep kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.musheer360.swiftslate.data.remote.**$$serializer { *; }
-keepclassmembers class com.musheer360.swiftslate.data.remote.** {
    *** Companion;
}
-keepclasseswithmembers class com.musheer360.swiftslate.data.remote.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Retrofit interfaces
-keep,allowobfuscation interface com.musheer360.swiftslate.data.remote.ApiService

# Preserve line numbers for readable crash stack traces
-keepattributes SourceFile,LineNumberTable

# Remove debug logging in release
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
