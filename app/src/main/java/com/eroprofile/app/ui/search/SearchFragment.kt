package com.eroprofile.app.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.eroprofile.app.R
import com.eroprofile.app.data.scraper.WebViewScraper
import com.eroprofile.app.ui.BaseWebFragment
import java.net.URLEncoder

class SearchFragment : BaseWebFragment() {

    override val initialUrl = "${WebViewScraper.BASE_URL}/m/video/list?search="

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        // Get the WebView layout from parent
        val root = super.onCreateView(inflater, container, savedInstanceState)

        // Inflate and prepend the search bar
        val searchBar = inflater.inflate(R.layout.search_bar, container, false)
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

        // Wrap: searchBar on top, webview below
        val wrapper = ConstraintLayout(requireContext())
        wrapper.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        // Return composite view
        val linear = android.widget.LinearLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            orientation = android.widget.LinearLayout.VERTICAL
            addView(searchBar)
            addView(root)
        }
        return linear
    }

    private fun performSearch(query: String) {
        if (query.trim().isEmpty()) return
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        loadUrl("${WebViewScraper.BASE_URL}/m/video/list?search=$encoded")
    }

    private fun hideKeyboard(view: View) {
        val imm = ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }
}
