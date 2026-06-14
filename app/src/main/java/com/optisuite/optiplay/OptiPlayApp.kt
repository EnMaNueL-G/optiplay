package com.optisuite.optiplay

import android.app.Application
import com.optisuite.optiplay.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class OptiPlayApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@OptiPlayApp)
            modules(appModule)
        }
    }
}
