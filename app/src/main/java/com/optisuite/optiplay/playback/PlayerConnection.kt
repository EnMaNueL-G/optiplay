package com.optisuite.optiplay.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.optisuite.optiplay.data.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Lo que muestra la UI de la pista actual (también para archivos abiertos desde otras apps). */
data class NowPlayingMeta(val mediaId: String, val title: String, val artist: String, val artworkUri: Uri?)

/**
 * Puente entre la UI y el PlaybackService. Mantiene un MediaController y expone
 * el estado de reproducción como StateFlows que Compose observa.
 */
class PlayerConnection(context: Context) {

    private var controller: MediaController? = null
    private var future: ListenableFuture<MediaController>? = null

    /** Pantallas que usan la conexión (puede haber dos: la normal y una abierta desde "Abrir con"). */
    private var users = 0

    /** Órdenes dadas antes de que el controlador esté listo (p. ej. un toque justo al abrir la app). */
    private val pending = ArrayList<(MediaController) -> Unit>()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentMediaId = MutableStateFlow<String?>(null)
    val currentMediaId: StateFlow<String?> = _currentMediaId

    private val _currentMeta = MutableStateFlow<NowPlayingMeta?>(null)
    val currentMeta: StateFlow<NowPlayingMeta?> = _currentMeta

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs

    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode

    private val _speed = MutableStateFlow(1f)
    val speed: StateFlow<Float> = _speed

    /** Cambia cada vez que cambia la cola (para refrescar la pantalla Cola). */
    private val _queueVersion = MutableStateFlow(0)
    val queueVersion: StateFlow<Int> = _queueVersion

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            sync(player)
            if (events.contains(Player.EVENT_TIMELINE_CHANGED) || events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                _queueVersion.value++
            }
        }
    }

    /** Vuelca el estado real del reproductor (al conectar no llega ningún onEvents). */
    private fun sync(p: Player) {
        _isPlaying.value = p.isPlaying
        val item = p.currentMediaItem
        _currentMediaId.value = item?.mediaId
        _currentMeta.value = item?.let {
            NowPlayingMeta(
                mediaId = it.mediaId,
                title = it.mediaMetadata.title?.toString() ?: "",
                artist = it.mediaMetadata.artist?.toString() ?: "",
                artworkUri = it.mediaMetadata.artworkUri
            )
        }
        _durationMs.value = p.duration.coerceAtLeast(0L)
        _shuffle.value = p.shuffleModeEnabled
        _repeatMode.value = p.repeatMode
        _speed.value = p.playbackParameters.speed
    }

    fun connect(context: Context) {
        users++
        if (controller != null || future != null) return
        val token = SessionToken(
            context.applicationContext,
            ComponentName(context.applicationContext, PlaybackService::class.java)
        )
        val f = MediaController.Builder(context.applicationContext, token).buildAsync()
        future = f
        f.addListener({
            if (future !== f) return@addListener // se liberó mientras conectaba
            val c = runCatching { f.get() }.getOrNull()
            if (c == null) { future = null; return@addListener }
            controller = c
            c.addListener(listener)
            sync(c)
            _queueVersion.value++
            pending.forEach { it(c) }
            pending.clear()
        }, MoreExecutors.directExecutor())
    }

    private fun withController(action: (MediaController) -> Unit) {
        val c = controller
        if (c != null) action(c) else pending.add(action)
    }

    fun playQueue(songs: List<Song>, startIndex: Int) = playItems(songs.map { it.toMediaItem() }, startIndex)

    fun playItems(items: List<MediaItem>, startIndex: Int) = withController { c ->
        c.setMediaItems(items, startIndex, 0L)
        c.prepare()
        c.play()
    }

    fun togglePlayPause() = withController { c ->
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
            c.play()
        }
    }

    fun pause() { controller?.pause() }
    fun play() = withController { it.play() }

    fun next() { controller?.seekToNext() }
    fun previous() { controller?.seekToPrevious() }
    fun seekTo(positionMs: Long) { controller?.seekTo(positionMs) }

    fun setSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
        _speed.value = speed
    }

    /** Salta a un índice de la cola actual. */
    fun seekToItem(index: Int) { controller?.seekToDefaultPosition(index) }

    /** Cola actual (mediaIds) para la pantalla de Cola. */
    fun queueMediaIds(): List<String> {
        val c = controller ?: return emptyList()
        return (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId }
    }
    /** Títulos de la cola (para pistas que no están en la biblioteca). */
    fun queueTitles(): List<String> {
        val c = controller ?: return emptyList()
        return (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaMetadata.title?.toString() ?: "" }
    }
    fun currentIndex(): Int = controller?.currentMediaItemIndex ?: 0

    /** Inserta la canción justo después de la actual. Si no hay nada sonando, la reproduce. */
    fun playNext(song: Song) = withController { c ->
        if (c.mediaItemCount == 0) { playQueue(listOf(song), 0); return@withController }
        c.addMediaItem(c.currentMediaItemIndex + 1, song.toMediaItem())
    }

    /** Añade al final de la cola. Si no hay nada sonando, la reproduce. */
    fun addToQueue(song: Song) = withController { c ->
        if (c.mediaItemCount == 0) { playQueue(listOf(song), 0); return@withController }
        c.addMediaItem(song.toMediaItem())
    }

    fun removeFromQueue(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) c.removeMediaItem(index)
    }

    fun moveInQueue(from: Int, to: Int) {
        val c = controller ?: return
        if (from in 0 until c.mediaItemCount && to in 0 until c.mediaItemCount) c.moveMediaItem(from, to)
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun setShuffle(enabled: Boolean) = withController { it.shuffleModeEnabled = enabled }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun currentPositionMs(): Long = controller?.currentPosition ?: 0L

    /** Solo se desconecta cuando la última pantalla que la usa se cierra. */
    fun release() {
        users = (users - 1).coerceAtLeast(0)
        if (users > 0) return
        pending.clear()
        controller?.removeListener(listener)
        future?.let { MediaController.releaseFuture(it) }
        future = null
        controller = null
    }
}
