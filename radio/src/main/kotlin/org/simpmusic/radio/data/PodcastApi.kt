package org.simpmusic.radio.data

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

data class Podcast(
    val id: String,
    val name: String,
    val author: String,
    val artwork: String,
    val feedUrl: String,
)

data class PodcastEpisode(
    val show: String,
    val title: String,
    val url: String,
    val artwork: String,
    val durationSeconds: Int?,
    val published: String,
) {
    /** "local-" keeps the player from treating a recorded episode as a live stream. */
    val trackId: String get() = "radio:local-pod-" + (url.hashCode().toLong() and 0xffffffffL).toString(16)
}

/** Finds podcasts through Apple's free directory search and reads their RSS feeds for episodes. */
class PodcastApi {
    private suspend fun get(url: String): String =
        withContext(Dispatchers.IO) {
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 10000
                c.readTimeout = 15000
                c.instanceFollowRedirects = true
                c.setRequestProperty("User-Agent", "SimpMusic-Radio/1.0")
                if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
                c.inputStream.bufferedReader().use { it.readText() }
            } finally {
                c.disconnect()
            }
        }

    suspend fun search(
        term: String,
        limit: Int = 40,
    ): List<Podcast> {
        val json = JSONObject(get("https://itunes.apple.com/search?media=podcast&entity=podcast&limit=$limit&term=${URLEncoder.encode(term, "UTF-8")}"))
        val arr = json.optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val feed = o.optString("feedUrl")
            if (feed.isBlank()) null
            else Podcast(
                id = o.optString("collectionId"),
                name = o.optString("collectionName"),
                author = o.optString("artistName"),
                artwork = o.optString("artworkUrl600").ifBlank { o.optString("artworkUrl100") },
                feedUrl = feed.replaceFirst("http://", "https://"),
            )
        }
    }

    suspend fun episodes(
        podcast: Podcast,
        limit: Int = 100,
    ): List<PodcastEpisode> {
        val xml = get(podcast.feedUrl)
        return withContext(Dispatchers.Default) { parseFeed(xml, podcast, limit) }
    }

    private fun parseFeed(
        xml: String,
        podcast: Podcast,
        limit: Int,
    ): List<PodcastEpisode> {
        val p = XmlPullParserFactory.newInstance().newPullParser()
        p.setInput(xml.reader())
        val out = ArrayList<PodcastEpisode>()
        var inItem = false
        var title = ""
        var url = ""
        var duration: Int? = null
        var pub = ""
        var image = ""
        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT && out.size < limit) {
            when (event) {
                XmlPullParser.START_TAG ->
                    when (p.name) {
                        "item" -> {
                            inItem = true
                            title = ""; url = ""; duration = null; pub = ""; image = ""
                        }
                        "title" -> if (inItem) title = p.nextText().trim()
                        "pubDate" -> if (inItem) pub = p.nextText().trim().take(16)
                        "enclosure" -> if (inItem) url = (p.getAttributeValue(null, "url") ?: "").replaceFirst("http://", "https://")
                        "duration" -> if (inItem) duration = parseDuration(p.nextText().trim())
                        "image" -> if (inItem) image = p.getAttributeValue(null, "href") ?: ""
                    }
                XmlPullParser.END_TAG ->
                    if (p.name == "item") {
                        inItem = false
                        if (url.isNotBlank()) out.add(PodcastEpisode(podcast.name, title.ifBlank { "Episode" }, url, image.ifBlank { podcast.artwork }, duration, pub))
                    }
            }
            event = p.next()
        }
        return out
    }

    private fun parseDuration(text: String): Int? {
        if (text.isBlank()) return null
        val parts = text.split(':').mapNotNull { it.toIntOrNull() }
        return when (parts.size) {
            1 -> parts[0]
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> null
        }
    }
}
