package com.eroprofile.app.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import com.eroprofile.app.ui.video.VideoPlayerActivity

abstract class BaseWebFragment : Fragment() {

    protected var webView: WebView? = null
    private var progressBar: ProgressBar? = null

    abstract val initialUrl: String

    private val darkCss = """
        header, .site-header, nav.main-nav, .top-navigation,
        .ad, .ads, .advertisement, [class*=banner-ad],
        .cookie-notice, .popup, .modal-overlay,
        .install-app-banner, .download-app, #downloadBanner { display: none !important; }
        body, html { background: #1A1A1A !important; color: #E0E0E0 !important; }
        a { color: #FF6600 !important; }
    """.trimIndent().replace("\n", " ")

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = buildWebView()

    @SuppressLint("SetJavaScriptEnabled")
    protected fun buildWebView(): FrameLayout {
        val ctx = requireContext()

        val bar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, 6
            )
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(0xFFFF6600.toInt())
            visibility = View.GONE
        }
        progressBar = bar

        val wv = WebView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            settings.apply {
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
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                    val url = req.url.toString()
                    return when {
                        url.endsWith(".m4v") || url.endsWith(".mp4") || url.contains(".m3u8") -> {
                            openPlayer(url, ""); true
                        }
                        url.contains("/m/video/view") || url.contains("/video/view") -> {
                            openPlayer(url, ""); true
                        }
                        url.contains("eroprofile.com") -> false
                        else -> { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))); true }
                    }
                }
                override fun onPageFinished(view: WebView, url: String) = injectCss(view)
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, p: Int) {
                    bar.progress = p
                    bar.visibility = if (p < 100) View.VISIBLE else View.GONE
                }
            }
        }
        webView = wv

        wv.loadUrl(initialUrl)

        return FrameLayout(ctx).apply {
            setBackgroundColor(0xFF1A1A1A.toInt())
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(wv)
            addView(bar)
        }
    }

    private fun injectCss(view: WebView) {
        val js = """(function(){
            if(document.getElementById('_ep'))return;
            var s=document.createElement('style');
            s.id='_ep';
            s.innerHTML='${darkCss.replace("'","\\'")}';
            document.head&&document.head.appendChild(s);
        })();"""
        view.evaluateJavascript(js, null)
    }

    private fun openPlayer(url: String, title: String) {
        startActivity(Intent(requireContext(), VideoPlayerActivity::class.java).apply {
            putExtra(VideoPlayerActivity.EXTRA_URL, url)
            putExtra(VideoPlayerActivity.EXTRA_TITLE, title)
        })
    }

    fun loadUrl(url: String) = webView?.loadUrl(url)

    override fun onDestroyView() {
        webView?.destroy()
        webView = null
        super.onDestroyView()
    }
}
