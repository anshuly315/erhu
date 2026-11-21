package org.simpmusic.radio.local

import android.content.Context

/** Local play counts and last-played times, kept on the phone to drive the smart mixes. */
class PlayStats(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("local_play_stats", Context.MODE_PRIVATE)

    fun count(id: Long) = prefs.getInt("c$id", 0)

    fun lastPlayed(id: Long) = prefs.getLong("t$id", 0L)

    fun clear() = prefs.edit().clear().apply()

    fun record(id: Long) {
        prefs.edit().putInt("c$id", count(id) + 1).putLong("t$id", System.currentTimeMillis()).apply()
    }
}

/** Mixes built from the library and its play stats. */
data class SmartMix(val key: String, val title: String, val songs: List<LocalSong>)

object SmartMixes {
    private const val DAY = 86_400_000L

    fun build(
        all: List<LocalSong>,
        stats: PlayStats,
        seed: Long,
    ): List<SmartMix> {
        if (all.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val out = ArrayList<SmartMix>()
        out += SmartMix("recent", "Recently added", all.sortedByDescending { it.addedAt }.take(60))
        val played = all.filter { stats.count(it.id) > 0 }
        if (played.isNotEmpty()) {
            out += SmartMix("most", "Most played", played.sortedByDescending { stats.count(it.id) }.take(60))
            val old = played.filter { now - stats.lastPlayed(it.id) > 30 * DAY }.sortedByDescending { stats.count(it.id) }
            if (old.isNotEmpty()) out += SmartMix("forgotten", "Forgotten favourites", old.take(60))
        }
        val unplayed = all.filter { stats.count(it.id) == 0 }
        if (unplayed.size >= 5) out += SmartMix("discover", "Discover", unplayed.shuffled(java.util.Random(seed)).take(50))
        all.filter { it.durationMs >= 6 * 60_000 }.takeIf { it.size >= 3 }?.let { out += SmartMix("long", "Long listens", it.sortedByDescending { s -> s.durationMs }.take(60)) }
        all.filter { it.durationMs in 1..150_000 }.takeIf { it.size >= 3 }?.let { out += SmartMix("short", "Quick hits", it.shuffled(java.util.Random(seed)).take(60)) }
        all.filter { it.artist.isNotBlank() && !it.artist.equals("<unknown>", true) }
            .groupBy { it.artist }.entries.sortedByDescending { it.value.size }.take(4).filter { it.value.size >= 4 }
            .forEach { (artist, songs) -> out += SmartMix("artist:$artist", "More $artist", songs.shuffled(java.util.Random(seed))) }
        return out
    }
}
