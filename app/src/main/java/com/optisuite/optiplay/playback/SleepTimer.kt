package com.optisuite.optiplay.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Temporizador de apagado a nivel de PROCESO. Lo ejecuta el PlaybackService (no la pantalla),
 * así sigue funcionando aunque el usuario cierre la app desde Recientes.
 */
object SleepTimer {
    data class State(val endAtMs: Long = 0L, val endOfTrack: Boolean = false) {
        val active: Boolean get() = endAtMs > 0L || endOfTrack
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    fun start(minutes: Int) {
        _state.value = if (minutes > 0) State(endAtMs = System.currentTimeMillis() + minutes * 60_000L) else State()
    }

    /** Pausar al terminar la canción actual. */
    fun atEndOfTrack() { _state.value = State(endOfTrack = true) }

    fun cancel() { _state.value = State() }
}
