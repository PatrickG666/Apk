package com.eroprofile.app.data.repository

import com.eroprofile.app.data.models.Category
import com.eroprofile.app.data.models.Video
import com.eroprofile.app.data.scraper.EroProfileScraper

class VideoRepository {

    private val scraper = EroProfileScraper()

    suspend fun getVideos(sort: String = EroProfileScraper.SORT_RECENT, page: Int = 1): Result<List<Video>> =
        scraper.fetchVideos(sort, page)

    suspend fun searchVideos(query: String, page: Int = 1): Result<List<Video>> =
        scraper.searchVideos(query, page)

    suspend fun getCategoryVideos(categoryUrl: String, page: Int = 1): Result<List<Video>> =
        scraper.fetchCategoryVideos(categoryUrl, page)

    suspend fun getCategories(): Result<List<Category>> =
        scraper.fetchCategories()
}
