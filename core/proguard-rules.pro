# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep kotlinx.serialization generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.gamedeck.core.**$$serializer { *; }
-keepclassmembers class com.gamedeck.core.** {
    *** Companion;
}
-keepclasseswithmembers class com.gamedeck.core.** {
    kotlinx.serialization.KSerializer serializer(...);
}