package com.example.photoreviewer.ui.image

import android.app.Activity
import android.app.ActivityOptions
import android.content.ContentResolver
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.photoreviewer.R

class PhotoAdapter(
    private val contentResolver: ContentResolver,
    val onDelete: (Uri, Int) -> Unit,
    val onFavorite: (Uri) -> Unit
) : RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder>() {

    private val photos = mutableListOf<Uri>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_photo, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val photoUri = photos[position]
        holder.bind(photoUri)
        holder.itemView.setOnClickListener {
            val context = holder.itemView.context
            val intent = PhotoDetailActivity.Companion.newIntent(context, photoUri)

//            val options = ActivityOptions.makeSceneTransitionAnimation(
//                context as Activity,
//                holder.imageView,
//                "photo_transition"
//            )
//            context.startActivity(intent, options.toBundle())
            context.startActivity(intent)
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
}