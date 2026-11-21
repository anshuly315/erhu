package org.simpmusic.radio.data

import com.maxrave.common.Config
import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.data.model.searchResult.songs.Artist
import com.maxrave.domain.data.model.searchResult.songs.Thumbnail
import com.maxrave.domain.data.radio.RadioStreams
import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler
import com.maxrave.domain.mediaservice.handler.PlaylistType
import com.maxrave.domain.mediaservice.handler.QueueData

/**
 * Hands radio stations to the app's normal player: each station becomes a Track whose id is
 * "radio:<uuid>", and its stream URL is registered so the player layer can open it directly.
 */
class RadioPlayer(
    private val context: android.content.Context,
    private val handler: MediaPlayerHandler,
    private val api: RadioBrowserApi,
    private val store: RadioStore,
) {
    /** Re-registers saved stations so a restored queue can still play after the app restarts. */
    fun restoreRegistry() {
        val all = store.favourites.value + store.history.value
        RadioStreams.registerAll(all.associate { RadioStreams.idFor(it.uuid) to it.streamUrl })
    }

    suspend fun play(
        stations: List<RadioStation>,
        index: Int,
        queueName: String = "Radio",
    ) {
        if (stations.isEmpty()) return
        val playable = stations.filterNot { it.uuid in store.hidden.value }
        val chosen = stations.getOrNull(index) ?: return
        val pos = playable.indexOfFirst { it.uuid == chosen.uuid }.coerceAtLeast(0)
        playable.forEach { RadioStreams.register(RadioStreams.idFor(it.uuid), it.streamUrl) }
        val tracks = playable.map { it.toTrack(FallbackArt.uriFor(context, it.uuid)) }
        val first = tracks[pos]
        handler.setQueueData(
            QueueData.Data(
                listTracks = tracks,
                firstPlayedTrack = first,
                playlistId = "radio_" + queueName.lowercase().replace(' ', '_'),
                playlistName = queueName,
                playlistType = PlaylistType.PLAYLIST,
                continuation = null,
            ),
        )
        handler.loadMediaItem(first, Config.PLAYLIST_CLICK, pos)
        store.addToHistory(chosen)
        // Tells the directory it was played, and refreshes the stream URL in case it moved.
        api.registerClick(chosen.uuid)?.let { RadioStreams.register(RadioStreams.idFor(chosen.uuid), it) }
    }
}

fun RadioStation.toTrack(fallbackArt: String? = null): Track =
    Track(
        album = null,
        artists = listOf(Artist(id = null, name = subtitle.ifBlank { "Live radio" })),
        duration = null,
        durationSeconds = null,
        isAvailable = true,
        isExplicit = false,
        likeStatus = null,
        thumbnails = (favicon.takeIf { it.startsWith("http") } ?: fallbackArt)?.let { listOf(Thumbnail(height = 512, url = it, width = 512)) },
        title = name,
        videoId = RadioStreams.idFor(uuid),
        videoType = null,
        category = null,
        feedbackTokens = null,
        resultType = null,
    )
