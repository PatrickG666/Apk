package com.eroprofile.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.eroprofile.app.data.models.Video
import com.eroprofile.app.data.repository.VideoRepository
import com.eroprofile.app.data.scraper.WebViewScraper
import kotlinx.coroutines.launch

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = VideoRepository(app)

    private val _videos = MutableLiveData<List<Video>>()
    val videos: LiveData<List<Video>> = _videos

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    private var currentSort = WebViewScraper.SORT_RECENT
    private var currentPage = 1
    private var isLastPage = false
    private val allVideos = mutableListOf<Video>()

    private var categoryUrl: String? = null
    var categoryName: String? = null

    init {
        loadVideos()
    }

    fun loadVideos(sort: String = currentSort) {
        if (sort != currentSort) {
            currentSort = sort
            currentPage = 1
            isLastPage = false
            allVideos.clear()
        }
        fetchPage()
    }

    fun loadCategory(url: String, name: String) {
        categoryUrl = url
        categoryName = name
        currentPage = 1
        isLastPage = false
        allVideos.clear()
        fetchPage()
    }

    fun loadNextPage() {
        if (!isLastPage && _isLoading.value != true) {
            currentPage++
            fetchPage()
        }
    }

    fun refresh() {
        currentPage = 1
        isLastPage = false
        allVideos.clear()
        fetchPage()
    }

    private fun fetchPage() {
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            val result = categoryUrl?.let {
                repository.getCategoryVideos(it, currentPage)
            } ?: repository.getVideos(currentSort, currentPage)
            _isLoading.value = false

            result.onSuccess { newVideos ->
                if (newVideos.isEmpty()) {
                    isLastPage = true
                    if (currentPage == 1) {
                        _error.value = "Nessun video trovato"
                    }
                } else {
                    _error.value = null
                    if (currentPage == 1) allVideos.clear()
                    allVideos.addAll(newVideos)
                    _videos.value = allVideos.toList()
                }
            }.onFailure { e ->
                _error.value = e.message ?: "Errore sconosciuto"
                if (currentPage > 1) currentPage--
            }
        }
    }
}
