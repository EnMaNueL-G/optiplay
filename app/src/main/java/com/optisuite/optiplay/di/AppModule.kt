package com.optisuite.optiplay.di

import com.optisuite.optiplay.audio.AudioEffects
import com.optisuite.optiplay.data.MediaRepository
import com.optisuite.optiplay.data.SettingsStore
import com.optisuite.optiplay.data.db.AppDatabase
import com.optisuite.optiplay.playback.PlayerConnection
import com.optisuite.optiplay.ui.PlayerViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { MediaRepository(androidContext()) }
    single { SettingsStore(androidContext()) }
    single { AppDatabase.build(androidContext()) }
    single { get<AppDatabase>().musicDao() }
    single { AudioEffects(androidContext()) }
    single { PlayerConnection(androidContext()) }
    viewModel { PlayerViewModel(androidContext(), get(), get(), get(), get(), get()) }
}
