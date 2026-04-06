package com.eroprofile.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
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
import com.eroprofile.app.data.models.Video

class VideoAdapter(
    private val onVideoClick: (Video) -> Unit
) : ListAdapter<Video, VideoAdapter.VideoViewHolder>(VideoDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_video, parent, false)
        return VideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val imgThumbnail: ImageView = itemView.findViewById(R.id.imgThumbnail)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvCategory: TextView = itemView.findViewById(R.id.tvCategory)
        private val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)
        private val tvViews: TextView = itemView.findViewById(R.id.tvViews)
        private val tvRating: TextView = itemView.findViewById(R.id.tvRating)
        private val tvAuthor: TextView = itemView.findViewById(R.id.tvAuthor)
        private val tvHd: TextView = itemView.findViewById(R.id.tvHd)

        fun bind(video: Video) {
            tvTitle.text = video.title
            tvCategory.text = video.category
            tvCategory.visibility = if (video.category.isNotEmpty()) View.VISIBLE else View.GONE
            tvDuration.text = video.duration
            tvDuration.visibility = if (video.duration.isNotEmpty()) View.VISIBLE else View.GONE
            tvViews.text = if (video.views.isNotEmpty()) "${video.views} views" else ""
            tvRating.visibility = if (video.rating.isNotEmpty()) View.VISIBLE else View.GONE
            tvRating.text = video.rating
            tvAuthor.visibility = if (video.author.isNotEmpty()) View.VISIBLE else View.GONE
            tvAuthor.text = video.author
            tvHd.visibility = if (video.isHd) View.VISIBLE else View.GONE

            val cookies = runCatching {
                CookieManager.getInstance().getCookie("https://www.eroprofile.com") ?: ""
            }.getOrDefault("")

            Glide.with(itemView.context)
                .load(GlideUrl(
                    video.thumbnailUrl,
                    LazyHeaders.Builder()
                        .addHeader("Referer", "https://www.eroprofile.com/")
                        .apply { if (cookies.isNotEmpty()) addHeader("Cookie", cookies) }
                        .build()
                ))
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop()
                .placeholder(R.color.ep_surface_variant)
                .error(R.color.ep_surface_variant)
                .into(imgThumbnail)

            itemView.setOnClickListener { onVideoClick(video) }
        }
    }

    private class VideoDiffCallback : DiffUtil.ItemCallback<Video>() {
        override fun areItemsTheSame(oldItem: Video, newItem: Video) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Video, newItem: Video) = oldItem == newItem
    }
}
