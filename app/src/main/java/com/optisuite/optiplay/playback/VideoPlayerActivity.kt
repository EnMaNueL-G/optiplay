package com.optisuite.optiplay.playback

import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlin.math.abs

private fun fmt(ms: Long): String {
    val t = (ms / 1000).coerceAtLeast(0)
    val h = t / 3600; val m = (t % 3600) / 60; val s = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private data class TrackChoice(val label: String, val selected: Boolean, val onSelect: () -> Unit)

/**
 * Reproductor de VÍDEO a pantalla completa con CONTROLES PROPIOS (Compose), no el de Media3,
 * para que el botón de Ajustes sea accesible y no quede pegado al borde.
 *  - Arrastre vertical IZQUIERDA = brillo · DERECHA = volumen · horizontal = avanzar/retroceder
 *  - Toque central = mostrar/ocultar controles · doble toque = play/pausa
 *  - Ajustes: velocidad, ajuste de pantalla, subtítulos y pista de audio.
 *  - PiP al salir. Decodifica todo lo que soporte MediaCodec (H.264/HEVC/AV1/VP9...).
 */
class VideoPlayerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_URI = "extra_uri"
        const val EXTRA_TITLE = "extra_title"
    }

    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val uriStr = intent.getStringExtra(EXTRA_URI)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        if (uriStr == null) { finish(); return }

        val exo = ExoPlayer.Builder(this).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(uriStr)))
            prepare()
            playWhenReady = true
        }
        player = exo

        setContent {
            val ctx = LocalContext.current
            var overlay by remember { mutableStateOf("") }
            var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
            var controlsVisible by remember { mutableStateOf(true) }
            var settingsOpen by remember { mutableStateOf(false) }
            var isPlaying by remember { mutableStateOf(true) }
            var position by remember { mutableStateOf(0L) }
            var duration by remember { mutableStateOf(0L) }
            var speed by remember { mutableStateOf(1f) }
            var tracksVersion by remember { mutableStateOf(0) }

            val audio = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
            val maxVol = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

            DisposableEffect(Unit) {
                val l = object : Player.Listener {
                    override fun onEvents(p: Player, e: Player.Events) {
                        isPlaying = p.isPlaying
                        duration = p.duration.coerceAtLeast(0L)
                        tracksVersion++
                    }
                }
                exo.addListener(l)
                onDispose { exo.removeListener(l) }
            }
            LaunchedEffect(Unit) {
                while (true) {
                    position = exo.currentPosition
                    kotlinx.coroutines.delay(300)
                }
            }
            // Auto-ocultar controles
            LaunchedEffect(controlsVisible, isPlaying, settingsOpen) {
                if (controlsVisible && isPlaying && !settingsOpen) {
                    kotlinx.coroutines.delay(3500)
                    controlsVisible = false
                }
            }

            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AndroidView(
                    factory = { c -> PlayerView(c).apply { this.player = exo; useController = false; this.resizeMode = resizeMode } },
                    update = { it.resizeMode = resizeMode },
                    modifier = Modifier.fillMaxSize()
                )

                // Capa de gestos (debajo de los controles; el centro de los controles es transparente)
                Box(
                    Modifier.fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { controlsVisible = !controlsVisible },
                                onDoubleTap = { if (exo.isPlaying) exo.pause() else exo.play() }
                            )
                        }
                        .pointerInput(Unit) {
                            var mode = 0; var startX = 0f; var seekTarget = 0L
                            detectDragGestures(
                                onDragStart = { o -> mode = 0; startX = o.x },
                                onDragEnd = { if (mode == 1) exo.seekTo(seekTarget.coerceAtLeast(0)); mode = 0; overlay = "" },
                                onDrag = { change, amount ->
                                    if (mode == 0) mode = if (abs(amount.x) > abs(amount.y)) 1 else if (startX < size.width / 2f) 2 else 3
                                    when (mode) {
                                        1 -> {
                                            seekTarget = (exo.currentPosition + (change.position.x - startX) * 60).toLong()
                                                .coerceIn(0, exo.duration.coerceAtLeast(0))
                                            overlay = "⏩ ${fmt(seekTarget)}"
                                        }
                                        2 -> {
                                            val lp = window.attributes
                                            val cur = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
                                            val nv = (cur - amount.y / 1000f).coerceIn(0.01f, 1f)
                                            lp.screenBrightness = nv; window.attributes = lp
                                            overlay = "☀ ${(nv * 100).toInt()}%"
                                        }
                                        3 -> {
                                            val cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                                            val delta = (-amount.y / 40f).toInt()
                                            if (delta != 0) {
                                                val nv = (cur + delta).coerceIn(0, maxVol)
                                                audio.setStreamVolume(AudioManager.STREAM_MUSIC, nv, 0)
                                                overlay = "🔊 ${nv * 100 / maxVol}%"
                                            }
                                        }
                                    }
                                }
                            )
                        }
                )

                // Controles propios
                AnimatedVisibility(visible = controlsVisible) {
                    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                        // Barra superior
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { finish() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = Color.White)
                            }
                            Text(title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                            IconButton(onClick = { settingsOpen = true; controlsVisible = true }) {
                                Icon(Icons.Filled.Settings, "Ajustes", tint = Color.White)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        // Barra inferior
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { if (exo.isPlaying) exo.pause() else exo.play() }) {
                                Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pausa", tint = Color.White)
                            }
                            Text(fmt(position), color = Color.White, style = MaterialTheme.typography.labelMedium)
                            Slider(
                                value = if (duration > 0) position.toFloat() / duration else 0f,
                                onValueChange = { exo.seekTo((it * duration).toLong()); controlsVisible = true },
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                            )
                            Text(fmt(duration), color = Color.White, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                // Indicador de gesto (brillo/volumen/seek)
                if (overlay.isNotEmpty()) {
                    Box(
                        Modifier.align(Alignment.Center).clip(RoundedCornerShape(8.dp))
                            .background(Color(0xAA000000)).padding(horizontal = 20.dp, vertical = 12.dp)
                    ) { Text(overlay, color = Color.White, style = MaterialTheme.typography.titleMedium) }
                }

                // Panel de Ajustes
                if (settingsOpen) {
                    SettingsPanel(
                        exo = exo,
                        tracksVersion = tracksVersion,
                        speed = speed,
                        onSpeed = { speed = it; exo.setPlaybackSpeed(it) },
                        resizeMode = resizeMode,
                        onResize = { resizeMode = it },
                        onDismiss = { settingsOpen = false }
                    )
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val canPip = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        if (canPip && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && player?.isPlaying == true) {
            runCatching { enterPictureInPictureMode(PictureInPictureParams.Builder().build()) }
        }
    }

    override fun onPictureInPictureModeChanged(isInPip: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPip, newConfig)
    }

    override fun onStop() {
        super.onStop()
        if (!isInPictureInPictureMode) player?.pause()
    }

    override fun onDestroy() {
        player?.release(); player = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }
}

