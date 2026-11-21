package org.simpmusic.radio.data

import android.content.Context

/**
 * The bundled artwork used when a station has no logo of its own: res/drawable-nodpi/genre_<name>.jpg,
 * a person with glowing headphones in front of a scene for that genre. The picture is chosen from the
 * station's tags and language; with no recognisable genre one is picked from the station id, so a
 * station always gets the same picture.
 */
object FallbackArt {
    private val genres: List<Pair<String, List<String>>> =
        listOf(
            "devotional" to listOf("devotional", "religio", "spiritual", "gospel", "christian", "islam", "quran", "bhajan", "kirtan", "hindu", "gurbani", "sufi", "worship", "prayer", "catholic", "bible"),
            "bollywood" to listOf("bollywood", "hindi", "indian", "bhangra", "punjabi", "tamil", "telugu", "filmi", "desi", "bengali", "marathi", "malayalam", "kannada"),
            "electronic" to listOf("electro", "edm", "dance", "house", "techno", "trance", "club", "dj", "dubstep", "drum", "bass", "trap", "synth"),
            "rock" to listOf("rock", "metal", "punk", "grunge", "alternative", "indie", "hard"),
            "jazz" to listOf("jazz", "blues", "soul", "swing", "funk", "bossa"),
            "hiphop" to listOf("hip hop", "hip-hop", "hiphop", "rap", "r&b", "rnb", "urban"),
            "classical" to listOf("classical", "orchestra", "piano", "opera", "symphon", "baroque", "instrumental"),
            "talk" to listOf("news", "talk", "sport", "politic", "information", "speech", "comedy", "business", "podcast", "public radio", "weather"),
            "country" to listOf("country", "folk", "bluegrass", "acoustic", "americana", "western"),
            "reggae" to listOf("reggae", "ska", "dancehall", "caribbean", "reggaeton", "dub"),
            "chill" to listOf("chill", "ambient", "lofi", "lo-fi", "relax", "easy", "new age", "meditation", "sleep", "lounge", "smooth"),
            "pop" to listOf("pop", "top 40", "top40", "hits", "chart", "80s", "90s", "oldies", "adult contemporary", "love"),
            "world" to listOf("world", "international", "ethnic", "latin", "african", "arab", "salsa", "tropical", "reggaet"),
        )

    private val all = genres.map { it.first }

    /** The genre key for these tags/language, or null when nothing matches. */
    fun genreOf(
        tags: String,
        language: String = "",
    ): String? {
        val haystack = ("$tags,$language").lowercase()
        return genres.firstOrNull { (_, words) -> words.any { haystack.contains(it) } }?.first
    }

    /** The drawable resource for this station, or 0 if the app ships no pictures. */
    fun resFor(
        context: Context,
        uuid: String,
        tags: String = "",
        language: String = "",
    ): Int {
        val key = genreOf(tags, language) ?: all[(uuid.hashCode() and 0x7fffffff) % all.size]
        return context.resources.getIdentifier("genre_$key", "drawable", context.packageName)
    }

    /** Same picture as a URI the notification, lock screen and Android Auto can load. */
    fun uriFor(
        context: Context,
        uuid: String,
        tags: String = "",
        language: String = "",
    ): String? =
        resFor(context, uuid, tags, language).takeIf { it != 0 }?.let { "android.resource://${context.packageName}/$it" }
}
