# Grandma's Launcher — ProGuard rules
# Phase 1 has minifyEnabled = false so this file is not active yet.
# Kept for future use.
-keepattributes *Annotation*
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
