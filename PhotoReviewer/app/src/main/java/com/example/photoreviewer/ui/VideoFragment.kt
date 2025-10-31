package com.example.photoreviewer.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.photoreviewer.databinding.FragmentVideoBinding
import com.example.photoreviewer.viewmodel.VideoViewModel
import kotlinx.coroutines.launch

class VideoFragment : Fragment() {

    private var _binding: FragmentVideoBinding? = null
    private val binding get() = _binding!!

    private val viewModel: VideoViewModel by viewModels()
    private lateinit var videoAdapter: VideoAdapter

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

        setupRecyclerView()
        observeViewModel()
        viewModel.loadVideos()
    }

    private fun setupRecyclerView() {
        videoAdapter = VideoAdapter()
        binding.videoRecyclerView.apply {
            adapter = videoAdapter
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        }

        PagerSnapHelper().attachToRecyclerView(binding.videoRecyclerView)

        binding.videoRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            private var currentlyPlayingHolder: VideoAdapter.VideoViewHolder? = null

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val position = layoutManager.findFirstCompletelyVisibleItemPosition()
                    if (position != RecyclerView.NO_POSITION) {
                        val holder = recyclerView.findViewHolderForAdapterPosition(position)
                        if (holder is VideoAdapter.VideoViewHolder) {
                            currentlyPlayingHolder?.playerView?.player?.pause()
                            holder.playerView.player?.play()
                            currentlyPlayingHolder = holder
                        } else {
                            currentlyPlayingHolder?.playerView?.player?.pause()
                            currentlyPlayingHolder = null
                        }
                    }
                }
            }
        })
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