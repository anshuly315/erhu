package com.erhu.erhu.feature.library

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.erhu.erhu.core.designsystem.foundation.Motion
import com.erhu.erhu.core.model.Song
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

private const val QUEUE_LANDING_THRESHOLD = 0.1f
private const val QUEUE_HANDOFF_TIMEOUT = 1000L

internal typealias QueueDragMotion = ReorderDragMotion<Song>

/** Shared reorder motion: hold the old rows until landing and the persisted update agree. */
@Stable
internal class ReorderDragMotion<T>(
    private val rowHeight: Float,
    private val itemHeights: () -> List<Float> = { emptyList() },
) {
    var rows by mutableStateOf<List<T>?>(null)
        private set
    var index by mutableStateOf<Int?>(null)
        private set
    var offset by mutableFloatStateOf(0f)
        private set
    var settling by mutableStateOf(false)
        private set
    var handoff by mutableIntStateOf(0)
        private set
    private var generation = 0
    private var heights = emptyList<Float>()
    private var landings = emptyList<Float>()

    fun begin(
        queue: List<T>,
        at: Int,
    ) {
        if (index != null || at !in queue.indices) return
        generation++
        rows = queue.toList()
        heights = itemHeights().takeIf { sizes -> sizes.size == queue.size && sizes.all { it > 0f && it.isFinite() } } ?: emptyList()
        val starts = heights.runningFold(0f) { total, height -> total + height }
        landings =
            heights.indices.map { to ->
                if (to > at) starts[to + 1] - starts[at + 1] else starts[to] - starts[at]
            }
        index = at
        offset = 0f
    }

    fun drag(
        at: Int,
        delta: Float,
    ) {
        if (index == at && !settling && delta.isFinite()) offset += delta
    }

    fun queueChanged(queue: List<T>) {
        if (!settling && rows != null && rows != queue) reset()
    }

    suspend fun finish(
        commit: Boolean,
        latestQueue: () -> List<T>,
        reorder: suspend (Int, Int) -> Boolean,
    ) {
        val from = index
        val original = rows
        if (from == null || original == null || settling) return
        val session = generation
        settling = true
        val to = if (commit) dropIndex() else from
        try {
            Animatable(offset).animateTo(landingOffset(to), Motion.gestureSettle(QUEUE_LANDING_THRESHOLD)) {
                if (generation == session) offset = value
            }
            val canCommit = generation == session && commit && from != to
            if (canCommit && latestQueue() == original && reorder(from, to)) {
                val expected = original.toMutableList().apply { add(to, removeAt(from)) }
                // Keep the landed visual in place while the controller's queue reaches Compose.
                withTimeoutOrNull(QUEUE_HANDOFF_TIMEOUT) {
                    snapshotFlow { latestQueue() }.first { it == expected || it != original }
                }
                handoff++
            }
        } finally {
            if (generation == session) reset()
        }
    }

    fun dropIndex(): Int {
        val from = index ?: return 0
        val last = rows?.lastIndex ?: from
        return if (heights.isEmpty()) {
            queueDropIndex(from, offset, rowHeight, last)
        } else {
            val found = landings.binarySearch(offset)
            if (found >= 0) {
                found
            } else {
                val upper = (-found - 1).coerceIn(0, last)
                val lower = (upper - 1).coerceAtLeast(0)
                if (kotlin.math.abs(landings[lower] - offset) <= kotlin.math.abs(landings[upper] - offset)) lower else upper
            }
        }
    }

    private fun landingOffset(to: Int): Float {
        val from = index ?: return 0f
        return landings.getOrNull(to) ?: ((to - from) * rowHeight)
    }

    fun displacement(at: Int): Float {
        val from = index ?: return 0f
        val to = dropIndex()
        val height = heights.getOrNull(from) ?: rowHeight
        return when {
            from < to && at in (from + 1)..to -> -height
            from > to && at in to..<from -> height
            else -> 0f
        }
    }

    private fun reset() {
        generation++
        rows = null
        index = null
        offset = 0f
        settling = false
    }
}

internal fun queueDropIndex(
    from: Int,
    offset: Float,
    rowHeight: Float,
    last: Int,
): Int = (from + (offset / rowHeight).roundToInt()).coerceIn(0, last)
