package com.example.photoreviewer.ui

import android.content.Context
import android.graphics.Canvas
import android.os.Vibrator
import android.view.View
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.photoreviewer.viewmodel.PhotoViewModel
import kotlin.math.abs

class SwipeCallback(private val adapter: PhotoAdapter, private val viewModel: PhotoViewModel) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        return if (viewHolder is PhotoAdapter.VideoViewHolder) {
            makeMovementFlags(0, 0)
        } else {
            makeMovementFlags(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT)
        }
    }

    override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
        return false
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.adapterPosition
        if (position == RecyclerView.NO_POSITION) return
        val photoUri = adapter.getPhotoUri(position) ?: return

        if (direction == ItemTouchHelper.LEFT || direction == ItemTouchHelper.RIGHT) {
            val vibrator = viewHolder.itemView.context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(50)
            adapter.onDelete(photoUri, position) // Pass position along with Uri
            viewModel.removePhotoFromList(photoUri)
        }
//        else if (direction == ItemTouchHelper.RIGHT) {
//            adapter.onFavorite(photoUri)
//            viewModel.removePhotoFromList(photoUri)
//        }
    }

    override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            if (viewHolder is PhotoAdapter.PhotoViewHolder) {
                val holder = viewHolder
                val itemView = holder.itemView
                val viewWidth = itemView.width.toFloat()
                val swipeProgress = (abs(dX) / viewWidth).coerceIn(0f, 1f)

                // 1. Rotation
                val rotationAngle = 20f
                itemView.rotation = (dX / viewWidth) * rotationAngle

                // 2. Scale Down
                val scaleFactor = 0.2f // Shrink by 20% at full swipe
                val scale = 1.0f - swipeProgress * scaleFactor
                itemView.scaleX = scale
                itemView.scaleY = scale

                // 3. Arc Motion (slight upward movement)
                val arcFactor = 0.1f // How much it moves up
                itemView.translationY = -swipeProgress * (itemView.height * arcFactor)

                // 4. Fade out the image view
                holder.imageView.alpha = 1.0f - swipeProgress

                // Indicator visibility
                if (dX > 0 || dX < 0) { // Swiping Right (Favorite)
                    holder.favoriteIndicator.visibility = View.GONE
                    holder.deleteIndicator.visibility = View.VISIBLE
                }
//                else if (dX < 0) { // Swiping Left (Delete)
//                    holder.deleteIndicator.visibility = View.VISIBLE
//                    holder.favoriteIndicator.visibility = View.GONE
//                }

                else {
                    holder.deleteIndicator.visibility = View.GONE
                    holder.favoriteIndicator.visibility = View.GONE
                }
            }
        }
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        val holder = viewHolder as PhotoAdapter.PhotoViewHolder
        val itemView = holder.itemView

        // Reset all animated properties
        itemView.rotation = 0f
        itemView.scaleX = 1.0f
        itemView.scaleY = 1.0f
        itemView.translationY = 0f
        holder.imageView.alpha = 1.0f

        // Hide indicators
        holder.deleteIndicator.visibility = View.GONE
        holder.favoriteIndicator.visibility = View.GONE
    }
}
