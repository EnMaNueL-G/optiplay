package com.optisuite.optiplay.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.optisuite.optiplay.data.Song
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Puente entre la UI y el PlaybackService. Mantiene un MediaController y expone
 * el estado de reproducción como StateFlows que Compose observa.
 */
class PlayerConnection(context: Context) {

    private var controller: MediaController? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentMediaId = MutableStateFlow<String?>(null)
    val currentMediaId: StateFlow<String?> = _currentMediaId

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs

    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            _isPlaying.value = player.isPlaying
            _currentMediaId.value = player.currentMediaItem?.mediaId
            _durationMs.value = player.duration.coerceAtLeast(0L)
            _shuffle.value = player.shuffleModeEnabled
            _repeatMode.value = player.repeatMode
        }
    }

    fun connect(context: Context, onReady: () -> Unit = {}) {
        if (controller != null) { onReady(); return }
        val token = SessionToken(
            context.applicationContext,
            ComponentName(context.applicationContext, PlaybackService::class.java)
        )
        val future = MediaController.Builder(context.applicationContext, token).buildAsync()
        future.addListener({
            controller = future.get().also { it.addListener(listener) }
            onReady()
        }, MoreExecutors.directExecutor())
    }

    fun playQueue(songs: List<Song>, startIndex: Int) {
        val c = controller ?: return
        c.setMediaItems(songs.map { it.toMediaItem() }, startIndex, 0L)
        c.prepare()
        c.play()
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun pause() { controller?.pause() }
    fun play() { controller?.play() }

    fun next() = controller?.seekToNext()
    fun previous() = controller?.seekToPrevious()
    fun seekTo(positionMs: Long) = controller?.seekTo(positionMs)

    private val _speed = MutableStateFlow(1f)
    val speed: StateFlow<Float> = _speed
    fun setSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
        _speed.value = speed
    }

    /** Salta a un índice de la cola actual. */
    fun seekToItem(index: Int) = controller?.seekToDefaultPosition(index)

    /** Cola actual (mediaIds) para la pantalla de Cola. */
    fun queueMediaIds(): List<String> {
        val c = controller ?: return emptyList()
        return (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId }
    }
    fun currentIndex(): Int = controller?.currentMediaItemIndex ?: 0

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun currentPositionMs(): Long = controller?.currentPosition ?: 0L

    fun release() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }
}
