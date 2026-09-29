package com.optisuite.optiplay.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.optisuite.optiplay.data.Video
import com.optisuite.optiplay.playback.VideoPlayerActivity
import com.optisuite.optiplay.ui.PlayerViewModel
import com.optisuite.optiplay.ui.components.formatDuration

@Composable
fun VideosScreen(vm: PlayerViewModel, contentPadding: PaddingValues) {
    val ctx = LocalContext.current
    val videos by vm.videos.collectAsStateWithLifecycle()
    val progress by vm.videoProgress.collectAsStateWithLifecycle()
    var folder by rememberSaveable { mutableStateOf<String?>(null) }

    if (videos.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
            Text("No se encontraron vídeos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val folders = remember(videos) {
        videos.groupingBy { it.folder }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
    }
    val shown = remember(videos, folder) { if (folder == null) videos else videos.filter { it.folder == folder } }
    val continuing = remember(videos, progress) { videos.filter { (progress[it.uri.toString()] ?: 0f) > 0f } }

    LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        if (folders.size > 1) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { FilterChip(selected = folder == null, onClick = { folder = null }, label = { Text("Todos (${videos.size})") }) }
                    items(folders) { f ->
                        FilterChip(selected = folder == f, onClick = { folder = f }, label = { Text(f.ifBlank { "Otros" }) })
                    }
                }
            }
        }
        if (folder == null && continuing.isNotEmpty()) {
            item {
                Text(
                    "Continuar viendo",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            items(continuing, key = { "c" + it.id }) { v ->
                VideoRow(v, progress[v.uri.toString()]) { open(ctx, v) }
            }
            item {
                Text(
                    "Todos los vídeos",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
                )
            }
        }
        items(shown, key = { it.id }) { v ->
            VideoRow(v, progress[v.uri.toString()]) { open(ctx, v) }
        }
    }
}

private fun open(ctx: android.content.Context, v: Video) {
    ctx.startActivity(
        Intent(ctx, VideoPlayerActivity::class.java)
            .putExtra(VideoPlayerActivity.EXTRA_URI, v.uri.toString())
            .putExtra(VideoPlayerActivity.EXTRA_TITLE, v.title)
    )
}

@Composable
private fun VideoRow(v: Video, watched: Float?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(112.dp, 64.dp).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Movie, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            // Fotograma real del vídeo (se genera en el móvil, sin red)
            val ctx = LocalContext.current
            val request = remember(v.id) {
                // Fotograma al ~10 % (máx. 60 s): muchos vídeos empiezan en negro
                ImageRequest.Builder(ctx).data(v.uri)
                    .videoFrameMillis((v.durationMs / 10).coerceIn(0L, 60_000L))
                    .build()
            }
            AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Text(
                formatDuration(v.durationMs),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                    .clip(RoundedCornerShape(4.dp)).background(Color(0xAA000000)).padding(horizontal = 4.dp)
            )
            if (watched != null && watched > 0f) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color(0x66FFFFFF))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(watched.coerceIn(0f, 1f)).background(MaterialTheme.colorScheme.primary))
                }
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(v.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (v.height > 0) Text("${minOf(v.width, v.height).takeIf { it > 0 } ?: v.height}p", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(sizeLabel(v.sizeBytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (v.folder.isNotBlank()) Text(v.folder, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun sizeLabel(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.1f GB".format(mb / 1024) else "%.0f MB".format(mb)
}
