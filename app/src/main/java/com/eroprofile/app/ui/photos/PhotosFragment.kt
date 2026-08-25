package com.eroprofile.app.ui.photos

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.eroprofile.app.R
import com.eroprofile.app.adapters.LoadingFooterAdapter
import com.eroprofile.app.adapters.VideoAdapter
import com.eroprofile.app.data.models.Video
import com.eroprofile.app.databinding.FragmentPhotosBinding
import com.eroprofile.app.ui.video.VideoPlayerActivity
import org.json.JSONArray

class PhotosFragment : Fragment() {

    private var _binding: FragmentPhotosBinding? = null
    private val binding get() = _binding!!

    private var webView: WebView? = null
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var videoAdapter: VideoAdapter
    private lateinit var videoFooter: LoadingFooterAdapter
    private lateinit var photoAdapter: PhotoAdapter
    private lateinit var photoFooter: LoadingFooterAdapter

    data class Photo(val url: String, val title: String, val thumb: String)

    private val allVideos = mutableListOf<Video>()
    private val allPhotos = mutableListOf<Photo>()

    private var isVideoMode = true
    private var isLoadingMore = false
    private var hasMorePages = true
    private var currentPage = 1
    private var pollAttempts = 0
    private val maxPollAttempts = 20
    private val pollIntervalMs = 800L

