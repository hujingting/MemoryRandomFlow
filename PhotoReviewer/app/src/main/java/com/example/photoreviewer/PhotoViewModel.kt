package com.example.photoreviewer

import android.app.Application
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import android.util.Log
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Sealed class to represent the deletion request event
sealed class DeletionRequest {
    data class RequiresPendingIntent(val intent: PendingIntent) : DeletionRequest()
}

class PhotoViewModel(application: Application) : AndroidViewModel(application) {

    private val _photos = MutableStateFlow<List<Uri>>(emptyList())
    val photos = _photos.asStateFlow()

    private val _photosToDelete = MutableStateFlow<Set<Uri>>(emptySet())
    val photosToDelete = _photosToDelete.asStateFlow()

    private val _deletionRequest = MutableSharedFlow<DeletionRequest>()
    val deletionRequest = _deletionRequest.asSharedFlow()

    fun markPhotoForDeletion(uri: Uri) {
        _photosToDelete.value = _photosToDelete.value + uri
    }

    fun clearDeletionList() {
        _photosToDelete.value = emptySet()
    }

    // Called after the user confirms deletion in our custom dialog
    fun requestDeleteMarkedPhotos(contentResolver: ContentResolver) {
        viewModelScope.launch {
            val urisToDelete = _photosToDelete.value.toList()
            if (urisToDelete.isEmpty()) return@launch

            try {
                Log.d("PhotoViewModel", "Android SDK: ${Build.VERSION.SDK_INT}")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // Android 11+ supports trashing
                    Log.d("PhotoViewModel", "Using createTrashRequest for Android R+")
                    val pendingIntent = MediaStore.createTrashRequest(contentResolver, urisToDelete, true)
                    _deletionRequest.emit(DeletionRequest.RequiresPendingIntent(pendingIntent))
                } else { // Pre-Android 11
                    Log.d("PhotoViewModel", "Using permanent delete for pre-Android R")
                    // Fallback to permanent deletion as trash is not supported
                    for (uri in urisToDelete) {
                        val deletedRows = contentResolver.delete(uri, null, null)
                        Log.d("PhotoViewModel", "Deleted URI: $uri, rows affected: $deletedRows")
                    }
                    finalizeDeletion()
                }
            } catch (e: Exception) {
                println("Error requesting deletion: $e")
                // Optionally, provide feedback to the user about the error
            }
        }
    }

    // Called after the system dialog (from PendingIntent) returns a success
    fun finalizeDeletion() {
        viewModelScope.launch {
            val deletedUris = _photosToDelete.value
            if (deletedUris.isNotEmpty()) {
                val currentPhotos = _photos.value
                val updatedPhotos = currentPhotos.filterNot { it in deletedUris }
                _photos.value = updatedPhotos

                if (updatedPhotos.isEmpty()) {
                    loadPhotos()
                }
            }
            _photosToDelete.value = emptySet()
        }
    }

    fun loadPhotos() {
        viewModelScope.launch {
            val photoUris = mutableListOf<Uri>()
            val contentResolver = getApplication<Application>().contentResolver

            val projection = arrayOf(MediaStore.Images.Media._ID)
            val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    photoUris.add(contentUri)
                }
            }
            _photos.value = photoUris.shuffled().take(16) // Keep the change to 16 photos
        }
    }
}