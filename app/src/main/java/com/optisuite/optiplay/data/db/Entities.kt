package com.optisuite.optiplay.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val songId: Long,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_songs",
    indices = [Index("playlistId"), Index(value = ["playlistId", "songId"], unique = true)]
)
data class PlaylistSongEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val songId: Long,
    val position: Int,
    val addedAt: Long = System.currentTimeMillis()
)

/** Historial / contador de reproducciones (para "recientes" y "más escuchadas"). */
@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val songId: Long,
    val lastPlayedAt: Long,
    val playCount: Int
)
