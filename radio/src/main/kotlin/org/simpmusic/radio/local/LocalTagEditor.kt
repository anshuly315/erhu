package org.simpmusic.radio.local

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import java.util.logging.Level
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import org.json.JSONObject

/** What the file says about itself right now. */
data class LocalTags(val title: String, val artist: String, val album: String, val genre: String, val year: String)

/** An online match for the file, used to fill the tag fields and offer cover art. */
data class TagSuggestion(
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val year: String,
    val artwork: String,
)

/**
 * Edits the tags and cover of ONE file the user picked, nothing in bulk. Android only lets an app
 * change other apps' media after the user agrees, so [write] throws SecurityException until then and
 * the caller asks through [writeRequest].
 */
object LocalTagEditor {
    /** The MediaStore id inside a local track id, or null for anything that is not a phone file. */
    fun mediaIdOf(trackId: String): Long? = trackId.removePrefix("radio:local-").takeIf { trackId.startsWith("radio:local-") }?.toLongOrNull()

    fun uriOf(mediaId: Long): Uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId)

    /** The consent prompt for API 30+; null where the SecurityException itself carries one or none is needed. */
    fun writeRequest(
        context: Context,
        uri: Uri,
    ): android.content.IntentSender? =
        if (android.os.Build.VERSION.SDK_INT >= 30) MediaStore.createWriteRequest(context.contentResolver, listOf(uri)).intentSender else null

    private fun extensionOf(
        context: Context,
        uri: Uri,
    ): String {
        context.contentResolver.query(uri, arrayOf(MediaStore.Audio.Media.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0)?.substringAfterLast('.', "")?.lowercase()?.takeIf { e -> e.isNotBlank() }?.let { e -> return e }
        }
        return "mp3"
    }

    suspend fun read(
        context: Context,
        uri: Uri,
    ): LocalTags? =
        withContext(Dispatchers.IO) {
            runCatching {
                silence()
                val file = copyToCache(context, uri, "read")
                try {
                    val tag = AudioFileIO.read(file).tag
                    LocalTags(
                        tag?.getFirst(FieldKey.TITLE).orEmpty(),
                        tag?.getFirst(FieldKey.ARTIST).orEmpty(),
                        tag?.getFirst(FieldKey.ALBUM).orEmpty(),
                        tag?.getFirst(FieldKey.GENRE).orEmpty(),
                        tag?.getFirst(FieldKey.YEAR).orEmpty(),
                    )
                } finally {
                    file.delete()
                }
            }.getOrNull()
        }

    private fun silence() {
        java.util.logging.Logger.getLogger("org.jaudiotagger").level = Level.OFF
    }

    private fun copyToCache(
        context: Context,
        uri: Uri,
        tag: String,
    ): File {
        val dir = File(context.cacheDir, "tag-edit").apply { mkdirs() }
        val f = File(dir, "${UUID.randomUUID()}-$tag.${extensionOf(context, uri)}")
        context.contentResolver.openInputStream(uri)!!.use { i -> f.outputStream().use { i.copyTo(it) } }
        return f
    }

    /**
     * Writes the fields and, when given, the cover. The edit happens on a cached copy that is read
     * back and checked; only then is it streamed over the original, and the original is restored if
     * that fails half way.
     */
    suspend fun write(
        context: Context,
        uri: Uri,
        tags: LocalTags,
        artwork: ByteArray?,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                silence()
                val backup = copyToCache(context, uri, "orig")
                val edited = File(backup.parentFile, backup.name.replace("-orig.", "-edit."))
                try {
                    backup.copyTo(edited, overwrite = true)
                    val af = AudioFileIO.read(edited)
                    val tag = af.tagOrCreateAndSetDefault
                    fun put(key: FieldKey, v: String) { if (v.isBlank()) tag.deleteField(key) else tag.setField(key, v.trim()) }
                    put(FieldKey.TITLE, tags.title)
                    put(FieldKey.ARTIST, tags.artist)
                    put(FieldKey.ALBUM, tags.album)
                    put(FieldKey.GENRE, tags.genre)
                    if (tags.year.isBlank()) tag.deleteField(FieldKey.YEAR) else { tag.deleteField(FieldKey.YEAR); tag.setField(FieldKey.YEAR, tags.year.trim()) }
                    if (artwork != null) {
                        val art = File(edited.parentFile, "${UUID.randomUUID()}-art.jpg")
                        try {
                            art.writeBytes(downscale(artwork))
                            tag.deleteArtworkField()
                            tag.setField(ArtworkFactory.createArtworkFromFile(art))
                        } finally {
                            art.delete()
                        }
                    }
                    af.tag = tag
                    AudioFileIO.write(af)
                    val check = AudioFileIO.read(edited).tag ?: error("No tag after write")
                    if (tags.title.isNotBlank() && check.getFirst(FieldKey.TITLE) != tags.title.trim()) error("Title did not read back")
                    try {
                        context.contentResolver.openOutputStream(uri, "wt")!!.use { o -> edited.inputStream().use { it.copyTo(o) } }
                    } catch (e: SecurityException) {
                        throw e
                    } catch (e: Exception) {
                        runCatching { context.contentResolver.openOutputStream(uri, "wt")!!.use { o -> backup.inputStream().use { it.copyTo(o) } } }
                        throw e
                    }
                    MediaStore.scanFile(context, arrayOf(null), null, null)
                } finally {
                    backup.delete()
                    edited.delete()
                }
            }
        }

    private fun downscale(raw: ByteArray): ByteArray {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, o)
        var s = 1
        while (o.outWidth / s > 1440 || o.outHeight / s > 1440) s *= 2
        val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = s }) ?: return raw
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it); bmp.recycle() }.toByteArray()
    }

    /** Looks the song up in Apple's keyless catalogue search, best match first. */
    suspend fun suggest(
        title: String,
        artist: String,
    ): List<TagSuggestion> =
        withContext(Dispatchers.IO) {
            runCatching {
                val term = URLEncoder.encode("$title $artist".trim(), "UTF-8")
                val c = URL("https://itunes.apple.com/search?media=music&entity=song&limit=8&term=$term").openConnection() as HttpURLConnection
                c.connectTimeout = 10000; c.readTimeout = 15000
                val arr = JSONObject(c.inputStream.bufferedReader().use { it.readText() }).optJSONArray("results") ?: return@runCatching emptyList()
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    TagSuggestion(
                        o.optString("trackName"), o.optString("artistName"), o.optString("collectionName"),
                        o.optString("primaryGenreName"), o.optString("releaseDate").take(4),
                        // The catalogue serves any size from the same link; ask for 1200 px.
                        o.optString("artworkUrl100").replace("100x100bb", "1200x1200bb"),
                    )
                }
            }.getOrDefault(emptyList())
        }

    suspend fun download(url: String): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching { URL(url).openStream().use { it.readBytes() } }.getOrNull()
        }
}
