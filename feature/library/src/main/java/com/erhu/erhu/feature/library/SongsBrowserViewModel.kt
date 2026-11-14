package com.erhu.erhu.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erhu.erhu.core.common.permission.PermissionManager
import com.erhu.erhu.core.common.result.AppResult
import com.erhu.erhu.core.database.repository.SongsBrowserRepository
import com.erhu.erhu.core.datastore.SongsBrowserPreferencesRepository
import com.erhu.erhu.core.datastore.repository.ThemeRepository
import com.erhu.erhu.core.model.*
import com.erhu.erhu.core.playback.repository.PlaybackRepository
import com.erhu.erhu.feature.library.mix.Moods
import com.erhu.erhu.feature.library.vybe.VybeEngine
import com.erhu.erhu.feature.library.vybe.VybeStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Year

internal data class SongsBrowserState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val songs: List<Song> = emptyList(),
    val total: Int = 0,
    val genres: List<String> = emptyList(),
    val preferences: SongsBrowserPreferences = SongsBrowserPreferences(),
    val query: String = "",
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SongsBrowserViewModel @Inject constructor(
    private val repository: SongsBrowserRepository,
    private val preferences: SongsBrowserPreferencesRepository,
    private val playback: PlaybackRepository,
    private val engine: VybeEngine,
    theme: ThemeRepository,
    private val permissions: PermissionManager,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val retry = MutableStateFlow(0)
    private val failed = MutableStateFlow(false)
    private val visible = MutableStateFlow(false)
    private val writeFailed = MutableStateFlow(false)
    val saveFailed: StateFlow<Boolean> = writeFailed.asStateFlow()
    private val query = savedState.getStateFlow("songsQuery", "")
    private val rows = retry.flatMapLatest { repository.observe().catch { failed.value = true } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    private val options = retry.flatMapLatest { preferences.observe().catch { failed.value = true } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    private data class MoodContext(val enabled: Boolean, val visible: Boolean, val version: Long, val ready: Boolean)
    private val moodContext = combine(theme.vybeEnabled, visible, engine.catalogVersion, engine.status) { enabled, shown, version, status ->
        MoodContext(enabled != false, shown, version, status is VybeStatus.Ready)
    }
    private data class MoodKey(val mood: SongsMood, val version: Long, val catalog: List<SongTags>)
    private var cachedKey: MoodKey? = null
    private var cachedMatches: Set<Long>? = null
    private var ownsModel = false

    init {
        viewModelScope.launch {
            combine(options, theme.vybeEnabled, visible) { prefs, enabled, shown ->
                shown && enabled != false && prefs != null && prefs.mood != SongsMood.ALL
            }.distinctUntilChanged().collect { needed ->
                if (needed) { ownsModel = true; engine.setScreenVisible(true); engine.start() }
                else if (ownsModel) { ownsModel = false; engine.setScreenVisible(false) }
            }
        }
    }

    internal val state = combine(rows, options, query, moodContext, failed) { entries, prefs, text, context, error ->
        BrowserInput(entries, prefs, text, context, error)
    }.mapLatest { input ->
        val entries = input.entries
        val prefs = input.prefs
        if (entries == null || prefs == null) return@mapLatest SongsBrowserState(loading = !input.error, failed = input.error)
        withContext(Dispatchers.Default) {
            val catalog = entries.map { entry ->
                val song = entry.song
                SongTags(song.id, song.title, song.artist, song.album, song.genre, entry.year, song.bpm, null)
            }
            val moodIds = if (prefs.mood != SongsMood.ALL && input.context.enabled && input.context.visible && input.context.ready) {
                val key = MoodKey(prefs.mood, input.context.version, catalog)
                if (key == cachedKey) cachedMatches else {
                    val matches = try { engine.matchMood(moodQuery(prefs.mood), catalog) }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { null }
                    ensureActive()
                    cachedKey = key; cachedMatches = matches
                    matches
                }
            } else null
            val needle = input.text.trim()
            val filtered = entries.filter { entry ->
                val song = entry.song
                val genres = Genres.parse(song.genre)
                val genreMatch = when {
                    prefs.genre.isEmpty() -> true
                    prefs.genre == "untagged" -> genres.isEmpty()
                    else -> genres.any { it.equals(prefs.genre.removePrefix("genre:"), ignoreCase = true) }
                }
                genreMatch && (needle.isEmpty() || listOf(song.title, song.artist, song.album).any { it.contains(needle, ignoreCase = true) }) &&
                    (prefs.mood == SongsMood.ALL || if (moodIds != null) song.id in moodIds else matchesMetadata(entry, prefs.mood))
            }
            val titleOrder = compareBy<SongBrowserEntry, String>(String.CASE_INSENSITIVE_ORDER) { it.song.title }.thenBy { it.song.id }
            val comparator = when (prefs.sort) {
                SongsSort.TITLE_ASC -> titleOrder
                SongsSort.TITLE_DESC -> compareByDescending<SongBrowserEntry, String>(String.CASE_INSENSITIVE_ORDER) { it.song.title }.thenBy { it.song.id }
                SongsSort.RECENTLY_ADDED -> compareByDescending<SongBrowserEntry> { it.addedAtMs }.then(titleOrder)
                SongsSort.RECENTLY_PLAYED -> compareByDescending<SongBrowserEntry> { it.lastPlayedAtMs }.then(titleOrder)
                SongsSort.ARTIST -> compareBy<SongBrowserEntry, String>(String.CASE_INSENSITIVE_ORDER) { it.song.artist }.then(titleOrder)
                SongsSort.ALBUM -> compareBy<SongBrowserEntry, String>(String.CASE_INSENSITIVE_ORDER) { it.song.album }.then(titleOrder)
                SongsSort.MOST_PLAYED -> compareByDescending<SongBrowserEntry> { it.plays }.thenByDescending { it.lastPlayedAtMs }.then(titleOrder)
            }
            SongsBrowserState(loading = false, failed = input.error, songs = filtered.sortedWith(comparator).map { it.song }, total = entries.size,
                genres = entries.flatMap { Genres.parse(it.song.genre) }.distinctBy { it.lowercase(java.util.Locale.ROOT) }.sortedWith(String.CASE_INSENSITIVE_ORDER),
                preferences = prefs, query = input.text)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SongsBrowserState())

    private data class BrowserInput(val entries: List<SongBrowserEntry>?, val prefs: SongsBrowserPreferences?, val text: String,
        val context: MoodContext, val error: Boolean)

    fun setVisible(value: Boolean) {
        visible.value = value
        if (value && permissions.hasAudioPermission()) viewModelScope.launch {
            if (repository.backfillAddedDates() is AppResult.Error) writeFailed.value = true
        }
    }
    fun setQuery(value: String) { savedState["songsQuery"] = value }
    fun setSort(value: SongsSort) = save { preferences.setSort(value) }
    fun setGenre(value: String) = save { preferences.setGenre(value) }
    fun setMood(value: SongsMood) = save { preferences.setMood(value) }
    fun clearFilters() { setQuery(""); save { preferences.clear() } }
    fun consumeSaveError() { writeFailed.value = false }
    fun reload() { failed.value = false; retry.value += 1; if (visible.value) setVisible(true) }
    private fun save(block: suspend () -> AppResult<Unit>) { viewModelScope.launch { if (block() is AppResult.Error) writeFailed.value = true } }
    fun play(songs: List<Song>, index: Int) { if (index in songs.indices) playback.playCollection(songs, index, null) }
    override fun onCleared() { if (ownsModel) engine.setScreenVisible(false); super.onCleared() }
}

private fun moodQuery(mood: SongsMood): String = when (mood) {
    SongsMood.CHILL -> "Calm chilled relaxing mellow music"
    SongsMood.HIGH_ENERGY -> "High energy energetic intense upbeat music"
    SongsMood.LATE_NIGHT -> "Late night smooth atmospheric moody music"
    SongsMood.FEEL_GOOD -> "Happy uplifting cheerful feel good music"
    SongsMood.PARTY -> "Party dance upbeat celebration music"
    SongsMood.LOVE -> "Romantic love songs tender affectionate music"
    SongsMood.SAD -> "Sad reflective melancholic emotional music"
    SongsMood.DARK -> "Dark brooding ominous haunting music"
    SongsMood.FOCUS -> "Focus concentration calm instrumental music"
    SongsMood.THROWBACK -> "Oldies nostalgic retro classic throwback music"
    SongsMood.ALL -> ""
}

private fun matchesMetadata(entry: SongBrowserEntry, mood: SongsMood): Boolean {
    val song = entry.song
    val track = TrackFacts(song.id, song.title, 0, song.artist, 0, song.album, song.genre, song.bpm,
        song.durationMs, song.isFavorite, entry.addedAtMs, entry.year)
    val genres = Genres.parse(song.genre).map { it.lowercase(java.util.Locale.ROOT) }
    fun tagged(vararg words: String) = genres.any { genre -> words.any { it in genre } }
    return when (mood) {
        SongsMood.ALL -> true
        SongsMood.CHILL -> Moods.isChill(track)
        SongsMood.HIGH_ENERGY -> Moods.isHighEnergy(track)
        SongsMood.LATE_NIGHT -> Moods.isLateNight(track)
        SongsMood.FEEL_GOOD -> tagged("feel good", "feel-good", "happy", "funk", "disco", "ska")
        SongsMood.PARTY -> tagged("party", "dance", "house", "edm", "disco", "reggaeton")
        SongsMood.LOVE -> tagged("love", "romantic", "romance")
        SongsMood.SAD -> tagged("sad", "melanchol", "heartbreak")
        SongsMood.DARK -> tagged("dark", "goth", "doom", "horror", "industrial")
        SongsMood.FOCUS -> tagged("ambient", "instrumental", "classical", "piano", "lo-fi", "lofi", "meditation")
        SongsMood.THROWBACK -> (entry.year in 1900..(Year.now().value - 10)) || tagged("retro", "oldies")
    }
}
