package com.eroprofile.app.ui.photos

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.eroprofile.app.R
import com.eroprofile.app.databinding.FragmentHomeBinding
import org.json.JSONArray
import android.webkit.CookieManager

class PhotosFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var webView: WebView? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var photoAdapter: PhotoAdapter

    private val allPhotos = mutableListOf<String>()
    private var isLoadingMore = false
    private var hasMorePages = true
    private var currentPage = 1
    private var pollAttempts = 0
    private val maxPollAttempts = 20
    private val pollIntervalMs = 800L

    private val baseUrl = "https://www.eroprofile.com/m/photos/home"

    private val extractPhotosJs = """
        (function() {
            try {
                var results = [];
                var seen = {};
                document.querySelectorAll('a[href]').forEach(function(a) {
                    var href = a.href || '';
                    if (!href.match(/\/photos\/view|\/m\/photo\/view/i)) return;
                    if (seen[href]) return;
                    seen[href] = true;
                    var img = a.querySelector('img');
                    if (!img) return;
                    var thumb = img.getAttribute('data-src') || img.getAttribute('data-lazy') || img.getAttribute('data-original') || img.src || '';
                    if (!thumb || thumb.endsWith('.gif')) return;
                    results.push({url:href, thumb:thumb});
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

        photoAdapter = PhotoAdapter()
        val gridLayout = GridLayoutManager(requireContext(), 2)
        binding.recyclerVideos.apply {
            layoutManager = gridLayout
            adapter = photoAdapter
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                    if (dy <= 0 || isLoadingMore || !hasMorePages) return
                    val lastVisible = gridLayout.findLastVisibleItemPosition()
                    if (lastVisible >= gridLayout.itemCount - 4) triggerLoadMore()
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
            userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36"
            cacheMode = android.webkit.WebSettings.LOAD_CACHE_ELSE_NETWORK
            loadsImagesAutomatically = false
            blockNetworkImage = true
        }
        wv.webChromeClient = WebChromeClient()
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                pollAttempts = 0
                if (isLoadingMore) pollForNewPhotos(0) else schedulePoll()
            }

            override fun shouldInterceptRequest(
                view: WebView, request: android.webkit.WebResourceRequest
            ): android.webkit.WebResourceResponse? {
                val url = request.url.toString()
                if (url.contains("google-analytics") || url.contains("googletagmanager") ||
                    url.contains("doubleclick") || url.contains("facebook.net") ||
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
        if (pollAttempts >= maxPollAttempts) { showError("Nessuna foto trovata"); return }
        pollAttempts++
        webView?.evaluateJavascript(extractPhotosJs) { raw ->
            val photos = parsePhotos(unescapeJs(raw))
            if (photos.isNotEmpty()) {
                requireActivity().runOnUiThread {
                    allPhotos.clear()
                    allPhotos.addAll(photos)
                    photoAdapter.submitList(allPhotos.toList())
                    binding.progressBar.visibility = View.GONE
                    binding.errorView.visibility = View.GONE
                    binding.swipeRefresh.isRefreshing = false
                }
            } else handler.postDelayed({ schedulePoll() }, pollIntervalMs)
        }
    }

    private fun triggerLoadMore() {
        isLoadingMore = true
        currentPage++
        handler.removeCallbacksAndMessages(null)
        pollAttempts = 0
        webView?.loadUrl(buildPageUrl(currentPage))
    }

    private fun pollForNewPhotos(attempt: Int) {
        if (attempt >= maxPollAttempts) {
            hasMorePages = false; isLoadingMore = false; return
        }
        val known = allPhotos.toHashSet()
        webView?.evaluateJavascript(extractPhotosJs) { raw ->
            val newPhotos = parsePhotos(unescapeJs(raw)).filter { it !in known }
            if (newPhotos.isNotEmpty()) {
                requireActivity().runOnUiThread {
                    allPhotos.addAll(newPhotos)
                    photoAdapter.submitList(allPhotos.toList())
                    isLoadingMore = false
                }
            } else handler.postDelayed({ pollForNewPhotos(attempt + 1) }, pollIntervalMs)
        }
    }

    private fun buildPageUrl(page: Int): String =
        if (page <= 1) baseUrl
        else "https://www.eroprofile.com/m/photos/search?pnum=$page"

    private fun parsePhotos(json: String): List<String> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            arr.getJSONObject(i).optString("thumb").takeIf { it.isNotEmpty() }
        }
    } catch (_: Exception) { emptyList() }

    private fun unescapeJs(raw: String?): String {
        if (raw == null) return "[]"
        return if (raw.startsWith("\"") && raw.endsWith("\"")) {
            raw.removeSurrounding("\"").replace("\\\"", "\"").replace("\\/", "/").replace("\\n", "")
        } else raw
    }

    private fun loadFresh() {
        handler.removeCallbacksAndMessages(null)
        currentPage = 1; pollAttempts = 0; isLoadingMore = false; hasMorePages = true
        allPhotos.clear(); photoAdapter.submitList(emptyList())
        binding.progressBar.visibility = View.VISIBLE
        binding.errorView.visibility = View.GONE
        webView?.loadUrl(baseUrl)
    }

    private fun showError(msg: String) {
        binding.progressBar.visibility = View.GONE
        binding.swipeRefresh.isRefreshing = false
        binding.errorView.visibility = View.VISIBLE
        binding.errorMessage.text = msg
    }

    override fun onDestroyView() {
        handler.removeCallbacksAndMessages(null)
        webView?.destroy(); webView = null
        _binding = null
        super.onDestroyView()
    }

    // ── Adapter ────────────────────────────────────────────────────────────────

    inner class PhotoAdapter : ListAdapter<String, PhotoAdapter.PhotoHolder>(PhotoDiff()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = PhotoHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_photo, parent, false)
        )

        override fun onBindViewHolder(holder: PhotoHolder, position: Int) {
            holder.bind(getItem(position))
        }

        inner class PhotoHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val img: ImageView = view.findViewById(R.id.imgPhoto)
            fun bind(thumbUrl: String) {
                val cookies = runCatching {
                    CookieManager.getInstance().getCookie("https://www.eroprofile.com") ?: ""
                }.getOrDefault("")
                Glide.with(img.context)
                    .load(GlideUrl(thumbUrl, LazyHeaders.Builder()
                        .addHeader("Referer", "https://www.eroprofile.com/")
                        .apply { if (cookies.isNotEmpty()) addHeader("Cookie", cookies) }
                        .build()))
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .centerCrop()
                    .placeholder(R.color.ep_surface_variant)
                    .into(img)
            }
        }
    }

    private class PhotoDiff : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(a: String, b: String) = a == b
        override fun areContentsTheSame(a: String, b: String) = a == b
    }
}
