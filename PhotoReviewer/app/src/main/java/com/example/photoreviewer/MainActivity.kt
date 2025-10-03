package com.example.photoreviewer

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.AdapterView.OnItemClickListener
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import coil.load
import com.example.photoreviewer.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: PhotoViewModel by viewModels()
    private lateinit var photoAdapter: PhotoAdapter

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.loadPhotos()
        } else {
            // Handle permission denial
            binding.infoText.text = "需要读取照片的权限"
            binding.infoText.visibility = View.VISIBLE
        }
    }

    private val deleteRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.finalizeDeletion()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewPager()
        observeViewModel()
        requestPermission()
        setupDeleteButton()
        setupRandomizeButton()
    }

    private fun setupViewPager() {
        photoAdapter = PhotoAdapter()
        binding.photoViewPager.adapter = photoAdapter
    }

    private fun setupDeleteButton() {
        binding.deleteButton.setOnClickListener {
            val currentPosition = binding.photoViewPager.currentItem
            val currentPhotoUri = photoAdapter.getPhotoUri(currentPosition)

            if (currentPhotoUri != null) {
                viewModel.clearDeletionList() // Ensure only one photo is marked
                viewModel.markPhotoForDeletion(currentPhotoUri)
                showDeleteConfirmationDialog()
            }
        }
    }

    private fun setupRandomizeButton() {
        binding.randomizeButton.setOnClickListener {
            viewModel.loadPhotos()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.photos.collect { photos ->
                        binding.infoText.visibility = if (photos.isEmpty()) View.VISIBLE else View.GONE
                        photoAdapter.submitList(photos)
                        // Reset ViewPager position if current position is out of bounds
                        if (binding.photoViewPager.currentItem >= photos.size && photos.isNotEmpty()) {
                            binding.photoViewPager.currentItem = photos.size - 1
                        } else if (photos.isEmpty()) {
                            binding.photoViewPager.currentItem = 0
                        }
                    }
                }

                launch {
                    viewModel.deletionRequest.collectLatest { request ->
                        when (request) {
                            is DeletionRequest.RequiresPendingIntent -> {
                                val intentSenderRequest = IntentSenderRequest.Builder(request.intent.intentSender).build()
                                deleteRequestLauncher.launch(intentSenderRequest)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showDeleteConfirmationDialog() {
        if (viewModel.photosToDelete.value.isEmpty()) return

        val photoCount = viewModel.photosToDelete.value.size // Will be 1 in this simplified flow

        MaterialAlertDialogBuilder(this)
            .setTitle("移至回收站")
            .setMessage("要将这张照片移至回收站吗？") // Singular message
            .setNegativeButton("取消") { dialog, _ ->
                viewModel.clearDeletionList()
                // No need to reload photos here, as deletion wasn't confirmed
                dialog.dismiss()
            }
            .setPositiveButton("移至回收站") { dialog, _ ->
                viewModel.requestDeleteMarkedPhotos(contentResolver)
                dialog.dismiss()
            }
            .show()
    }

    private fun requestPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                viewModel.loadPhotos()
            }
            else -> {
                permissionLauncher.launch(permission)
            }
        }
    }
}

class PhotoAdapter : RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder>() {

    private val photos = mutableListOf<Uri>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_photo, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(photos[position])
    }

    override fun getItemCount(): Int = photos.size

    fun submitList(newPhotos: List<Uri>) {
        photos.clear()
        photos.addAll(newPhotos)
        notifyDataSetChanged() // This is simple, for a real app use DiffUtil
    }

    fun getPhotoUri(position: Int): Uri? {
        return if (position >= 0 && position < photos.size) photos[position] else null
    }

    class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.photo_image_view)
        fun bind(uri: Uri) {
            imageView.load(uri)
        }
    }
}