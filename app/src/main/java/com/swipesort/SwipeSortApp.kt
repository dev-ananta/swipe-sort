package com.swipesort

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.swipesort.data.FileRepository
import com.swipesort.data.Settings

/**
 * Application = the tiny dependency container (manual DI) + Coil's image loader.
 * ViewModels get the repository and settings from here.
 */
class SwipeSortApp : Application(), ImageLoaderFactory {

    lateinit var repository: FileRepository
        private set
    lateinit var settings: Settings
        private set

    override fun onCreate() {
        super.onCreate()
        repository = FileRepository(this)
        settings = Settings(this)
    }

    /** Thumbnails: images decode at the size they're shown; videos show a frame. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(VideoFrameDecoder.Factory()) }
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.20).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("thumbs")).maxSizeBytes(150L * 1024 * 1024).build() }
        .crossfade(true)
        .build()
}
