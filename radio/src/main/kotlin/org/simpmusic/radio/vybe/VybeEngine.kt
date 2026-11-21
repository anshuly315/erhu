package org.simpmusic.radio.vybe

import android.content.Context
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.simpmusic.radio.local.LocalSong
import org.simpmusic.radio.vybe.core.EmbeddingRole
import org.simpmusic.radio.vybe.core.IndexedSong
import org.simpmusic.radio.vybe.core.MediaPipeTextEmbedder
import org.simpmusic.radio.vybe.core.SongText
import org.simpmusic.radio.vybe.core.SongVectors
import org.simpmusic.radio.vybe.core.VybeAnswer
import org.simpmusic.radio.vybe.core.VybeDiscovery
import org.simpmusic.radio.vybe.core.VybeEmbedderException
import org.simpmusic.radio.vybe.core.VybeIndexFile
import org.simpmusic.radio.vybe.core.VybeMatch
import org.simpmusic.radio.vybe.core.VybeNameIndex
import org.simpmusic.radio.vybe.core.VybeText
import org.simpmusic.radio.vybe.core.textHash

sealed interface VybeStatus {
    data object NoModel : VybeStatus
    data object LoadingModel : VybeStatus
    data class Downloading(val bytes: Long, val total: Long, val verifying: Boolean = false) : VybeStatus
    data class Indexing(val done: Int, val total: Int) : VybeStatus
    data class Ready(val songs: Int) : VybeStatus
    data class Failed(val message: String) : VybeStatus
}

/**
 * VYBE for the songs on the phone: describe what you want to hear and it ranks your own music by
 * how close each song's description (artist, genre, year) is to the words. Google's EmbeddingGemma
 * runs on the device through MediaPipe; the only network use is the one-time model download.
 * Model work runs on one background thread, in order, as the model and index file have one owner.
 */
class VybeEngine private constructor(context: Context) {
    private val context = context.applicationContext
    private val dispatcher = Executors.newSingleThreadExecutor { Thread(it, "vybe").apply { isDaemon = true } }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow<VybeStatus>(if (modelInstalledQuick()) VybeStatus.LoadingModel else VybeStatus.NoModel)
    val status: StateFlow<VybeStatus> = _status

    private var embedder: MediaPipeTextEmbedder? = null
    private var index: VybeIndexFile? = null
    private var library: List<SongText> = emptyList()
    private var names = VybeNameIndex(emptyList())
    private var job: Job? = null
    private var download: Job? = null

    private fun dir() = File(context.filesDir, "vybe").apply { mkdirs() }
    private fun modelFile() = File(dir(), "embedding_gemma.task")
    private fun modelInstalledQuick() = modelFile().let { it.isFile && it.length() == MODEL_BYTES }

    /** Downloads the model straight from Google, resuming a partial file, and checks its SHA-256. */
    fun downloadModel() {
        if (download?.isActive == true) return
        download =
            ioScope.launch {
                val part = File(dir(), "embedding_gemma.task.part")
                try {
                    var have = if (part.isFile) part.length() else 0L
                    if (have > MODEL_BYTES) { part.delete(); have = 0 }
                    if (have < MODEL_BYTES) {
                        val c = URL(MODEL_URL).openConnection() as HttpURLConnection
                        c.connectTimeout = 15000; c.readTimeout = 30000
                        if (have > 0) c.setRequestProperty("Range", "bytes=$have-")
                        val resumed = c.responseCode == 206
                        if (c.responseCode != 200 && !resumed) error("Download failed (${c.responseCode})")
                        if (!resumed) have = 0
                        c.inputStream.use { input ->
                            java.io.FileOutputStream(part, resumed).use { out ->
                                val buf = ByteArray(64 * 1024)
                                var last = 0L
                                while (true) {
                                    ensureActive()
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    out.write(buf, 0, n); have += n
                                    if (have - last > 512 * 1024) { last = have; _status.value = VybeStatus.Downloading(have, MODEL_BYTES) }
                                }
                            }
                        }
                    }
                    _status.value = VybeStatus.Downloading(MODEL_BYTES, MODEL_BYTES, verifying = true)
                    if (part.length() != MODEL_BYTES || sha256(part) != MODEL_SHA256) {
                        part.delete()
                        error("The downloaded model did not match its checksum. Try again.")
                    }
                    if (!part.renameTo(modelFile())) error("Could not save the model")
                    _status.value = VybeStatus.LoadingModel
                } catch (e: CancellationException) {
                    _status.value = if (modelInstalledQuick()) VybeStatus.LoadingModel else VybeStatus.NoModel
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Model download failed", e)
                    _status.value = VybeStatus.Failed(e.message ?: "Download failed")
                }
            }
    }

