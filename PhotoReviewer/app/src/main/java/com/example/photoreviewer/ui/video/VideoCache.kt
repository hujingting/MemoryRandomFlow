package com.example.photoreviewer.ui.video

import android.content.Context
import androidx.media3.database.ExoDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

object VideoCache {

    private var simpleCache: SimpleCache? = null
    private const val MAX_CACHE_SIZE: Long = 100 * 1024 * 1024 // 100MB

    @Synchronized
    fun getInstance(context: Context): SimpleCache {
        if (simpleCache == null) {
            val cacheDirectory = File(context.cacheDir, "media")
            val databaseProvider = ExoDatabaseProvider(context)
            simpleCache = SimpleCache(
                cacheDirectory,
                LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE),
                databaseProvider
            )
        }
        return simpleCache!!
    }
}