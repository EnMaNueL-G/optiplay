package com.optisuite.optiplay.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.optisuite.optiplay.data.Video
import com.optisuite.optiplay.playback.VideoPlayerActivity
import com.optisuite.optiplay.ui.PlayerViewModel
import com.optisuite.optiplay.ui.components.formatDuration

@Composable
fun VideosScreen(vm: PlayerViewModel, contentPadding: PaddingValues) {
    val ctx = LocalContext.current
    val videos by vm.videos.collectAsStateWithLifecycle()

    if (videos.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
            Text("No se encontraron vídeos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        items(videos, key = { it.id }) { v ->
            VideoRow(v) {
                ctx.startActivity(
                    Intent(ctx, VideoPlayerActivity::class.java)
                        .putExtra(VideoPlayerActivity.EXTRA_URI, v.uri.toString())
                        .putExtra(VideoPlayerActivity.EXTRA_TITLE, v.title)
                )
            }
        }
    }
}

@Composable
private fun VideoRow(v: Video, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(96.dp, 56.dp).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Movie, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.Filled.PlayCircle, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(28.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(v.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(formatDuration(v.durationMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (v.height > 0) Text("${v.height}p", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${v.sizeBytes / (1024 * 1024)} MB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
