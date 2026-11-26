# Stream resolution reaches these libraries by reflection or by name, so R8
# must not rename or drop them. They belong to :legato:source, which is the
# module that uses them, so the rules live with it.

-keep class org.schabi.newpipe.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.** { *; }
-keep class com.metrolist.innertubex.** { *; }
-keep class io.ktor.** { *; }
-keep class com.google.protobuf.** { *; }

# The PoToken WebView calls back into Kotlin by method name.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Optional pieces of these libraries that Android doesn't have.
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn javax.lang.model.**
-dontwarn org.slf4j.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn com.google.re2j.**
-dontwarn jdk.dynalink.**
-dontwarn java.lang.management.**
# NewPipe's Bandcamp extractors use jsoup, which the app no longer ships.
-dontwarn org.jsoup.**
