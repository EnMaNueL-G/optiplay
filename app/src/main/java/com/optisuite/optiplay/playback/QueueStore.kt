package com.optisuite.optiplay.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.json.JSONArray
import org.json.JSONObject

/** Guarda la cola, la pista y la posición para reanudar tras cerrar la app o reiniciar el móvil. */
class QueueStore(context: Context) {

    data class Saved(val items: List<MediaItem>, val index: Int, val positionMs: Long)

    private val prefs = context.getSharedPreferences("optiplay_queue", Context.MODE_PRIVATE)
    private var lastIds: List<String>? = null

    /**
     * Solo se guardan pistas de la biblioteca: el permiso de un archivo abierto desde otra app
     * ("Abrir con") es temporal y dejaría de poder leerse al reiniciar.
     * La pista actual se guarda por id (no por índice) para que no se desalinee.
     */
    fun saveQueue(items: List<MediaItem>, currentId: String?, positionMs: Long) {
        val keep = items.filter { !it.mediaId.startsWith("ext:") && it.localConfiguration != null }
        val ids = keep.map { it.mediaId }
        if (ids == lastIds) { savePosition(currentId, positionMs); return } // la cola no cambió
        lastIds = ids
        val arr = JSONArray()
        keep.forEach { item ->
            val md = item.mediaMetadata
            arr.put(JSONObject().apply {
                put("id", item.mediaId)
                put("uri", item.localConfiguration!!.uri.toString())
                put("title", md.title?.toString() ?: "")
                put("artist", md.artist?.toString() ?: "")
                put("album", md.albumTitle?.toString() ?: "")
                md.artworkUri?.let { put("art", it.toString()) }
            })
        }
        prefs.edit().putString("queue", arr.toString()).apply()
        savePosition(currentId, positionMs)
    }

    fun savePosition(currentId: String?, positionMs: Long) {
        prefs.edit().putString("current", currentId ?: "").putLong("pos", positionMs).apply()
    }

    fun load(): Saved? = runCatching {
        val arr = JSONArray(prefs.getString("queue", null) ?: return null)
        if (arr.length() == 0) return null
        val items = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            MediaItem.Builder()
                .setMediaId(o.getString("id"))
                .setUri(Uri.parse(o.getString("uri")))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(o.optString("title"))
                        .setArtist(o.optString("artist"))
                        .setAlbumTitle(o.optString("album"))
                        .setArtworkUri(o.optString("art").takeIf { it.isNotEmpty() }?.let(Uri::parse))
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .build()
                )
                .build()
        }
        val current = prefs.getString("current", "") ?: ""
        val index = items.indexOfFirst { it.mediaId == current }
        // Si la pista actual era externa (no guardada), empezar la cola desde el principio.
        if (index < 0) Saved(items, 0, 0L)
        else Saved(items, index, prefs.getLong("pos", 0L).coerceAtLeast(0L))
    }.getOrNull()
}
