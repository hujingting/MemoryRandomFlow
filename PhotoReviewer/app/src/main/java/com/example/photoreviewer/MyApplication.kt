package com.example.photoreviewer

import android.app.Application
import com.tencent.mmkv.MMKV

import coil.ImageLoader
import coil.ImageLoaderFactory

class MyApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        MMKV.initialize(this)
    }

    override fun newImageLoader(): ImageLoader {
        return Coil.newImageLoader(this)
    }
}