    private val videoBaseUrl = "https://www.eroprofile.com/m/videos/search?niche=all"
    private val photoBaseUrl = "https://www.eroprofile.com/m/photos/home"

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
                    var title = a.getAttribute('title') || img.getAttribute('alt') || '';
                    if (!title) {
                        var p = a.parentElement;
                        if (p) { var t = p.querySelector('.title,.name,h3,h4'); if (t) title = t.textContent.trim(); }
                    }
                    results.push({url:href, title:title, thumb:thumb});
                });
                return JSON.stringify(results);
            } catch(e) { return '[]'; }
        })();
    """.trimIndent()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPhotosBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAdapters()
        setupWebView()
        setupToggle()
        binding.swipeRefresh.setOnRefreshListener { loadFresh() }
        binding.btnRetry.setOnClickListener { loadFresh() }
        switchToVideoMode()
        loadFresh()
    }

    private fun setupAdapters() {
        videoAdapter = VideoAdapter { video ->
            startActivity(Intent(requireContext(), VideoPlayerActivity::class.java).apply {
                putExtra(VideoPlayerActivity.EXTRA_URL, video.url)
                putExtra(VideoPlayerActivity.EXTRA_TITLE, video.title)
            })
        }
        videoFooter = LoadingFooterAdapter()
        photoAdapter = PhotoAdapter()
        photoFooter = LoadingFooterAdapter()
    }

    private fun setupToggle() {
        binding.btnVideo.setOnClickListener {
            if (isVideoMode) return@setOnClickListener
            isVideoMode = true
            updateToggleStyle()
            loadFresh()
        }
        binding.btnFoto.setOnClickListener {
            if (!isVideoMode) return@setOnClickListener
            isVideoMode = false
            updateToggleStyle()
            loadFresh()
        }
    }

    private fun updateToggleStyle() {
        binding.btnVideo.setBackgroundResource(
            if (isVideoMode) R.drawable.bg_chip_selected else R.drawable.bg_chip
        )
        binding.btnVideo.setTextColor(resources.getColor(
            if (isVideoMode) R.color.ep_text_primary else R.color.ep_text_secondary, null
        ))
        binding.btnFoto.setBackgroundResource(
            if (!isVideoMode) R.drawable.bg_chip_selected else R.drawable.bg_chip
        )
        binding.btnFoto.setTextColor(resources.getColor(
            if (!isVideoMode) R.color.ep_text_primary else R.color.ep_text_secondary, null
        ))
    }

    private fun switchToVideoMode() {
        val llm = LinearLayoutManager(requireContext())
        binding.recyclerView.apply {
            layoutManager = llm
            adapter = ConcatAdapter(videoAdapter, videoFooter)
            clearOnScrollListeners()
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                    if (dy <= 0 || isLoadingMore || !hasMorePages) return
                    if (llm.findLastVisibleItemPosition() >= llm.itemCount - 4) triggerLoadMore()
                }
            })
        }
    }

    private fun switchToPhotoMode() {
        val llm = LinearLayoutManager(requireContext())
        binding.recyclerView.apply {
            layoutManager = llm
            adapter = ConcatAdapter(photoAdapter, photoFooter)
            clearOnScrollListeners()
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                    if (dy <= 0 || isLoadingMore || !hasMorePages) return
                    if (llm.findLastVisibleItemPosition() >= llm.itemCount - 4) triggerLoadMore()
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
                if (isLoadingMore) pollForMore(0) else schedulePoll()
            }
            override fun shouldInterceptRequest(
                view: WebView, request: android.webkit.WebResourceRequest
            ): android.webkit.WebResourceResponse? {
                val u = request.url.toString()
                if (u.contains("google-analytics") || u.contains("googletagmanager") ||
                    u.contains("doubleclick") || u.contains("facebook.net") ||
                    u.contains("/ads/") ||
                    u.endsWith(".woff") || u.endsWith(".woff2") ||
                    u.endsWith(".ttf") || u.endsWith(".otf")
                ) return android.webkit.WebResourceResponse("text/plain", "utf-8",
                    java.io.ByteArrayInputStream(ByteArray(0)))
                return null
            }
        }
        binding.root.addView(wv, ConstraintLayout.LayoutParams(1, 1))
        webView = wv
    }

    private fun schedulePoll() {
        if (pollAttempts >= maxPollAttempts) { showError("Nessun contenuto trovato"); return }
        pollAttempts++
        val js = if (isVideoMode) extractVideosJs else extractPhotosJs
        webView?.evaluateJavascript(js) { raw ->
            val escaped = unescapeJs(raw)
            if (isVideoMode) {
                val videos = parseVideos(escaped)
                if (videos.isNotEmpty()) {
                    requireActivity().runOnUiThread {
                        allVideos.clear(); allVideos.addAll(videos)
                        videoAdapter.submitList(allVideos.toList())
                        binding.progressBar.visibility = View.GONE
                        binding.errorView.visibility = View.GONE
                        binding.swipeRefresh.isRefreshing = false
                    }
                } else {
                    handler.postDelayed({ schedulePoll() }, pollIntervalMs)
                }
            } else {
                val photos = parsePhotos(escaped)
                if (photos.isNotEmpty()) {
                    requireActivity().runOnUiThread {
                        allPhotos.clear(); allPhotos.addAll(photos)
                        photoAdapter.submitList(allPhotos.toList())
                        binding.progressBar.visibility = View.GONE
                        binding.errorView.visibility = View.GONE
                        binding.swipeRefresh.isRefreshing = false
                    }
                } else {
                    handler.postDelayed({ schedulePoll() }, pollIntervalMs)
                }
            }
        }
    }

    private fun triggerLoadMore() {
        isLoadingMore = true
        currentPage++
        if (isVideoMode) videoFooter.show() else photoFooter.show()
        handler.removeCallbacksAndMessages(null)
        pollAttempts = 0
        webView?.loadUrl(buildPageUrl(currentPage))
    }

    private fun pollForMore(attempt: Int) {
        if (attempt >= maxPollAttempts) {
            hasMorePages = false; isLoadingMore = false
            requireActivity().runOnUiThread {
                if (isVideoMode) videoFooter.hide() else photoFooter.hide()
            }
            return
        }
        val js = if (isVideoMode) extractVideosJs else extractPhotosJs
        webView?.evaluateJavascript(js) { raw ->
            val escaped = unescapeJs(raw)
            if (isVideoMode) {
                val knownUrls = allVideos.map { it.url }.toHashSet()
                val newVideos = parseVideos(escaped).filter { it.url !in knownUrls }
                if (newVideos.isNotEmpty()) {
                    requireActivity().runOnUiThread {
                        allVideos.addAll(newVideos)
                        videoAdapter.submitList(allVideos.toList())
                        videoFooter.hide(); isLoadingMore = false
                    }
                } else {
                    handler.postDelayed({ pollForMore(attempt + 1) }, pollIntervalMs)
                }
            } else {
                val knownUrls = allPhotos.map { it.url }.toHashSet()
                val newPhotos = parsePhotos(escaped).filter { it.url !in knownUrls }
                if (newPhotos.isNotEmpty()) {
                    requireActivity().runOnUiThread {
                        allPhotos.addAll(newPhotos)
                        photoAdapter.submitList(allPhotos.toList())
                        photoFooter.hide(); isLoadingMore = false
                    }
                } else {
                    handler.postDelayed({ pollForMore(attempt + 1) }, pollIntervalMs)
                }
            }
        }
    }

    private fun buildPageUrl(page: Int): String = if (isVideoMode) {
        if (page <= 1) "$videoBaseUrl&sort=date" else "$videoBaseUrl&sort=date&pnum=$page"
    } else {
        if (page <= 1) photoBaseUrl else "https://www.eroprofile.com/m/photos/search?pnum=$page"
    }

    private fun parseVideos(json: String): List<Video> = try {
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

    private fun parsePhotos(json: String): List<Photo> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val url = o.optString("url", "")
            val thumb = o.optString("thumb", "")
            if (url.isEmpty() || thumb.isEmpty()) null
            else Photo(url, o.optString("title", ""), thumb)
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
        if (isVideoMode) {
            allVideos.clear(); videoAdapter.submitList(emptyList()); videoFooter.hide()
            switchToVideoMode()
        } else {
            allPhotos.clear(); photoAdapter.submitList(emptyList()); photoFooter.hide()
            switchToPhotoMode()
        }
        binding.progressBar.visibility = View.VISIBLE
        binding.errorView.visibility = View.GONE
        webView?.loadUrl(buildPageUrl(1))
    }

    private fun showError(msg: String) {
        requireActivity().runOnUiThread {
            binding.progressBar.visibility = View.GONE
            binding.swipeRefresh.isRefreshing = false
            binding.errorView.visibility = View.VISIBLE
            binding.errorMessage.text = msg
        }
    }

    override fun onDestroyView() {
        handler.removeCallbacksAndMessages(null)
        webView?.destroy(); webView = null
        _binding = null
        super.onDestroyView()
    }

    // ── Photo Adapter ──────────────────────────────────────────────────────────

    inner class PhotoAdapter : ListAdapter<Photo, PhotoAdapter.PhotoHolder>(PhotoDiff()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = PhotoHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_photo, parent, false)
        )

        override fun onBindViewHolder(holder: PhotoHolder, position: Int) = holder.bind(getItem(position))

        inner class PhotoHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val imgPhoto: ImageView = view.findViewById(R.id.imgPhoto)
            private val tvTitle: TextView = view.findViewById(R.id.tvTitle)

            fun bind(photo: Photo) {
                tvTitle.text = photo.title
                tvTitle.visibility = if (photo.title.isNotEmpty()) View.VISIBLE else View.GONE
                val cookies = runCatching {
                    CookieManager.getInstance().getCookie("https://www.eroprofile.com") ?: ""
                }.getOrDefault("")
                Glide.with(imgPhoto.context)
                    .load(GlideUrl(photo.thumb, LazyHeaders.Builder()
                        .addHeader("Referer", "https://www.eroprofile.com/")
                        .apply { if (cookies.isNotEmpty()) addHeader("Cookie", cookies) }
                        .build()))
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .centerCrop()
                    .placeholder(R.color.ep_surface_variant)
                    .into(imgPhoto)
                itemView.setOnClickListener {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(photo.url)))
                }
            }
        }

    }

    private class PhotoDiff : DiffUtil.ItemCallback<Photo>() {
        override fun areItemsTheSame(a: Photo, b: Photo) = a.url == b.url
        override fun areContentsTheSame(a: Photo, b: Photo) = a == b
    }
}
