package com.optisuite.optiplay.playback

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.optisuite.optiplay.audio.AudioEffects
import org.koin.android.ext.android.inject

/**
 * Servicio de reproducción en segundo plano basado en Media3 (sucesor de ExoPlayer).
 * Expone una MediaSession para notificación, controles de auriculares, lockscreen y Android Auto.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val effects: AudioEffects by inject()

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true) // pausa al desconectar auriculares
            .build()

        // Une el reproductor a la sesión de audio del ecualizador para que los efectos se apliquen.
        runCatching { player.setAudioSessionId(effects.audioSessionId) }

        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
