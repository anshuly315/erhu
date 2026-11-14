package com.erhu.erhu.core.model

/** A library browsing projection; playback still receives ordinary Songs. */
data class SongBrowserEntry(
    val song: Song,
    val addedAtMs: Long,
    val year: Int,
    val plays: Long,
    val lastPlayedAtMs: Long,
)

enum class SongsSort { TITLE_ASC, TITLE_DESC, RECENTLY_ADDED, RECENTLY_PLAYED, ARTIST, ALBUM, MOST_PLAYED }
enum class SongsMood { ALL, CHILL, HIGH_ENERGY, LATE_NIGHT, FEEL_GOOD, PARTY, LOVE, SAD, DARK, FOCUS, THROWBACK }

data class SongsBrowserPreferences(
    val sort: SongsSort = SongsSort.MOST_PLAYED,
    /** Empty = all, "untagged" = no genre, "genre:<name>" = a particular tag. */
    val genre: String = "",
    val mood: SongsMood = SongsMood.ALL,
)
