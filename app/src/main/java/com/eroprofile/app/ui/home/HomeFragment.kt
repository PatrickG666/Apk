package com.eroprofile.app.ui.home

import com.eroprofile.app.data.scraper.WebViewScraper
import com.eroprofile.app.ui.BaseWebFragment

class HomeFragment : BaseWebFragment() {
    override val initialUrl = "${WebViewScraper.BASE_URL}/m/video/list?sort=${WebViewScraper.SORT_RECENT}"
}
