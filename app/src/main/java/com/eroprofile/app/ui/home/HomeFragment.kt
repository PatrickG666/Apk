package com.eroprofile.app.ui.home

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.eroprofile.app.adapters.VideoAdapter
import com.eroprofile.app.data.models.Video
import com.eroprofile.app.databinding.FragmentHomeBinding
import com.eroprofile.app.ui.video.VideoPlayerActivity
import org.json.JSONArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var webView: WebView? = null
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var videoAdapter: VideoAdapter

    private val logFile = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "ep_debug.txt"
    )

    private fun log(msg: String) {
        val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        runCatching { logFile.appendText("[$ts][Home] $msg\n") }
    }

    private var currentSort = "date"
    private var pollAttempts = 0
    private val maxPollAttempts = 15
    private val pollIntervalMs = 2000L
    private val pollStartDelayMs = 3000L

    private val baseUrl = "https://www.eroprofile.com/m/videos/home"

    private val extractVideosJs = """
        (function() {
            try {
                var results = [];
                var seen = {};
                document.querySelectorAll('a[href]').forEach(function(a) {
                    var href = a.href || '';
                    if (!href.match(/\/video[s]?\/view|\/m\/video\/view|\/watch\//i)) return;
                    if (seen[href]) return;
                    seen[href] = true;
                    var img = a.querySelector('img');
                    if (!img) return;
                    var thumb = img.getAttribute('data-src') || img.getAttribute('data-lazy') || img.getAttribute('data-original') || img.src || '';
                    var title = a.getAttribute('title') || img.getAttribute('alt') || '';
                    if (!title) {
                        var p = a.parentElement;
                        if (p) { var t = p.querySelector('.title,.name,h3,h4'); if (t) title = t.textContent.trim(); }
                    }
                    if (!title || title.length < 2) return;
                    var dur = '';
                    var d = a.querySelector('[class*=dur],[class*=time],[class*=len]');
                    if (!d && a.parentElement) d = a.parentElement.querySelector('[class*=dur],[class*=time],[class*=len]');
                    if (d) dur = d.textContent.trim();
                    results.push({url:href, title:title, thumb:thumb, duration:dur});
                });
                return JSON.stringify(results);
            } catch(e) { return '[]'; }
        })();
    """.trimIndent()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupWebView()
        setupChips()
        setupSwipeRefresh()
        setupRetry()

        loadUrl(buildUrl(currentSort))
    }

    private fun setupRecyclerView() {
        videoAdapter = VideoAdapter { video ->
            val intent = Intent(requireContext(), VideoPlayerActivity::class.java).apply {
                putExtra(VideoPlayerActivity.EXTRA_URL, video.url)
                putExtra(VideoPlayerActivity.EXTRA_TITLE, video.title)
            }
            startActivity(intent)
        }
        binding.recyclerVideos.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = videoAdapter
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val wv = WebView(requireContext())
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36"
        }
        wv.webChromeClient = WebChromeClient()
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                log("onPageFinished: $url")
                view.evaluateJavascript(
                    "document.title + ' | links:' + document.querySelectorAll('a').length + ' | imgs:' + document.querySelectorAll('img').length"
                ) { info -> log("pageInfo: $info") }
                pollAttempts = 0
                handler.postDelayed({ schedulePoll() }, pollStartDelayMs)
            }
        }
        wv.addJavascriptInterface(object {
            @JavascriptInterface
            fun onVideos(json: String) {
                requireActivity().runOnUiThread { handleVideos(json) }
            }
        }, "Android")

        val lp = ConstraintLayout.LayoutParams(1, 1)
        binding.root.addView(wv, lp)
        webView = wv
    }

    private fun schedulePoll() {
        if (pollAttempts >= maxPollAttempts) {
            showError("Could not load videos after ${maxPollAttempts * pollIntervalMs / 1000}s")
            return
        }
        pollAttempts++
        webView?.evaluateJavascript(extractVideosJs) { raw ->
            val json = unescapeJs(raw)
            val count = try { JSONArray(json).length() } catch (_: Exception) { -1 }
            log("poll #$pollAttempts: count=$count raw=${raw?.take(200)}")
            if (count > 0) {
                requireActivity().runOnUiThread { handleVideos(json) }
            } else {
                handler.postDelayed({ schedulePoll() }, pollIntervalMs)
            }
        }
    }

    private fun unescapeJs(raw: String?): String {
        if (raw == null) return "[]"
        return if (raw.startsWith("\"") && raw.endsWith("\"")) {
            raw.removeSurrounding("\"")
                .replace("\\\"", "\"")
                .replace("\\/", "/")
                .replace("\\n", "")
        } else raw
    }

    private fun handleVideos(json: String) {
        try {
            val arr = JSONArray(json)
            val videos = mutableListOf<Video>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val url = o.optString("url", "")
                if (url.isEmpty()) continue
                val title = o.optString("title", "Video")
                val thumb = o.optString("thumb", "")
                val duration = o.optString("duration", "")
                videos.add(
                    Video(
                        id = url.hashCode().toString(),
                        title = title,
                        url = url,
                        thumbnailUrl = thumb,
                        duration = duration,
                        views = "",
                        rating = "",
                        author = "",
                        isHd = false
                    )
                )
            }
            log("handleVideos: parsed=${videos.size}")
            videos.take(3).forEachIndexed { i, v ->
                log("  video[$i] title=${v.title.take(30)} thumb=${v.thumbnailUrl}")
            }
            if (videos.isNotEmpty()) {
                binding.progressBar.visibility = View.GONE
                binding.errorView.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                videoAdapter.submitList(videos)
            }
        } catch (_: Exception) {
            // ignore parse errors, keep polling
        }
    }

    private fun showError(message: String) {
        binding.progressBar.visibility = View.GONE
        binding.swipeRefresh.isRefreshing = false
        binding.errorView.visibility = View.VISIBLE
        binding.errorMessage.text = message
    }

    private fun setupChips() {
        val chips = listOf(
            binding.chipRecent to "date",
            binding.chipPopular to "views",
            binding.chipTopRated to "rated"
        )
        chips.forEach { (chip, sort) ->
            chip.setOnClickListener {
                if (currentSort == sort) return@setOnClickListener
                currentSort = sort
                loadUrl(buildUrl(sort))
            }
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            loadUrl(buildUrl(currentSort))
        }
    }

    private fun setupRetry() {
        binding.btnRetry.setOnClickListener {
            loadUrl(buildUrl(currentSort))
        }
    }

    private fun buildUrl(sort: String): String =
        "$baseUrl?sort=$sort"

    private fun loadUrl(url: String) {
        handler.removeCallbacksAndMessages(null)
        pollAttempts = 0
        binding.progressBar.visibility = View.VISIBLE
        binding.errorView.visibility = View.GONE
        videoAdapter.submitList(emptyList())
        webView?.loadUrl(url)
    }

    override fun onDestroyView() {
        handler.removeCallbacksAndMessages(null)
        webView?.destroy()
        webView = null
        _binding = null
        super.onDestroyView()
    }
}
