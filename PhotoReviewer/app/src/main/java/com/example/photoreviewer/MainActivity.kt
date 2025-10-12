package com.example.photoreviewer

import android.Manifest
import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.photoreviewer.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

import android.content.Context
import android.os.Vibrator

import androidx.media3.ui.PlayerView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

import com.tencent.mmkv.MMKV

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
            // If user cancels deletion, reload photos to restore the swiped item
            viewModel.loadPhotos()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        observeViewModel()
        requestPermission()
        setupDeleteButton()
        setupRandomizeButton()
        setupUndoButton()
        setupSettingsFab()
        setupFilterChips()
    }

    private fun setupFilterChips() {
        binding.chipGroupFilter.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                when (checkedIds[0]) {
                    R.id.chip_all -> viewModel.setPhotoType(PhotoType.ALL)
                    R.id.chip_images -> viewModel.setPhotoType(PhotoType.IMAGES)
                    R.id.chip_gifs -> viewModel.setPhotoType(PhotoType.GIFS)
                    R.id.chip_videos -> viewModel.setPhotoType(PhotoType.VIDEOS)
                }
            }
        }
    }

    private fun setupSettingsFab() {
        binding.settingsFab.setOnClickListener {
            val mmkv = MMKV.defaultMMKV()
            val deletedCount = mmkv.decodeInt("deleted_photo_count", 0)
            binding.deletedCountTextView.text = "已删除 $deletedCount 照片"

            val deletedSize = mmkv.decodeLong("deleted_photo_size", 0L)
            binding.deletedSizeTextView.text = "总共释放 ${formatFileSize(deletedSize)} 空间"

            binding.transformationLayout.startTransform()
        }
        binding.myCardView.setOnClickListener {
            binding.transformationLayout.finishTransform()
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

    private fun setupUndoButton() {
        binding.undoButton.setOnClickListener {
            viewModel.undoLastDeletion()
        }
    }

    private fun setupRecyclerView() {
        photoAdapter = PhotoAdapter(
            contentResolver,
            onDelete = {
                uri, position -> viewModel.markPhotoForDeletion(uri, position)
            },
            onFavorite = {
                viewModel.favoritePhoto(it)
            }
        )

        binding.photoRecyclerView.apply {
            adapter = photoAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
            itemAnimator = DefaultItemAnimator()
        }

        val itemTouchHelper = ItemTouchHelper(SwipeCallback(photoAdapter, viewModel))
        itemTouchHelper.attachToRecyclerView(binding.photoRecyclerView)

        val snapHelper = PagerSnapHelper()
        snapHelper.attachToRecyclerView(binding.photoRecyclerView)

        binding.photoRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            private var currentlyPlayingHolder: PhotoAdapter.VideoViewHolder? = null

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
                        }
                    }
                    val lastVisibleItemPosition = layoutManager.findLastCompletelyVisibleItemPosition()
                    val itemCount = recyclerView.adapter?.itemCount ?: 0

                    if (itemCount > 0 && lastVisibleItemPosition == itemCount - 1) {
                        showEndOfListDialog()
                    }
                }
            }
        })
    }

    private fun showEndOfListDialog() {
        // Prevent showing dialog if one is already visible
        if (supportFragmentManager.findFragmentByTag("endOfListDialog") != null) {
            return
        }

        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("本组照片回顾完毕")
        builder.setMessage("要再来一组照片吗")
        builder.setPositiveButton("再来一组") { dialog, _ ->
            viewModel.randomizePhotos()
            binding.photoRecyclerView.scrollToPosition(0)
            dialog.dismiss()
        }
        builder.setNegativeButton("取消") { dialog, _ ->
            dialog.dismiss()
        }
        val dialog = builder.create()
        dialog.show()
    }

    private fun setupDeleteButton() {
        binding.deleteButton.setOnClickListener {
            if (viewModel.photosToDelete.value.isNotEmpty()) {
                // If there are photos marked for deletion, delete them
                viewModel.requestDeleteMarkedPhotos(contentResolver)
            } else {
                // Otherwise, delete the currently visible photo
                val layoutManager = binding.photoRecyclerView.layoutManager as LinearLayoutManager
                val position = layoutManager.findFirstCompletelyVisibleItemPosition()
                if (position != RecyclerView.NO_POSITION) {
                    photoAdapter.getPhotoUri(position)?.let { uri ->
                        // Mark for deletion (so it can be undone)
                        viewModel.markPhotoForDeletion(uri, position)
                        // Remove from the ViewModel, which will update the UI
                        viewModel.removePhotoFromList(uri)
                        // Request the actual deletion from the system
                        viewModel.requestDeleteMarkedPhotos(contentResolver)
                    }
                }
            }
        }
    }

    private fun setupRandomizeButton() {
        binding.randomizeButton.setOnClickListener {
            viewModel.randomizePhotos()
            binding.photoRecyclerView.scrollToPosition(0)
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.photos.collect { photos ->
                        binding.infoText.visibility = if (photos.isEmpty()) View.VISIBLE else View.GONE
                        photoAdapter.submitList(photos)
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

    private fun requestPermission() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val allGranted = permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

        if (allGranted) {
            viewModel.loadPhotos()
        } else {
            permissionLauncher.launch(permissions)
        }
    }
}

class PhotoAdapter(
    private val contentResolver: android.content.ContentResolver,
    val onDelete: (Uri, Int) -> Unit,
    val onFavorite: (Uri) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val photos = mutableListOf<Uri>()

    companion object {
        private const val VIEW_TYPE_PHOTO = 0
        private const val VIEW_TYPE_VIDEO = 1
    }

    override fun getItemViewType(position: Int): Int {
        val uri = photos[position]
        val type = contentResolver.getType(uri)
        return if (type?.startsWith("video") == true) {
            VIEW_TYPE_VIDEO
        } else {
            VIEW_TYPE_PHOTO
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_photo, parent, false)
        return if (viewType == VIEW_TYPE_VIDEO) {
            VideoViewHolder(view)
        } else {
            PhotoViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val photoUri = photos[position]
        when (holder) {
            is PhotoViewHolder -> holder.bind(photoUri)
            is VideoViewHolder -> holder.bind(photoUri)
        }
        holder.itemView.setOnClickListener {
            val context = holder.itemView.context
            val intent = PhotoDetailActivity.newIntent(context, photoUri)

            val options = ActivityOptions.makeSceneTransitionAnimation(
                context as Activity,
                if (holder is PhotoViewHolder) holder.imageView else holder.itemView.findViewById(R.id.player_view),
                "photo_transition"
            )
            context.startActivity(intent, options.toBundle())
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is VideoViewHolder) {
            holder.playerView.player?.release()
            holder.playerView.player = null
        }
    }

    override fun getItemCount(): Int = photos.size

    fun submitList(newPhotos: List<Uri>) {
        photos.clear()
        photos.addAll(newPhotos)
        notifyDataSetChanged()
    }

    fun getPhotoUri(position: Int): Uri? {
        return if (position >= 0 && position < photos.size) photos[position] else null
    }

    fun addPhoto(position: Int, uri: Uri) {
        photos.add(position, uri)
        notifyItemInserted(position)
    }

    fun removeAt(position: Int) {
        if (position < photos.size) {
            photos.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    inner class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.photo_image_view)
        val deleteIndicator: View = itemView.findViewById(R.id.delete_indicator_layout)
        val favoriteIndicator: View = itemView.findViewById(R.id.favorite_indicator_layout)

        fun bind(uri: Uri) {
            imageView.visibility = View.VISIBLE
            (itemView.findViewById(R.id.player_view) as PlayerView).visibility = View.GONE
            imageView.load(uri)
            deleteIndicator.visibility = View.GONE
            favoriteIndicator.visibility = View.GONE
        }
    }

    inner class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val playerView: PlayerView = itemView.findViewById(R.id.player_view)

        fun bind(uri: Uri) {
            (itemView.findViewById(R.id.photo_image_view) as ImageView).visibility = View.GONE
            playerView.visibility = View.VISIBLE
            val player = ExoPlayer.Builder(itemView.context).build()
            playerView.player = player
            val mediaItem = MediaItem.fromUri(uri)
            player.setMediaItem(mediaItem)
            player.prepare()
        }
    }
}

class SwipeCallback(private val adapter: PhotoAdapter, private val viewModel: PhotoViewModel) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        return if (viewHolder is PhotoAdapter.VideoViewHolder) {
            makeMovementFlags(0, 0)
        } else {
            makeMovementFlags(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT)
        }
    }

    override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
        return false
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.adapterPosition
        if (position == RecyclerView.NO_POSITION) return
        val photoUri = adapter.getPhotoUri(position) ?: return

        if (direction == ItemTouchHelper.LEFT) {
            val vibrator = viewHolder.itemView.context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(50)
            adapter.onDelete(photoUri, position) // Pass position along with Uri
            viewModel.removePhotoFromList(photoUri)
        } else if (direction == ItemTouchHelper.RIGHT) {
            adapter.onFavorite(photoUri)
            viewModel.removePhotoFromList(photoUri)
        }
    }

    override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            if (viewHolder is PhotoAdapter.PhotoViewHolder) {
                val holder = viewHolder
                val itemView = holder.itemView
                val viewWidth = itemView.width.toFloat()
                val swipeProgress = (abs(dX) / viewWidth).coerceIn(0f, 1f)

                // 1. Rotation
                val rotationAngle = 20f
                itemView.rotation = (dX / viewWidth) * rotationAngle

                // 2. Scale Down
                val scaleFactor = 0.2f // Shrink by 20% at full swipe
                val scale = 1.0f - swipeProgress * scaleFactor
                itemView.scaleX = scale
                itemView.scaleY = scale

                // 3. Arc Motion (slight upward movement)
                val arcFactor = 0.1f // How much it moves up
                itemView.translationY = -swipeProgress * (itemView.height * arcFactor)

                // 4. Fade out the image view
                holder.imageView.alpha = 1.0f - swipeProgress

                // Indicator visibility
                if (dX > 0) { // Swiping Right (Favorite)
                    holder.favoriteIndicator.visibility = View.VISIBLE
                    holder.deleteIndicator.visibility = View.GONE
                } else if (dX < 0) { // Swiping Left (Delete)
                    holder.deleteIndicator.visibility = View.VISIBLE
                    holder.favoriteIndicator.visibility = View.GONE
                } else {
                    holder.deleteIndicator.visibility = View.GONE
                    holder.favoriteIndicator.visibility = View.GONE
                }
            }
        }
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        val holder = viewHolder as PhotoAdapter.PhotoViewHolder
        val itemView = holder.itemView

        // Reset all animated properties
        itemView.rotation = 0f
        itemView.scaleX = 1.0f
        itemView.scaleY = 1.0f
        itemView.translationY = 0f
        holder.imageView.alpha = 1.0f

        // Hide indicators
        holder.deleteIndicator.visibility = View.GONE
        holder.favoriteIndicator.visibility = View.GONE
    }
}