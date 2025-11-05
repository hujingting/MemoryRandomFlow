package com.example.photoreviewer.utils

import android.content.Context
import android.os.Build
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.size.Precision

object Coil {
    fun newImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.25) // 使用最多 25% 的可用内存
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(512L * 1024 * 1024) // 512MB 磁盘缓存
                    .build()
            }
            .respectCacheHeaders(false) // 忽略服务器缓存设置
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .precision(Precision.INEXACT) // 允许不精确的尺寸以提高性能
            .build()
    }

    fun newImageLoaderForDetail(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.15) // 详情页使用更少的内存，避免OOM
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(512L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .precision(Precision.INEXACT)
            .build()
    }
}