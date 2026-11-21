package org.simpmusic.radio.data

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Client for https://api.radio-browser.info, the free community directory of internet radio.
 * Mirrors are discovered once and tried in turn, as the project asks of its clients.
 */
class RadioBrowserApi {
    private val fallbackServers =
        listOf(
            "https://de1.api.radio-browser.info",
            "https://de2.api.radio-browser.info",
            "https://nl1.api.radio-browser.info",
            "https://at1.api.radio-browser.info",
        )

    @Volatile
    private var servers: List<String> = emptyList()

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private suspend fun serverList(): List<String> {
        if (servers.isNotEmpty()) return servers
        val found =
            runCatching {
                JSONArray(rawGet("https://all.api.radio-browser.info/json/servers", 6000))
                    .let { arr -> (0 until arr.length()).map { "https://" + arr.getJSONObject(it).getString("name") } }
                    .distinct()
                    .shuffled()
            }.getOrDefault(emptyList())
        servers = found.ifEmpty { fallbackServers.shuffled() }
        return servers
    }

    private suspend fun rawGet(
        url: String,
        timeoutMs: Int = 10000,
    ): String =
        withContext(Dispatchers.IO) {
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = timeoutMs
                c.readTimeout = timeoutMs
                c.setRequestProperty("User-Agent", USER_AGENT)
                if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
                c.inputStream.bufferedReader().use { it.readText() }
            } finally {
                c.disconnect()
            }
        }

    private suspend fun get(path: String): String {
        var last: Throwable? = null
        for (server in serverList()) {
            try {
                return rawGet(server + path)
            } catch (t: Throwable) {
                last = t
            }
        }
        servers = emptyList()
        throw last ?: IllegalStateException("No Radio Browser server reachable")
    }

    private fun parseStations(json: String): List<RadioStation> {
        val arr = JSONArray(json)
        return (0 until arr.length()).mapNotNull { RadioStation.fromApi(arr.getJSONObject(it)) }
    }

    private suspend fun stations(
        path: String,
        params: String = "",
    ): List<RadioStation> =
        parseStations(get("$path?hidebroken=true$params"))

    suspend fun popular(limit: Int = 60) = stations("/json/stations/topvote/$limit")

    suspend fun trending(limit: Int = 60) = stations("/json/stations/topclick/$limit")

    suspend fun search(
        query: String,
        limit: Int = 80,
    ) = stations("/json/stations/search", "&name=${enc(query)}&limit=$limit&order=clickcount&reverse=true")

    suspend fun byCountry(
        code: String,
        limit: Int = 200,
    ) = stations("/json/stations/bycountrycodeexact/${enc(code)}", "&limit=$limit&order=votes&reverse=true")

    suspend fun byLanguage(
        language: String,
        limit: Int = 200,
    ) = stations("/json/stations/bylanguageexact/${enc(language)}", "&limit=$limit&order=votes&reverse=true")

    suspend fun byTag(
        tag: String,
        limit: Int = 200,
    ) = stations("/json/stations/bytagexact/${enc(tag)}", "&limit=$limit&order=votes&reverse=true")

    /** Stations within [radiusKm] of a point, for "Near me". */
    suspend fun near(
        lat: Double,
        lon: Double,
        radiusKm: Int = 150,
        limit: Int = 80,
    ) = stations(
        "/json/stations/search",
        "&geo_lat=$lat&geo_long=$lon&geo_distance=${radiusKm * 1000}&limit=$limit&order=votes&reverse=true",
    ).filter { it.lat != null }

    suspend fun countries(): List<RadioFacet> =
        facets("/json/countries?hidebroken=true&order=stationcount&reverse=true") { o ->
            RadioFacet(o.optString("name"), o.optString("iso_3166_1"), o.optInt("stationcount"))
        }.filter { it.code.isNotBlank() }

    suspend fun languages(): List<RadioFacet> =
        facets("/json/languages?hidebroken=true&order=stationcount&reverse=true&limit=300") { o ->
            RadioFacet(o.optString("name"), o.optString("name"), o.optInt("stationcount"))
        }

    suspend fun tags(): List<RadioFacet> =
        facets("/json/tags?hidebroken=true&order=stationcount&reverse=true&limit=200") { o ->
            RadioFacet(o.optString("name"), o.optString("name"), o.optInt("stationcount"))
        }

    private suspend fun facets(
        path: String,
        map: (org.json.JSONObject) -> RadioFacet,
    ): List<RadioFacet> {
        val arr = JSONArray(get(path))
        return (0 until arr.length()).map { map(arr.getJSONObject(it)) }.filter { it.name.isNotBlank() }
    }

    /** Tells the directory a station was played (it ranks stations by this) and returns its working URL. */
    suspend fun registerClick(uuid: String): String? =
        runCatching {
            val o = org.json.JSONObject(get("/json/url/${enc(uuid)}"))
            o.optString("url").takeIf { o.optBoolean("ok", true) && it.isNotBlank() }
        }.getOrNull()

    /** Votes for a station, which is how listeners lift good ones in the Popular list. */
    suspend fun vote(uuid: String) {
        runCatching { get("/json/vote/${enc(uuid)}") }
    }

    private companion object {
        const val USER_AGENT = "SimpMusic-Radio/1.0 (+https://github.com/maxrave-dev/SimpMusic)"
    }
}
