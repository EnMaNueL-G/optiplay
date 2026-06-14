package com.optisuite.optiplay.ui.screens

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
import androidx.compose.material.icons.filled.Add
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir a lista") },
        text = {
            Column {
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
                            vm.createPlaylist(newName) { id ->
                                vm.addToPlaylist(id, songId)
                            }
                            Toast.makeText(ctx, "Añadida a “$newName”", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    }) { Text("Crear y añadir") }
                }
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(playlists, key = { it.id }) { pl ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                vm.addToPlaylist(pl.id, songId)
                                Toast.makeText(ctx, "Añadida a “${pl.name}”", Toast.LENGTH_SHORT).show()
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
