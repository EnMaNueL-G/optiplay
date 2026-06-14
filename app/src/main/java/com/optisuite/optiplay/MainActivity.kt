package com.optisuite.optiplay

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.optisuite.optiplay.ui.PlayerViewModel
import com.optisuite.optiplay.ui.components.MiniPlayer
import com.optisuite.optiplay.ui.screens.AddToPlaylistDialog
import com.optisuite.optiplay.ui.screens.AlbumDetailScreen
import com.optisuite.optiplay.ui.screens.AlbumsScreen
import com.optisuite.optiplay.ui.screens.ArtistDetailScreen
import com.optisuite.optiplay.ui.screens.ArtistsScreen
import com.optisuite.optiplay.ui.screens.DetailScaffold
import com.optisuite.optiplay.ui.screens.EqualizerScreen
import com.optisuite.optiplay.ui.screens.FavoritesScreen
import com.optisuite.optiplay.ui.screens.FoldersScreen
import com.optisuite.optiplay.ui.screens.HomeScreen
import com.optisuite.optiplay.ui.screens.MostPlayedScreen
import com.optisuite.optiplay.ui.screens.NowPlayingScreen
import com.optisuite.optiplay.ui.screens.PlaylistDetailScreen
import com.optisuite.optiplay.ui.screens.QueueScreen
import com.optisuite.optiplay.ui.screens.RecentsScreen
import com.optisuite.optiplay.ui.screens.SettingsScreen
import com.optisuite.optiplay.ui.screens.SongsScreen
import com.optisuite.optiplay.ui.screens.VideosScreen
import com.optisuite.optiplay.ui.theme.OptiPlayTheme
import org.koin.androidx.compose.koinViewModel

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Inicio", Icons.Filled.Home),
    SONGS("Canciones", Icons.Filled.MusicNote),
    VIDEOS("Vídeos", Icons.Filled.VideoLibrary),
    EQ("EQ", Icons.Filled.GraphicEq),
    SETTINGS("Ajustes", Icons.Filled.Settings)
}

private sealed interface Detail {
    data object Favorites : Detail
    data object Recents : Detail
    data object MostPlayed : Detail
    data object Albums : Detail
    data object Artists : Detail
    data object Folders : Detail
    data object Queue : Detail
    data class Album(val id: Long, val name: String) : Detail
    data class Artist(val name: String) : Detail
    data class Playlist(val id: Long, val name: String) : Detail
}

private fun requiredPermissions(): Array<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.READ_MEDIA_AUDIO)
        add(Manifest.permission.READ_MEDIA_VIDEO)
        add(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        add(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}.toTypedArray()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: PlayerViewModel = koinViewModel()
            val theme by vm.themeMode.collectAsStateWithLifecycle()
            val dynamic by vm.dynamicColor.collectAsStateWithLifecycle()
            OptiPlayTheme(themeMode = theme, dynamicColor = dynamic) {
                AppRoot(vm)
            }
        }
    }
}

