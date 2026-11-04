# Methods called from JavaScript must keep their names.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.packflow.shell.ShellApp
-keep class com.packflow.shell.MainActivity
