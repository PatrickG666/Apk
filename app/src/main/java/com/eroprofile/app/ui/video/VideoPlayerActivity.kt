package com.eroprofile.app.ui.video

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.MediaController
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.eroprofile.app.databinding.ActivityVideoPlayerBinding
import java.io.ByteArrayInputStream

class VideoPlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
    }

    private lateinit var binding: ActivityVideoPlayerBinding
    private var scraperWebView: WebView? = null
    private var streamFound = false
    private var pageUrl = ""
    private var videoWidth = 0
    private var videoHeight = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pageUrl = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }
        binding.tvTitle.text = intent.getStringExtra(EXTRA_TITLE) ?: ""

        binding.btnBack.setOnClickListener { finish() }
        binding.btnOpenBrowser.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(pageUrl)))
        }

        setupVideoView()
        setupScraperWebView()
    }

    private fun setupVideoView() {
        val mc = MediaController(this)
        mc.setAnchorView(binding.videoView)
        binding.videoView.setMediaController(mc)

        binding.videoView.setOnErrorListener { _, _, _ ->
            binding.loadingView.visibility = View.GONE
            binding.errorView.visibility = View.VISIBLE
            true
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupScraperWebView() {
        val wv = WebView(this)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            userAgentString = "Mozilla/5.0 (Linux; Android 13; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.144 Mobile Safari/537.36"
        }
        wv.webChromeClient = WebChromeClient()
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest) = false

            override fun shouldInterceptRequest(
                view: WebView, request: WebResourceRequest
            ): WebResourceResponse? {
                val url = request.url.toString()
                if (!streamFound && isVideoStream(url)) {
                    streamFound = true
                    runOnUiThread { playStream(url) }
                    return WebResourceResponse("text/plain", "UTF-8",
                        ByteArrayInputStream(ByteArray(0)))
                }
                return null
            }
        }
        binding.root.addView(wv, android.view.ViewGroup.LayoutParams(1, 1))
        scraperWebView = wv
        wv.loadUrl(pageUrl)
    }

    private fun isVideoStream(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains(".mp4") || lower.contains(".m4v") ||
               lower.contains(".m3u8") || lower.contains(".webm") ||
               lower.contains(".flv")
    }

    private fun playStream(streamUrl: String) {
        binding.loadingView.visibility = View.GONE
        binding.videoView.apply {
            setVideoURI(Uri.parse(streamUrl))
            setOnPreparedListener { mp ->
                mp.setOnVideoSizeChangedListener { _, w, h ->
                    if (w > 0 && h > 0) {
                        videoWidth = w
                        videoHeight = h
                        updateVideoLayout()
                    }
                }
                mp.setOnInfoListener { _, what, _ ->
                    if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                        binding.loadingView.visibility = View.GONE
                    }
                    false
                }
                start()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateVideoLayout()
    }

    private fun updateVideoLayout() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val cs = ConstraintSet()
        cs.clone(binding.root)
        if (isLandscape) {
            cs.constrainHeight(binding.videoView.id, 0)
            cs.connect(binding.videoView.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        } else {
            cs.clear(binding.videoView.id, ConstraintSet.BOTTOM)
            val h = if (videoWidth > 0 && videoHeight > 0) {
                val screenW = resources.displayMetrics.widthPixels
                (screenW.toFloat() * videoHeight / videoWidth).toInt()
            } else (200 * resources.displayMetrics.density).toInt()
            cs.constrainHeight(binding.videoView.id, h)
        }
        cs.applyTo(binding.root)
    }

    override fun onPause() {
        super.onPause()
        if (binding.videoView.isPlaying) binding.videoView.pause()
    }

    override fun onResume() {
        super.onResume()
        binding.videoView.resume()
    }

    override fun onDestroy() {
        binding.videoView.stopPlayback()
        scraperWebView?.destroy()
        scraperWebView = null
        super.onDestroy()
    }
}
