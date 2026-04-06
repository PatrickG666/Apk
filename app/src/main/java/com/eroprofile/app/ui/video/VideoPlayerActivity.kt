package com.eroprofile.app.ui.video

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.eroprofile.app.databinding.ActivityVideoPlayerBinding
import java.io.ByteArrayInputStream

class VideoPlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
    }

    private lateinit var binding: ActivityVideoPlayerBinding
    private var player: ExoPlayer? = null
    private var scraperWebView: WebView? = null
    private var streamFound = false
    private var pageUrl = ""

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

        setupPlayer()
        setupScraperWebView()
    }

    private fun setupPlayer() {
        player = ExoPlayer.Builder(this).build()
        binding.playerView.player = player
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
                    // Block WebView from downloading the video (save bandwidth)
                    return WebResourceResponse("text/plain", "UTF-8",
                        ByteArrayInputStream(ByteArray(0)))
                }
                return null
            }
        }
        // Attach to window (1×1px) so it can make network requests
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
        player?.apply {
            setMediaItem(MediaItem.fromUri(streamUrl))
            prepare()
            playWhenReady = true
        }
    }

    private fun showError() {
        binding.loadingView.visibility = View.GONE
        binding.errorView.visibility = View.VISIBLE
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        scraperWebView?.destroy()
        scraperWebView = null
        super.onDestroy()
    }
}
