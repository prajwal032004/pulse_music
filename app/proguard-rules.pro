# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep readable stack traces in crash reports.
-keepattributes SourceFile,LineNumberTable

# App components referenced from the manifest / by reflection.
-keep class com.example.widget.** { *; }
-keep class com.example.playback.PulseMediaSessionService { *; }

# Room entities and DAOs.
-keep class com.example.data.entity.** { *; }
-keep class com.example.data.dao.** { *; }

# Guava futures used by Media3 sessions.
-dontwarn com.google.common.**
-dontwarn org.checkerframework.**
-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**
