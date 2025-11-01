
package com.example.photoreviewer.ui

import android.net.Uri
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class VideoPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    var videoUris: List<Uri> = emptyList()

    fun submitList(newUris: List<Uri>) {
        val oldUris = videoUris

        // Simple diffing for item removal
        if (newUris.size < oldUris.size) {
            val removedUri = oldUris.find { it !in newUris }
            if (removedUri != null) {
                val removedIndex = oldUris.indexOf(removedUri)
                videoUris = newUris
                notifyItemRemoved(removedIndex)
                return
            }
        }

        videoUris = newUris
        notifyDataSetChanged()
    }

    override fun getItemId(position: Int): Long {
        return videoUris[position].hashCode().toLong()
    }

    override fun containsItem(itemId: Long): Boolean {
        return videoUris.any { it.hashCode().toLong() == itemId }
    }

    override fun getItemCount(): Int = videoUris.size

    override fun createFragment(position: Int): Fragment {
        val videoUri = videoUris[position]
        return VideoPlayerFragment.newInstance(videoUri)
    }
}