    /** Removes the model and the index it built to free the space. */
    fun deleteModel() {
        download?.cancel()
        job?.cancel()
        scope.launch {
            embedder?.close(); embedder = null; index = null
            dir().deleteRecursively()
            _status.value = VybeStatus.NoModel
        }
    }

    fun isInstalled() = modelInstalledQuick()

    fun cancelDownload() { download?.cancel() }

    /** Loads the model (if installed) and indexes [songs]; safe to call whenever the library changes. */
    fun sync(songs: List<LocalSong>) {
        if (!modelInstalledQuick()) { if (_status.value is VybeStatus.LoadingModel) _status.value = VybeStatus.NoModel; return }
        val texts = songs.map { it.toText() }
        job?.cancel()
        job = scope.launch {
            if (!loadModel()) return@launch
            library = texts
            names = VybeNameIndex(texts)
            reindex(texts)
        }
    }

    suspend fun search(query: String, limit: Int = 60): VybeAnswer? =
        withContext(dispatcher) {
            val model = embedder ?: return@withContext null
            val file = index ?: return@withContext null
            val stored = file.entries()
            val live = library.mapNotNull { s -> documentHash(s).takeIf { stored[s.songId] == it }?.let { s.songId to it } }.toMap()
            VybeDiscovery(model, Vectors(file, live)).search(query, library, limit, names)
        }

    private fun loadModel(): Boolean {
        if (embedder != null) return true
        _status.value = VybeStatus.LoadingModel
        return try {
            val m = MediaPipeTextEmbedder.fromFile(context, modelFile())
            embedder = m
            index = VybeIndexFile(File(dir(), "index.bin"), MODEL_ID, m.dimension)
            true
        } catch (e: VybeEmbedderException) {
            Log.w(TAG, "Model load failed", e)
            _status.value = VybeStatus.Failed("Could not load the model: ${e.message}")
            false
        }
    }

    private suspend fun reindex(songs: List<SongText>) {
        val model = embedder ?: return
        val file = index ?: return
        val stored = file.entries()
        val wanted = songs.associate { it.songId to documentHash(it) }
        val missing = songs.filter { stored[it.songId] != wanted[it.songId] }
        var done = songs.size - missing.size
        _status.value = if (missing.isEmpty()) VybeStatus.Ready(songs.size) else VybeStatus.Indexing(done, songs.size)
        val batch = mutableListOf<IndexedSong>()
        try {
            for ((text, group) in missing.groupBy { VybeText.describe(it) }) {
                currentCoroutineContext().ensureActive()
                val vector = model.embed(text, EmbeddingRole.DOCUMENT)
                group.forEach { batch += IndexedSong(it.songId, wanted.getValue(it.songId), vector) }
                done += group.size
                if (batch.size >= 8) { file.append(batch.toList()); batch.clear() }
                _status.value = VybeStatus.Indexing(done, songs.size)
                yield()
            }
            file.append(batch)
            if (file.recordCount() > wanted.size * 2 + 64) file.compact(wanted)
            _status.value = VybeStatus.Ready(songs.size)
        } catch (e: CancellationException) {
            file.append(batch)
            throw e
        } catch (e: Exception) {
            file.append(batch)
            Log.w(TAG, "Indexing failed", e)
            _status.value = VybeStatus.Failed("Indexing stopped: ${e.message}")
        }
    }

    private class Vectors(private val file: VybeIndexFile, private val live: Map<Long, Long>) : SongVectors {
        override fun rank(limit: Int, score: (songId: Long, vector: FloatArray) -> Float?): List<VybeMatch> = file.rank(live, limit, score)
        override fun vectorOf(songId: Long): FloatArray? = if (songId in live) file.vectorOf(songId) else null
    }

    private fun LocalSong.toText() = SongText(songId = id, title = title, artist = artist, album = album, genre = genre, year = year)

    companion object {
        private const val TAG = "Vybe"
        const val MODEL_URL = "https://storage.googleapis.com/mediapipe-models/text_embedder/embedding_gemma/int4int8/latest/embedding_gemma.task"
        const val MODEL_BYTES = 183816181L
        const val MODEL_SHA256 = "913b7a1edc7c7c3d1da3979ec1d0648ed9e0a370f181bb59ab177ca4b97707ad"
        private val MODEL_ID = "embeddinggemma-300m-int4int8|$MODEL_SHA256|doc${VybeText.DOCUMENT_VERSION}"

        @Volatile private var instance: VybeEngine? = null
        fun get(context: Context): VybeEngine = instance ?: synchronized(this) { instance ?: VybeEngine(context).also { instance = it } }

        fun documentHash(song: SongText): Long = textHash(VybeText.describe(song))

        private fun sha256(f: File): String {
            val d = MessageDigest.getInstance("SHA-256")
            f.inputStream().buffered().use { i -> val b = ByteArray(64 * 1024); while (true) { val n = i.read(b); if (n < 0) break; d.update(b, 0, n) } }
            return d.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
