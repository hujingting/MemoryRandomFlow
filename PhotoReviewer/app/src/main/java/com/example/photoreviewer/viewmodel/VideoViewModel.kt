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

    fun loadVideos() {
        viewModelScope.launch {
            val videoUris = repository.getPhotos(PhotoType.VIDEOS)
            _videos.value = videoUris
        }
    }
}
