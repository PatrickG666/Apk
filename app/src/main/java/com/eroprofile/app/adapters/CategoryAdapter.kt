package com.eroprofile.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.eroprofile.app.R
import com.eroprofile.app.data.models.Category

class CategoryAdapter(
    private val onCategoryClick: (Category) -> Unit
) : ListAdapter<Category, CategoryAdapter.CategoryViewHolder>(CategoryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val imgBg: ImageView = itemView.findViewById(R.id.imgCategoryBg)
        private val tvName: TextView = itemView.findViewById(R.id.tvCategoryName)

        fun bind(category: Category) {
            tvName.text = category.name

            if (category.thumbnailUrl.isNotEmpty()) {
                val glideUrl = GlideUrl(
                    category.thumbnailUrl,
                    LazyHeaders.Builder()
                        .addHeader("Referer", "https://www.eroprofile.com/")
                        .build()
                )
                Glide.with(itemView.context)
                    .load(glideUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .centerCrop()
                    .placeholder(R.color.ep_surface_variant)
                    .error(R.color.ep_surface_variant)
                    .into(imgBg)
            } else {
                imgBg.setBackgroundColor(
                    itemView.context.getColor(R.color.ep_surface_variant)
                )
            }

            itemView.setOnClickListener { onCategoryClick(category) }
        }
    }

    private class CategoryDiffCallback : DiffUtil.ItemCallback<Category>() {
        override fun areItemsTheSame(oldItem: Category, newItem: Category) = oldItem.url == newItem.url
        override fun areContentsTheSame(oldItem: Category, newItem: Category) = oldItem == newItem
    }
}
