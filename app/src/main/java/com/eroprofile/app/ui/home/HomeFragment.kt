package com.eroprofile.app.ui.home

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class HomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return TextView(requireContext()).apply {
            text = "FRAGMENT FUNZIONA\ncontainer=$container"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#FF6600"))
            gravity = Gravity.CENTER
            textSize = 20f
        }
    }
}
