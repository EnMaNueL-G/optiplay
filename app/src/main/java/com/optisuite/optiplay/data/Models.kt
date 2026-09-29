package com.optisuite.optiplay.data

import android.net.Uri

/** Una pista de audio leída de MediaStore. */
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val data: String,          // ruta del archivo
    val uri: Uri,              // content:// reproducible
    val albumArtUri: Uri,      // content://media/external/audio/albumart/<albumId>
    val folder: String,        // carpeta contenedora (para vista Carpetas)
    val track: Int,
    val year: Int,
    val mimeType: String,
    val dateAdded: Long = 0L   // segundos (MediaStore.DATE_ADDED)
)

/** Agrupación por álbum para la vista Álbumes. */
data class AlbumGroup(
    val albumId: Long,
    val album: String,
    val artist: String,
    val songCount: Int,
    val albumArtUri: Uri,
    val songs: List<Song>
)

/** Agrupación por carpeta para la vista Carpetas. */
data class FolderGroup(
    val path: String,
    val name: String,
    val songCount: Int,
    val songs: List<Song>
)

/** Agrupación por artista. */
data class ArtistGroup(
    val artist: String,
    val songCount: Int,
    val albumCount: Int,
    val albumArtUri: Uri,
    val songs: List<Song>
)

/** Un vídeo de la biblioteca (MediaStore.Video). */
data class Video(
    val id: Long,
    val title: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val uri: Uri,
    val data: String,
    val folder: String,
    val width: Int,
    val height: Int,
    val mimeType: String
)