@Composable
private fun SettingsPanel(
    exo: ExoPlayer,
    tracksVersion: Int,
    speed: Float,
    onSpeed: (Float) -> Unit,
    resizeMode: Int,
    onResize: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    // Scrim que cierra al tocar fuera
    Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.width(320.dp).heightIn(max = 460.dp).clickable(enabled = false) {}
        ) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Text("Ajustes de reproducción", style = MaterialTheme.typography.titleMedium)

                Spacer(Modifier.size(12.dp))
                Text("Velocidad", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    listOf(0.5f, 1f, 1.5f, 2f).forEach { sp ->
                        FilterChip(selected = speed == sp, onClick = { onSpeed(sp) }, label = { Text("${sp}x") })
                    }
                }

                Spacer(Modifier.size(12.dp))
                Text("Ajuste de pantalla", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    val modes = listOf(
                        AspectRatioFrameLayout.RESIZE_MODE_FIT to "Encajar",
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Recortar",
                        AspectRatioFrameLayout.RESIZE_MODE_FILL to "Llenar"
                    )
                    modes.forEach { (m, label) ->
                        FilterChip(selected = resizeMode == m, onClick = { onResize(m) }, label = { Text(label) })
                    }
                }

                // Subtítulos y audio (si el archivo tiene varias pistas)
                val textTracks = collectTracks(exo, C.TRACK_TYPE_TEXT, tracksVersion, allowDisable = true)
                val audioTracks = collectTracks(exo, C.TRACK_TYPE_AUDIO, tracksVersion, allowDisable = false)
                if (textTracks.isNotEmpty()) {
                    Spacer(Modifier.size(12.dp))
                    Text("Subtítulos", style = MaterialTheme.typography.labelLarge)
                    textTracks.forEach { t -> TrackRow(t) }
                }
                if (audioTracks.size > 1) {
                    Spacer(Modifier.size(12.dp))
                    Text("Pista de audio", style = MaterialTheme.typography.labelLarge)
                    audioTracks.forEach { t -> TrackRow(t) }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(t: TrackChoice) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = t.onSelect).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (t.selected) "● " else "○ ", color = if (t.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(t.label, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun collectTracks(exo: ExoPlayer, type: Int, @Suppress("UNUSED_PARAMETER") version: Int, allowDisable: Boolean): List<TrackChoice> {
    val out = ArrayList<TrackChoice>()
    val groups = exo.currentTracks.groups.filter { it.type == type && it.isSupported }
    if (groups.isEmpty()) return out
    if (allowDisable) {
        val noneSelected = groups.none { g -> (0 until g.length).any { g.isTrackSelected(it) } }
        out.add(TrackChoice("Desactivar", noneSelected) {
            exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(type, true).build()
        })
    }
    groups.forEach { g ->
        for (i in 0 until g.length) {
            val f = g.getTrackFormat(i)
            val label = f.label ?: f.language ?: "Pista ${i + 1}"
            out.add(TrackChoice(label, g.isTrackSelected(i)) {
                exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(type, false)
                    .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i))
                    .build()
            })
        }
    }
    return out
}
