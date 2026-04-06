package com.eroprofile.app

import android.content.Context
import android.webkit.CookieManager
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.AppGlideModule
import okhttp3.OkHttpClient
import java.io.InputStream

@GlideModule
class EroProfileGlideModule : AppGlideModule() {

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                // Attach WebView cookies + Referer so CDN serves thumbnails
                val cookies = runCatching {
                    CookieManager.getInstance().getCookie("https://www.eroprofile.com") ?: ""
                }.getOrDefault("")
                val modified = req.newBuilder()
                    .header("Referer", "https://www.eroprofile.com/")
                    .header("Origin", "https://www.eroprofile.com")
                    .apply { if (cookies.isNotEmpty()) header("Cookie", cookies) }
                    .build()
                chain.proceed(modified)
            }
            .build()

        registry.replace(
            GlideUrl::class.java,
            InputStream::class.java,
            OkHttpUrlLoader.Factory(client)
        )
    }
}
