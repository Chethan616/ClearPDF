# ML Kit Text Recognition — keep classes touched via reflection.
-keep class com.google.mlkit.vision.text.** { *; }
-dontwarn com.google.mlkit.vision.text.**

# Tesseract4Android — JNI-bound native API, keep the whole surface.
-keep class com.googlecode.tesseract.android.** { *; }
-dontwarn com.googlecode.tesseract.android.**
