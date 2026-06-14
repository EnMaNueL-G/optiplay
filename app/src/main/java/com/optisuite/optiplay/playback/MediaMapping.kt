package com.optisuite.optiplay.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.optisuite.optiplay.data.Song

/** Convierte una Song de la biblioteca en un MediaItem reproducible por Media3. */
fun Song.toMediaItem(): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setArtworkUri(albumArtUri)
        .setIsBrowsable(false)
        .setIsPlayable(true)
        .build()
    return MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(metadata)
        .build()
}
