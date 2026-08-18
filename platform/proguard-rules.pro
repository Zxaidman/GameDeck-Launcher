# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep Shizuku reflection-based access
-keep class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**