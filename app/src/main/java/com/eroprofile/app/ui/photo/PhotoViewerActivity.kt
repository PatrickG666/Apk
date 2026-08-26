package com.eroprofile.app.ui.photo

import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.eroprofile.app.databinding.ActivityPhotoViewerBinding

class PhotoViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_THUMB = "extra_thumb"
    }

    private lateinit var binding: ActivityPhotoViewerBinding
    private var scraperWebView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val pageUrl = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }
        val thumbUrl = intent.getStringExtra(EXTRA_THUMB) ?: ""
        binding.tvTitle.text = intent.getStringExtra(EXTRA_TITLE) ?: ""
        binding.btnBack.setOnClickListener { finish() }

        binding.imgPhoto.setOnClickListener {
            binding.topBar.visibility =
                if (binding.topBar.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        loadPhoto(thumbUrl, pageUrl)
    }

    private fun loadPhoto(thumbUrl: String, pageUrl: String) {
        val cookies = runCatching {
            CookieManager.getInstance().getCookie("https://www.eroprofile.com") ?: ""
        }.getOrDefault("")

        val headers = LazyHeaders.Builder()
            .addHeader("Referer", "https://www.eroprofile.com/")
            .apply { if (cookies.isNotEmpty()) addHeader("Cookie", cookies) }
            .build()

        fun loadUrl(url: String) {
            if (url.isEmpty()) {
                binding.progressBar.visibility = View.GONE
                return
            }
            Glide.with(this)
                .load(GlideUrl(url, headers))
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .fitCenter()
                .listener(object : RequestListener<android.graphics.drawable.Drawable> {
                    override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<android.graphics.drawable.Drawable>, isFirst: Boolean): Boolean {
                        binding.progressBar.visibility = View.GONE
                        return false
                    }
                    override fun onResourceReady(res: android.graphics.drawable.Drawable, model: Any, target: Target<android.graphics.drawable.Drawable>?, ds: DataSource, isFirst: Boolean): Boolean {
                        binding.progressBar.visibility = View.GONE
                        return false
                    }
                })
                .into(binding.imgPhoto)
        }

        if (thumbUrl.isNotEmpty()) {
            loadUrl(thumbUrl)
        }

        // Scrape full-size image in background and upgrade if found
        scrapeFullSize(pageUrl, headers) { fullUrl ->
            if (fullUrl.isNotEmpty() && fullUrl != thumbUrl) {
                runOnUiThread { loadUrl(fullUrl) }
            }
        }
    }

    private fun scrapeFullSize(pageUrl: String, headers: LazyHeaders, onFound: (String) -> Unit) {
        val wv = WebView(this)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = "Mozilla/5.0 (Linux; Android 13; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.144 Mobile Safari/537.36"
            loadsImagesAutomatically = false
            blockNetworkImage = true
        }
        wv.webChromeClient = WebChromeClient()
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                val js = """
                    (function() {
                        var sel = [
                            '.photo-content img', '.main-photo img', '.photo-view img',
                            '[class*=photo] img[src*=cdn]', 'img[src*="/photos/"]',
                            'img[src*="eroprofile"]'
                        ];
                        for (var i = 0; i < sel.length; i++) {
                            var el = document.querySelector(sel[i]);
                            if (el) { var s = el.getAttribute('data-src') || el.src; if (s && !s.endsWith('.gif')) return s; }
                        }
                        var imgs = Array.from(document.querySelectorAll('img[src]'))
                            .filter(function(i){ return !i.src.endsWith('.gif') && i.src.includes('eroprofile'); })
                            .sort(function(a,b){ return (parseInt(b.getAttribute('width'))||0) - (parseInt(a.getAttribute('width'))||0); });
                        return imgs.length > 0 ? imgs[0].src : '';
                    })();
                """.trimIndent()
                view.evaluateJavascript(js) { raw ->
                    val url = raw?.trim()?.removeSurrounding("\"")?.replace("\\/", "/") ?: ""
                    if (url.isNotEmpty() && url != "null") onFound(url)
                    scraperWebView?.destroy()
                    scraperWebView = null
                }
            }
        }
        binding.root.addView(wv, android.widget.FrameLayout.LayoutParams(1, 1))
        scraperWebView = wv
        wv.loadUrl(pageUrl)
    }

    override fun onDestroy() {
        scraperWebView?.destroy()
        scraperWebView = null
        super.onDestroy()
    }
}
