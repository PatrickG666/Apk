package com.eroprofile.app.data.repository

import android.content.Context
import com.eroprofile.app.data.models.Category
import com.eroprofile.app.data.models.Video
import com.eroprofile.app.data.scraper.WebViewScraper

class VideoRepository(context: Context) {

    private val scraper = WebViewScraper(context)

    suspend fun getVideos(sort: String = WebViewScraper.SORT_RECENT, page: Int = 1): Result<List<Video>> =
        scraper.fetchVideos(sort, page)

    suspend fun searchVideos(query: String, page: Int = 1): Result<List<Video>> =
        scraper.searchVideos(query, page)

    suspend fun getCategoryVideos(categoryUrl: String, page: Int = 1): Result<List<Video>> =
        scraper.fetchCategoryVideos(categoryUrl, page)

    suspend fun getCategories(): Result<List<Category>> =
        scraper.fetchCategories()
}
