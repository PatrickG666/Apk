package com.eroprofile.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.eroprofile.app.R

class LoadingFooterAdapter : RecyclerView.Adapter<LoadingFooterAdapter.Holder>() {

    private var visible = false

    fun show() {
        if (!visible) {
            visible = true
            notifyItemInserted(0)
        }
    }

    fun hide() {
        if (visible) {
            visible = false
            notifyItemRemoved(0)
        }
    }

    override fun getItemCount() = if (visible) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_loading_footer, parent, false)
    )

    override fun onBindViewHolder(holder: Holder, position: Int) {}

    class Holder(view: View) : RecyclerView.ViewHolder(view)
}
