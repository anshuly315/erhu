package com.maxrave.domain.mediaservice.automix

/**
 * Per-pair and per-collection crossfade overrides ("Transition Studio").
 *
 * A value is a crossfade length in milliseconds, or [OFF] for no crossfade at all. A pair beats a
 * collection, a collection beats the global setting (no entry = global). Read by both crossfade
 * trigger paths through [resolve], so a guard can never exist on only one of them.
 */
object TransitionOverrides {
    const val OFF = 0

    @Volatile private var pairs: Map<String, Int> = emptyMap()

    @Volatile private var collections: Map<String, Int> = emptyMap()

    @Volatile private var membership: Map<String, String> = emptyMap()

    /** Called after any change so the owner can persist it. */
    @Volatile var onChanged: (() -> Unit)? = null

    fun pairKey(
        outgoingId: String,
        incomingId: String,
    ) = "$outgoingId|$incomingId"

    fun load(
        pairs: Map<String, Int>,
        collections: Map<String, Int>,
    ) {
        this.pairs = pairs
        this.collections = collections
    }

    fun snapshotPairs(): Map<String, Int> = pairs

    fun snapshotCollections(): Map<String, Int> = collections

    /** Tells the player which collection (album) each track belongs to. */
    fun registerMembership(trackToCollection: Map<String, String>) {
        membership = membership + trackToCollection
    }

    fun collectionOf(trackId: String): String? = membership[trackId]

    fun setPair(
        outgoingId: String,
        incomingId: String,
        ms: Int?,
    ) {
        val key = pairKey(outgoingId, incomingId)
        pairs = if (ms == null) pairs - key else pairs + (key to ms)
        onChanged?.invoke()
    }

    fun setCollection(
        collection: String,
        ms: Int?,
    ) {
        collections = if (ms == null) collections - collection else collections + (collection to ms)
        onChanged?.invoke()
    }

    fun pairOverride(
        outgoingId: String,
        incomingId: String,
    ): Int? = pairs[pairKey(outgoingId, incomingId)]

    fun collectionOverride(collection: String): Int? = collections[collection]

    /** The override that applies to this transition, or null to use the global setting. */
    fun resolve(
        outgoingId: String,
        incomingId: String,
    ): Int? {
        pairs[pairKey(outgoingId, incomingId)]?.let { return it }
        val out = membership[outgoingId] ?: return null
        // A collection setting covers fades inside it only, never the edges.
        if (membership[incomingId] != out) return null
        return collections[out]
    }

    fun clearAll() {
        pairs = emptyMap()
        collections = emptyMap()
        onChanged?.invoke()
    }
}
