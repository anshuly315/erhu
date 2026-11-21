package org.simpmusic.radio.local

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.maxrave.common.Config
import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.data.model.searchResult.songs.Artist
import com.maxrave.domain.data.model.searchResult.songs.Thumbnail
import com.maxrave.domain.data.radio.RadioStreams
import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler
import com.maxrave.domain.mediaservice.handler.PlaylistType
import com.maxrave.domain.mediaservice.handler.QueueData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.simpmusic.radio.data.FallbackArt

data class LocalSong(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val addedAt: Long,
    val uri: String,
    val genre: String? = null,
    val year: Int = 0,
    /** The folder the file sits in, e.g. "Music/Albums"; empty when unknown. */
    val folder: String = "",
) {
    val albumArt: String get() = "content://media/external/audio/albumart/$albumId"
    val trackId: String get() = RadioStreams.PREFIX + "local-" + id
}

/** "Music/Albums/" or "/storage/emulated/0/Music/Albums/song.mp3" -> "Music/Albums". */
internal fun folderOf(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    var p = raw.trim()
    if (p.startsWith("/storage/emulated/0/")) p = p.removePrefix("/storage/emulated/0/")
    if (p.contains('.') && p.substringAfterLast('/').contains('.')) p = p.substringBeforeLast('/', "")
    return p.trim('/')
}

/** Folders the user switched off. Everything is on by default, so new folders show up by themselves. */
object LocalFolders {
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("local_folders", Context.MODE_PRIVATE)

    fun excluded(context: Context): Set<String> = prefs(context).getStringSet("excluded", emptySet()) ?: emptySet()

    fun setExcluded(context: Context, folders: Set<String>) = prefs(context).edit().putStringSet("excluded", folders).apply()
}

/** Reads the music already on the phone from MediaStore. */
object LocalMusic {
    suspend fun scan(context: Context): List<LocalSong> =
        withContext(Dispatchers.IO) {
            val collection =
                if (Build.VERSION.SDK_INT >= 29) {
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                } else {
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
            val cols =
                arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.ALBUM_ID,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.DATE_ADDED,
                    MediaStore.Audio.Media.YEAR,
                ) + (if (Build.VERSION.SDK_INT >= 30) arrayOf(MediaStore.Audio.Media.GENRE) else emptyArray()) +
                (if (Build.VERSION.SDK_INT >= 29) arrayOf(MediaStore.Audio.Media.RELATIVE_PATH) else arrayOf(MediaStore.Audio.Media.DATA))
            // Real songs only: no ringtones, notification sounds, or clips under 30 seconds.
            val where = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 30000"
            val out = ArrayList<LocalSong>()
            runCatching {
                context.contentResolver.query(collection, cols, where, null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { c ->
                    val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                    val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val iAdded = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                    val iYear = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                    val iGenre = if (Build.VERSION.SDK_INT >= 30) c.getColumnIndex(MediaStore.Audio.Media.GENRE) else -1
                    val iPath = c.getColumnIndex(if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.RELATIVE_PATH else MediaStore.Audio.Media.DATA)
                    while (c.moveToNext()) {
                        val id = c.getLong(iId)
                        val artist = c.getString(iArtist)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Unknown artist"
                        out +=
                            LocalSong(
                                id = id,
                                title = c.getString(iTitle) ?: "Unknown",
                                artist = artist,
                                album = c.getString(iAlbum).orEmpty(),
                                albumId = c.getLong(iAlbumId),
                                durationMs = c.getLong(iDur),
                                addedAt = c.getLong(iAdded),
                                uri = ContentUris.withAppendedId(collection, id).toString(),
                                genre = if (iGenre >= 0) c.getString(iGenre)?.takeIf { it.isNotBlank() } else null,
                                year = c.getInt(iYear),
                                folder = folderOf(if (iPath >= 0) c.getString(iPath) else null),
                            )
                    }
                }
            }
            out
        }
}

/** Hands phone files to the app's normal player. */
class LocalPlayer(
    private val context: Context,
    private val handler: MediaPlayerHandler,
) {
    suspend fun play(
        songs: List<LocalSong>,
        index: Int,
        queueName: String = "My music",
    ) {
        val chosen = songs.getOrNull(index) ?: return
        PlayStats(context).record(chosen.id)
        songs.forEach { RadioStreams.register(it.trackId, it.uri) }
        val tracks = songs.map { it.toTrack(FallbackArt.uriFor(context, it.id.toString())) }
        org.simpmusic.radio.data.RadioNow.station.value = null
        handler.setQueueData(
            QueueData.Data(
                listTracks = tracks,
                firstPlayedTrack = tracks[index],
                playlistId = "local_" + queueName.lowercase().replace(' ', '_'),
                playlistName = queueName,
                playlistType = PlaylistType.PLAYLIST,
                continuation = null,
            ),
        )
        handler.loadMediaItem(tracks[index], Config.PLAYLIST_CLICK, index)
    }
}

private fun LocalSong.toTrack(fallbackArt: String?): Track =
    Track(
        album = null,
        artists = listOf(Artist(id = null, name = artist)),
        duration = null,
        durationSeconds = (durationMs / 1000).toInt(),
        isAvailable = true,
        isExplicit = false,
        likeStatus = null,
        thumbnails = listOf(Thumbnail(height = 512, url = albumArt, width = 512)),
        title = title,
        videoId = trackId,
        videoType = null,
        category = null,
        feedbackTokens = null,
        resultType = null,
    )
