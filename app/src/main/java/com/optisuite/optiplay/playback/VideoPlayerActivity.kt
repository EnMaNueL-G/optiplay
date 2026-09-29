package com.optisuite.optiplay.playback

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Rational
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.optisuite.optiplay.audio.AudioEffects
import com.optisuite.optiplay.data.db.MusicDao
import com.optisuite.optiplay.data.db.VideoProgressEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import kotlin.math.abs

private fun fmt(ms: Long): String {
    val t = (ms / 1000).coerceAtLeast(0)
    val h = t / 3600; val m = (t % 3600) / 60; val s = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private data class TrackChoice(val label: String, val selected: Boolean, val onSelect: () -> Unit)

/**
 * Reproductor de VÍDEO a pantalla completa con CONTROLES PROPIOS (Compose).
 *  - Arrastre vertical IZQUIERDA = brillo · DERECHA = volumen · horizontal = avanzar/retroceder
 *  - Toque = mostrar/ocultar controles · doble toque: izquierda −10 s, centro play/pausa, derecha +10 s
 *  - Bloqueo de pantalla, rotación, continuar donde se quedó, subtítulos externos (.srt/.vtt/.ass)
 *  - Se abre también desde otras apps ("Abrir con…"). PiP al salir.
 *  - Decodifica lo que soporten los decodificadores del móvil (sin FFmpeg).
 */
class VideoPlayerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_URI = "extra_uri"
        const val EXTRA_TITLE = "extra_title"
        private const val MIN_RESUME_MS = 5_000L
        private const val END_MARGIN_MS = 10_000L
    }

    private val dao: MusicDao by inject()
    private val effects: AudioEffects by inject()
    private var player: ExoPlayer? = null

    private var currentUri: Uri? = null
    private val title = mutableStateOf("")
    private val resumedFrom = mutableStateOf(0L)
    private val inPip = mutableStateOf(false)
    private val subtitleName = mutableStateOf<String?>(null)
    /** El usuario eligió orientación a mano: no rotar automáticamente según el vídeo. */
    private var userOrientation = false
    private var lastRecoveryAt = 0L

    private val pickSubtitle = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) loadSubtitle(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val exo = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true // pausa la música de fondo al abrir un vídeo
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        runCatching { exo.setAudioSessionId(effects.audioSessionId) } // el ecualizador también aplica al vídeo
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (userOrientation || videoSize.width == 0 || videoSize.height == 0) return
                val w = videoSize.width * videoSize.pixelWidthHeightRatio
                requestedOrientation = if (w > videoSize.height) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // Algunos móviles pierden el decodificador al pasar a segundo plano (p. ej. al abrir el
                // selector de subtítulos) aunque el vídeo se reproduzca bien: reintentar en la misma posición.
                val now = System.currentTimeMillis()
                if (now - lastRecoveryAt > 5_000L) {
                    lastRecoveryAt = now
                    exo.prepare()
                    return
                }
                Toast.makeText(this@VideoPlayerActivity,
                    "No se puede reproducir este vídeo (el móvil no tiene decodificador para este formato)", Toast.LENGTH_LONG).show()
            }
        })
        player = exo

        if (!openFromIntent(intent)) { finish(); return }

        setContent {
            VideoScreen(exo)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        saveProgress()
        openFromIntent(intent)
    }

    /** Carga el vídeo del intent (desde la biblioteca o desde "Abrir con…" de otra app). */
    private fun openFromIntent(intent: Intent): Boolean {
        // "Abrir con" (VIEW) usa intent.data con permiso temporal. EXTRA_URI solo lo usa la propia
        // biblioteca y solo se aceptan vídeos de MediaStore (la actividad está exportada).
        val uri = if (intent.action == Intent.ACTION_VIEW) intent.data ?: return false
        else intent.getStringExtra(EXTRA_URI)?.takeIf { it.startsWith("content://media/") }?.let(Uri::parse) ?: return false
        val exo = player ?: return false
        currentUri = uri
        subtitleName.value = null
        resumedFrom.value = 0L
        title.value = intent.getStringExtra(EXTRA_TITLE) ?: displayName(uri) ?: "Vídeo"
        exo.setMediaItem(MediaItem.fromUri(uri))
        exo.prepare()
        exo.playWhenReady = true
        lifecycleScope.launch {
            val saved = runCatching { dao.videoProgress(uri.toString()) }.getOrNull() ?: return@launch
            if (currentUri != uri) return@launch
            if (saved.positionMs > MIN_RESUME_MS && saved.positionMs < saved.durationMs - END_MARGIN_MS) {
                exo.seekTo(saved.positionMs)
                resumedFrom.value = saved.positionMs
            }
        }
        return true
    }

    private fun displayName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.substringBeforeLast('.') else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')

    /** Guarda dónde se quedó. Al llegar casi al final se borra (vídeo terminado). */
    private fun saveProgress() {
        val exo = player ?: return
        val uri = currentUri?.toString() ?: return
        val pos = exo.currentPosition
        val dur = exo.duration
        if (dur <= 0 || dur == C.TIME_UNSET) return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                if (pos < MIN_RESUME_MS || pos > dur - END_MARGIN_MS) dao.clearVideoProgress(uri)
                else dao.saveVideoProgress(VideoProgressEntity(uri, pos, dur))
            }
        }
    }

    private fun loadSubtitle(sub: Uri) {
        val exo = player ?: return
        val video = currentUri ?: return
        val name = displayName(sub) ?: "subtítulos"
        val lower = (runCatching {
            contentResolver.query(sub, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: sub.toString()).lowercase()
        val mime = when {
            lower.endsWith(".vtt") -> MimeTypes.TEXT_VTT
            lower.endsWith(".ass") || lower.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            lower.endsWith(".ttml") || lower.endsWith(".xml") -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.APPLICATION_SUBRIP
        }
        val pos = exo.currentPosition
        val item = MediaItem.Builder()
            .setUri(video)
            .setSubtitleConfigurations(
                listOf(
                    MediaItem.SubtitleConfiguration.Builder(sub)
                        .setMimeType(mime)
                        .setLabel(name)
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                )
            )
            .build()
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .build()
        exo.setMediaItem(item, pos)
        exo.prepare()
        exo.playWhenReady = true
        subtitleName.value = name
    }

    private fun cycleOrientation(): String {
        userOrientation = true
        return when (requestedOrientation) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE -> {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT; "Vertical"
            }
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT -> {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER; "Automática"
            }
            else -> {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE; "Horizontal"
            }
        }
    }

    @Composable
    private fun VideoScreen(exo: ExoPlayer) {
        val ctx = LocalContext.current
        var overlay by remember { mutableStateOf("") }
        var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
        var controlsVisible by remember { mutableStateOf(true) }
        var settingsOpen by remember { mutableStateOf(false) }
        var locked by remember { mutableStateOf(false) }
        var isPlaying by remember { mutableStateOf(true) }
        var position by remember { mutableStateOf(0L) }
        var duration by remember { mutableStateOf(0L) }
        var speed by remember { mutableStateOf(1f) }
        var tracksVersion by remember { mutableStateOf(0) }
        var seeking by remember { mutableStateOf<Float?>(null) }
        val resumed by resumedFrom
        val pip by inPip

        val audio = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
        val maxVol = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }

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
                delay(300)
            }
        }
        // Auto-ocultar controles
        LaunchedEffect(controlsVisible, isPlaying, settingsOpen, locked) {
            if (controlsVisible && isPlaying && !settingsOpen) {
                delay(3500)
                controlsVisible = false
            }
        }
        // Aviso "continuando desde…" durante unos segundos
        LaunchedEffect(resumed) {
            if (resumed > 0L) { delay(7000); resumedFrom.value = 0L }
        }
        LaunchedEffect(overlay) {
            if (overlay.startsWith("⏪") || overlay.startsWith("⏩ +") || overlay.startsWith("🔄")) {
                delay(700); overlay = ""
            }
        }

        fun seekBy(deltaMs: Long) {
            val target = (exo.currentPosition + deltaMs).coerceIn(0L, exo.duration.coerceAtLeast(0L))
            exo.seekTo(target)
            overlay = if (deltaMs < 0) "⏪ −${abs(deltaMs) / 1000} s" else "⏩ +${deltaMs / 1000} s"
        }

        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { c -> PlayerView(c).apply { this.player = exo; useController = false; this.resizeMode = resizeMode } },
                update = { it.resizeMode = resizeMode },
                modifier = Modifier.fillMaxSize()
            )

            if (!pip) {
                // Capa de gestos (debajo de los controles; el centro de los controles es transparente)
                Box(
                    Modifier.fillMaxSize()
                        .pointerInput(locked) {
                            detectTapGestures(
                                onTap = { controlsVisible = !controlsVisible },
                                onDoubleTap = { o ->
                                    if (locked) return@detectTapGestures
                                    when {
                                        o.x < size.width / 3f -> seekBy(-10_000)
                                        o.x > size.width * 2f / 3f -> seekBy(10_000)
                                        else -> if (exo.isPlaying) exo.pause() else exo.play()
                                    }
                                }
                            )
                        }
                        .pointerInput(locked) {
                            if (locked) return@pointerInput
                            var mode = 0
                            var startX = 0f
                            var totalY = 0f
                            var startPos = 0L
                            var seekTarget = 0L
                            var startVol = 0
                            var startBright = 0.5f
                            detectDragGestures(
                                onDragStart = { o ->
                                    mode = 0; startX = o.x; totalY = 0f
                                    startPos = exo.currentPosition
                                    startVol = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                                    val b = window.attributes.screenBrightness
                                    startBright = if (b < 0) 0.5f else b
                                },
                                onDragEnd = {
                                    if (mode == 1) exo.seekTo(seekTarget.coerceAtLeast(0))
                                    mode = 0; overlay = ""
                                },
                                onDragCancel = { mode = 0; overlay = "" },
                                onDrag = { change, amount ->
                                    if (mode == 0) mode = if (abs(amount.x) > abs(amount.y)) 1 else if (startX < size.width / 2f) 2 else 3
                                    totalY += amount.y
                                    when (mode) {
                                        1 -> {
                                            // 1 pantalla de ancho ≈ 90 s
                                            val dx = change.position.x - startX
                                            seekTarget = (startPos + dx / size.width * 90_000).toLong()
                                                .coerceIn(0, exo.duration.coerceAtLeast(0))
                                            val diff = (seekTarget - startPos) / 1000
                                            overlay = "${fmt(seekTarget)}  (${if (diff >= 0) "+" else "−"}${abs(diff)} s)"
                                        }
                                        2 -> {
                                            val nv = (startBright - totalY / size.height).coerceIn(0.01f, 1f)
                                            val lp = window.attributes
                                            lp.screenBrightness = nv; window.attributes = lp
                                            overlay = "☀ ${(nv * 100).toInt()}%"
                                        }
                                        3 -> {
                                            // Desplazamiento ACUMULADO: arrastrar toda la altura = de 0 a máximo
                                            val nv = (startVol - totalY / size.height * maxVol).toInt().coerceIn(0, maxVol)
                                            if (nv != audio.getStreamVolume(AudioManager.STREAM_MUSIC)) {
                                                audio.setStreamVolume(AudioManager.STREAM_MUSIC, nv, 0)
                                            }
                                            overlay = "🔊 ${nv * 100 / maxVol}%"
                                        }
                                    }
                                }
                            )
                        }
                )
            }

            // Controles propios
            AnimatedVisibility(visible = controlsVisible && !pip) {
                if (locked) {
                    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                        IconButton(
                            onClick = { locked = false; controlsVisible = true },
                            modifier = Modifier.align(Alignment.CenterStart).padding(16.dp)
                                .clip(RoundedCornerShape(24.dp)).background(Color(0x88000000))
                        ) { Icon(Icons.Filled.Lock, "Desbloquear", tint = Color.White) }
                    }
                } else {
                    Column(Modifier.fillMaxSize().background(Color(0x33000000)).windowInsetsPadding(WindowInsets.systemBars)) {
                        // Barra superior
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { finish() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = Color.White)
                            }
                            Text(title.value, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                            IconButton(onClick = { overlay = "🔄 " + cycleOrientation() }) {
                                Icon(Icons.Filled.ScreenRotation, "Girar", tint = Color.White)
                            }
                            IconButton(onClick = { settingsOpen = true; controlsVisible = true }) {
                                Icon(Icons.Filled.Settings, "Ajustes", tint = Color.White)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        // Centro: −10 / play / +10
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { seekBy(-10_000) }, modifier = Modifier.size(56.dp)) {
                                Icon(Icons.Filled.Replay10, "Retroceder 10 s", tint = Color.White, modifier = Modifier.size(36.dp))
                            }
                            Spacer(Modifier.width(32.dp))
                            IconButton(
                                onClick = { if (exo.isPlaying) exo.pause() else exo.play() },
                                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(36.dp)).background(Color(0x66000000))
                            ) {
                                Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pausa", tint = Color.White, modifier = Modifier.size(48.dp))
                            }
                            Spacer(Modifier.width(32.dp))
                            IconButton(onClick = { seekBy(10_000) }, modifier = Modifier.size(56.dp)) {
                                Icon(Icons.Filled.Forward10, "Avanzar 10 s", tint = Color.White, modifier = Modifier.size(36.dp))
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        // Barra inferior
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { locked = true; settingsOpen = false }) {
                                Icon(Icons.Filled.LockOpen, "Bloquear controles", tint = Color.White)
                            }
                            val shown = seeking?.let { (it * duration).toLong() } ?: position
                            Text(fmt(shown), color = Color.White, style = MaterialTheme.typography.labelMedium)
                            Slider(
                                value = seeking ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                                onValueChange = { seeking = it; controlsVisible = true },
                                onValueChangeFinished = {
                                    seeking?.let { exo.seekTo((it * duration).toLong()) }
                                    seeking = null
                                },
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                            )
                            Text(fmt(duration), color = Color.White, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // "Continuando desde…" con opción de empezar desde el principio
            if (resumed > 0L && !pip) {
                Surface(
                    color = Color(0xDD202020), shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.systemBars).padding(bottom = 72.dp)
                ) {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Continuando desde ${fmt(resumed)}", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { exo.seekTo(0); resumedFrom.value = 0L }) { Text("Desde el inicio") }
                    }
                }
            }

            // Indicador de gesto (brillo/volumen/seek)
            if (overlay.isNotEmpty() && !pip) {
                Box(
                    Modifier.align(Alignment.Center).clip(RoundedCornerShape(8.dp))
                        .background(Color(0xAA000000)).padding(horizontal = 20.dp, vertical = 12.dp)
                ) { Text(overlay, color = Color.White, style = MaterialTheme.typography.titleMedium) }
            }

            // Panel de Ajustes
            if (settingsOpen && !pip) {
                SettingsPanel(
                    exo = exo,
                    tracksVersion = tracksVersion,
                    speed = speed,
                    onSpeed = { speed = it; exo.setPlaybackSpeed(it) },
                    resizeMode = resizeMode,
                    onResize = { resizeMode = it },
                    subtitleName = subtitleName.value,
                    onPickSubtitle = {
                        runCatching { pickSubtitle.launch(arrayOf("*/*")) }
                            .onFailure { Toast.makeText(ctx, "No hay gestor de archivos disponible", Toast.LENGTH_SHORT).show() }
                    },
                    onDismiss = { settingsOpen = false }
                )
            }
        }
    }

    private fun pipParams(): PictureInPictureParams? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val vs = player?.videoSize
        val b = PictureInPictureParams.Builder()
        if (vs != null && vs.width > 0 && vs.height > 0) {
            // Android limita la proporción de PiP a 1:2.39 … 2.39:1
            val w = (vs.width * vs.pixelWidthHeightRatio).toInt().coerceAtLeast(1)
            val ratio = (w.toFloat() / vs.height).coerceIn(1f / 2.39f, 2.39f)
            b.setAspectRatio(Rational((ratio * 1000).toInt(), 1000))
        }
        return b.build()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val canPip = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        if (canPip && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && player?.isPlaying == true) {
            runCatching { enterPictureInPictureMode(pipParams()!!) }
        }
    }

    override fun onPictureInPictureModeChanged(isInPip: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPip, newConfig)
        inPip.value = isInPip
        // Cerrar la ventana flotante con la X: la actividad ya no está visible → parar el sonido.
        if (!isInPip && !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
            player?.pause()
        }
    }

    override fun onStop() {
        super.onStop()
        saveProgress()
        if (!isInPictureInPictureMode) player?.pause()
    }

    override fun onDestroy() {
        saveProgress()
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
    subtitleName: String?,
    onPickSubtitle: () -> Unit,
    onDismiss: () -> Unit
) {
    // Scrim que cierra al tocar fuera
    Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.width(340.dp).heightIn(max = 460.dp).clickable(enabled = false) {}
        ) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Text("Ajustes de reproducción", style = MaterialTheme.typography.titleMedium)

                Spacer(Modifier.size(12.dp))
                Text("Velocidad", style = MaterialTheme.typography.labelLarge)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp)
                ) {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { sp ->
                        FilterChip(selected = speed == sp, onClick = { onSpeed(sp) }, label = { Text("${sp}x") })
                    }
                }

                Spacer(Modifier.size(12.dp))
                Text("Ajuste de pantalla", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    val modes = listOf(
                        AspectRatioFrameLayout.RESIZE_MODE_FIT to "Encajar",
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Recortar",
                        AspectRatioFrameLayout.RESIZE_MODE_FILL to "Estirar"
                    )
                    modes.forEach { (m, label) ->
                        FilterChip(selected = resizeMode == m, onClick = { onResize(m) }, label = { Text(label) })
                    }
                }

                // Subtítulos: los del archivo + cargar uno externo
                val textTracks = collectTracks(exo, C.TRACK_TYPE_TEXT, tracksVersion, allowDisable = true)
                val audioTracks = collectTracks(exo, C.TRACK_TYPE_AUDIO, tracksVersion, allowDisable = false)
                Spacer(Modifier.size(12.dp))
                Text("Subtítulos", style = MaterialTheme.typography.labelLarge)
                textTracks.forEach { t -> TrackRow(t) }
                OutlinedButton(onClick = onPickSubtitle, modifier = Modifier.padding(top = 4.dp)) {
                    Text(if (subtitleName != null) "Cambiar archivo (.srt, .vtt, .ass)" else "Cargar archivo (.srt, .vtt, .ass)")
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
            val label = f.label ?: f.language?.let { java.util.Locale(it).displayLanguage.replaceFirstChar(Char::uppercase) } ?: "Pista ${i + 1}"
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
