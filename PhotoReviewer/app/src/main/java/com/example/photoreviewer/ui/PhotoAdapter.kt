package com.example.photoreviewer.ui

import android.app.Activity
import android.app.ActivityOptions
import android.content.ContentResolver
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.photoreviewer.PhotoDetailActivity
import com.example.photoreviewer.R

class PhotoAdapter(
    private val contentResolver: ContentResolver,
    val onDelete: (Uri, Int) -> Unit,
    val onFavorite: (Uri) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val photos = mutableListOf<Uri>()

    companion object {
        private const val VIEW_TYPE_PHOTO = 0
        private const val VIEW_TYPE_VIDEO = 1
    }

    override fun getItemViewType(position: Int): Int {
        val uri = photos[position]
        val type = contentResolver.getType(uri)
        return if (type?.startsWith("video") == true) {
            VIEW_TYPE_VIDEO
        } else {
            VIEW_TYPE_PHOTO
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_photo, parent, false)
        return if (viewType == VIEW_TYPE_VIDEO) {
            VideoViewHolder(view)
        } else {
            PhotoViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val photoUri = photos[position]
        when (holder) {
            is PhotoViewHolder -> holder.bind(photoUri)
            is VideoViewHolder -> holder.bind(photoUri)
        }
        holder.itemView.setOnClickListener {
            val context = holder.itemView.context
            val intent = PhotoDetailActivity.newIntent(context, photoUri)

            val options = ActivityOptions.makeSceneTransitionAnimation(
                context as Activity,
                if (holder is PhotoViewHolder) holder.imageView else holder.itemView.findViewById(R.id.player_view),
                "photo_transition"
            )
            context.startActivity(intent, options.toBundle())
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is VideoViewHolder) {
            holder.playerView.player?.release()
            holder.playerView.player = null
        }
    }

    override fun getItemCount(): Int = photos.size

    fun submitList(newPhotos: List<Uri>) {
        photos.clear()
        photos.addAll(newPhotos)
        notifyDataSetChanged()
    }

    fun getPhotoUri(position: Int): Uri? {
        return if (position >= 0 && position < photos.size) photos[position] else null
    }

    fun addPhoto(position: Int, uri: Uri) {
        photos.add(position, uri)
        notifyItemInserted(position)
    }

    fun removeAt(position: Int) {
        if (position < photos.size) {
            photos.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    inner class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.photo_image_view)
        val deleteIndicator: View = itemView.findViewById(R.id.delete_indicator_layout)
        val favoriteIndicator: View = itemView.findViewById(R.id.favorite_indicator_layout)

        fun bind(uri: Uri) {
            imageView.visibility = View.VISIBLE
            (itemView.findViewById(R.id.player_view) as PlayerView).visibility = View.GONE
            imageView.load(uri)
            deleteIndicator.visibility = View.GONE
            favoriteIndicator.visibility = View.GONE
        }
    }

    inner class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val playerView: PlayerView = itemView.findViewById(R.id.player_view)

        fun bind(uri: Uri) {
            (itemView.findViewById(R.id.photo_image_view) as ImageView).visibility = View.GONE
            playerView.visibility = View.VISIBLE
            val player = ExoPlayer.Builder(itemView.context).build()
            playerView.player = player
            val mediaItem = MediaItem.fromUri(uri)
            player.setMediaItem(mediaItem)
            player.prepare()
        }
    }
}
