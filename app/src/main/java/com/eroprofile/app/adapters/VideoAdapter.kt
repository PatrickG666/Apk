package com.eroprofile.app.adapters

import android.graphics.drawable.Drawable
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.eroprofile.app.R
import com.eroprofile.app.data.models.Video
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VideoAdapter(
    private val onVideoClick: (Video) -> Unit
) : ListAdapter<Video, VideoAdapter.VideoViewHolder>(VideoDiffCallback()) {

    private val logFile = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "ep_debug.txt"
    )
    private var logged = false  // log only first failure to avoid spam

    private fun log(msg: String) {
        val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        runCatching { logFile.appendText("[$ts][Glide] $msg\n") }
    }

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
            tvDuration.text = video.duration
            tvDuration.visibility = if (video.duration.isNotEmpty()) View.VISIBLE else View.GONE
            tvViews.text = if (video.views.isNotEmpty()) "${video.views} views" else ""
            tvRating.visibility = if (video.rating.isNotEmpty()) View.VISIBLE else View.GONE
            tvRating.text = video.rating
            tvAuthor.visibility = if (video.author.isNotEmpty()) View.VISIBLE else View.GONE
            tvAuthor.text = video.author
            tvHd.visibility = if (video.isHd) View.VISIBLE else View.GONE

            Glide.with(itemView.context)
                .load(video.thumbnailUrl)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .centerCrop()
                .placeholder(R.color.ep_surface_variant)
                .error(R.color.ep_surface_variant)
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?, model: Any?,
                        target: Target<Drawable>, isFirstResource: Boolean
                    ): Boolean {
                        if (!logged) {
                            logged = true
                            log("FAIL url=${video.thumbnailUrl}")
                            log("     causes=${e?.causes?.joinToString { it.javaClass.simpleName + ": " + it.message }}")
                        }
                        return false
                    }
                    override fun onResourceReady(
                        resource: Drawable, model: Any, target: Target<Drawable>?,
                        dataSource: DataSource, isFirstResource: Boolean
                    ): Boolean {
                        if (!logged) { logged = true; log("OK url=${video.thumbnailUrl}") }
                        return false
                    }
                })
                .into(imgThumbnail)

            itemView.setOnClickListener { onVideoClick(video) }
        }
    }

    private class VideoDiffCallback : DiffUtil.ItemCallback<Video>() {
        override fun areItemsTheSame(oldItem: Video, newItem: Video) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Video, newItem: Video) = oldItem == newItem
    }
}
