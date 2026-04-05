package com.eroprofile.app.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.eroprofile.app.data.models.Video
import com.eroprofile.app.data.repository.VideoRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = VideoRepository(app)

    private val _results = MutableLiveData<List<Video>>()
    val results: LiveData<List<Video>> = _results

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    private var searchJob: Job? = null
    private var currentQuery = ""
    private var currentPage = 1
    private val allResults = mutableListOf<Video>()

    fun search(query: String) {
        if (query.trim() == currentQuery && currentPage > 1) return
        if (query.trim().isEmpty()) {
            _results.value = emptyList()
            return
        }

        searchJob?.cancel()
        currentQuery = query.trim()
        currentPage = 1
        allResults.clear()

        searchJob = viewModelScope.launch {
            delay(400)
            fetchResults()
        }
    }

    fun loadNextPage() {
        if (_isLoading.value != true && currentQuery.isNotEmpty()) {
            currentPage++
            viewModelScope.launch { fetchResults() }
        }
    }

    private suspend fun fetchResults() {
        _isLoading.value = true
        _error.value = null

        val result = repository.searchVideos(currentQuery, currentPage)
        _isLoading.value = false

        result.onSuccess { videos ->
            if (currentPage == 1) allResults.clear()
            allResults.addAll(videos)
            _results.value = allResults.toList()
        }.onFailure { e ->
            _error.value = e.message
            if (currentPage > 1) currentPage--
        }
    }
}
