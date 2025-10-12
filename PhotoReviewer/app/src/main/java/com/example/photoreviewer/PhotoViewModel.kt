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
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Sealed class to represent the deletion request event
sealed class DeletionRequest {
    data class RequiresPendingIntent(val intent: PendingIntent) : DeletionRequest()
}

enum class PhotoType {
    ALL, IMAGES, GIFS, VIDEOS
}

class PhotoViewModel(application: Application) : AndroidViewModel(application) {

    private val mmkv = MMKV.defaultMMKV()

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

    fun clearDeletionList() {
        _photosToDelete.value = emptyList()
    }

    // Called after the user confirms deletion in our custom dialog
    fun requestDeleteMarkedPhotos(contentResolver: ContentResolver) {
        viewModelScope.launch {
            val pairsToDelete = _photosToDelete.value
            if (pairsToDelete.isEmpty()) return@launch

            val urisToDelete = pairsToDelete.map { it.first }

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
            val deletedPairs = _photosToDelete.value
            if (deletedPairs.isNotEmpty()) {
                val deletedUris = deletedPairs.map { it.first }.toSet()
                val currentPhotos = _photos.value
                val updatedPhotos = currentPhotos.filterNot { it in deletedUris }
                _photos.value = updatedPhotos

                val deletedCount = deletedPairs.size
                val currentDeletedCount = mmkv.decodeInt("deleted_photo_count", 0)
                mmkv.encode("deleted_photo_count", currentDeletedCount + deletedCount)

                var deletedSize = 0L
                for (pair in deletedPairs) {
                    deletedSize += getFileSize(pair.first) ?: 0
                }
                val currentDeletedSize = mmkv.decodeLong("deleted_photo_size", 0L)
                mmkv.encode("deleted_photo_size", currentDeletedSize + deletedSize)

                if (updatedPhotos.isEmpty()) {
                    loadPhotos()
                }
            }
            _photosToDelete.value = emptyList()
        }
    }

    fun favoritePhoto(uri: Uri) {
        // For now, just log this action. A real implementation would save this to a database.
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

    private fun getFileSize(uri: Uri): Long? {
        return try {
            getApplication<Application>().contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
        } catch (e: Exception) {
            null
        }
    }

    fun randomizePhotos(photoType: PhotoType = _photoType.value) {
        Log.d("PhotoViewModel", "randomizePhotos with type: $photoType")
        viewModelScope.launch {
            val photoUris = mutableListOf<Uri>()
            val contentResolver = getApplication<Application>().contentResolver

            val projection = when (photoType) {
                PhotoType.VIDEOS -> arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.MIME_TYPE)
                else -> arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.MIME_TYPE)
            }
            val sortOrder = when (photoType) {
                PhotoType.VIDEOS -> "${MediaStore.Video.Media.DATE_TAKEN} DESC"
                else -> "${MediaStore.Images.Media.DATE_TAKEN} DESC"
            }

            val selection = when (photoType) {
                PhotoType.IMAGES -> "${MediaStore.Images.Media.MIME_TYPE} = ? OR ${MediaStore.Images.Media.MIME_TYPE} = ?"
                PhotoType.GIFS -> "${MediaStore.Images.Media.MIME_TYPE} = ?"
                PhotoType.VIDEOS -> "${MediaStore.Video.Media.MIME_TYPE} LIKE ?"
                PhotoType.ALL -> null
            }

            val selectionArgs = when (photoType) {
                PhotoType.IMAGES -> arrayOf("image/jpeg", "image/png")
                PhotoType.GIFS -> arrayOf("image/gif")
                PhotoType.VIDEOS -> arrayOf("video/%")
                PhotoType.ALL -> null
            }

            val queryUri = when (photoType) {
                PhotoType.VIDEOS -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                else -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            Log.d("PhotoViewModel", "Selection: $selection, Args: ${selectionArgs?.joinToString()}")

            contentResolver.query(
                queryUri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = when (photoType) {
                    PhotoType.VIDEOS -> cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    else -> cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                }
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val contentUri = ContentUris.withAppendedId(
                        queryUri,
                        id
                    )
                    photoUris.add(contentUri)
                    if (photoType == PhotoType.VIDEOS) {
                        Log.d("PhotoViewModel", "Found video: $contentUri")
                    }
                }
            }
            Log.d("PhotoViewModel", "Found ${photoUris.size} photos")
            _photos.value = photoUris.shuffled().take(16) // Keep the change to 16 photos
        }
    }
}