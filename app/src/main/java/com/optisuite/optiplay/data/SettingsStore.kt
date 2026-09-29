package com.optisuite.optiplay.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "nexus_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }

/** Preferencias locales (DataStore). Nada se sincroniza fuera del dispositivo. */
class SettingsStore(private val context: Context) {

    private val themeKey = intPreferencesKey("theme_mode")
    private val dynamicKey = intPreferencesKey("dynamic_color")

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map {
        ThemeMode.entries.getOrElse(it[themeKey] ?: 0) { ThemeMode.SYSTEM }
    }

    val dynamicColor: Flow<Boolean> = context.dataStore.data.map {
        (it[dynamicKey] ?: 1) == 1
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[themeKey] = mode.ordinal }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[dynamicKey] = if (enabled) 1 else 0 }
    }
}
