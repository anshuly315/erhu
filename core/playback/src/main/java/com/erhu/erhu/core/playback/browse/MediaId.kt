package com.erhu.erhu.core.playback.browse

import android.net.Uri

/**
 * Media ID constants for the Android Auto / media browser tree.
 */
object MediaId {
    const val ROOT = "root"

    const val SONGS = "songs"
    const val ALBUMS = "albums"
    const val ARTISTS = "artists"
    const val PLAYLISTS = "playlists"

    const val ALBUM_PREFIX = "album_"
    const val ARTIST_PREFIX = "artist_"
    const val PLAYLIST_PREFIX = "playlist_"
    const val SEARCH_PREFIX = "search:"

    /** Browser-only identity. The actual player continues to receive numeric song IDs. */
    data class TrackTarget(
        val parentId: String,
        val songId: Long,
        val position: Int?,
    )

    fun track(
        parentId: String,
        songId: Long,
        position: Int? = null,
    ): String =
        Uri
            .Builder()
            .scheme("erhu")
            .authority("track")
            .appendPath(parentId)
            .appendPath(songId.toString())
            .apply { position?.let { appendPath(it.toString()) } }
            .build()
            .toString()

    @Suppress("ReturnCount", "MagicNumber")
    fun parseTrack(id: String): TrackTarget? {
        val uri = Uri.parse(id)
        if (uri.scheme != "erhu" || uri.authority != "track") return null
        val parts = uri.pathSegments
        if (parts.size !in 2..3) return null
        val songId = parts[1].toLongOrNull() ?: return null
        val position = if (parts.size == 3) parts[2].toIntOrNull() ?: return null else null
        return TrackTarget(parts[0], songId, position)
    }

    /** All top-level browsable category IDs, in the order they should be displayed. */
    val ROOT_CHILDREN = listOf(SONGS, ALBUMS, ARTISTS, PLAYLISTS)
}
