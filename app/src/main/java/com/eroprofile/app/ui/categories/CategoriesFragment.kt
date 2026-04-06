package com.eroprofile.app.ui.categories

import com.eroprofile.app.data.scraper.WebViewScraper
import com.eroprofile.app.ui.BaseWebFragment

class CategoriesFragment : BaseWebFragment() {
    override val initialUrl = "${WebViewScraper.BASE_URL}/m/videos/home"
}
