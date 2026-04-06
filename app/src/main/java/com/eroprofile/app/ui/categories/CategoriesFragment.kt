package com.eroprofile.app.ui.categories

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.eroprofile.app.R
import com.eroprofile.app.adapters.CategoryAdapter
import com.eroprofile.app.data.models.Category
import com.eroprofile.app.databinding.FragmentCategoriesBinding
import org.json.JSONArray

class CategoriesFragment : Fragment() {

    private var _binding: FragmentCategoriesBinding? = null
    private val binding get() = _binding!!

    private var webView: WebView? = null
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var categoryAdapter: CategoryAdapter

    private var pollAttempts = 0
    private val maxPollAttempts = 15
    private val pollIntervalMs = 2000L
    private val pollStartDelayMs = 3000L

    private val loadUrl = "https://www.eroprofile.com/m/videos/home"

    private val extractNichesJs = """
        (function() {
            try {
                var results = [];
                var seen = {};
                document.querySelectorAll('a[href]').forEach(function(a) {
                    var href = a.href || '';
                    if (!href.match(/niche|\/tag\/|\/categor|\/niche\//i)) return;
                    if (seen[href]) return;
                    seen[href] = true;
                    var name = a.textContent.trim();
                    if (!name || name.length < 2) return;
                    var img = a.querySelector('img');
                    var thumb = img ? (img.getAttribute('data-src') || img.src || '') : '';
                    results.push({name:name, url:href, thumb:thumb});
                });
                if (results.length === 0) {
                    document.querySelectorAll('select').forEach(function(sel) {
                        Array.from(sel.options).forEach(function(opt) {
                            if (!opt.value || opt.value === '' || opt.value === '0') return;
                            var url = opt.value.startsWith('http') ? opt.value : window.location.origin + opt.value;
                            if (seen[url]) return;
                            seen[url] = true;
                            results.push({name:opt.text.trim(), url:url, thumb:''});
                        });
                    });
                }
                return JSON.stringify(results);
            } catch(e) { return '[]'; }
        })();
    """.trimIndent()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCategoriesBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupWebView()

        binding.progressBar.visibility = View.VISIBLE
        webView?.loadUrl(loadUrl)
    }

    private fun setupRecyclerView() {
        categoryAdapter = CategoryAdapter { niche ->
            val bundle = Bundle().apply { putString("url", niche.url) }
            findNavController().navigate(R.id.nav_category_videos, bundle)
        }
        binding.recyclerCategories.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = categoryAdapter
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val wv = WebView(requireContext())
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36"
        }
        wv.webChromeClient = WebChromeClient()
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                pollAttempts = 0
                handler.postDelayed({ schedulePoll() }, pollStartDelayMs)
            }
        }
        wv.addJavascriptInterface(object {
            @JavascriptInterface
            fun onNiches(json: String) {
                requireActivity().runOnUiThread { handleNiches(json) }
            }
        }, "Android")

        val lp = ConstraintLayout.LayoutParams(1, 1)
        binding.root.addView(wv, lp)
        webView = wv
    }

    private fun schedulePoll() {
        if (pollAttempts >= maxPollAttempts) {
            binding.progressBar.visibility = View.GONE
            return
        }
        pollAttempts++
        webView?.evaluateJavascript(extractNichesJs) { raw ->
            val json = unescapeJs(raw)
            val hasItems = try { JSONArray(json).length() > 0 } catch (_: Exception) { false }
            if (hasItems) {
                requireActivity().runOnUiThread { handleNiches(json) }
            } else {
                handler.postDelayed({ schedulePoll() }, pollIntervalMs)
            }
        }
    }

    private fun unescapeJs(raw: String?): String {
        if (raw == null) return "[]"
        return if (raw.startsWith("\"") && raw.endsWith("\"")) {
            raw.removeSurrounding("\"")
                .replace("\\\"", "\"")
                .replace("\\/", "/")
                .replace("\\n", "")
        } else raw
    }

    private fun handleNiches(json: String) {
        try {
            val arr = JSONArray(json)
            val niches = mutableListOf<Category>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val name = o.optString("name", "").trim()
                val url = o.optString("url", "")
                if (name.isEmpty() || url.isEmpty()) continue
                val thumb = o.optString("thumb", "")
                niches.add(Category(name = name, url = url, thumbnailUrl = thumb))
            }
            if (niches.isNotEmpty()) {
                binding.progressBar.visibility = View.GONE
                categoryAdapter.submitList(niches)
            }
        } catch (_: Exception) {
            // ignore parse errors, keep polling
        }
    }

    override fun onDestroyView() {
        handler.removeCallbacksAndMessages(null)
        webView?.destroy()
        webView = null
        _binding = null
        super.onDestroyView()
    }
}
