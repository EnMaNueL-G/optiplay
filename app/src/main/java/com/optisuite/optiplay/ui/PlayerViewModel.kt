package com.optisuite.optiplay.ui

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.optisuite.optiplay.audio.AudioEffects
import com.optisuite.optiplay.data.AlbumGroup
import com.optisuite.optiplay.data.ArtistGroup
import com.optisuite.optiplay.data.FolderGroup
import com.optisuite.optiplay.data.MediaRepository
import com.optisuite.optiplay.data.SettingsStore
import com.optisuite.optiplay.data.Song
import com.optisuite.optiplay.data.ThemeMode
import com.optisuite.optiplay.data.Video
import com.optisuite.optiplay.data.db.MusicDao
import com.optisuite.optiplay.data.db.PlaylistEntity
import com.optisuite.optiplay.playback.PlayerConnection
import com.optisuite.optiplay.playback.SleepTimer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
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
            SongSort.RECENT -> filtered.sortedByDescending { it.dateAdded }
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

    private val playlistFlows = HashMap<Long, StateFlow<List<Song>>>()
    fun playlistSongs(playlistId: Long): StateFlow<List<Song>> = playlistFlows.getOrPut(playlistId) {
        combine(dao.playlistSongIds(playlistId), byId) { ids, idx -> ids.mapNotNull { idx[it] } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    }

    /** uri del vídeo -> fracción vista (0..1), para la barra de "continuar viendo". */
    val videoProgress: StateFlow<Map<String, Float>> = dao.allVideoProgress()
        .map { list -> list.associate { it.uri to (if (it.durationMs > 0) it.positionMs.toFloat() / it.durationMs else 0f) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs

    // ---- Temporizador (lo ejecuta el servicio; aquí solo se muestra) ----
    val sleepState: StateFlow<SleepTimer.State> = SleepTimer.state
    private val ticker = flow { while (true) { emit(System.currentTimeMillis()); delay(1_000) } }
    val sleepMinutesLeft: StateFlow<Int> = combine(SleepTimer.state, ticker) { st, now ->
        if (st.endAtMs <= 0L) 0 else (((st.endAtMs - now).coerceAtLeast(0L) + 59_999L) / 60_000L).toInt()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /**
     * Pista actual. Si no está en la biblioteca (archivo abierto desde otra app o cola antigua),
     * se construye con los metadatos del reproductor (id = -1: sin favoritos/listas).
     */
    val currentSong: StateFlow<Song?> = combine(player.currentMeta, byId) { meta, idx ->
        if (meta == null) null
        else meta.mediaId.toLongOrNull()?.let { idx[it] } ?: Song(
            id = -1L,
            title = meta.title.ifBlank { "Sin título" },
            artist = meta.artist.ifBlank { "Desconocido" },
            album = "",
            albumId = -1L,
            durationMs = 0L,
            data = "",
            uri = Uri.EMPTY,
            albumArtUri = meta.artworkUri ?: Uri.EMPTY,
            folder = "",
            track = 0,
            year = 0,
            mimeType = ""
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Refresca la biblioteca cuando se añaden o borran archivos (otra app, descarga, etc.).
    private val mediaObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { scheduleReload() }
    }
    private var reloadJob: Job? = null
    private var observing = false

    init {
        player.connect(appContext)
        viewModelScope.launch {
            while (isActive) {
                _positionMs.value = player.currentPositionMs()
                delay(500)
            }
        }
    }

    private fun scheduleReload() {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch { delay(1_500); load(force = true) }
    }

    fun load(force: Boolean = false) {
        if (_loading.value && !force) return
        if (!observing) {
            observing = true
            runCatching {
                appContext.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, mediaObserver)
                appContext.contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, mediaObserver)
            }
        }
        viewModelScope.launch {
            _loading.value = true
            val list = runCatching { repo.querySongs() }.getOrDefault(emptyList())
            _songs.value = list
            byId.value = list.associateBy { it.id }
            _videos.value = runCatching { repo.queryVideos() }.getOrDefault(emptyList())
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
        player.setShuffle(shuffle)
        player.playQueue(list, if (shuffle) list.indices.random() else 0)
    }

    /** Reproduce un archivo de audio abierto desde otra app (gestor de archivos, WhatsApp...). */
    fun playExternalAudio(uri: Uri) {
        val name = runCatching {
            appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "Audio"
        val item = MediaItem.Builder()
            .setMediaId("ext:$uri")
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name.substringBeforeLast('.'))
                    .setArtist("Archivo externo")
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build()
            )
            .build()
        player.playItems(listOf(item), 0)
    }

    fun toggleFavorite(songId: Long) = viewModelScope.launch { if (songId >= 0) dao.toggleFavorite(songId) }
    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }
    fun setDynamic(enabled: Boolean) = viewModelScope.launch { settings.setDynamicColor(enabled) }

    // ---- Playlists ----
    fun createPlaylist(name: String, onCreated: (Long) -> Unit = {}) = viewModelScope.launch {
        val id = dao.createPlaylist(PlaylistEntity(name = name.trim().ifBlank { "Lista" }))
        onCreated(id)
    }

    /** [onResult] recibe false si la canción ya estaba en la lista. */
    fun addToPlaylist(playlistId: Long, songId: Long, onResult: (Boolean) -> Unit = {}) = viewModelScope.launch {
        onResult(dao.addSongToPlaylist(playlistId, songId))
    }

    /** Canción pendiente de "Añadir a lista" (la UI muestra el diálogo cuando != null). */
    private val _pendingAddSong = MutableStateFlow<Long?>(null)
    val pendingAddSong: StateFlow<Long?> = _pendingAddSong
    fun requestAddToPlaylist(songId: Long) { if (songId >= 0) _pendingAddSong.value = songId }
    fun clearAddRequest() { _pendingAddSong.value = null }
    fun songById(id: Long): Song? = byId.value[id]
    fun removeFromPlaylist(playlistId: Long, songId: Long) = viewModelScope.launch { dao.removeFromPlaylist(playlistId, songId) }
    fun deletePlaylist(playlistId: Long) = viewModelScope.launch { dao.deletePlaylistFully(playlistId) }

    // ---- Sleep timer ----
    fun startSleepTimer(minutes: Int) = SleepTimer.start(minutes)
    fun sleepAtEndOfTrack() = SleepTimer.atEndOfTrack()
    fun cancelSleepTimer() = SleepTimer.cancel()

    override fun onCleared() {
        runCatching { appContext.contentResolver.unregisterContentObserver(mediaObserver) }
        player.release()
        super.onCleared()
    }
}