@Composable
private fun AppRoot(vm: PlayerViewModel) {
    val ctx = LocalContext.current

    fun hasAudioPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
    }

    var granted by remember { mutableStateOf(hasAudioPermission()) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val audioPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (result[audioPerm] == true) { granted = true; vm.load() }
    }

    LaunchedEffect(Unit) {
        if (granted) vm.load() else launcher.launch(requiredPermissions())
    }

    var tab by remember { mutableStateOf(Tab.HOME) }
    var showNowPlaying by remember { mutableStateOf(false) }
    val stack = remember { mutableStateListOf<Detail>() }
    val current by vm.currentSong.collectAsStateWithLifecycle()
    val isPlaying by vm.player.isPlaying.collectAsStateWithLifecycle()
    val positionMs by vm.positionMs.collectAsStateWithLifecycle()
    val durationMs by vm.player.durationMs.collectAsStateWithLifecycle()
    val pendingAdd by vm.pendingAddSong.collectAsStateWithLifecycle()
    val sleepLeft by vm.sleepMinutesLeft.collectAsStateWithLifecycle()

    fun push(d: Detail) { stack.add(d) }
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                Column {
                    if (sleepLeft > 0) {
                        Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth().clickable { vm.cancelSleepTimer() }) {
                            Text(
                                "⏱ Apagado en $sleepLeft min — toca para cancelar",
                                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                    val s = current
                    if (s != null) {
                        val dur = durationMs.coerceAtLeast(s.durationMs).coerceAtLeast(1L)
                        MiniPlayer(
                            song = s,
                            isPlaying = isPlaying,
                            progress = positionMs.toFloat() / dur,
                            onTogglePlay = { vm.player.togglePlayPause() },
                            onNext = { vm.player.next() },
                            onExpand = { showNowPlaying = true }
                        )
                    }
                    NavigationBar {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = tab == t,
                                onClick = { tab = t },
                                icon = { Icon(t.icon, t.label) },
                                label = { Text(t.label, maxLines = 1) }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            if (!granted) {
                Box(Modifier.fillMaxSize().padding(padding)) {
                    Text("Concede acceso a tu música para empezar.", Modifier.padding(24.dp).align(Alignment.Center))
                }
            } else when (tab) {
                Tab.HOME -> HomeScreen(
                    vm, padding,
                    onOpenFavorites = { push(Detail.Favorites) },
                    onOpenRecents = { push(Detail.Recents) },
                    onOpenMostPlayed = { push(Detail.MostPlayed) },
                    onOpenAlbums = { push(Detail.Albums) },
                    onOpenArtists = { push(Detail.Artists) },
                    onOpenFolders = { push(Detail.Folders) },
                    onOpenPlaylist = { id, name -> push(Detail.Playlist(id, name)) }
                )
                Tab.SONGS -> SongsScreen(vm, padding)
                Tab.VIDEOS -> VideosScreen(vm, padding)
                Tab.EQ -> EqualizerScreen(vm, padding)
                Tab.SETTINGS -> SettingsScreen(vm, padding)
            }
        }

        val top = stack.lastOrNull()
        AnimatedVisibility(
            visible = top != null,
            enter = slideInVertically(initialOffsetY = { it / 4 }),
            exit = slideOutVertically(targetOffsetY = { it / 4 })
        ) {
            when (val d = top) {
                Detail.Favorites -> FavoritesScreen(vm, ::pop)
                Detail.Recents -> RecentsScreen(vm, ::pop)
                Detail.MostPlayed -> MostPlayedScreen(vm, ::pop)
                Detail.Albums -> DetailScaffold("Álbumes", ::pop) { p -> AlbumsScreen(vm, p) { id, name -> push(Detail.Album(id, name)) } }
                Detail.Artists -> ArtistsScreen(vm, ::pop) { name -> push(Detail.Artist(name)) }
                Detail.Folders -> DetailScaffold("Carpetas", ::pop) { p -> FoldersScreen(vm, p) }
                Detail.Queue -> QueueScreen(vm, ::pop)
                is Detail.Album -> AlbumDetailScreen(vm, d.id, d.name, ::pop)
                is Detail.Artist -> ArtistDetailScreen(vm, d.name, ::pop)
                is Detail.Playlist -> PlaylistDetailScreen(vm, d.id, d.name, ::pop)
                null -> {}
            }
        }

        AnimatedVisibility(
            visible = showNowPlaying && current != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            NowPlayingScreen(vm, onCollapse = { showNowPlaying = false }, onOpenQueue = {
                showNowPlaying = false; push(Detail.Queue)
            })
        }

        pendingAdd?.let { songId ->
            AddToPlaylistDialog(vm, songId, onDismiss = { vm.clearAddRequest() })
        }
    }

    BackHandler(enabled = showNowPlaying || stack.isNotEmpty()) {
        when {
            showNowPlaying -> showNowPlaying = false
            stack.isNotEmpty() -> pop()
        }
    }
}
