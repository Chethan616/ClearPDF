# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Optimize for performance
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification

# Compose already ships its own ProGuard rules via consumer-rules.
# Only keep what R8 can't infer automatically.
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# ML Kit Document Scanner
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_document_scanner.** { *; }
-dontwarn com.google.mlkit.**

# ── .docx viewer (DocxWebRenderer) ──
# These two live in `android.print` on purpose: the print framework's result callbacks have
# package-private constructors, and being in that package is the only way to subclass them and
# drive a PrintDocumentAdapter without the system print dialog. R8 renaming or repackaging them
# would move them out of `android.print` and the access check would fail at runtime — on release
# builds only, which is the worst way to find out. `-keep` pins both the name and the package.
-keep class android.print.OpenLayoutResultCallback { *; }
-keep class android.print.OpenWriteResultCallback { *; }

# The WebView bridge is only ever called from JavaScript, so nothing in the app references these
# methods and R8 would otherwise consider them unused.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
