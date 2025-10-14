package com.example.photoreviewer.viewmodel

import android.app.Application
import android.app.PendingIntent
import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.photoreviewer.data.PhotoRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsInfo(val deletedCount: Int, val deletedSize: Long)

class PhotoViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PhotoRepository(application)

    private val _photoType = MutableStateFlow(PhotoType.ALL)
    val photoType = _photoType.asStateFlow()

    private val _photos = MutableStateFlow<List<Uri>>(emptyList())
    val photos = _photos.asStateFlow()

    private val _photosToDelete = MutableStateFlow<List<Pair<Uri, Int>>>(emptyList())
    val photosToDelete = _photosToDelete.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo = _canUndo.asStateFlow()

    private val _photoToRestore = MutableSharedFlow<Pair<Uri, Int>>()
    val photoToRestore = _photoToRestore.asSharedFlow()

    private val _deletionRequest = MutableSharedFlow<DeletionRequest>()
    val deletionRequest = _deletionRequest.asSharedFlow()

    private val _settingsInfo = MutableStateFlow<SettingsInfo?>(null)
    val settingsInfo = _settingsInfo.asStateFlow()

    fun showSettings() {
        val deletedCount = repository.getDeletedPhotoCount()
        val deletedSize = repository.getDeletedPhotoSize()
        _settingsInfo.value = SettingsInfo(deletedCount, deletedSize)
    }

    fun onSettingsShown() {
        _settingsInfo.value = null
    }

    fun markPhotoForDeletion(uri: Uri, position: Int) {
        _photosToDelete.value = _photosToDelete.value + Pair(uri, position)
        _canUndo.value = true
    }

    fun undoLastDeletion() {
        viewModelScope.launch {
            val currentDeletions = _photosToDelete.value
            if (currentDeletions.isNotEmpty()) {
                val pairToRestore = currentDeletions.last()
                _photosToDelete.value = currentDeletions.dropLast(1)
                _photoToRestore.emit(pairToRestore)
                if (_photosToDelete.value.isEmpty()) {
                    _canUndo.value = false
                }
            }
        }
    }

    fun deleteCurrentPhoto(contentResolver: ContentResolver, uri: Uri?) {
        if (uri == null) return
        markPhotoForDeletion(uri, -1) // Position is not important here
        removePhotoFromList(uri)
        requestDeleteMarkedPhotos(contentResolver)
    }

    fun requestDeleteMarkedPhotos(contentResolver: ContentResolver) {
        viewModelScope.launch {
            val pairsToDelete = _photosToDelete.value
            if (pairsToDelete.isEmpty()) return@launch

            val urisToDelete = pairsToDelete.map { it.first }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val pendingIntent = MediaStore.createTrashRequest(contentResolver, urisToDelete, true)
                    _deletionRequest.emit(DeletionRequest.RequiresPendingIntent(pendingIntent))
                } else {
                    for (uri in urisToDelete) {
                        contentResolver.delete(uri, null, null)
                    }
                    finalizeDeletion()
                }
            } catch (e: Exception) {
                println("Error requesting deletion: $e")
            }
        }
    }

    fun finalizeDeletion() {
        viewModelScope.launch {
            val deletedPairs = _photosToDelete.value
            if (deletedPairs.isNotEmpty()) {
                val deletedUris = deletedPairs.map { it.first }.toSet()
                val currentPhotos = _photos.value
                val updatedPhotos = currentPhotos.filterNot { it in deletedUris }
                _photos.value = updatedPhotos

                val deletedCount = deletedPairs.size
                repository.incrementDeletedPhotoCount(deletedCount)

                var deletedSize = 0L
                for (pair in deletedPairs) {
                    deletedSize += repository.getFileSize(pair.first) ?: 0
                }
                repository.incrementDeletedPhotoSize(deletedSize)

                if (updatedPhotos.isEmpty()) {
                    loadPhotos()
                }
            }
            _photosToDelete.value = emptyList()
        }
    }

    fun favoritePhoto(uri: Uri) {
        Log.d("PhotoViewModel", "Favorited photo: $uri")
    }

    fun removePhotoFromList(uri: Uri) {
        _photos.value = _photos.value.filterNot { it == uri }
    }

    fun setPhotoType(photoType: PhotoType) {
        _photoType.value = photoType
        randomizePhotos(photoType)
    }

    fun loadPhotos() {
        if (_photos.value.isNotEmpty()) {
            return
        }
        randomizePhotos(_photoType.value)
    }

    fun randomizePhotos(photoType: PhotoType = _photoType.value) {
        viewModelScope.launch {
            val photoUris = repository.getPhotos(photoType)
            _photos.value = photoUris.shuffled().take(16)
        }
    }
}
