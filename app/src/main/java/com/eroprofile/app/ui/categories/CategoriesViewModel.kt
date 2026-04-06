package com.eroprofile.app.ui.categories

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.eroprofile.app.data.models.Category
import com.eroprofile.app.data.repository.VideoRepository
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CategoriesViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = VideoRepository(app)
    private val logFile = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "ep_debug.txt"
    )

    private val _categories = MutableLiveData<List<Category>>()
    val categories: LiveData<List<Category>> = _categories

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    init {
        loadCategories()
    }

    private fun log(msg: String) {
        val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        runCatching { logFile.appendText("[$ts] $msg\n") }
    }

    fun loadCategories() {
        log("loadCategories: START")
        _isLoading.value = true
        viewModelScope.launch {
            log("loadCategories: calling repository.getCategories()")
            val result = repository.getCategories()
            _isLoading.value = false
            result
                .onSuccess {
                    log("loadCategories: SUCCESS count=${it.size}" +
                        if (it.isNotEmpty()) " first=${it[0].name} url=${it[0].url}" else "")
                    _categories.value = it
                }
                .onFailure {
                    log("loadCategories: FAILURE msg=${it.message}")
                    _categories.value = emptyList()
                }
        }
    }
}
