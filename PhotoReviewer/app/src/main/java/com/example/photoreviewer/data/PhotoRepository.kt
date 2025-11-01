package com.example.photoreviewer.data

import android.app.Application
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.example.photoreviewer.viewmodel.PhotoType
import com.tencent.mmkv.MMKV

class PhotoRepository(private val application: Application) {

    private val mmkv = MMKV.defaultMMKV()

    fun getDeletedPhotoCount(): Int {
        return mmkv.decodeInt("deleted_photo_count", 0)
    }

    fun getDeletedPhotoSize(): Long {
        return mmkv.decodeLong("deleted_photo_size", 0L)
    }

    fun incrementDeletedPhotoCount(count: Int) {
        val currentCount = getDeletedPhotoCount()
        mmkv.encode("deleted_photo_count", currentCount + count)
    }

    fun incrementDeletedPhotoSize(size: Long) {
        val currentSize = getDeletedPhotoSize()
        mmkv.encode("deleted_photo_size", currentSize + size)
    }

    fun getFileSize(uri: Uri): Long? {
        return try {
            application.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun deleteVideo(videoUri: Uri): Boolean {
        return try {
            val rowsDeleted = application.contentResolver.delete(videoUri, null, null)
            rowsDeleted > 0
        } catch (e: Exception) {
            Log.e("PhotoRepository", "Error deleting video: $videoUri", e)
            false
        }
    }

    fun getPhotos(photoType: PhotoType): List<Uri> {
        val photoUris = mutableListOf<Uri>()
        val contentResolver = application.contentResolver

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

        Log.d("PhotoRepository", "Selection: $selection, Args: ${selectionArgs?.joinToString()}")

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
                    Log.d("PhotoRepository", "Found video: $contentUri")
                }
            }
        }
        Log.d("PhotoRepository", "Found ${photoUris.size} photos")
        return photoUris
    }
}
