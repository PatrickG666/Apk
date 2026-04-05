package com.eroprofile.app.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import com.eroprofile.app.ui.video.VideoPlayerActivity

abstract class BaseWebFragment : Fragment() {

    var webView: WebView? = null
    abstract val initialUrl: String

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val wv = WebView(requireContext())
        wv.setBackgroundColor(Color.BLACK)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36"
        }
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                val url = req.url.toString()
                return when {
                    url.contains(".m4v") || url.contains(".mp4") || url.contains(".m3u8") ||
                    url.contains("/m/video/view") || url.contains("/video/view") -> {
                        startActivity(Intent(requireContext(), VideoPlayerActivity::class.java).apply {
                            putExtra(VideoPlayerActivity.EXTRA_URL, url)
                            putExtra(VideoPlayerActivity.EXTRA_TITLE, "")
                        })
                        true
                    }
                    url.contains("eroprofile.com") -> false
                    else -> { runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }; true }
                }
            }
        }
        webView = wv
        wv.loadUrl(initialUrl)
        return wv
    }

    fun loadUrl(url: String) = webView?.loadUrl(url)

    override fun onDestroyView() {
        webView?.destroy()
        webView = null
        super.onDestroyView()
    }
}
