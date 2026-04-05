package com.eroprofile.app.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import com.eroprofile.app.R
import com.eroprofile.app.ui.video.VideoPlayerActivity

abstract class BaseWebFragment : Fragment() {

    protected var webView: WebView? = null
    private var progressBar: ProgressBar? = null

    abstract val initialUrl: String

    // Dark CSS injected after page load
    private val darkCss = """
        header, .site-header, nav.main-nav, .top-navigation,
        .ad, .ads, .advertisement, .banner-ad, [id*=ad], [class*=banner-ad],
        .cookie-notice, .popup, .modal-overlay, .install-app-banner,
        .download-app, #downloadBanner { display: none !important; }
        body, html { background-color: #1A1A1A !important; color: #E0E0E0 !important; }
        a { color: #FF6600 !important; }
        .video-list, .list-videos, ul, li { background: #1A1A1A !important; }
    """.trimIndent().replace("\n", " ")

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_webview, container, false)
        webView = root.findViewById<WebView>(R.id.webView)
        progressBar = root.findViewById<ProgressBar>(R.id.progressBar)

        webView?.settings?.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36"
        }

        webView?.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                return when {
                    // Direct video file → ExoPlayer
                    url.contains(".m4v") || url.contains(".mp4") || url.contains(".m3u8") -> {
                        openVideoPlayer(url, "")
                        true
                    }
                    // Video page → VideoPlayerActivity (loads in its own WebView)
                    url.contains("/m/video/view") || url.contains("/video/view") -> {
                        openVideoPlayer(url, "")
                        true
                    }
                    // Keep eroprofile navigation inside this WebView
                    url.contains("eroprofile.com") -> false
                    // External links → system browser
                    else -> {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        true
                    }
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                injectCss(view)
            }
        }

        webView?.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progressBar?.progress = newProgress
                progressBar?.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
            }
        }

        webView?.loadUrl(initialUrl)
        return root
    }

    private fun injectCss(view: WebView) {
        val js = """
            (function() {
                if (document.getElementById('ep_dark')) return;
                var s = document.createElement('style');
                s.id = 'ep_dark';
                s.innerHTML = '${darkCss.replace("'", "\\'")}';
                document.head && document.head.appendChild(s);
            })();
        """.trimIndent()
        view.evaluateJavascript(js, null)
    }

    private fun openVideoPlayer(url: String, title: String) {
        startActivity(
            Intent(requireContext(), VideoPlayerActivity::class.java).apply {
                putExtra(VideoPlayerActivity.EXTRA_URL, url)
                putExtra(VideoPlayerActivity.EXTRA_TITLE, title)
            }
        )
    }

    fun loadUrl(url: String) {
        webView?.loadUrl(url)
    }

    override fun onDestroyView() {
        webView?.destroy()
        webView = null
        super.onDestroyView()
    }
}
