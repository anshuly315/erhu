package org.simpmusic.radio.data

import org.json.JSONObject

/** One internet radio station, as served by the Radio Browser directory. */
data class RadioStation(
    val uuid: String,
    val name: String,
    val streamUrl: String,
    val homepage: String = "",
    val favicon: String = "",
    val tags: String = "",
    val country: String = "",
    val countryCode: String = "",
    val language: String = "",
    val codec: String = "",
    val bitrate: Int = 0,
    val votes: Int = 0,
    val isHls: Boolean = false,
    val lat: Double? = null,
    val lon: Double? = null,
) {
    val subtitle: String
        get() = listOf(country, tags.split(',').firstOrNull { it.isNotBlank() }?.trim().orEmpty())
            .filter { it.isNotBlank() }
            .joinToString(" · ")

    val quality: String
        get() = listOf(codec.takeIf { it.isNotBlank() }, bitrate.takeIf { it > 0 }?.let { "$it kbps" })
            .filterNotNull()
            .joinToString(" · ")

    fun toJson(): JSONObject =
        JSONObject()
            .put("uuid", uuid).put("name", name).put("url", streamUrl).put("homepage", homepage)
            .put("favicon", favicon).put("tags", tags).put("country", country)
            .put("cc", countryCode).put("lang", language).put("codec", codec)
            .put("bitrate", bitrate).put("votes", votes).put("hls", isHls)
            .also { o ->
                lat?.let { o.put("lat", it) }
                lon?.let { o.put("lon", it) }
            }

    companion object {
        fun fromJson(o: JSONObject): RadioStation =
            RadioStation(
                uuid = o.optString("uuid"),
                name = o.optString("name").trim(),
                streamUrl = o.optString("url"),
                homepage = o.optString("homepage"),
                favicon = o.optString("favicon"),
                tags = o.optString("tags"),
                country = o.optString("country"),
                countryCode = o.optString("cc"),
                language = o.optString("lang"),
                codec = o.optString("codec"),
                bitrate = o.optInt("bitrate"),
                votes = o.optInt("votes"),
                isHls = o.optBoolean("hls"),
                lat = if (o.has("lat")) o.optDouble("lat") else null,
                lon = if (o.has("lon")) o.optDouble("lon") else null,
            )

        /** Parses one entry of the Radio Browser API's own JSON. */
        fun fromApi(o: JSONObject): RadioStation? {
            val url = o.optString("url_resolved").ifBlank { o.optString("url") }
            val uuid = o.optString("stationuuid")
            if (uuid.isBlank() || url.isBlank()) return null
            return RadioStation(
                uuid = uuid,
                name = o.optString("name").trim().ifBlank { "Unnamed station" },
                streamUrl = url,
                homepage = o.optString("homepage"),
                favicon = o.optString("favicon"),
                tags = o.optString("tags"),
                country = o.optString("country"),
                countryCode = o.optString("countrycode"),
                language = o.optString("language"),
                codec = o.optString("codec"),
                bitrate = o.optInt("bitrate"),
                votes = o.optInt("votes"),
                isHls = o.optInt("hls") == 1,
                lat = if (o.isNull("geo_lat")) null else o.optDouble("geo_lat"),
                lon = if (o.isNull("geo_long")) null else o.optDouble("geo_long"),
            )
        }
    }
}

/** A country, language or tag with its station count, for the Browse lists. */
data class RadioFacet(
    val name: String,
    val code: String,
    val count: Int,
)
