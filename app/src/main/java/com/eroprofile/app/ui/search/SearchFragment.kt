package com.eroprofile.app.ui.search

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.eroprofile.app.R
import com.eroprofile.app.data.scraper.WebViewScraper
import com.eroprofile.app.ui.BaseWebFragment
import java.net.URLEncoder

class SearchFragment : BaseWebFragment() {

    override val initialUrl = "${WebViewScraper.BASE_URL}/m/video/list?search="

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        // Build the WebView via parent (returns the WebView itself)
        val webView = super.onCreateView(inflater, container, savedInstanceState)

        // Search bar
        val searchBar = inflater.inflate(R.layout.search_bar, null, false)
        val searchInput = searchBar.findViewById<EditText>(R.id.searchInput)
        val clearBtn = searchBar.findViewById<ImageView>(R.id.btnClear)

        searchInput.doAfterTextChanged { text ->
            clearBtn.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch(searchInput.text.toString())
                hideKeyboard(searchInput)
                true
            } else false
        }
        clearBtn.setOnClickListener {
            searchInput.text.clear()
            loadUrl("${WebViewScraper.BASE_URL}/m/video/list?search=")
        }

        return LinearLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
            addView(searchBar, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            addView(webView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            ))
        }
    }

    private fun performSearch(query: String) {
        if (query.trim().isEmpty()) return
        loadUrl("${WebViewScraper.BASE_URL}/m/video/list?search=${URLEncoder.encode(query.trim(), "UTF-8")}")
    }

    private fun hideKeyboard(view: View) {
        ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(view.windowToken, 0)
    }
}
