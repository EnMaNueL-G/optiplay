package com.optisuite.optiplay.audio

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Efectos de audio sobre una sesión de audio PROPIA de OptiPlay (la sesión global 0 no funciona en
 * Samsung). El reproductor de música y el de vídeo se unen a esta sesión, así que solo afecta a OptiPlay.
 * Ecualizador con las bandas que exponga el dispositivo (normalmente 5) + BassBoost + Virtualizer.
 * Persiste en SharedPreferences. Robusto: si el equipo no soporta un efecto, se ignora sin romper.
 */
class AudioEffects(context: Context) {

    private val prefs = context.getSharedPreferences("optiplay_eq", Context.MODE_PRIVATE)

    /** Sesión de audio dedicada: el ExoPlayer del servicio se une a esta sesión. */
    val audioSessionId: Int = runCatching {
        (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).generateAudioSessionId()
    }.getOrDefault(0)

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    private val _enabled = MutableStateFlow(prefs.getBoolean("enabled", false))
    val enabled: StateFlow<Boolean> = _enabled

    private val _bands = MutableStateFlow<List<Short>>(emptyList())
    val bands: StateFlow<List<Short>> = _bands

    private val _bass = MutableStateFlow(prefs.getInt("bass", 0))
    val bass: StateFlow<Int> = _bass

    private val _virtual = MutableStateFlow(prefs.getInt("virtual", 0))
    val virtual: StateFlow<Int> = _virtual

    private val _preset = MutableStateFlow(prefs.getInt("preset", -1))
    val preset: StateFlow<Int> = _preset

    var available: Boolean = false
        private set

    var minLevel: Short = -1500
        private set
    var maxLevel: Short = 1500
        private set
    var centerFreqs: List<Int> = emptyList()
        private set
    var presetNames: List<String> = emptyList()
        private set

    init { runCatching { initEffects() } }

    private fun initEffects() {
        val sid = audioSessionId
        equalizer = Equalizer(1000, sid).apply {
            val range = bandLevelRange
            minLevel = range[0]
            maxLevel = range[1]
            centerFreqs = (0 until numberOfBands).map { getCenterFreq(it.toShort()) }
            presetNames = (0 until numberOfPresets).map { getPresetName(it.toShort()) }
        }
        bassBoost = runCatching { BassBoost(1000, sid) }.getOrNull()
        virtualizer = runCatching { Virtualizer(1000, sid) }.getOrNull()
        available = true

        // Restaurar bandas guardadas
        val saved = (0 until (equalizer?.numberOfBands ?: 0)).map { i ->
            prefs.getInt("band_$i", 0).toShort()
        }
        _bands.value = saved
        applyAll()
    }

    private fun applyAll() {
        val on = _enabled.value
        equalizer?.enabled = on
        bassBoost?.enabled = on && _bass.value > 0
        virtualizer?.enabled = on && _virtual.value > 0
        if (on) {
            _bands.value.forEachIndexed { i, level ->
                runCatching { equalizer?.setBandLevel(i.toShort(), level) }
            }
            runCatching { bassBoost?.setStrength(_bass.value.toShort()) }
            runCatching { virtualizer?.setStrength(_virtual.value.toShort()) }
        }
    }

    fun setEnabled(on: Boolean) {
        _enabled.value = on
        prefs.edit().putBoolean("enabled", on).apply()
        applyAll()
    }

    fun setBand(index: Int, level: Short) {
        val list = _bands.value.toMutableList()
        if (index !in list.indices) return
        list[index] = level
        _bands.value = list
        prefs.edit().putInt("band_$index", level.toInt()).apply()
        _preset.value = -1
        prefs.edit().putInt("preset", -1).apply()
        runCatching { equalizer?.setBandLevel(index.toShort(), level) }
    }

    fun usePreset(presetIndex: Int) {
        val eq = equalizer ?: return
        runCatching {
            eq.usePreset(presetIndex.toShort())
            val newBands = (0 until eq.numberOfBands).map { eq.getBandLevel(it.toShort()) }
            _bands.value = newBands
            newBands.forEachIndexed { i, lvl -> prefs.edit().putInt("band_$i", lvl.toInt()).apply() }
            _preset.value = presetIndex
            prefs.edit().putInt("preset", presetIndex).apply()
        }
    }

    fun setBass(strength: Int) {
        _bass.value = strength
        prefs.edit().putInt("bass", strength).apply()
        bassBoost?.enabled = _enabled.value && strength > 0
        runCatching { bassBoost?.setStrength(strength.toShort()) }
    }

    fun setVirtualizer(strength: Int) {
        _virtual.value = strength
        prefs.edit().putInt("virtual", strength).apply()
        virtualizer?.enabled = _enabled.value && strength > 0
        runCatching { virtualizer?.setStrength(strength.toShort()) }
    }

    fun release() {
        equalizer?.release(); bassBoost?.release(); virtualizer?.release()
        equalizer = null; bassBoost = null; virtualizer = null
    }
}
