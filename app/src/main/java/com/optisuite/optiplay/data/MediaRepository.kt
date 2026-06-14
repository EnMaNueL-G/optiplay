package com.optisuite.optiplay.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Lee la biblioteca de audio del dispositivo vía MediaStore.
 * Sin red, sin telemetría: todo es local.
 */
class MediaRepository(private val context: Context) {

    private val albumArtBase: Uri = Uri.parse("content://media/external/audio/albumart")

    suspend fun querySongs(): List<Song> = withContext(Dispatchers.IO) {
        val songs = ArrayList<Song>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.MIME_TYPE
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND " +
            "${MediaStore.Audio.Media.DURATION} > 5000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val trackCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val albumId = c.getLong(albumIdCol)
                val data = c.getString(dataCol) ?: ""
                val folder = File(data).parentFile?.absolutePath ?: ""
                songs.add(
                    Song(
                        id = id,
                        title = c.getString(titleCol) ?: "Desconocido",
                        artist = c.getString(artistCol) ?: "Artista desconocido",
                        album = c.getString(albumCol) ?: "Álbum desconocido",
                        albumId = albumId,
                        durationMs = c.getLong(durCol),
                        data = data,
                        uri = ContentUris.withAppendedId(collection, id),
                        albumArtUri = ContentUris.withAppendedId(albumArtBase, albumId),
                        folder = folder,
                        track = c.getInt(trackCol),
                        year = c.getInt(yearCol),
                        mimeType = c.getString(mimeCol) ?: ""
                    )
                )
            }
        }
        songs
    }

    fun groupByAlbum(songs: List<Song>): List<AlbumGroup> =
        songs.groupBy { it.albumId }
            .map { (albumId, list) ->
                val first = list.first()
                AlbumGroup(
                    albumId = albumId,
                    album = first.album,
                    artist = first.artist,
                    songCount = list.size,
                    albumArtUri = first.albumArtUri,
                    songs = list.sortedBy { it.track }
                )
            }
            .sortedBy { it.album.lowercase() }

    fun groupByFolder(songs: List<Song>): List<FolderGroup> =
        songs.groupBy { it.folder }
            .map { (path, list) ->
                FolderGroup(
                    path = path,
                    name = path.substringAfterLast('/').ifEmpty { path },
                    songCount = list.size,
                    songs = list
                )
            }
            .sortedBy { it.name.lowercase() }

    fun groupByArtist(songs: List<Song>): List<ArtistGroup> =
        songs.groupBy { it.artist }
            .map { (artist, list) ->
                ArtistGroup(
                    artist = artist,
                    songCount = list.size,
                    albumCount = list.map { it.albumId }.distinct().size,
                    albumArtUri = list.first().albumArtUri,
                    songs = list.sortedBy { it.title.lowercase() }
                )
            }
            .sortedBy { it.artist.lowercase() }

    suspend fun queryVideos(): List<Video> = withContext(Dispatchers.IO) {
        val videos = ArrayList<Video>()
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.MIME_TYPE
        )
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"
        context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val wCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val hCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val data = c.getString(dataCol) ?: ""
                videos.add(
                    Video(
                        id = id,
                        title = c.getString(titleCol) ?: "Vídeo",
                        durationMs = c.getLong(durCol),
                        sizeBytes = c.getLong(sizeCol),
                        uri = ContentUris.withAppendedId(collection, id),
                        data = data,
                        folder = File(data).parentFile?.name ?: "",
                        width = c.getInt(wCol),
                        height = c.getInt(hCol),
                        mimeType = c.getString(mimeCol) ?: ""
                    )
                )
            }
        }
        videos
    }
}
