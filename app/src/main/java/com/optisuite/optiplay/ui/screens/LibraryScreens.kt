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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.optisuite.optiplay.ui.PlayerViewModel
import com.optisuite.optiplay.ui.components.AlbumArt
import com.optisuite.optiplay.ui.components.SongRow

@Composable
fun SongsScreen(vm: PlayerViewModel, contentPadding: PaddingValues) {
    val loading by vm.loading.collectAsStateWithLifecycle()
    val songs by vm.filteredSongs.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val current by vm.currentSong.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        when {
            loading && songs.isEmpty() ->
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            songs.isEmpty() && query.isBlank() ->
                Text(
                    "No se encontró música.",
                    Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            else -> LazyColumn(contentPadding = contentPadding) {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = vm::onQueryChange,
                            leadingIcon = { Icon(Icons.Filled.Search, null) },
                            placeholder = { Text("Buscar…") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        var sortMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Ordenar") }
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                val opts = listOf(
                                    com.optisuite.optiplay.ui.SongSort.TITLE to "Título",
                                    com.optisuite.optiplay.ui.SongSort.ARTIST to "Artista",
                                    com.optisuite.optiplay.ui.SongSort.ALBUM to "Álbum",
                                    com.optisuite.optiplay.ui.SongSort.DURATION to "Duración",
                                    com.optisuite.optiplay.ui.SongSort.RECENT to "Añadidas recientemente"
                                )
                                opts.forEach { (s, label) ->
                                    DropdownMenuItem(text = { Text(label) }, onClick = { vm.setSort(s); sortMenu = false })
                                }
                            }
                        }
                    }
                }
                items(songs, key = { it.id }) { song ->
                    SongRow(song, isCurrent = current?.id == song.id, onLongClick = { vm.requestAddToPlaylist(song.id) }) { vm.playFrom(songs, song) }
                }
            }
        }
        if (songs.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = { vm.playAll(shuffle = true) },
                icon = { Icon(Icons.Filled.Shuffle, null) },
                text = { Text("Aleatorio") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            )
        }
    }
}

@Composable
fun AlbumsScreen(vm: PlayerViewModel, contentPadding: PaddingValues, onOpenAlbum: (Long, String) -> Unit) {
    val albums by vm.albums.collectAsStateWithLifecycle()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)
    ) {
        items(albums, key = { it.albumId }) { album ->
            Column(Modifier.clickable { onOpenAlbum(album.albumId, album.album) }) {
                AlbumArt(album.albumArtUri, Modifier.fillMaxWidth(), corner = 12)
                Text(
                    album.album, style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Text(
                    "${album.artist} · ${album.songCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun FoldersScreen(vm: PlayerViewModel, contentPadding: PaddingValues) {
    val folders by vm.folders.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        items(folders, key = { it.path }) { folder ->
            Row(
                Modifier.fillMaxWidth()
                    .clickable { vm.playFrom(folder.songs, folder.songs.first()) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(folder.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${folder.songCount} pistas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Filled.PlayArrow, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
