package com.optisuite.optiplay.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.optisuite.optiplay.data.AlbumGroup
import com.optisuite.optiplay.data.ArtistGroup
import com.optisuite.optiplay.data.FolderGroup
import com.optisuite.optiplay.data.MediaRepository
import com.optisuite.optiplay.data.SettingsStore
import com.optisuite.optiplay.data.Song
import com.optisuite.optiplay.data.ThemeMode
import com.optisuite.optiplay.audio.AudioEffects
import com.optisuite.optiplay.data.Video
import com.optisuite.optiplay.data.db.MusicDao
import com.optisuite.optiplay.data.db.PlaylistEntity
import com.optisuite.optiplay.playback.PlayerConnection
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class SongSort { TITLE, ARTIST, ALBUM, DURATION, RECENT }

class PlayerViewModel(
    private val appContext: Context,
    private val repo: MediaRepository,
    val settings: SettingsStore,
    private val dao: MusicDao,
    val effects: AudioEffects,
    val player: PlayerConnection
) : ViewModel() {

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs

    /** Índice id -> Song para resolver favoritos/playlists/historial. */
    private val byId = MutableStateFlow<Map<Long, Song>>(emptyMap())

    private val _videos = MutableStateFlow<List<Video>>(emptyList())
    val videos: StateFlow<List<Video>> = _videos

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _sort = MutableStateFlow(SongSort.TITLE)
    val sort: StateFlow<SongSort> = _sort

    val albums: StateFlow<List<AlbumGroup>> = _songs
        .map { repo.groupByAlbum(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val artists: StateFlow<List<ArtistGroup>> = _songs
        .map { repo.groupByArtist(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val folders: StateFlow<List<FolderGroup>> = _songs
        .map { repo.groupByFolder(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Lista filtrada por búsqueda + ordenada. */
    val filteredSongs: StateFlow<List<Song>> = combine(_songs, _query, _sort) { list, q, sort ->
        val filtered = if (q.isBlank()) list else list.filter {
            it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true)
        }
        when (sort) {
            SongSort.TITLE -> filtered.sortedBy { it.title.lowercase() }
            SongSort.ARTIST -> filtered.sortedBy { it.artist.lowercase() }
            SongSort.ALBUM -> filtered.sortedBy { it.album.lowercase() }
            SongSort.DURATION -> filtered.sortedBy { it.durationMs }
            SongSort.RECENT -> filtered // ya viene en orden de inserción
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val themeMode: StateFlow<ThemeMode> =
        settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)
    val dynamicColor: StateFlow<Boolean> =
        settings.dynamicColor.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    // ---- Persistencia (Room) ----
    val favorites: StateFlow<Set<Long>> = dao.favoriteIds()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val favoriteSongs: StateFlow<List<Song>> = combine(dao.favoriteIds(), byId) { ids, idx ->
        ids.mapNotNull { idx[it] }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val recentSongs: StateFlow<List<Song>> = combine(dao.recentIds(50), byId) { ids, idx ->
        ids.mapNotNull { idx[it] }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val mostPlayed: StateFlow<List<Song>> = combine(dao.mostPlayedIds(50), byId) { ids, idx ->
        ids.mapNotNull { idx[it] }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val playlists: StateFlow<List<PlaylistEntity>> =
        dao.playlists().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun playlistSongs(playlistId: Long): StateFlow<List<Song>> =
        combine(dao.playlistSongIds(playlistId), byId) { ids, idx -> ids.mapNotNull { idx[it] } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs

    private val _sleepMinutesLeft = MutableStateFlow(0)
    val sleepMinutesLeft: StateFlow<Int> = _sleepMinutesLeft

    val currentSong: StateFlow<Song?> = combine(player.currentMediaId, byId) { id, idx ->
        id?.toLongOrNull()?.let { idx[it] }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        player.connect(appContext)
        viewModelScope.launch {
            while (isActive) {
                if (player.isPlaying.value) _positionMs.value = player.currentPositionMs()
                delay(500)
            }
        }
        // Registrar reproducciones para "recientes" y "más escuchadas".
        viewModelScope.launch {
            player.currentMediaId.filterNotNull().distinctUntilChanged().collect { id ->
                id.toLongOrNull()?.let { dao.recordPlay(it) }
            }
        }
    }

    fun load() {
        if (_loading.value) return
        viewModelScope.launch {
            _loading.value = true
            val list = repo.querySongs()
            _songs.value = list
            byId.value = list.associateBy { it.id }
            _videos.value = repo.queryVideos()
            _loading.value = false
        }
    }

    fun onQueryChange(q: String) { _query.value = q }
    fun setSort(s: SongSort) { _sort.value = s }

    fun playFrom(queue: List<Song>, song: Song) {
        val idx = queue.indexOf(song).coerceAtLeast(0)
        player.playQueue(queue, idx)
    }

    fun playAll(shuffle: Boolean = false) {
        val list = filteredSongs.value.ifEmpty { _songs.value }
        if (list.isEmpty()) return
        if (shuffle && !player.shuffle.value) player.toggleShuffle()
        player.playQueue(list, 0)
    }

    fun toggleFavorite(songId: Long) = viewModelScope.launch { dao.toggleFavorite(songId) }
    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }
    fun setDynamic(enabled: Boolean) = viewModelScope.launch { settings.setDynamicColor(enabled) }

    // ---- Playlists ----
    fun createPlaylist(name: String, onCreated: (Long) -> Unit = {}) = viewModelScope.launch {
        val id = dao.createPlaylist(PlaylistEntity(name = name.trim().ifBlank { "Lista" }))
        onCreated(id)
    }
    fun addToPlaylist(playlistId: Long, songId: Long) = viewModelScope.launch { dao.addSongToPlaylist(playlistId, songId) }

    /** Canción pendiente de "Añadir a lista" (la UI muestra el diálogo cuando != null). */
    private val _pendingAddSong = MutableStateFlow<Long?>(null)
    val pendingAddSong: StateFlow<Long?> = _pendingAddSong
    fun requestAddToPlaylist(songId: Long) { _pendingAddSong.value = songId }
    fun clearAddRequest() { _pendingAddSong.value = null }
    fun removeFromPlaylist(playlistId: Long, songId: Long) = viewModelScope.launch { dao.removeFromPlaylist(playlistId, songId) }
    fun deletePlaylist(playlistId: Long) = viewModelScope.launch { dao.deletePlaylistFully(playlistId) }

    // ---- Sleep timer ----
    private var sleepJob: kotlinx.coroutines.Job? = null
    fun startSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) { _sleepMinutesLeft.value = 0; return }
        sleepJob = viewModelScope.launch {
            var left = minutes
            _sleepMinutesLeft.value = left
            while (left > 0 && isActive) {
                delay(60_000)
                left--
                _sleepMinutesLeft.value = left
            }
            if (isActive) {
                player.pause()
                _sleepMinutesLeft.value = 0
            }
        }
    }
    fun cancelSleepTimer() { sleepJob?.cancel(); _sleepMinutesLeft.value = 0 }

    override fun onCleared() {
        player.release()
        super.onCleared()
    }
}
