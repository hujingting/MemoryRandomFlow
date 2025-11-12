package com.example.photoreviewer.ui.video

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource

object PlayerManager {

    private var player: ExoPlayer? = null

    fun getPlayer(context: Context): ExoPlayer {
        if (player == null) {
            val cache = VideoCache.getInstance(context)
            val cacheDataSourceFactory = CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context))

            val mediaSourceFactory = ProgressiveMediaSource.Factory(cacheDataSourceFactory)

            player = ExoPlayer.Builder(context.applicationContext)
                .setMediaSourceFactory(mediaSourceFactory)
                .build().apply {
                    repeatMode = Player.REPEAT_MODE_ONE
                }
        }
        return player!!
    }

    fun releasePlayer() {
        player?.release()
        player = null
    }
}