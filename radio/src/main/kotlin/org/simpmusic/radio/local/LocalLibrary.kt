package org.simpmusic.radio.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** A playlist of phone files, kept on the phone. [songIds] are MediaStore ids in play order. */
data class LocalPlaylist(
    val id: String,
    val name: String,
    val songIds: List<Long>,
    val createdAt: Long,
)

/** Favourites and playlists for the Local tab. Small, so one JSON blob in preferences is enough. */
class LocalLibrary private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("local_library", Context.MODE_PRIVATE)
    private val _favourites = MutableStateFlow(readFavourites())
    private val _playlists = MutableStateFlow(readPlaylists())

    val favourites: StateFlow<Set<Long>> = _favourites.asStateFlow()
    val playlists: StateFlow<List<LocalPlaylist>> = _playlists.asStateFlow()

    fun toggleFavourite(songId: Long) {
        _favourites.value = _favourites.value.let { if (songId in it) it - songId else it + songId }
        prefs.edit().putString("favs", JSONArray(_favourites.value.toList()).toString()).apply()
    }

    /** Drops songs whose files are gone from favourites and every playlist. */
    fun forget(ids: Set<Long>) {
        if (ids.isEmpty()) return
        _favourites.value = _favourites.value - ids
        prefs.edit().putString("favs", JSONArray(_favourites.value.toList()).toString()).apply()
        saveAll(_playlists.value.map { it.copy(songIds = it.songIds.filterNot { id -> id in ids }) })
    }

    fun createPlaylist(name: String, songIds: List<Long> = emptyList()): LocalPlaylist {
        val p = LocalPlaylist(java.util.UUID.randomUUID().toString(), name.trim().ifBlank { "New playlist" }, songIds.distinct(), System.currentTimeMillis())
        saveAll(_playlists.value + p)
        return p
    }

    fun rename(id: String, name: String) = update(id) { it.copy(name = name.trim().ifBlank { it.name }) }

    fun delete(id: String) = saveAll(_playlists.value.filterNot { it.id == id })

    fun addSongs(id: String, songIds: List<Long>) = update(id) { it.copy(songIds = (it.songIds + songIds).distinct()) }

    fun removeSong(id: String, songId: Long) = update(id) { it.copy(songIds = it.songIds - songId) }

    fun move(id: String, from: Int, to: Int) =
        update(id) { p ->
            if (from !in p.songIds.indices || to !in p.songIds.indices) p
            else p.copy(songIds = p.songIds.toMutableList().also { it.add(to, it.removeAt(from)) })
        }

    private fun update(id: String, change: (LocalPlaylist) -> LocalPlaylist) = saveAll(_playlists.value.map { if (it.id == id) change(it) else it })

    private fun saveAll(list: List<LocalPlaylist>) {
        _playlists.value = list
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(JSONObject().put("id", p.id).put("name", p.name).put("at", p.createdAt).put("ids", JSONArray(p.songIds)))
        }
        prefs.edit().putString("playlists", arr.toString()).apply()
    }

    private fun readFavourites(): Set<Long> =
        runCatching {
            val a = JSONArray(prefs.getString("favs", "[]"))
            (0 until a.length()).map { a.getLong(it) }.toSet()
        }.getOrDefault(emptySet())

    private fun readPlaylists(): List<LocalPlaylist> =
        runCatching {
            val a = JSONArray(prefs.getString("playlists", "[]"))
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                val ids = o.getJSONArray("ids")
                LocalPlaylist(o.getString("id"), o.getString("name"), (0 until ids.length()).map { ids.getLong(it) }, o.optLong("at"))
            }
        }.getOrDefault(emptyList())

    companion object {
        @Volatile private var instance: LocalLibrary? = null

        fun get(context: Context): LocalLibrary =
            instance ?: synchronized(this) { instance ?: LocalLibrary(context).also { instance = it } }
    }
}

/** Songs grouped as an album. */
data class LocalAlbum(val albumId: Long, val title: String, val artist: String, val songs: List<LocalSong>) {
    val art: String get() = "content://media/external/audio/albumart/$albumId"
}

data class LocalArtist(val name: String, val songs: List<LocalSong>, val albums: Int)

fun List<LocalSong>.toAlbums(): List<LocalAlbum> =
    groupBy { it.albumId }.map { (id, s) ->
        val sorted = s.sortedWith(compareBy({ it.title.lowercase() }))
        val artists = s.map { it.artist }.distinct()
        LocalAlbum(id, s.first().album.ifBlank { "Unknown album" }, if (artists.size == 1) artists[0] else "Various artists", sorted)
    }.sortedBy { it.title.lowercase() }

fun List<LocalSong>.toArtists(): List<LocalArtist> =
    groupBy { it.artist }.map { (n, s) -> LocalArtist(n, s, s.map { it.albumId }.distinct().size) }.sortedBy { it.name.lowercase() }
