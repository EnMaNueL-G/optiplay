package com.optisuite.optiplay.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.optisuite.optiplay.data.db.PlaylistEntity
import com.optisuite.optiplay.ui.PlayerViewModel

@Composable
fun HomeScreen(
    vm: PlayerViewModel,
    contentPadding: PaddingValues,
    onOpenFavorites: () -> Unit,
    onOpenRecents: () -> Unit,
    onOpenMostPlayed: () -> Unit,
    onOpenAlbums: () -> Unit,
    onOpenArtists: () -> Unit,
    onOpenFolders: () -> Unit,
    onOpenPlaylist: (Long, String) -> Unit
) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        item {
            Text("OptiPlay", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 12.dp))
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickCard(Modifier.weight(1f), Icons.Filled.Favorite, "Favoritos", onOpenFavorites)
                QuickCard(Modifier.weight(1f), Icons.Filled.History, "Recientes", onOpenRecents)
                QuickCard(Modifier.weight(1f), Icons.Filled.TrendingUp, "Top", onOpenMostPlayed)
            }
        }
        item { SectionTitle("Explorar") }
        item { BrowseRow(Icons.Filled.Album, "Álbumes", onOpenAlbums) }
        item { BrowseRow(Icons.Filled.Person, "Artistas", onOpenArtists) }
        item { BrowseRow(Icons.Filled.Folder, "Carpetas", onOpenFolders) }

        item {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Tus listas", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { showCreate = true }) {
                    Icon(Icons.Filled.Add, null); Spacer(Modifier.size(4.dp)); Text("Nueva")
                }
            }
        }
        if (playlists.isEmpty()) {
            item { Text("Aún no tienes listas. Crea una con “Nueva”.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(playlists, key = { it.id }) { pl ->
            PlaylistRow(pl) { onOpenPlaylist(pl.id, pl.name) }
        }
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Nueva lista") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, placeholder = { Text("Nombre") }) },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) vm.createPlaylist(name)
                    showCreate = false
                }) { Text("Crear") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun SectionTitle(t: String) {
    Text(t, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}

@Composable
private fun QuickCard(modifier: Modifier, icon: ImageVector, label: String, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick)) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun BrowseRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
private fun PlaylistRow(pl: PlaylistEntity, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(28.dp))
        Text(pl.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 16.dp))
    }
}
