package com.eroprofile.app.ui.categories

import com.eroprofile.app.data.scraper.WebViewScraper
import com.eroprofile.app.ui.BaseWebFragment

class CategoryVideosFragment : BaseWebFragment() {
    override val initialUrl get() = arguments?.getString("url") ?: WebViewScraper.BASE_URL
}
