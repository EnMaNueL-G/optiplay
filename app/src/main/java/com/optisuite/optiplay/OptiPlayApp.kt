package com.optisuite.optiplay

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.optisuite.optiplay.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class OptiPlayApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@OptiPlayApp)
            modules(appModule)
        }
    }

    /** Coil con decodificador de vídeo: miniaturas a partir de un fotograma, todo local. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(VideoFrameDecoder.Factory()) }
        .crossfade(true)
        .build()
}
