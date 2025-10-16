package com.example.photoreviewer.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.photoreviewer.R
import com.example.photoreviewer.ui.ZoomableImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

class PhotoDetailActivity : AppCompatActivity() {

    private lateinit var fileSizeTextView: TextView
    private lateinit var sizeTextView: TextView
    private lateinit var dateTextView: TextView
    private lateinit var locationTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        window.requestFeature(Window.FEATURE_CONTENT_TRANSITIONS)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_detail)
        postponeEnterTransition()

        val photoUri: Uri? = intent?.data
        val imageView: ZoomableImageView = findViewById(R.id.photo_detail_view)
        fileSizeTextView = findViewById(R.id.file_size_text_view)
        sizeTextView = findViewById(R.id.size_text_view)
        dateTextView = findViewById(R.id.date_text_view)
        locationTextView = findViewById(R.id.location_text_view)

        if (photoUri != null) {
            imageView.load(photoUri) {
                listener(
                    onSuccess = { _, _ -> startPostponedEnterTransition() },
                    onError = { _, _ -> startPostponedEnterTransition() }
                )
            }
            loadPhotoMetadata(photoUri)

            findViewById<View>(R.id.share_button).setOnClickListener {
                sharePhoto(photoUri)
            }
        } else {
            finish()
        }
    }

    private fun sharePhoto(uri: Uri) {
        val shareIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, uri)
            type = contentResolver.getType(uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share Photo"))
    }

    private fun loadPhotoMetadata(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val dimensions = getImageDimensions(uri)
            val fileSize = getFileSize(uri)
            var date: String? = null
            var location: String? = null

            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val exifInterface = ExifInterface(inputStream)
                    date = getDateTime(exifInterface)
                    location = getGeoLocation(exifInterface)
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }

            withContext(Dispatchers.Main) {
                updateMetadataUI(dimensions, fileSize, date, location)
            }
        }
    }

    private fun updateMetadataUI(dimensions: Pair<Int, Int>?, fileSize: Long?, date: String?, location: String?) {
        if (fileSize != null && fileSize > 0) {
            fileSizeTextView.text = "File Size: ${formatFileSize(fileSize)}"
            fileSizeTextView.visibility = View.VISIBLE
        } else {
            fileSizeTextView.visibility = View.GONE
        }

        if (dimensions != null && dimensions.first > 0 && dimensions.second > 0) {
            sizeTextView.text = "Size: ${dimensions.first} x ${dimensions.second}"
            sizeTextView.visibility = View.VISIBLE
        } else {
            sizeTextView.visibility = View.GONE
        }

        if (date != null) {
            dateTextView.text = "Date: $date"
            dateTextView.visibility = View.VISIBLE
        } else {
            dateTextView.visibility = View.GONE
        }

        if (location != null) {
            locationTextView.text = "Location: $location"
            locationTextView.visibility = View.VISIBLE
        } else {
            locationTextView.visibility = View.GONE
        }
    }

    private fun getFileSize(uri: Uri): Long? {
        return try {
            contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    private fun formatFileSize(size: Long): String {
        val kb = size / 1024
        val mb = kb / 1024
        return when {
            mb > 0 -> "$mb MB"
            kb > 0 -> "$kb KB"
            else -> "$size Bytes"
        }
    }

    private fun getImageDimensions(uri: Uri): Pair<Int, Int>? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
            Pair(options.outWidth, options.outHeight)
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    private fun getDateTime(exifInterface: ExifInterface): String? {
        val dateTime = exifInterface.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exifInterface.getAttribute(ExifInterface.TAG_DATETIME)
        if (dateTime == null) return null

        // EXIF format: "YYYY:MM:DD HH:MM:SS"
        val parser = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault())
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return try {
            parser.parse(dateTime)?.let { formatter.format(it) }
        } catch (e: Exception) {
            // If parsing fails, return the raw string
            dateTime
        }
    }

    private fun getGeoLocation(exifInterface: ExifInterface): String? {
        return try {
            val latLong = exifInterface.latLong
            if (latLong != null) {
                val geocoder = Geocoder(this@PhotoDetailActivity, Locale.getDefault())
                val addresses = geocoder.getFromLocation(latLong[0], latLong[1], 1)
                if (addresses?.isNotEmpty() == true) {
                    val address = addresses[0]
                    listOfNotNull(address.locality, address.adminArea, address.countryName).joinToString(", ")
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    companion object {
        fun newIntent(context: Context, photoUri: Uri): Intent {
            return Intent(context, PhotoDetailActivity::class.java).apply {
                data = photoUri
            }
        }
    }
}