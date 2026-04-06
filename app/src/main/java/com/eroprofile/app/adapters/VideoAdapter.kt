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
        private val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)
        private val tvViews: TextView = itemView.findViewById(R.id.tvViews)
        private val tvRating: TextView = itemView.findViewById(R.id.tvRating)
        private val tvAuthor: TextView = itemView.findViewById(R.id.tvAuthor)
        private val tvHd: TextView = itemView.findViewById(R.id.tvHd)

        fun bind(video: Video) {
            tvTitle.text = video.title

            if (video.duration.isNotEmpty()) {
                tvDuration.text = video.duration
                tvDuration.visibility = View.VISIBLE
            } else {
                tvDuration.visibility = View.GONE
            }

            if (video.views.isNotEmpty()) {
                tvViews.text = "${video.views} views"
            } else {
                tvViews.text = ""
            }

            if (video.rating.isNotEmpty()) {
                tvRating.text = video.rating
                tvRating.visibility = View.VISIBLE
            } else {
                tvRating.visibility = View.GONE
            }

            if (video.author.isNotEmpty()) {
                tvAuthor.text = video.author
                tvAuthor.visibility = View.VISIBLE
            } else {
                tvAuthor.visibility = View.GONE
            }

            tvHd.visibility = if (video.isHd) View.VISIBLE else View.GONE

            val glideUrl = GlideUrl(
                video.thumbnailUrl,
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
                .into(imgThumbnail)

            itemView.setOnClickListener { onVideoClick(video) }
        }
    }

    private class VideoDiffCallback : DiffUtil.ItemCallback<Video>() {
        override fun areItemsTheSame(oldItem: Video, newItem: Video) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Video, newItem: Video) = oldItem == newItem
    }
}
