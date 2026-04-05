package com.eroprofile.app.data.scraper

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.eroprofile.app.data.models.Video
import com.eroprofile.app.data.models.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import kotlin.coroutines.resume

class WebViewScraper(private val context: Context) {

    companion object {
        const val BASE_URL = "https://www.eroprofile.com"
        const val SORT_RECENT = "date"
        const val SORT_POPULAR = "views"
        const val SORT_TOP_RATED = "rated"
        private const val TIMEOUT_MS = 60_000L   // 60s total
        private const val POLL_INTERVAL_MS = 2_000L  // check every 2s
        private const val POLL_START_MS = 4_000L     // first check after 4s
    }

    // JS injected after page load to extract video data as JSON
    private val extractVideosJS = """
        (function() {
            var results = [];
            // Find all anchors containing an img whose href looks like a video
            var anchors = document.querySelectorAll('a');
            anchors.forEach(function(a) {
                var href = a.href || '';
                if (!href.match(/video.*view|\/v\/[0-9]|\/videos?\//i)) return;
                var img = a.querySelector('img');
                if (!img) return;
                var thumb = img.dataset.src || img.dataset.lazySrc || img.src || '';
                var title = a.title || img.alt || '';
                if (!title) {
                    // look for text in siblings
                    var parent = a.parentElement;
                    if (parent) {
                        var t = parent.querySelector('p,span,h3,h4,.title,.name');
                        if (t) title = t.textContent.trim();
                    }
                }
                if (!title) title = 'Video';
                var container = a.parentElement || a;
                var duration = '';
                var d = a.querySelector('[class*=dur],[class*=time],[class*=length]') ||
                         container.querySelector('[class*=dur],[class*=time],[class*=length]');
                if (d) duration = d.textContent.trim();
                results.push({url: href, title: title, thumb: thumb, duration: duration});
            });
            return JSON.stringify(results);
        })();
    """.trimIndent()

    private val extractCategoriesJS = """
        (function() {
            var results = [];
            var anchors = document.querySelectorAll('a');
            anchors.forEach(function(a) {
                var href = a.href || '';
                if (!href.match(/categor|tag=/i)) return;
                var name = a.textContent.trim();
                var img = a.querySelector('img');
                var thumb = img ? (img.dataset.src || img.src || '') : '';
                if (name) results.push({name: name, url: href, thumb: thumb});
            });
            return JSON.stringify(results);
        })();
    """.trimIndent()

    suspend fun fetchVideos(sort: String = SORT_RECENT, page: Int = 1): Result<List<Video>> {
        val url = "$BASE_URL/m/video/list?sort=$sort&p=$page"
        return fetchAndParse(url) { json -> parseVideos(json) }
    }

    suspend fun searchVideos(query: String, page: Int = 1): Result<List<Video>> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "$BASE_URL/m/video/list?search=$encoded&p=$page"
        return fetchAndParse(url) { json -> parseVideos(json) }
    }

    suspend fun fetchCategoryVideos(categoryUrl: String, page: Int = 1): Result<List<Video>> {
        val url = if (page > 1) "$categoryUrl?p=$page" else categoryUrl
        return fetchAndParse(url) { json -> parseVideos(json) }
    }

    suspend fun fetchCategories(): Result<List<Category>> {
        val url = "$BASE_URL/m/video/categories"
        return try {
            val json = loadPageAndEval(url, extractCategoriesJS)
                ?: return Result.failure(Exception("Timeout caricamento pagina"))
            val arr = JSONArray(json)
            val categories = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Category(
                    name = o.optString("name", ""),
                    url = o.optString("url", ""),
                    thumbnailUrl = o.optString("thumb", "")
                )
            }.filter { it.name.isNotEmpty() && it.url.isNotEmpty() }
            Result.success(categories)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun <T> fetchAndParse(url: String, parser: (String) -> List<T>): Result<List<T>> {
        return try {
            val json = loadPageAndEval(url, extractVideosJS)
                ?: return Result.failure(Exception("Timeout dopo ${TIMEOUT_MS/1000}s: sito lento o contenuto protetto da Cloudflare"))
            val items = parser(json)
            if (items.isEmpty()) {
                Result.failure(Exception("Nessun video trovato nella pagina"))
            } else {
                Result.success(items)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseVideos(json: String): List<Video> {
        val arr = JSONArray(json)
        return (0 until arr.length()).mapNotNull { i ->
            try {
                val o = arr.getJSONObject(i)
                val url = o.optString("url", "")
                if (url.isEmpty()) return@mapNotNull null
                Video(
                    id = url.substringAfterLast("/").substringBefore("?").ifEmpty { url.hashCode().toString() },
                    title = o.optString("title", "Video"),
                    url = url,
                    thumbnailUrl = o.optString("thumb", ""),
                    duration = o.optString("duration", "")
                )
            } catch (_: Exception) { null }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun loadPageAndEval(url: String, js: String): String? =
        withTimeoutOrNull(TIMEOUT_MS) {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    val handler = Handler(Looper.getMainLooper())
                    val webView = WebView(context)
                    webView.settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36"
                    }

                    var resolved = false

                    fun resolve(result: String?) {
                        if (!resolved) {
                            resolved = true
                            handler.removeCallbacksAndMessages(null)
                            webView.stopLoading()
                            webView.destroy()
                            cont.resume(result)
                        }
                    }

                    // JS that returns results OR null/"[]" if not ready yet
                    val wrappedJs = """
                        (function() {
                            try { return ($js); } catch(e) { return '[]'; }
                        })();
                    """.trimIndent()

                    // Polling function: run JS every POLL_INTERVAL_MS until results found
                    fun scheduleEval(delayMs: Long) {
                        handler.postDelayed({
                            if (resolved) return@postDelayed
                            webView.evaluateJavascript(wrappedJs) { raw ->
                                if (resolved) return@evaluateJavascript
                                // Unescape the JS string result
                                val json = if (raw != null && raw.startsWith("\"")) {
                                    raw.removeSurrounding("\"")
                                        .replace("\\\"", "\"")
                                        .replace("\\'", "'")
                                        .replace("\\n", "")
                                } else raw ?: "[]"

                                val hasItems = try {
                                    JSONArray(json).length() > 0
                                } catch (_: Exception) { false }

                                if (hasItems) {
                                    resolve(json)
                                } else {
                                    // Not ready yet, poll again
                                    scheduleEval(POLL_INTERVAL_MS)
                                }
                            }
                        }, delayMs)
                    }

                    webView.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = false
                    }

                    cont.invokeOnCancellation { resolve(null) }

                    webView.loadUrl(url)
                    // Start polling after POLL_START_MS regardless of page load events
                    scheduleEval(POLL_START_MS)
                }
            }
        }
}
