package com.eroprofile.app.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
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
import androidx.fragment.app.Fragment
import com.eroprofile.app.ui.video.VideoPlayerActivity

abstract class BaseWebFragment : Fragment() {

    var webView: WebView? = null
    abstract val initialUrl: String

    // Injected before page JS runs to hide WebView fingerprint from Cloudflare
    private val antiDetectionJs = """
        (function() {
            // Hide webdriver flag (main Cloudflare trigger)
            Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
            // Fake plugins list
            Object.defineProperty(navigator, 'plugins', { get: () => [
                { name: 'Chrome PDF Plugin' }, { name: 'Chrome PDF Viewer' }, { name: 'Native Client' }
            ]});
            // Fake languages
            Object.defineProperty(navigator, 'languages', { get: () => ['it-IT', 'it', 'en-US', 'en'] });
            // Remove automation-related properties
            delete window.cdc_adoQpoasnfa76pfcZLmcfl_Array;
            delete window.cdc_adoQpoasnfa76pfcZLmcfl_Promise;
            delete window.cdc_adoQpoasnfa76pfcZLmcfl_Symbol;
        })();
    """.trimIndent()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val wv = WebView(requireContext())
        wv.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        wv.setBackgroundColor(Color.parseColor("#1A1A1A"))

        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            // User agent that matches a real Chrome on Android
            userAgentString = "Mozilla/5.0 (Linux; Android 13; SM-S918B) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.6099.144 Mobile Safari/537.36"
        }

        wv.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                // Inject anti-detection JS as early as possible
                view.evaluateJavascript(antiDetectionJs, null)
            }

            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                val url = req.url.toString()
                // Only intercept actual video files → VideoPlayerActivity
                // Let EVERYTHING else (including Cloudflare challenge redirects) load in the WebView
                if (url.contains(".m4v") || url.contains(".mp4") || url.contains(".m3u8") ||
                    url.contains("/m/video/view") || url.contains("/video/view")) {
                    startActivity(Intent(requireContext(), VideoPlayerActivity::class.java).apply {
                        putExtra(VideoPlayerActivity.EXTRA_URL, url)
                        putExtra(VideoPlayerActivity.EXTRA_TITLE, "")
                    })
                    return true
                }
                return false  // let WebView handle all other URLs (Cloudflare, redirects, etc.)
            }

            override fun onPageFinished(view: WebView, url: String) {
                // Re-inject after load + apply dark theme
                view.evaluateJavascript(antiDetectionJs, null)
                view.evaluateJavascript("""
                    (function(){
                        if(document.getElementById('_ep'))return;
                        var s=document.createElement('style');
                        s.id='_ep';
                        s.innerHTML='body,html{background:#1A1A1A!important;color:#E0E0E0!important}' +
                            '.ad,.ads,.cookie-notice,.popup,.install-app-banner,.download-app,' +
                            'nav,.navbar,.site-nav,#site-header,.site-header{display:none!important}' +
                            'a{color:#FF6600!important}';
                        document.head&&document.head.appendChild(s);
                    })();
                """.trimIndent(), null)
            }
        }

        // Required for JS alerts/confirms used by Cloudflare challenge
        wv.webChromeClient = WebChromeClient()

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
