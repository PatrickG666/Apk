package com.eroprofile.app.ui.video

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.eroprofile.app.databinding.ActivityVideoPlayerBinding

class VideoPlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
    }

    private lateinit var binding: ActivityVideoPlayerBinding

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val url = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""

        binding.tvTitle.text = title

        setupWebView(url)

        binding.btnBack.setOnClickListener { finish() }

        binding.btnOpenBrowser.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(url: String) {
        binding.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36"
        }

        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val reqUrl = request.url.toString()
                // Keep navigation within eroprofile
                return if (reqUrl.contains("eroprofile.com")) {
                    false
                } else {
                    // Open external URLs in system browser
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(reqUrl)))
                    true
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.progressBar.visibility = View.GONE
                // Inject CSS to hide ads/headers and match dark theme
                injectDarkThemeCss()
            }
        }

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.progressBar.progress = newProgress
                binding.progressBar.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
            }
        }

        binding.webView.loadUrl(url)
    }

    private fun injectDarkThemeCss() {
        val css = """
            /* Hide navigation bars, ads, banners */
            header, .site-header, nav.main-nav, .ad, .ads, .advertisement,
            .banner, .sidebar, footer, .footer, .cookie-notice,
            .popup, .modal-overlay, .top-bar { display: none !important; }

            /* Dark background */
            body { background-color: #1A1A1A !important; color: #FFFFFF !important; }

            /* Video container full width */
            .video-player, #player, .video-wrap, .video-container {
                width: 100% !important;
                max-width: 100% !important;
            }
        """.trimIndent().replace("\n", " ")

        val js = """
            (function() {
                var style = document.createElement('style');
                style.type = 'text/css';
                style.innerHTML = '$css';
                document.head.appendChild(style);
            })();
        """.trimIndent()

        binding.webView.evaluateJavascript(js, null)
    }

    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        binding.webView.destroy()
        super.onDestroy()
    }
}
