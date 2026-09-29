package com.optisuite.optiplay.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.optisuite.optiplay.ui.PlayerViewModel

@Composable
fun AddToPlaylistDialog(vm: PlayerViewModel, songId: Long, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    val song = vm.songById(songId)
    fun added(name: String) = { ok: Boolean ->
        Toast.makeText(ctx, if (ok) "Añadida a “$name”" else "Ya estaba en “$name”", Toast.LENGTH_SHORT).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(song?.title ?: "Canción", maxLines = 1) },
        text = {
            Column {
                if (song != null) {
                    ActionRow(Icons.AutoMirrored.Filled.QueueMusic, "Reproducir a continuación") {
                        vm.player.playNext(song)
                        Toast.makeText(ctx, "Sonará a continuación", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                    ActionRow(Icons.AutoMirrored.Filled.PlaylistAdd, "Añadir a la cola") {
                        vm.player.addToQueue(song)
                        Toast.makeText(ctx, "Añadida a la cola", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                    ActionRow(Icons.Filled.Share, "Compartir archivo") {
                        runCatching {
                            val send = Intent(Intent.ACTION_SEND)
                                .setType(song.mimeType.ifBlank { "audio/*" })
                                .putExtra(Intent.EXTRA_STREAM, song.uri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            ctx.startActivity(Intent.createChooser(send, "Compartir “${song.title}”"))
                        }
                        onDismiss()
                    }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Text("Añadir a lista", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(
                    Modifier.fillMaxWidth().clickable { creating = true }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Crear nueva lista", Modifier.padding(start = 12.dp), color = MaterialTheme.colorScheme.primary)
                }
                if (creating) {
                    OutlinedTextField(
                        value = newName, onValueChange = { newName = it },
                        singleLine = true, placeholder = { Text("Nombre de la lista") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(onClick = {
                        if (newName.isNotBlank()) {
                            val name = newName
                            vm.createPlaylist(name) { id -> vm.addToPlaylist(id, songId, added(name)) }
                            onDismiss()
                        }
                    }) { Text("Crear y añadir") }
                }
                LazyColumn(Modifier.heightIn(max = 220.dp)) {
                    items(playlists, key = { it.id }) { pl ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                vm.addToPlaylist(pl.id, songId, added(pl.name))
                                onDismiss()
                            }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, modifier = Modifier.size(24.dp))
                            Text(pl.name, Modifier.padding(start = 12.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

@Composable
private fun ActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, modifier = Modifier.size(24.dp))
        Text(label, Modifier.padding(start = 12.dp))
    }
}
