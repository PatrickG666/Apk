package com.eroprofile.app.ui.categories

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.eroprofile.app.data.models.Category
import com.eroprofile.app.data.repository.VideoRepository
import kotlinx.coroutines.launch

class CategoriesViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = VideoRepository(app)

    private val _categories = MutableLiveData<List<Category>>()
    val categories: LiveData<List<Category>> = _categories

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    init {
        loadCategories()
    }

    fun loadCategories() {
        _isLoading.value = true
        viewModelScope.launch {
            val result = repository.getCategories()
            _isLoading.value = false
            result.onSuccess { _categories.value = it }
                .onFailure { _categories.value = emptyList() }
        }
    }
}
