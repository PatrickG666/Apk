package com.eroprofile.app.ui.home

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.fragment.app.Fragment

class HomeFragment : Fragment() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val wv = WebView(requireContext())
        wv.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        wv.setBackgroundColor(Color.RED) // visibile se WebView renderizza
        wv.settings.javaScriptEnabled = true
        // Prima carichiamo HTML locale per testare il rendering
        wv.loadData(
            "<html><body style='background:#FF6600;margin:0;display:flex;align-items:center;justify-content:center;height:100vh;'>" +
            "<h1 style='color:white;font-size:40px;text-align:center;'>WEBVIEW OK</h1></body></html>",
            "text/html", "UTF-8"
        )
        return wv
    }
}
