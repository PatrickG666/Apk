package com.eroprofile.app.data.scraper

import com.eroprofile.app.data.models.Category
import com.eroprofile.app.data.models.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.util.concurrent.TimeUnit

class EroProfileScraper {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "it-IT,it;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Referer", BASE_URL)
                .build()
            chain.proceed(request)
        }
        .build()

    companion object {
        const val BASE_URL = "https://www.eroprofile.com"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36"

        // Sort options
        const val SORT_RECENT = "date"
        const val SORT_POPULAR = "views"
        const val SORT_TOP_RATED = "rated"
    }

    /** Fetch videos from the main listing page */
    suspend fun fetchVideos(sort: String = SORT_RECENT, page: Int = 1): Result<List<Video>> =
        withContext(Dispatchers.IO) {
            try {
                val url = "$BASE_URL/m/video/list?sort=$sort&p=$page"
                val doc = fetchDocument(url)
                val videos = parseVideoList(doc)
                Result.success(videos)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** Search for videos */
    suspend fun searchVideos(query: String, page: Int = 1): Result<List<Video>> =
        withContext(Dispatchers.IO) {
            try {
                val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
                val url = "$BASE_URL/m/video/list?search=$encodedQuery&p=$page"
                val doc = fetchDocument(url)
                val videos = parseVideoList(doc)
                Result.success(videos)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** Fetch videos for a specific category */
    suspend fun fetchCategoryVideos(categoryUrl: String, page: Int = 1): Result<List<Video>> =
        withContext(Dispatchers.IO) {
            try {
                val url = if (page > 1) "$categoryUrl?p=$page" else categoryUrl
                val doc = fetchDocument(url)
                val videos = parseVideoList(doc)
                Result.success(videos)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** Fetch the list of categories */
    suspend fun fetchCategories(): Result<List<Category>> =
        withContext(Dispatchers.IO) {
            try {
                val url = "$BASE_URL/m/video/categories"
                val doc = fetchDocument(url)
                val categories = parseCategories(doc)
                Result.success(categories)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun fetchDocument(url: String): Document {
        val request = Request.Builder().url(url).get().build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
        val body = response.body?.string() ?: throw IOException("Empty response")
        return Jsoup.parse(body, url)
    }

    private fun parseVideoList(doc: Document): List<Video> {
        val videos = mutableListOf<Video>()

        // eroprofile video list items — try multiple selector patterns
        val items = doc.select(
            "div.videoListWrapper, ul.list-videos li, div.thumb-item, " +
            ".videoListItem, li.video-item, .video-box, div.thumbBlock, " +
            "div.videoBlock, li.videoItem, div.video-item, div.thumb, " +
            "ul.videoList li, div.item[class*=video], li[class*=video], " +
            "div[class*=thumb], div[class*=video]"
        ).filter { el ->
            // Keep only elements that contain a link to a video
            el.selectFirst("a[href*=video]") != null && el.selectFirst("img") != null
        }

        // Fallback: find any anchor that links to a video view page
        val effectiveItems = if (items.isEmpty()) {
            doc.select("a[href*=/m/video/view], a[href*=/video/view]")
                .map { it.parent() ?: it }
                .distinctBy { it.attr("href").ifEmpty { it.html() } }
        } else items

        for (item in effectiveItems) {
            try {
                // Try multiple selector patterns to handle site changes
                val linkEl = item.selectFirst("a[href*=/m/video/view], a[href*=/video/view], a[href*=/video/]") ?: continue
                val href = linkEl.attr("abs:href").ifEmpty { linkEl.attr("href") }
                if (href.isEmpty()) continue

                val title = item.selectFirst("span.title, .video-title, h3, .title, p.title")
                    ?.text()?.trim()
                    ?: linkEl.attr("title").trim()
                    ?: item.selectFirst("img")?.attr("alt")?.trim()
                    ?: ""

                val thumbEl = item.selectFirst("img")
                val thumbUrl = thumbEl?.let {
                    it.attr("data-src").ifEmpty { it.attr("src") }
                } ?: ""

                val duration = item.selectFirst(".duration, span.duration, .time, .video-duration")
                    ?.text()?.trim() ?: ""

                val views = item.selectFirst(".views, span.views, .view-count")
                    ?.text()?.trim()?.replace("[^0-9KMk,.]".toRegex(), "")?.trim() ?: ""

                val rating = item.selectFirst(".rating, .score, .percent")
                    ?.text()?.trim() ?: ""

                val author = item.selectFirst(".username, .author, .user")
                    ?.text()?.trim() ?: ""

                val isHd = item.selectFirst(".hd, .badge-hd, span.hd") != null ||
                        item.text().contains("HD", ignoreCase = false)

                val id = extractVideoId(href)

                if (title.isNotEmpty() || href.isNotEmpty()) {
                    videos.add(
                        Video(
                            id = id,
                            title = title.ifEmpty { "Video" },
                            url = href,
                            thumbnailUrl = thumbUrl,
                            duration = duration,
                            views = views,
                            rating = rating,
                            author = author,
                            isHd = isHd
                        )
                    )
                }
            } catch (_: Exception) {
                // Skip malformed items
            }
        }

        return videos
    }

    private fun parseCategories(doc: Document): List<Category> {
        val categories = mutableListOf<Category>()

        val items = doc.select(
            "ul.list-categories li, div.category-item, .cat-item, " +
            "div.categoriesList a, ul.categories li, a[href*=/category/], a[href*=/m/video/list]"
        )

        for (item in items) {
            try {
                val linkEl = if (item.tagName() == "a") item else item.selectFirst("a") ?: continue
                val href = linkEl.attr("abs:href").ifEmpty { linkEl.attr("href") }
                if (href.isEmpty() || href.contains("/list?sort=")) continue

                val name = item.selectFirst("span.name, .cat-name, h3, span")
                    ?.text()?.trim()
                    ?: linkEl.text().trim()

                if (name.isEmpty()) continue

                val thumbUrl = item.selectFirst("img")?.let {
                    it.attr("data-src").ifEmpty { it.attr("src") }
                } ?: ""

                val count = item.selectFirst(".count, span.count")?.text()?.trim() ?: ""

                categories.add(
                    Category(
                        name = name,
                        url = href,
                        thumbnailUrl = thumbUrl,
                        count = count
                    )
                )
            } catch (_: Exception) {
                // Skip malformed items
            }
        }

        // Fallback: hardcoded popular categories if scraping fails
        if (categories.isEmpty()) {
            return getHardcodedCategories()
        }

        return categories
    }

    private fun extractVideoId(url: String): String {
        return url.substringAfterLast("/").substringBefore("?").ifEmpty { url.hashCode().toString() }
    }

    private fun getHardcodedCategories(): List<Category> = listOf(
        "Amateur", "Anal", "Asian", "BBW", "Babes", "Blowjob",
        "Brunette", "Creampie", "Cumshot", "Ebony", "Femdom",
        "Hardcore", "Latina", "Lesbian", "Masturbation", "Mature",
        "MILF", "POV", "Redhead", "Solo", "Squirt", "Teen",
        "Threesome", "Voyeur"
    ).map { name ->
        val slug = name.lowercase().replace(" ", "-")
        Category(
            name = name,
            url = "$BASE_URL/m/video/list?tag=$slug",
            thumbnailUrl = ""
        )
    }
}
