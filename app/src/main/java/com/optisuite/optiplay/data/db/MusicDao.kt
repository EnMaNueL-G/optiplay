package com.optisuite.optiplay.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {

    // ---- Favoritos ----
    @Query("SELECT songId FROM favorites ORDER BY addedAt DESC")
    fun favoriteIds(): Flow<List<Long>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE songId = :id)")
    suspend fun isFavorite(id: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addFavorite(fav: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE songId = :id")
    suspend fun removeFavorite(id: Long)

    @Transaction
    suspend fun toggleFavorite(id: Long) {
        if (isFavorite(id)) removeFavorite(id) else addFavorite(FavoriteEntity(id))
    }

    // ---- Playlists ----
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun playlists(): Flow<List<PlaylistEntity>>

    @Insert
    suspend fun createPlaylist(p: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun clearPlaylistSongs(playlistId: Long)

    @Query("SELECT songId FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position ASC")
    fun playlistSongIds(playlistId: Long): Flow<List<Long>>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun nextPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistSong(item: PlaylistSongEntity)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeFromPlaylist(playlistId: Long, songId: Long)

    @Query("SELECT COUNT(*) FROM playlist_songs WHERE playlistId = :playlistId")
    fun playlistCount(playlistId: Long): Flow<Int>

    @Transaction
    suspend fun addSongToPlaylist(playlistId: Long, songId: Long) {
        insertPlaylistSong(PlaylistSongEntity(playlistId = playlistId, songId = songId, position = nextPosition(playlistId)))
    }

    @Transaction
    suspend fun deletePlaylistFully(playlistId: Long) {
        clearPlaylistSongs(playlistId)
        deletePlaylist(playlistId)
    }

    // ---- Historial / reproducciones ----
    @Query("SELECT * FROM history WHERE songId = :id")
    suspend fun historyOf(id: Long): HistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(h: HistoryEntity)

    @Query("SELECT songId FROM history ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun recentIds(limit: Int): Flow<List<Long>>

    @Query("SELECT songId FROM history ORDER BY playCount DESC, lastPlayedAt DESC LIMIT :limit")
    fun mostPlayedIds(limit: Int): Flow<List<Long>>

    @Transaction
    suspend fun recordPlay(id: Long) {
        val existing = historyOf(id)
        upsertHistory(
            HistoryEntity(
                songId = id,
                lastPlayedAt = System.currentTimeMillis(),
                playCount = (existing?.playCount ?: 0) + 1
            )
        )
    }
}
