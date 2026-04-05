package com.eroprofile.app.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
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

    abstract val initialUrl: String

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = buildWebView()

    @SuppressLint("SetJavaScriptEnabled")
    fun buildWebView(): FrameLayout {
        val ctx = requireContext()

        // Progress bar — plain horizontal, no custom style
        val progressBar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).also {
            it.max = 100
            it.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, 8
            )
            it.visibility = View.GONE
        }

        // WebView
        val wv = WebView(ctx).also {
            it.setBackgroundColor(Color.parseColor("#1A1A1A"))
            it.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36"
        }

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                val url = req.url.toString()
                return when {
                    url.contains(".m4v") || url.contains(".mp4") || url.contains(".m3u8") -> {
                        openPlayer(url); true
                    }
                    url.contains("/m/video/view") || url.contains("/video/view") -> {
                        openPlayer(url); true
                    }
                    url.contains("eroprofile.com") -> false
                    else -> {
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        true
                    }
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                injectDarkTheme(view)
            }
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
            }
        }

        webView = wv
        wv.loadUrl(initialUrl)

        return FrameLayout(ctx).apply {
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(wv)
            addView(progressBar)
        }
    }

    private fun injectDarkTheme(view: WebView) {
        view.evaluateJavascript("""
            (function(){
                if(document.getElementById('_ep'))return;
                var s=document.createElement('style');
                s.id='_ep';
                s.innerHTML='body,html{background:#1A1A1A!important;color:#E0E0E0!important}a{color:#FF6600!important}header,.ad,.ads,.cookie-notice,.popup,.install-app-banner{display:none!important}';
                document.head&&document.head.appendChild(s);
            })();
        """.trimIndent(), null)
    }

    private fun openPlayer(url: String) {
        startActivity(Intent(requireContext(), VideoPlayerActivity::class.java).apply {
            putExtra(VideoPlayerActivity.EXTRA_URL, url)
            putExtra(VideoPlayerActivity.EXTRA_TITLE, "")
        })
    }

    fun loadUrl(url: String) = webView?.loadUrl(url)

    override fun onDestroyView() {
        webView?.destroy()
        webView = null
        super.onDestroyView()
    }
}
