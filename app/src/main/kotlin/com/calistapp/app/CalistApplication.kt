package com.calistapp.app

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.calistapp.app.sync.StepPullWorker
import com.calistapp.app.ui.exercises.VideoFrameFetcher
import com.calistapp.app.ui.exercises.VideoThumbUrlMapper
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CalistApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        // Register Calistapp's own daily step-pull from FitPal, so it exists even on a cold start
        // the FitPal wake-broadcast spun up (no Activity). KEEP, so it isn't reset each launch.
        runCatching { StepPullWorker.schedulePeriodic(this) }
    }

    // A GIF-capable image loader so exercise demos animate (static images work too), plus a mapper +
    // fetcher pair that thumbnails a video by frame-grabbing it for exercises with no animated GIF.
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory())
            else add(GifDecoder.Factory())
            add(VideoThumbUrlMapper())
            add(VideoFrameFetcher.Factory())
        }
        .crossfade(true)
        .build()
}
