# Add project specific ProGuard rules here.

# Keep JSoup
-keep class org.jsoup.** { *; }

# Keep OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }

# Keep Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.AppGlideModule { *; }
-keep class com.bumptech.glide.GeneratedAppGlideModuleImpl
