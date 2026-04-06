package com.eroprofile.app.ui.categories

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
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
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.eroprofile.app.adapters.LoadingFooterAdapter
import com.eroprofile.app.adapters.VideoAdapter
import com.eroprofile.app.data.models.Video
import com.eroprofile.app.databinding.FragmentHomeBinding
import com.eroprofile.app.ui.video.VideoPlayerActivity
import org.json.JSONArray

class CategoryVideosFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var webView: WebView? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var videoAdapter: VideoAdapter
    private lateinit var footerAdapter: LoadingFooterAdapter

    // Pagination state
    private var currentPage = 1
    private var isLoadingMore = false
    private var hasMorePages = true
    private val allVideos = mutableListOf<Video>()

    private var pollAttempts = 0
    private val maxPollAttempts = 15

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

        binding.sortChipsScroll.visibility = View.GONE

        videoAdapter = VideoAdapter { video ->
            startActivity(Intent(requireContext(), VideoPlayerActivity::class.java).apply {
                putExtra(VideoPlayerActivity.EXTRA_URL, video.url)
                putExtra(VideoPlayerActivity.EXTRA_TITLE, video.title)
            })
        }
        footerAdapter = LoadingFooterAdapter()

        val gridLayout = GridLayoutManager(requireContext(), 2)
        gridLayout.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) =
                if (position >= videoAdapter.itemCount) 2 else 1
        }

        binding.recyclerVideos.apply {
            layoutManager = gridLayout
            adapter = ConcatAdapter(videoAdapter, footerAdapter)
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy <= 0) return
                    val lastVisible = gridLayout.findLastVisibleItemPosition()
                    val total = gridLayout.itemCount
                    if (lastVisible >= total - 4 && !isLoadingMore && hasMorePages) {
                        loadNextPage()
                    }
                }
            })
        }

        binding.swipeRefresh.setOnRefreshListener { loadPage(1) }
        binding.btnRetry.setOnClickListener { loadPage(1) }

        setupWebView()

        val categoryUrl = arguments?.getString("url") ?: ""
        if (categoryUrl.isNotEmpty()) loadPage(1)
        else showError("URL categoria mancante")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val wv = WebView(requireContext())
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = "Mozilla/5.0 (Linux; Android 13; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.144 Mobile Safari/537.36"
        }
        wv.webChromeClient = WebChromeClient()
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                pollAttempts = 0
                handler.postDelayed({ schedulePoll() }, 3000)
            }
        }
        wv.addJavascriptInterface(object {
            @JavascriptInterface
            fun onVideos(json: String) {
                requireActivity().runOnUiThread { handleVideos(json) }
            }
        }, "Android")
        binding.root.addView(wv, ConstraintLayout.LayoutParams(1, 1))
        webView = wv
    }

    private fun schedulePoll() {
        if (pollAttempts >= maxPollAttempts) {
            if (currentPage == 1) showError("Nessun video trovato")
            else {
                hasMorePages = false
                isLoadingMore = false
                footerAdapter.hide()
            }
            return
        }
        pollAttempts++
        webView?.evaluateJavascript(extractVideosJs) { raw ->
            val json = unescapeJs(raw)
            val count = try { JSONArray(json).length() } catch (_: Exception) { -1 }
            if (count > 0) requireActivity().runOnUiThread { handleVideos(json) }
            else handler.postDelayed({ schedulePoll() }, 2000)
        }
    }

    private fun unescapeJs(raw: String?): String {
        if (raw == null) return "[]"
        return if (raw.startsWith("\"") && raw.endsWith("\"")) {
            raw.removeSurrounding("\"").replace("\\\"", "\"").replace("\\/", "/").replace("\\n", "")
        } else raw
    }

    private fun handleVideos(json: String) {
        try {
            val arr = JSONArray(json)
            val existingUrls = allVideos.map { it.url }.toSet()
            val newVideos = (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val url = o.optString("url")
                if (url.isEmpty() || url in existingUrls) null
                else Video(
                    id = url.hashCode().toString(),
                    title = o.optString("title", "Video"),
                    url = url,
                    thumbnailUrl = o.optString("thumb"),
                    duration = o.optString("duration")
                )
            }

            if (newVideos.isEmpty() && currentPage > 1) {
                hasMorePages = false
                isLoadingMore = false
                footerAdapter.hide()
                return
            }

            if (newVideos.isNotEmpty()) {
                allVideos.addAll(newVideos)
                binding.progressBar.visibility = View.GONE
                binding.errorView.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                videoAdapter.submitList(allVideos.toList())
                footerAdapter.hide()
                isLoadingMore = false
            }
        } catch (_: Exception) {
            isLoadingMore = false
            footerAdapter.hide()
        }
    }

    private fun loadNextPage() {
        isLoadingMore = true
        currentPage++
        footerAdapter.show()
        handler.removeCallbacksAndMessages(null)
        pollAttempts = 0
        webView?.loadUrl(buildPageUrl(currentPage))
    }

    private fun loadPage(page: Int) {
        handler.removeCallbacksAndMessages(null)
        currentPage = page
        pollAttempts = 0
        isLoadingMore = false
        hasMorePages = true
        if (page == 1) {
            allVideos.clear()
            videoAdapter.submitList(emptyList())
            binding.progressBar.visibility = View.VISIBLE
            binding.errorView.visibility = View.GONE
            footerAdapter.hide()
        }
        webView?.loadUrl(buildPageUrl(page))
    }

    private fun buildPageUrl(page: Int): String {
        val base = arguments?.getString("url") ?: return ""
        return if (page <= 1) base
        else if (base.contains("?")) "$base&page=$page"
        else "$base?page=$page"
    }

    private fun showError(msg: String) {
        binding.progressBar.visibility = View.GONE
        binding.swipeRefresh.isRefreshing = false
        binding.errorView.visibility = View.VISIBLE
        binding.errorMessage.text = msg
    }

    override fun onDestroyView() {
        handler.removeCallbacksAndMessages(null)
        webView?.destroy()
        webView = null
        _binding = null
        super.onDestroyView()
    }
}
