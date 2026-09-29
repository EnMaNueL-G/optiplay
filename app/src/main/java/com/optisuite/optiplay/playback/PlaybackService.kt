package com.optisuite.optiplay.playback

import android.app.PendingIntent
import android.content.Intent
import android.widget.Toast
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.optisuite.optiplay.MainActivity
import com.optisuite.optiplay.audio.AudioEffects
import com.optisuite.optiplay.data.db.MusicDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Servicio de reproducción en segundo plano basado en Media3.
 * Expone una MediaSession para notificación, controles de auriculares/Bluetooth y pantalla de bloqueo.
 * Aquí viven también el temporizador de apagado, el historial de reproducciones y la cola guardada,
 * para que funcionen aunque la pantalla de la app esté cerrada.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val effects: AudioEffects by inject()
    private val dao: MusicDao by inject()
    private lateinit var queueStore: QueueStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var positionSaver: Job? = null

    /** La pista actual aún no se ha contado en el historial (se cuenta cuando empieza a sonar). */
    private var pendingRecord = false
    private var consecutiveErrors = 0

    override fun onCreate() {
        super.onCreate()
        queueStore = QueueStore(this)
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true) // pausa al desconectar auriculares
            .setWakeMode(C.WAKE_MODE_LOCAL)    // sigue sonando con la pantalla apagada
            .build()

        // Une el reproductor a la sesión de audio del ecualizador para que los efectos se apliquen.
        runCatching { player.setAudioSessionId(effects.audioSessionId) }
        player.addListener(listener)

        // Reanudar donde se quedó (sin empezar a sonar solo).
        queueStore.load()?.let { saved ->
            player.setMediaItems(saved.items, saved.index, saved.positionMs)
            player.prepare()
        }

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(object : MediaSession.Callback {
                // Botón "play" de auriculares/Bluetooth con la app cerrada: reanuda la última cola.
                override fun onPlaybackResumption(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                    val saved = queueStore.load()
                        ?: return Futures.immediateFailedFuture(UnsupportedOperationException("Sin cola guardada"))
                    return Futures.immediateFuture(
                        MediaSession.MediaItemsWithStartPosition(saved.items, saved.index, saved.positionMs)
                    )
                }
            })
            .build()

        scope.launch { SleepTimer.state.collectLatest { runSleepTimer(it) } }
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_TIMELINE_CHANGED) || events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                saveQueue(player)
            }
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) pendingRecord = true
            if (player.isPlaying) {
                consecutiveErrors = 0
                if (pendingRecord) {
                    pendingRecord = false
                    player.currentMediaItem?.mediaId?.toLongOrNull()?.let { id -> scope.launch { dao.recordPlay(id) } }
                }
            }
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
                if (player.isPlaying) startPositionSaver(player) else {
                    positionSaver?.cancel()
                    queueStore.savePosition(player.currentMediaItem?.mediaId, player.currentPosition)
                }
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM && SleepTimer.state.value.endOfTrack) {
                SleepTimer.cancel()
            }
        }

        // Archivo borrado o formato no soportado: avisar y pasar a la siguiente en vez de parar la cola.
        override fun onPlayerError(error: PlaybackException) {
            val player = mediaSession?.player ?: return
            // Al restaurar la cola (sin que el usuario haya pulsado play) no avisar ni saltar a otra pista.
            if (!player.playWhenReady) return
            val title = player.currentMediaItem?.mediaMetadata?.title ?: "la pista"
            Toast.makeText(this@PlaybackService, "No se pudo reproducir «$title»", Toast.LENGTH_SHORT).show()
            consecutiveErrors++
            if (consecutiveErrors < 5 && player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            }
        }
    }

    private fun saveQueue(player: Player) {
        val items = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
        queueStore.saveQueue(items, player.currentMediaItem?.mediaId, player.currentPosition)
    }

    private fun startPositionSaver(player: Player) {
        positionSaver?.cancel()
        positionSaver = scope.launch {
            while (isActive) {
                delay(15_000)
                queueStore.savePosition(player.currentMediaItem?.mediaId, player.currentPosition)
            }
        }
    }

    /** Pausa a la hora fijada con un fundido de 8 s, o al terminar la pista. */
    private suspend fun runSleepTimer(state: SleepTimer.State) {
        val player = mediaSession?.player as? ExoPlayer ?: return
        player.pauseAtEndOfMediaItems = state.endOfTrack
        if (state.endAtMs <= 0L) return
        val fadeMs = 8_000L
        delay((state.endAtMs - fadeMs - System.currentTimeMillis()).coerceAtLeast(0L))
        try {
            val steps = 40
            repeat(steps) { i ->
                player.volume = 1f - (i + 1).toFloat() / steps
                delay(fadeMs / steps)
            }
            player.pause()
            SleepTimer.cancel()
        } finally {
            player.volume = 1f
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null) queueStore.savePosition(player.currentMediaItem?.mediaId, player.currentPosition)
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        mediaSession?.run {
            queueStore.savePosition(player.currentMediaItem?.mediaId, player.currentPosition)
            player.removeListener(listener)
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
