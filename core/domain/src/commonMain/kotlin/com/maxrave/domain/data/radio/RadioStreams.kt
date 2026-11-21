package com.maxrave.domain.data.radio

/**
 * Radio stations are queued as ordinary tracks whose videoId is "radio:<stationuuid>".
 * This registry maps that id to the station's live stream URL so the player layer can
 * build a MediaItem that points straight at the stream instead of at a YouTube video id.
 *
 * Copy-on-write so reads from the player thread never lock.
 */
object RadioStreams {
    const val PREFIX = "radio:"

    private var streams: Map<String, String> = emptyMap()

    fun isRadio(mediaId: String?): Boolean = mediaId != null && mediaId.startsWith(PREFIX)

    /** Files from the phone are queued as "radio:local-<id>" so they share the direct-URI path, but they are not live. */
    fun isLocal(mediaId: String?): Boolean = mediaId != null && mediaId.startsWith(PREFIX + "local-")

    fun idFor(stationUuid: String): String = PREFIX + stationUuid

    fun register(radioId: String, streamUrl: String) {
        streams = streams + (radioId to streamUrl)
    }

    fun registerAll(items: Map<String, String>) {
        streams = streams + items
    }

    fun urlFor(radioId: String): String? = streams[radioId]
}
