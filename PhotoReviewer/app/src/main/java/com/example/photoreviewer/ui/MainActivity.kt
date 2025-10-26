package com.example.photoreviewer.ui

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.ImageDecoder
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.photoreviewer.viewmodel.PhotoType
import com.example.photoreviewer.R
import com.example.photoreviewer.databinding.ActivityMainBinding
import com.example.photoreviewer.viewmodel.PhotoViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: PhotoViewModel by viewModels()
    private lateinit var photoAdapter: PhotoAdapter

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            viewModel.loadPhotos()
        } else {
            binding.infoText.text = "需要读取照片和视频的权限"
            binding.infoText.visibility = View.VISIBLE
        }
    }

    private val deleteRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.finalizeDeletion()
        } else {
            viewModel.loadPhotos()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupEdgeToEdge()
        setupRecyclerView()
        observeViewModel()
        requestPermission()
        setupClickListeners()
    }

    private fun setupEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.chipGroupFilter) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, insets.top, view.paddingRight, view.paddingBottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun setupClickListeners() {
        binding.deleteButton.setOnClickListener {
            if (viewModel.photosToDelete.value.isNotEmpty()) {
                viewModel.requestDeleteMarkedPhotos(contentResolver)
            } else {
                val layoutManager = binding.photoRecyclerView.layoutManager as LinearLayoutManager
                val position = layoutManager.findFirstCompletelyVisibleItemPosition()
                if (position != RecyclerView.NO_POSITION) {
                    photoAdapter.getPhotoUri(position)?.let {
                        viewModel.deleteCurrentPhoto(contentResolver, it)
                    }
                }
            }
        }
        binding.randomizeButton.setOnClickListener {
            viewModel.randomizePhotos()
            binding.photoRecyclerView.scrollToPosition(0)
        }
        binding.undoButton.setOnClickListener { viewModel.undoLastDeletion() }
        binding.settingsFab.setOnClickListener { viewModel.showSettings() }
        binding.myCardView.setOnClickListener { binding.transformationLayout.finishTransform() }
        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val type = when (checkedIds[0]) {
                    R.id.chip_images -> PhotoType.IMAGES
                    R.id.chip_gifs -> PhotoType.GIFS
                    R.id.chip_videos -> PhotoType.VIDEOS
                    else -> PhotoType.ALL
                }
                viewModel.setPhotoType(type)
            }
        }
    }

    private fun setupRecyclerView() {
        photoAdapter = PhotoAdapter(
            contentResolver,
            onDelete = { uri, position -> viewModel.markPhotoForDeletion(uri, position) },
            onFavorite = { viewModel.favoritePhoto(it) }
        )

        binding.photoRecyclerView.apply {
            adapter = photoAdapter
            layoutManager =
                LinearLayoutManager(this@MainActivity, LinearLayoutManager.VERTICAL, false)
            itemAnimator = DefaultItemAnimator()
        }

        ItemTouchHelper(
            SwipeCallback(
                photoAdapter,
                viewModel
            )
        ).attachToRecyclerView(binding.photoRecyclerView)
        PagerSnapHelper().attachToRecyclerView(binding.photoRecyclerView)

        binding.photoRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            private var currentlyPlayingHolder: PhotoAdapter.VideoViewHolder? = null

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val position = layoutManager.findFirstCompletelyVisibleItemPosition()
                    if (position != RecyclerView.NO_POSITION) {
                        val holder = recyclerView.findViewHolderForAdapterPosition(position)
                        if (holder is PhotoAdapter.VideoViewHolder) {
                            currentlyPlayingHolder?.playerView?.player?.pause()
                            holder.playerView.player?.play()
                            currentlyPlayingHolder = holder
                        } else {
                            currentlyPlayingHolder?.playerView?.player?.pause()
                            currentlyPlayingHolder = null
                        }
                        photoAdapter.getPhotoUri(position)?.let { uri ->
                            updateBackgroundColor(uri)
                        }
                    }

                    if (layoutManager.findLastCompletelyVisibleItemPosition() == photoAdapter.itemCount - 1) {
                        showEndOfListDialog()
                    }
                }
            }
        })
    }


    private suspend fun bitmapPalette(uri: Uri): Palette = withContext(Dispatchers.IO) {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            MediaStore.Images.Media.getBitmap(contentResolver, uri)
        }
        return@withContext Palette.from(bitmap).generate()
    }

    private fun updateBackgroundColor(uri: Uri) {
        val type = contentResolver.getType(uri)
        if (type?.startsWith("image/") == true) {
            lifecycleScope.launch(Dispatchers.Main) {
                val palette = bitmapPalette(uri)
                palette.dominantSwatch?.rgb?.let { color ->
                    val oldColor = (binding.root.background as? ColorDrawable)?.color
                        ?: android.graphics.Color.TRANSPARENT
                    val newColor = ColorUtils.setAlphaComponent(color, 204)

                    val colorAnimation = ValueAnimator.ofArgb(oldColor, newColor)
                    colorAnimation.duration = 300 // milliseconds
                    colorAnimation.addUpdateListener { animator ->
                        val animatedColor = animator.animatedValue as Int
                        binding.root.setBackgroundColor(animatedColor)
                        window.statusBarColor = animatedColor
                    }
                    colorAnimation.start()
                }
            }
        }
    }

    private fun showEndOfListDialog() {
        if (supportFragmentManager.findFragmentByTag("endOfListDialog") != null) return

        MaterialAlertDialogBuilder(this)
            .setTitle("本组照片回顾完毕")
            .setMessage("要再来一组照片吗")
            .setPositiveButton("再来一组") { dialog, _ ->
                viewModel.randomizePhotos()
                binding.photoRecyclerView.scrollToPosition(0)
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.photos.collect { photos ->
                        binding.infoText.visibility =
                            if (photos.isEmpty()) View.VISIBLE else View.GONE
                        photoAdapter.submitList(photos)
                        if (photos.isNotEmpty()) {
                            updateBackgroundColor(photos[0])
                        }
                    }
                }
                launch {
                    viewModel.photoToRestore.collect { pair ->
                        photoAdapter.addPhoto(pair.second, pair.first)
                        binding.photoRecyclerView.scrollToPosition(pair.second)
                    }
                }
                launch {
                    viewModel.deletionRequest.collectLatest { request ->
                        if (request is com.example.photoreviewer.viewmodel.DeletionRequest.RequiresPendingIntent) {
                            val intentSenderRequest =
                                androidx.activity.result.IntentSenderRequest.Builder(request.intent)
                                    .build()
                            deleteRequestLauncher.launch(intentSenderRequest)
                        }
                    }
                }
                launch {
                    viewModel.settingsInfo.collectLatest { settingsInfo ->
                        settingsInfo?.let {
                            binding.deletedCountTextView.text = "已删除 ${it.deletedCount} 照片"
                            binding.deletedSizeTextView.text =
                                "总共释放 ${formatFileSize(it.deletedSize)} 空间"
                            binding.transformationLayout.startTransform()
                            viewModel.onSettingsShown()
                        }
                    }
                }
            }
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

    private fun requestPermission() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (permissions.all {
                ContextCompat.checkSelfPermission(
                    this,
                    it
                ) == PackageManager.PERMISSION_GRANTED
            }) {
            viewModel.loadPhotos()
        } else {
            permissionLauncher.launch(permissions)
        }
    }
}