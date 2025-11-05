package com.example.photoreviewer.ui.video

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.example.photoreviewer.databinding.FragmentVideoPlayerBinding

class VideoPlayerFragment : Fragment() {

    private var _binding: FragmentVideoPlayerBinding? = null
    private val binding get() = _binding!!

    private var player: ExoPlayer? = null
    private var videoUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            videoUri = it.getParcelable(ARG_VIDEO_URI)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVideoPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        initializePlayer()
    }

    override fun onPause() {
        super.onPause()
        releasePlayer()
    }

    private fun initializePlayer() {
        if (player == null && videoUri != null) {
            val context = requireContext()

            // Create a CacheDataSource.Factory
            val cache = VideoCache.getInstance(context)
            val cacheDataSourceFactory = CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context))

            // Create a MediaSource.Factory with the CacheDataSource.Factory
            val mediaSourceFactory = ProgressiveMediaSource.Factory(cacheDataSourceFactory)

            // Create an ExoPlayer and set the MediaSource.Factory
            player = ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .build().apply {
                    val mediaItem = MediaItem.fromUri(videoUri!!)
                    setMediaItem(mediaItem)
                    repeatMode = Player.REPEAT_MODE_ONE // Loop the video
                    prepare()
                    play()
                }
            binding.playerView.player = player
        }
    }

    private fun releasePlayer() {
        player?.let {
            it.stop()
            it.release()
            player = null
        }
        binding.playerView.player = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_VIDEO_URI = "video_uri"

        fun newInstance(videoUri: Uri): VideoPlayerFragment {
            val fragment = VideoPlayerFragment()
            val args = Bundle()
            args.putParcelable(ARG_VIDEO_URI, videoUri)
            fragment.arguments = args
            return fragment
        }
    }
}