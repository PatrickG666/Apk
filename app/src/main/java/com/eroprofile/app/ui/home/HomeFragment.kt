package com.eroprofile.app.ui.home

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var webView: WebView? = null
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var videoAdapter: VideoAdapter
    private lateinit var footerAdapter: LoadingFooterAdapter

    private val allVideos = mutableListOf<Video>()
    private var isLoadingMore = false
    private var hasMorePages = true
    private var currentPage = 1
    private var currentSort = "date"

    private var pollAttempts = 0
    private val maxPollAttempts = 20
    private val pollIntervalMs = 800L

    private val baseUrl = "https://www.eroprofile.com/m/videos/search?niche=all"

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
                    var cat = '';
                    var container = a.closest('li,article,[class*=item],[class*=video],[class*=thumb]') || a.parentElement;
                    if (container) {
                        var cl = container.querySelector('a[href*="niche"],a[href*="/tag/"],a[href*="categor"],[class*=niche],[class*=categ],[class*=tag]');
                        if (cl) cat = cl.textContent.trim();
                    }
                    results.push({url:href, title:title, thumb:thumb, duration:dur, category:cat});
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
        loadFresh(currentSort)
    }

    private fun setupRecyclerView() {
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
                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                    if (dy <= 0 || isLoadingMore || !hasMorePages) return
                    val lastVisible = gridLayout.findLastVisibleItemPosition()
                    if (lastVisible >= gridLayout.itemCount - 4) triggerLoadMore()
                }
            })
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val wv = WebView(requireContext())
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36"
            cacheMode = android.webkit.WebSettings.LOAD_CACHE_ELSE_NETWORK
            loadsImagesAutomatically = false
            blockNetworkImage = true
        }
        wv.webChromeClient = WebChromeClient()
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                pollAttempts = 0
                if (isLoadingMore) pollForNewVideos(0) else schedulePoll()
            }

            override fun shouldInterceptRequest(
                view: WebView, request: android.webkit.WebResourceRequest
            ): android.webkit.WebResourceResponse? {
                val url = request.url.toString()
                if (url.contains("google-analytics") || url.contains("googletagmanager") ||
                    url.contains("doubleclick") || url.contains("facebook.net") ||
                    url.contains("/ads/") ||
                    url.endsWith(".woff") || url.endsWith(".woff2") ||
                    url.endsWith(".ttf") || url.endsWith(".otf")
                ) {
                    return android.webkit.WebResourceResponse(
                        "text/plain", "utf-8", java.io.ByteArrayInputStream(ByteArray(0))
                    )
                }
                return super.shouldInterceptRequest(view, request)
            }
        }
        binding.root.addView(wv, ConstraintLayout.LayoutParams(1, 1))
        webView = wv
    }

    private fun schedulePoll() {
        if (pollAttempts >= maxPollAttempts) {
            showError("Impossibile caricare i video")
            return
        }
        pollAttempts++
        webView?.evaluateJavascript(extractVideosJs) { raw ->
            val videos = parseJson(unescapeJs(raw))
            if (videos.isNotEmpty()) {
                requireActivity().runOnUiThread {
                    allVideos.clear()
                    allVideos.addAll(videos)
                    videoAdapter.submitList(allVideos.toList())
                    binding.progressBar.visibility = View.GONE
                    binding.errorView.visibility = View.GONE
                    binding.swipeRefresh.isRefreshing = false
                }
            } else {
                handler.postDelayed({ schedulePoll() }, pollIntervalMs)
            }
        }
    }

    private fun triggerLoadMore() {
        isLoadingMore = true
        currentPage++
        footerAdapter.show()
        handler.removeCallbacksAndMessages(null)
        pollAttempts = 0
        webView?.loadUrl(buildPageUrl(currentSort, currentPage))
    }

    private fun pollForNewVideos(attempt: Int) {
        if (attempt >= maxPollAttempts) {
            hasMorePages = false
            isLoadingMore = false
            requireActivity().runOnUiThread { footerAdapter.hide() }
            return
        }
        val knownUrls = allVideos.map { it.url }.toHashSet()
        webView?.evaluateJavascript(extractVideosJs) { raw ->
            val newVideos = parseJson(unescapeJs(raw)).filter { it.url !in knownUrls }
            if (newVideos.isNotEmpty()) {
                requireActivity().runOnUiThread {
                    allVideos.addAll(newVideos)
                    videoAdapter.submitList(allVideos.toList())
                    footerAdapter.hide()
                    isLoadingMore = false
                }
            } else {
                handler.postDelayed({ pollForNewVideos(attempt + 1) }, pollIntervalMs)
            }
        }
    }

    private fun buildPageUrl(sort: String, page: Int): String =
        if (page <= 1) "$baseUrl&sort=$sort"
        else "$baseUrl&sort=$sort&pnum=$page"

    private fun parseJson(json: String): List<Video> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val url = o.optString("url", "")
            if (url.isEmpty()) null
            else Video(
                id = url.hashCode().toString(),
                title = o.optString("title", "Video"),
                url = url,
                thumbnailUrl = o.optString("thumb", ""),
                duration = o.optString("duration", ""),
                category = o.optString("category", ""),
                views = "", rating = "", author = "", isHd = false
            )
        }
    } catch (_: Exception) { emptyList() }

    private fun unescapeJs(raw: String?): String {
        if (raw == null) return "[]"
        return if (raw.startsWith("\"") && raw.endsWith("\"")) {
            raw.removeSurrounding("\"")
                .replace("\\\"", "\"")
                .replace("\\/", "/")
                .replace("\\n", "")
        } else raw
    }

    private fun showError(message: String) {
        binding.progressBar.visibility = View.GONE
        binding.swipeRefresh.isRefreshing = false
        binding.errorView.visibility = View.VISIBLE
        binding.errorMessage.text = message
    }

    private fun loadFresh(sort: String) {
        handler.removeCallbacksAndMessages(null)
        currentPage = 1
        pollAttempts = 0
        isLoadingMore = false
        hasMorePages = true
        allVideos.clear()
        videoAdapter.submitList(emptyList())
        footerAdapter.hide()
        binding.progressBar.visibility = View.VISIBLE
        binding.errorView.visibility = View.GONE
        webView?.loadUrl("$baseUrl&sort=$sort")
    }

    private fun setupChips() {
        listOf(
            binding.chipRecent to "date",
            binding.chipPopular to "views",
            binding.chipTopRated to "rated"
        ).forEach { (chip, sort) ->
            chip.setOnClickListener {
                if (currentSort == sort) return@setOnClickListener
                currentSort = sort
                loadFresh(sort)
            }
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener { loadFresh(currentSort) }
    }

    private fun setupRetry() {
        binding.btnRetry.setOnClickListener { loadFresh(currentSort) }
    }

    override fun onDestroyView() {
        handler.removeCallbacksAndMessages(null)
        webView?.destroy()
        webView = null
        _binding = null
        super.onDestroyView()
    }
}
