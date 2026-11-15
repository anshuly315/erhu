package com.erhu.erhu.core.playback.browse

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import com.erhu.erhu.core.common.permission.PermissionManager
import com.erhu.erhu.core.database.repository.LibraryRepository
import com.erhu.erhu.core.database.repository.PlaylistRepository
import com.erhu.erhu.core.model.CollectionRef
import com.erhu.erhu.core.model.QueueOrigin
import com.erhu.erhu.core.model.Song
import com.erhu.erhu.core.model.TrackOccurrence
import com.erhu.erhu.core.playback.toMediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Adapts the existing library to external media browsers; it never owns or mutates playback. */
@UnstableApi
internal class CarMediaLibrary
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val library: LibraryRepository,
        private val playlists: PlaylistRepository,
        private val permissions: PermissionManager,
    ) {
        data class Selection(
            val items: List<MediaItem>,
            val startIndex: Int,
        )

        private data class Track(
            val song: Song,
            val parent: String,
            val position: Int? = null,
            val origin: QueueOrigin? = null,
        ) {
            val browseId: String get() = MediaId.track(parent, song.id, position)
        }

        fun hasAudioPermission(): Boolean = permissions.hasAudioPermission()

        suspend fun children(
            parent: String,
            page: Int,
            pageSize: Int,
        ): List<MediaItem>? =
            when (parent) {
                MediaId.ROOT -> MediaId.ROOT_CHILDREN.page(page, pageSize).map { BrowseTree.getCategoryFolder(it, context) }
                MediaId.ALBUMS ->
                    library.getAllAlbums().first().page(page, pageSize).map {
                        withArtwork(BrowseTree.albumFolder(it), "album", it.id)
                    }
                MediaId.ARTISTS ->
                    library.getAllArtists().first().page(page, pageSize).map {
                        withArtwork(BrowseTree.artistFolder(it), "artist", it.id)
                    }
                MediaId.PLAYLISTS ->
                    playlists.observePlaylists().first().page(page, pageSize).map {
                        withArtwork(BrowseTree.playlistFolder(it), "playlist", it.id)
                    }
                else -> tracks(parent)?.page(page, pageSize)?.map { browseItem(it) }
            }

        suspend fun item(id: String): MediaItem? =
            when {
                id == MediaId.ROOT -> BrowseTree.getRoot()
                id in MediaId.ROOT_CHILDREN -> BrowseTree.getCategoryFolder(id, context)
                id.startsWith(MediaId.ALBUM_PREFIX) ->
                    id.removePrefix(MediaId.ALBUM_PREFIX).toLongOrNull()?.let { albumId ->
                        library.getAlbumById(albumId).first()?.let { withArtwork(BrowseTree.albumFolder(it), "album", it.id) }
                    }
                id.startsWith(MediaId.ARTIST_PREFIX) ->
                    id.removePrefix(MediaId.ARTIST_PREFIX).toLongOrNull()?.let { artistId ->
                        library.getArtistById(artistId).first()?.let { withArtwork(BrowseTree.artistFolder(it), "artist", it.id) }
                    }
                id.startsWith(MediaId.PLAYLIST_PREFIX) ->
                    id.removePrefix(MediaId.PLAYLIST_PREFIX).toLongOrNull()?.let { playlistId ->
                        playlists.observePlaylist(playlistId).first()?.let { withArtwork(BrowseTree.playlistFolder(it), "playlist", it.id) }
                    }
                else -> resolveTrack(id)?.let { browseItem(it).buildUpon().setMediaId(id).build() }
            }

        suspend fun search(
            query: String,
            page: Int,
            pageSize: Int,
        ): List<MediaItem> =
            if (query.isBlank()) {
                emptyList()
            } else {
                tracks(MediaId.SEARCH_PREFIX + query.trim()).orEmpty().page(page, pageSize).map { browseItem(it) }
            }

        suspend fun searchCount(query: String): Int = if (query.isBlank()) 0 else library.searchSongs(query.trim()).first().size

        /** A single browse/voice selection loads its collection, beginning at the requested occurrence. */
        @Suppress("ReturnCount")
        suspend fun selection(request: MediaItem): Selection? {
            val query = request.requestMetadata.searchQuery
            val target = MediaId.parseTrack(request.mediaId)
            val legacySongId = request.mediaId.toLongOrNull()
            val parent =
                when {
                    query != null -> if (query.isBlank()) MediaId.SONGS else MediaId.SEARCH_PREFIX + query.trim()
                    target != null -> target.parentId
                    legacySongId != null -> MediaId.SONGS
                    else -> request.mediaId
                }
            val tracks = tracks(parent) ?: return null
            if (tracks.isEmpty()) return null
            val index =
                when {
                    target != null -> tracks.indexOfFirst { it.song.id == target.songId && it.position == target.position }
                    legacySongId != null && query == null -> tracks.indexOfFirst { it.song.id == legacySongId }
                    else -> 0
                }
            return if (index < 0) null else Selection(tracks.map { playbackItem(it) }, index)
        }

        /** Queue additions resolve one item only; they must never append a whole album/playlist. */
        suspend fun playable(request: MediaItem): MediaItem? = resolveTrack(request.mediaId)?.let { playbackItem(it) }

        private suspend fun resolveTrack(id: String): Track? {
            val target = MediaId.parseTrack(id)
            return if (target != null) {
                tracks(target.parentId)?.find { it.song.id == target.songId && it.position == target.position }
            } else {
                id.toLongOrNull()?.let { library.getSongById(it).first() }?.let { Track(it, MediaId.SONGS) }
            }
        }

        private suspend fun tracks(parent: String): List<Track>? =
            when {
                parent == MediaId.SONGS -> library.getAllSongs().first().map { Track(it, parent) }
                parent.startsWith(MediaId.SEARCH_PREFIX) ->
                    library.searchSongs(parent.removePrefix(MediaId.SEARCH_PREFIX)).first().map {
                        Track(it, parent)
                    }
                parent.startsWith(MediaId.ALBUM_PREFIX) ->
                    parent.removePrefix(MediaId.ALBUM_PREFIX).toLongOrNull()?.let { id ->
                        library.getAlbumById(id).first()?.let {
                            library.getSongsByAlbumId(id).first().map { song ->
                                Track(song, parent, origin = QueueOrigin(CollectionRef.album(id), TrackOccurrence(song.id)))
                            }
                        }
                    }
                parent.startsWith(MediaId.ARTIST_PREFIX) ->
                    parent.removePrefix(MediaId.ARTIST_PREFIX).toLongOrNull()?.let { id ->
                        library.getArtistById(id).first()?.let {
                            library.getSongsByArtistId(id).first().map { song ->
                                Track(song, parent)
                            }
                        }
                    }
                parent.startsWith(MediaId.PLAYLIST_PREFIX) ->
                    parent.removePrefix(MediaId.PLAYLIST_PREFIX).toLongOrNull()?.let { id ->
                        playlists.observePlaylist(id).first()?.let {
                            playlists.observePlaylistEntries(id).first().map { entry ->
                                Track(
                                    entry.song,
                                    parent,
                                    entry.position,
                                    QueueOrigin(CollectionRef.playlist(id), TrackOccurrence(entry.song.id, entry.position)),
                                )
                            }
                        }
                    }
                else -> null
            }

        private fun browseItem(track: Track): MediaItem = playbackItem(track).buildUpon().setMediaId(track.browseId).build()

        private fun playbackItem(track: Track): MediaItem {
            val item = track.song.toMediaItem(track.origin)
            val metadata =
                item.mediaMetadata
                    .buildUpon()
                    .setDurationMs(track.song.durationMs.coerceAtLeast(0))
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .setArtworkUri(CarArtworkProvider.uri(context, "song", track.song.id))
                    .build()
            return item.buildUpon().setMediaMetadata(metadata).build()
        }

        private fun withArtwork(
            item: MediaItem,
            kind: String,
            id: Long,
        ): MediaItem =
            item
                .buildUpon()
                .setMediaMetadata(
                    item.mediaMetadata
                        .buildUpon()
                        .setArtworkUri(CarArtworkProvider.uri(context, kind, id))
                        .build(),
                ).build()

        /** Long arithmetic handles legacy hosts requesting Integer.MAX_VALUE without overflowing. */
        private fun <T> List<T>.page(
            page: Int,
            pageSize: Int,
        ): List<T> {
            val start = page.toLong() * pageSize.toLong()
            return if (page < 0 || pageSize <= 0 || start >= size) {
                emptyList()
            } else {
                subList(start.toInt(), minOf(size.toLong(), start + pageSize).toInt())
            }
        }
    }
