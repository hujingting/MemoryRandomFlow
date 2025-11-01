package com.example.photoreviewer.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.photoreviewer.data.PhotoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VideoViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PhotoRepository(application)

    private val _videos = MutableStateFlow<List<Uri>>(emptyList())
    val videos = _videos.asStateFlow()

    fun deleteVideo(videoUri: Uri) {
        viewModelScope.launch {
            val isDeleted = repository.deleteVideo(videoUri)
            if (isDeleted) {
                val currentList = _videos.value
                _videos.value = currentList.filter { it != videoUri }
            }
        }
    }

    fun loadVideos() {
        viewModelScope.launch {
            val allVideoUris = repository.getPhotos(PhotoType.VIDEOS)
            val randomVideos = allVideoUris.shuffled().take(20)
            _videos.value = randomVideos
        }
    }
}
