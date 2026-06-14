package com.optisuite.optiplay.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.optisuite.optiplay.ui.PlayerViewModel
import com.optisuite.optiplay.ui.components.AlbumArt
import com.optisuite.optiplay.ui.components.formatDuration

@Composable
fun NowPlayingScreen(vm: PlayerViewModel, onCollapse: () -> Unit, onOpenQueue: () -> Unit) {
    val song by vm.currentSong.collectAsStateWithLifecycle()
    val isPlaying by vm.player.isPlaying.collectAsStateWithLifecycle()
    val positionMs by vm.positionMs.collectAsStateWithLifecycle()
    val durationMs by vm.player.durationMs.collectAsStateWithLifecycle()
    val shuffle by vm.player.shuffle.collectAsStateWithLifecycle()
    val repeat by vm.player.repeatMode.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val speed by vm.player.speed.collectAsStateWithLifecycle()
    val s = song ?: return

    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }
    val dur = durationMs.coerceAtLeast(s.durationMs).coerceAtLeast(1L)
    val sliderPos = if (dragging) dragValue else (positionMs.toFloat() / dur)

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCollapse) { Icon(Icons.Filled.KeyboardArrowDown, "Minimizar") }
                Text("Reproduciendo", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleSmall)
                val isFav = favorites.contains(s.id)
                IconButton(onClick = { vm.toggleFavorite(s.id) }) {
                    Icon(if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favorito",
                        tint = if (isFav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(24.dp))
            AlbumArt(s.albumArtUri, Modifier.fillMaxWidth(0.85f).aspectRatio(1f), corner = 20)
            Spacer(Modifier.height(32.dp))

            Text(s.title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Text(s.artist, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)

            Spacer(Modifier.height(24.dp))
            Slider(
                value = sliderPos.coerceIn(0f, 1f),
                onValueChange = { dragging = true; dragValue = it },
                onValueChangeFinished = {
                    vm.player.seekTo((dragValue * dur).toLong())
                    dragging = false
                }
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDuration((sliderPos * dur).toLong()), style = MaterialTheme.typography.labelMedium)
                Text(formatDuration(dur), style = MaterialTheme.typography.labelMedium)
            }

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.player.toggleShuffle() }) {
                    Icon(Icons.Filled.Shuffle, "Aleatorio", tint = if (shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { vm.player.previous() }) { Icon(Icons.Filled.SkipPrevious, "Anterior", Modifier.size(40.dp)) }
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(72.dp)) {
                    IconButton(onClick = { vm.player.togglePlayPause() }) {
                        Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pausa",
                            tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(40.dp))
                    }
                }
                IconButton(onClick = { vm.player.next() }) { Icon(Icons.Filled.SkipNext, "Siguiente", Modifier.size(40.dp)) }
                IconButton(onClick = { vm.player.cycleRepeat() }) {
                    Icon(
                        if (repeat == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        "Repetir",
                        tint = if (repeat != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
                TextButton(onClick = {
                    val cur = vm.player.speed.value
                    val next = speeds[(speeds.indexOf(cur).let { if (it < 0) 2 else it } + 1) % speeds.size]
                    vm.player.setSpeed(next)
                }) { Text("${speed}x") }
                TextButton(onClick = onOpenQueue) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, null, Modifier.size(18.dp)); Text(" Cola")
                }
                TextButton(onClick = { vm.requestAddToPlaylist(s.id) }) {
                    Icon(Icons.Filled.PlaylistAdd, null, Modifier.size(18.dp)); Text(" Lista")
                }
            }
        }
    }
}
