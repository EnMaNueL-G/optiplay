package com.optisuite.optiplay.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.optisuite.optiplay.BuildConfig
import com.optisuite.optiplay.data.ThemeMode
import com.optisuite.optiplay.ui.PlayerViewModel

private fun copy(ctx: Context, label: String, value: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, value))
    Toast.makeText(ctx, "$label copiado", Toast.LENGTH_SHORT).show()
}

@Composable
fun SettingsScreen(vm: PlayerViewModel, contentPadding: PaddingValues) {
    val ctx = LocalContext.current
    val theme by vm.themeMode.collectAsStateWithLifecycle()
    val dynamic by vm.dynamicColor.collectAsStateWithLifecycle()
    val songs by vm.songs.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(contentPadding).padding(16.dp)
    ) {
        Text("Apariencia", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        val labels = mapOf(
            ThemeMode.SYSTEM to "Automático (según el sistema)",
            ThemeMode.LIGHT to "Claro",
            ThemeMode.DARK to "Oscuro",
            ThemeMode.AMOLED to "AMOLED (negro puro)"
        )
        ThemeMode.entries.forEach { mode ->
            Row(
                Modifier.fillMaxWidth().selectable(selected = theme == mode, onClick = { vm.setTheme(mode) }).padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = theme == mode, onClick = { vm.setTheme(mode) })
                Text(labels[mode] ?: mode.name, Modifier.padding(start = 8.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Colores dinámicos (Material You)")
                Text("Extrae la paleta del fondo de pantalla", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = dynamic, onCheckedChange = { vm.setDynamic(it) })
        }

        Spacer(Modifier.height(16.dp)); Divider(); Spacer(Modifier.height(16.dp))

        val sleepLeft by vm.sleepMinutesLeft.collectAsStateWithLifecycle()
        Text("Temporizador de apagado", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        if (sleepLeft > 0) {
            Text("Activo: se pausará en $sleepLeft min", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { vm.cancelSleepTimer() }) { Text("Cancelar temporizador") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 45, 60).forEach { m ->
                    AssistChip(onClick = { vm.startSleepTimer(m) }, label = { Text("$m min") })
                }
            }
        }

        Spacer(Modifier.height(16.dp)); Divider(); Spacer(Modifier.height(16.dp))

        Text("Privacidad", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text("• Sin permiso de Internet.\n• Sin anuncios ni telemetría.\n• Tus datos nunca salen del dispositivo.", style = MaterialTheme.typography.bodyMedium)
        Text("Biblioteca: ${songs.size} pistas locales.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))

        Spacer(Modifier.height(16.dp)); Divider(); Spacer(Modifier.height(16.dp))

        Text("Acerca de", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text("OptiPlay", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("v${BuildConfig.VERSION_NAME} · por EnMaNueL-G", style = MaterialTheme.typography.bodyMedium)
        Text("Suite OptiSuite · 100% gratis y privado", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(12.dp))
        LinkRow(ctx, Icons.Filled.Language, "Sitio web oficial", "optisuite.app", "https://optisuite.app")
        Spacer(Modifier.height(8.dp))
        LinkRow(ctx, Icons.Filled.Email, "Soporte", "support@optisuite.app", "mailto:support@optisuite.app")

        Spacer(Modifier.height(16.dp))
        Text("Apoya el proyecto", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        DonationRow(ctx, "Binance Pay ID", "1165745950")
        Spacer(Modifier.height(8.dp))
        DonationRow(ctx, "BSC (BEP20)", "0xb6f6731a4ea87f8e1fd6f44f48b5bc4204571f08")
    }
}

private fun openUrl(ctx: Context, url: String) {
    runCatching {
        ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { Toast.makeText(ctx, "No se pudo abrir el enlace", Toast.LENGTH_SHORT).show() }
}

@Composable
private fun LinkRow(ctx: Context, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, url: String) {
    Card(Modifier.fillMaxWidth().clickable { openUrl(ctx, url) }) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            }
        }
    }
}

@Composable
private fun DonationRow(ctx: Context, label: String, value: String) {
    Card(Modifier.fillMaxWidth().clickable { copy(ctx, label, value) }) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(value, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            Icon(Icons.Filled.ContentCopy, "Copiar")
        }
    }
}
