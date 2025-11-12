package com.example.photoreviewer.ui.video

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import com.example.photoreviewer.databinding.FragmentVideoPlayerBinding
import com.example.photoreviewer.ui.MainActivity

class VideoPlayerFragment : Fragment() {

    private var _binding: FragmentVideoPlayerBinding? = null
    private val binding get() = _binding!!

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

        if ((activity as MainActivity).isVideoFragment()) {
            binding.playerView.player?.play()
        }
    }

    override fun onPause() {
        super.onPause()
        pauseVideo()
    }

    fun pauseVideo() {
        binding.playerView.player?.pause()
        binding.playerView.player = null
    }

    private fun initializePlayer() {
        if (videoUri != null) {
            val player = PlayerManager.getPlayer(requireContext())
            binding.playerView.player = player
            val mediaItem = MediaItem.fromUri(videoUri!!)
            player.setMediaItem(mediaItem)
            player.prepare()
//            player.play()
        }
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