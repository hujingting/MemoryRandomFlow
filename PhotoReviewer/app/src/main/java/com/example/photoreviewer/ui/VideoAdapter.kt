package com.example.photoreviewer.ui

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView
import com.example.photoreviewer.R

class VideoAdapter(
    private val videos: MutableList<Uri> = mutableListOf()
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_video, parent, false)
        return VideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(videos[position])
    }

    override fun getItemCount(): Int = videos.size

    fun submitList(newVideos: List<Uri>) {
        videos.clear()
        videos.addAll(newVideos)
        notifyDataSetChanged()
    }

    fun getVideoUri(position: Int): Uri? {
        return if (position >= 0 && position < videos.size) videos[position] else null
    }

    inner class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val playerView: PlayerView = itemView.findViewById(R.id.player_view)

        fun bind(uri: Uri) {
            val player = ExoPlayer.Builder(itemView.context).build()
            playerView.player = player
            val mediaItem = MediaItem.fromUri(uri)
            player.setMediaItem(mediaItem)
            player.prepare()
        }
    }
}
