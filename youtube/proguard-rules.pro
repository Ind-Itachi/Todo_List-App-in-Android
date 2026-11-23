# Keep the JavaScript bridge methods called from the YouTube page.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
