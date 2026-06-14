package com.optisuite.optiplay.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.optisuite.optiplay.data.Song
import com.optisuite.optiplay.ui.PlayerViewModel
import com.optisuite.optiplay.ui.components.AlbumArt
import com.optisuite.optiplay.ui.components.SongRow

@Composable
fun DetailScaffold(title: String, onBack: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(top = 36.dp, start = 4.dp, end = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") }
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            content(PaddingValues(bottom = 12.dp))
        }
    }
}

@Composable
private fun SongList(vm: PlayerViewModel, songs: List<Song>, padding: PaddingValues, emptyMsg: String) {
    val current by vm.currentSong.collectAsStateWithLifecycle()
    if (songs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyMsg, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayPill("Reproducir", Icons.Filled.PlayArrow) { vm.playFrom(songs, songs.first()) }
                PlayPill("Aleatorio", Icons.Filled.Shuffle) {
                    if (!vm.player.shuffle.value) vm.player.toggleShuffle(); vm.playFrom(songs, songs.random())
                }
            }
        }
        items(songs, key = { it.id }) { s ->
            SongRow(s, isCurrent = current?.id == s.id, onLongClick = { vm.requestAddToPlaylist(s.id) }) { vm.playFrom(songs, s) }
        }
    }
}

@Composable
private fun PlayPill(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
            Text(label, color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
fun FavoritesScreen(vm: PlayerViewModel, onBack: () -> Unit) {
    val songs by vm.favoriteSongs.collectAsStateWithLifecycle()
    DetailScaffold("Favoritos", onBack) { p -> SongList(vm, songs, p, "Marca canciones con ♥ para verlas aquí.") }
}

@Composable
fun RecentsScreen(vm: PlayerViewModel, onBack: () -> Unit) {
    val songs by vm.recentSongs.collectAsStateWithLifecycle()
    DetailScaffold("Reproducidas recientemente", onBack) { p -> SongList(vm, songs, p, "Aún no has reproducido nada.") }
}

@Composable
fun MostPlayedScreen(vm: PlayerViewModel, onBack: () -> Unit) {
    val songs by vm.mostPlayed.collectAsStateWithLifecycle()
    DetailScaffold("Más reproducidas", onBack) { p -> SongList(vm, songs, p, "Aún no hay estadísticas.") }
}

@Composable
fun AlbumDetailScreen(vm: PlayerViewModel, albumId: Long, name: String, onBack: () -> Unit) {
    val albums by vm.albums.collectAsStateWithLifecycle()
    val album = albums.firstOrNull { it.albumId == albumId }
    DetailScaffold(name, onBack) { p ->
        if (album == null) Box(Modifier.fillMaxSize()) {} else SongList(vm, album.songs, p, "")
    }
}

@Composable
fun ArtistsScreen(vm: PlayerViewModel, onBack: () -> Unit, onOpenArtist: (String) -> Unit) {
    val artists by vm.artists.collectAsStateWithLifecycle()
    DetailScaffold("Artistas", onBack) { p ->
        LazyColumn(contentPadding = p, modifier = Modifier.fillMaxSize()) {
            items(artists, key = { it.artist }) { a ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenArtist(a.artist) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AlbumArt(a.albumArtUri, Modifier.size(48.dp))
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(a.artist, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${a.songCount} pistas · ${a.albumCount} álbumes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun ArtistDetailScreen(vm: PlayerViewModel, artist: String, onBack: () -> Unit) {
    val artists by vm.artists.collectAsStateWithLifecycle()
    val a = artists.firstOrNull { it.artist == artist }
    DetailScaffold(artist, onBack) { p -> SongList(vm, a?.songs ?: emptyList(), p, "") }
}

@Composable
fun PlaylistDetailScreen(vm: PlayerViewModel, playlistId: Long, name: String, onBack: () -> Unit) {
    val songsFlow = remember(playlistId) { vm.playlistSongs(playlistId) }
    val songs by songsFlow.collectAsStateWithLifecycle()
    val current by vm.currentSong.collectAsStateWithLifecycle()
    DetailScaffold(name, onBack) { p ->
        if (songs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Lista vacía. Añade canciones con un toque largo en cualquier canción.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
            }
            return@DetailScaffold
        }
        LazyColumn(contentPadding = p, modifier = Modifier.fillMaxSize()) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlayPill("Reproducir", Icons.Filled.PlayArrow) { vm.playFrom(songs, songs.first()) }
                }
            }
            items(songs, key = { it.id }) { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        SongRow(s, isCurrent = current?.id == s.id) { vm.playFrom(songs, s) }
                    }
                    IconButton(onClick = { vm.removeFromPlaylist(playlistId, s.id) }) {
                        Icon(Icons.Filled.Delete, "Quitar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun QueueScreen(vm: PlayerViewModel, onBack: () -> Unit) {
    val currentId by vm.player.currentMediaId.collectAsStateWithLifecycle()
    val byIdSongs by vm.songs.collectAsStateWithLifecycle()
    val map = remember(byIdSongs) { byIdSongs.associateBy { it.id.toString() } }
    val queueIds = remember(currentId) { vm.player.queueMediaIds() }
    DetailScaffold("Cola de reproducción", onBack) { p ->
        LazyColumn(contentPadding = p, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(queueIds) { index, mid ->
                val s = map[mid]
                if (s != null) SongRow(s, isCurrent = mid == currentId) { vm.player.seekToItem(index) }
            }
        }
    }
}
