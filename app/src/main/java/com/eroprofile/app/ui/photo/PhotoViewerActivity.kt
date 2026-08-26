package com.eroprofile.app.ui.photo

import android.os.Bundle
import android.view.MotionEvent
import android.view.ScaleGestureDetector
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
import kotlin.math.abs

class PhotoViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_THUMB = "extra_thumb"
    }

    private lateinit var binding: ActivityPhotoViewerBinding
    private var scraperWebView: WebView? = null

    // Zoom / pan state
    private var currentScale = 1f
    private var panX = 0f
    private var panY = 0f
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var lastPanX = 0f
    private var lastPanY = 0f
    private var isScaling = false
    private lateinit var scaleDetector: ScaleGestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val pageUrl = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }
        val thumbUrl = intent.getStringExtra(EXTRA_THUMB) ?: ""
        binding.tvTitle.text = intent.getStringExtra(EXTRA_TITLE) ?: ""
        binding.btnBack.setOnClickListener { finish() }

        setupZoom()
        loadPhoto(thumbUrl, pageUrl)
    }

    private fun setupZoom() {
        scaleDetector = ScaleGestureDetector(this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    isScaling = true
                    return true
                }
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    currentScale = (currentScale * detector.scaleFactor).coerceIn(1f, 5f)
                    applyTransform()
                    return true
                }
                override fun onScaleEnd(detector: ScaleGestureDetector) {
                    if (currentScale <= 1f) resetTransform()
                    isScaling = false
                }
            })

        binding.imgPhoto.setOnTouchListener { v, event ->
            scaleDetector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    lastPanX = event.rawX
                    lastPanY = event.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!isScaling && event.pointerCount == 1 && currentScale > 1f) {
                        panX += event.rawX - lastPanX
                        panY += event.rawY - lastPanY
                        applyTransform()
                    }
                    lastPanX = event.rawX
                    lastPanY = event.rawY
                }
                MotionEvent.ACTION_UP -> {
                    val moved = abs(event.rawX - touchStartX) + abs(event.rawY - touchStartY)
                    if (!isScaling && moved < 20f) {
                        // Single tap → toggle top bar
                        binding.topBar.visibility =
                            if (binding.topBar.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                    }
                    if (currentScale <= 1f) resetTransform()
                }
            }
            true
        }
    }

    private fun applyTransform() {
        binding.imgPhoto.scaleX = currentScale
        binding.imgPhoto.scaleY = currentScale
        binding.imgPhoto.translationX = panX
        binding.imgPhoto.translationY = panY
    }

    private fun resetTransform() {
        currentScale = 1f
        panX = 0f
        panY = 0f
        binding.imgPhoto.scaleX = 1f
        binding.imgPhoto.scaleY = 1f
        binding.imgPhoto.translationX = 0f
        binding.imgPhoto.translationY = 0f
    }

    private fun loadPhoto(thumbUrl: String, pageUrl: String) {
        val cookies = runCatching {
            CookieManager.getInstance().getCookie("https://www.eroprofile.com") ?: ""
        }.getOrDefault("")

        val headers = LazyHeaders.Builder()
            .addHeader("Referer", "https://www.eroprofile.com/")
            .apply { if (cookies.isNotEmpty()) addHeader("Cookie", cookies) }
            .build()

        fun load(url: String) {
            if (url.isEmpty()) { binding.progressBar.visibility = View.GONE; return }
            Glide.with(this)
                .load(GlideUrl(url, headers))
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .fitCenter()
                .listener(object : RequestListener<android.graphics.drawable.Drawable> {
                    override fun onLoadFailed(e: GlideException?, model: Any?,
                        target: Target<android.graphics.drawable.Drawable>, isFirst: Boolean): Boolean {
                        binding.progressBar.visibility = View.GONE; return false
                    }
                    override fun onResourceReady(res: android.graphics.drawable.Drawable, model: Any,
                        target: Target<android.graphics.drawable.Drawable>?, ds: DataSource, isFirst: Boolean): Boolean {
                        binding.progressBar.visibility = View.GONE; return false
                    }
                })
                .into(binding.imgPhoto)
        }

        if (thumbUrl.isNotEmpty()) load(thumbUrl)

        scrapeFullSize(pageUrl, headers) { fullUrl ->
            if (fullUrl.isNotEmpty() && fullUrl != thumbUrl) runOnUiThread { load(fullUrl) }
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
                        var sel = ['.photo-content img','.main-photo img','.photo-view img',
                            '[class*=photo] img[src*=cdn]','img[src*="/photos/"]','img[src*="eroprofile"]'];
                        for (var i=0;i<sel.length;i++){
                            var el=document.querySelector(sel[i]);
                            if(el){var s=el.getAttribute('data-src')||el.src;if(s&&!s.endsWith('.gif'))return s;}
                        }
                        var imgs=Array.from(document.querySelectorAll('img[src]'))
                            .filter(function(i){return !i.src.endsWith('.gif')&&i.src.includes('eroprofile');})
                            .sort(function(a,b){return(parseInt(b.getAttribute('width'))||0)-(parseInt(a.getAttribute('width'))||0);});
                        return imgs.length>0?imgs[0].src:'';
                    })();
                """.trimIndent()
                view.evaluateJavascript(js) { raw ->
                    val found = raw?.trim()?.removeSurrounding("\"")?.replace("\\/", "/") ?: ""
                    if (found.isNotEmpty() && found != "null") onFound(found)
                    scraperWebView?.destroy(); scraperWebView = null
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
