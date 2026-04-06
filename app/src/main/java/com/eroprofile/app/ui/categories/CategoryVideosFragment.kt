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

    private val allVideos = mutableListOf<Video>()
    private var isLoadingMore = false
    private var hasMorePages = true

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
                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                    if (dy <= 0 || isLoadingMore || !hasMorePages) return
                    val lastVisible = gridLayout.findLastVisibleItemPosition()
                    val total = gridLayout.itemCount
                    if (lastVisible >= total - 4) triggerLoadMore()
                }
            })
        }

        binding.swipeRefresh.setOnRefreshListener { loadFresh() }
        binding.btnRetry.setOnClickListener { loadFresh() }

        setupWebView()
        loadFresh()
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
        binding.root.addView(wv, ConstraintLayout.LayoutParams(1, 1))
        webView = wv
    }

    private fun schedulePoll() {
        if (pollAttempts >= maxPollAttempts) {
            showError("Nessun video trovato")
            return
        }
        pollAttempts++
        webView?.evaluateJavascript(extractVideosJs) { raw ->
            val json = unescapeJs(raw)
            val count = try { JSONArray(json).length() } catch (_: Exception) { -1 }
            if (count > 0) requireActivity().runOnUiThread { handleInitialVideos(json) }
            else handler.postDelayed({ schedulePoll() }, 2000)
        }
    }

    private fun handleInitialVideos(json: String) {
        val videos = parseJson(json)
        if (videos.isEmpty()) return
        allVideos.clear()
        allVideos.addAll(videos)
        videoAdapter.submitList(allVideos.toList())
        binding.progressBar.visibility = View.GONE
        binding.errorView.visibility = View.GONE
        binding.swipeRefresh.isRefreshing = false
    }

    private fun triggerLoadMore() {
        isLoadingMore = true
        footerAdapter.show()
        webView?.evaluateJavascript("window.scrollTo(0, document.body.scrollHeight);") {
            handler.postDelayed({ pollForNewVideos(0) }, 2500)
        }
    }

    private fun pollForNewVideos(attempt: Int) {
        if (attempt >= 8) {
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
                webView?.evaluateJavascript("window.scrollTo(0, document.body.scrollHeight);") {}
                handler.postDelayed({ pollForNewVideos(attempt + 1) }, 2000)
            }
        }
    }

    private fun parseJson(json: String): List<Video> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val url = o.optString("url")
                if (url.isEmpty()) null
                else Video(
                    id = url.hashCode().toString(),
                    title = o.optString("title", "Video"),
                    url = url,
                    thumbnailUrl = o.optString("thumb"),
                    duration = o.optString("duration")
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun unescapeJs(raw: String?): String {
        if (raw == null) return "[]"
        return if (raw.startsWith("\"") && raw.endsWith("\"")) {
            raw.removeSurrounding("\"").replace("\\\"", "\"").replace("\\/", "/").replace("\\n", "")
        } else raw
    }

    private fun loadFresh() {
        val url = arguments?.getString("url") ?: run {
            showError("URL categoria mancante")
            return
        }
        handler.removeCallbacksAndMessages(null)
        pollAttempts = 0
        isLoadingMore = false
        hasMorePages = true
        allVideos.clear()
        videoAdapter.submitList(emptyList())
        footerAdapter.hide()
        binding.progressBar.visibility = View.VISIBLE
        binding.errorView.visibility = View.GONE
        webView?.loadUrl(url)
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
