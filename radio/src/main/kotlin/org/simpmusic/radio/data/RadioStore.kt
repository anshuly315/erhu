package org.simpmusic.radio.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray

/**
 * Everything radio keeps on the device: favourites, listening history, stations the listener hid
 * because they were dead, and the last station played. A small JSON blob in SharedPreferences, so
 * the music database and its migrations stay untouched.
 */
class RadioStore(
    context: Context,
) {
    private val prefs = context.applicationContext.getSharedPreferences("radio_store", Context.MODE_PRIVATE)

    private val _favourites = MutableStateFlow(readList(KEY_FAV))
    val favourites: StateFlow<List<RadioStation>> = _favourites

    private val _history = MutableStateFlow(readList(KEY_HISTORY))
    val history: StateFlow<List<RadioStation>> = _history

    private val _hidden = MutableStateFlow(prefs.getStringSet(KEY_HIDDEN, emptySet()).orEmpty())
    val hidden: StateFlow<Set<String>> = _hidden

    private fun readList(key: String): List<RadioStation> =
        runCatching {
            val arr = JSONArray(prefs.getString(key, "[]"))
            (0 until arr.length()).map { RadioStation.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())

    private fun writeList(
        key: String,
        list: List<RadioStation>,
    ) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    /** The station list layout: 0 cards, 1 list, 2 grid, 3 compact grid. */
    var viewMode: Int
        get() = prefs.getInt("view_mode", 2)
        set(v) = prefs.edit().putInt("view_mode", v.coerceIn(0, 5)).apply()

    fun isFavourite(uuid: String) = _favourites.value.any { it.uuid == uuid }

    fun toggleFavourite(station: RadioStation) {
        val next =
            if (isFavourite(station.uuid)) {
                _favourites.value.filterNot { it.uuid == station.uuid }
            } else {
                listOf(station) + _favourites.value
            }
        _favourites.value = next
        writeList(KEY_FAV, next)
    }

    fun addToHistory(station: RadioStation) {
        val next = (listOf(station) + _history.value.filterNot { it.uuid == station.uuid }).take(MAX_HISTORY)
        _history.value = next
        writeList(KEY_HISTORY, next)
        prefs.edit().putString(KEY_LAST, station.toJson().toString()).apply()
    }

    fun clearHistory() {
        _history.value = emptyList()
        writeList(KEY_HISTORY, emptyList())
    }

    fun lastStation(): RadioStation? =
        prefs.getString(KEY_LAST, null)?.let { runCatching { RadioStation.fromJson(org.json.JSONObject(it)) }.getOrNull() }

    fun hide(uuid: String) {
        val next = _hidden.value + uuid
        _hidden.value = next
        prefs.edit().putStringSet(KEY_HIDDEN, next).apply()
    }

    private companion object {
        const val KEY_FAV = "favourites"
        const val KEY_HISTORY = "history"
        const val KEY_HIDDEN = "hidden"
        const val KEY_LAST = "last"
        const val MAX_HISTORY = 100
    }
}
