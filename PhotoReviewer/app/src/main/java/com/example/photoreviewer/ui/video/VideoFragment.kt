package com.example.photoreviewer.ui.video

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.example.photoreviewer.databinding.FragmentVideoBinding
import com.example.photoreviewer.viewmodel.VideoViewModel
import kotlinx.coroutines.launch

class VideoFragment : Fragment() {

    private var _binding: FragmentVideoBinding? = null
    private val binding get() = _binding!!

    private val viewModel: VideoViewModel by viewModels()
    private lateinit var videoAdapter: VideoPagerAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVideoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViewPager()
        setupButtons()
        observeViewModel()
        viewModel.loadVideos()
    }

    private fun setupViewPager() {
        videoAdapter = VideoPagerAdapter(this)
        binding.videoViewPager.apply {
            offscreenPageLimit = 1
            adapter = videoAdapter
            orientation = ViewPager2.ORIENTATION_VERTICAL
        }
    }

    private fun setupButtons() {
        binding.btnRandom.setOnClickListener {
            viewModel.loadVideos()
        }

        binding.btnShare.setOnClickListener {
            val currentPosition = binding.videoViewPager.currentItem
            val currentVideoUri = videoAdapter.videoUris[currentPosition]
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/*"
                putExtra(Intent.EXTRA_STREAM, currentVideoUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "分享视频"))
        }

        binding.btnDelete.setOnClickListener {
           val currentPosition = binding.videoViewPager.currentItem
           val currentVideoUri = videoAdapter.videoUris[currentPosition]
           viewModel.deleteVideo(currentVideoUri)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.videos.collect { videos ->
                    videoAdapter.submitList(videos)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}