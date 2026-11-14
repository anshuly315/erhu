package com.erhu.erhu.feature.library

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.erhu.erhu.core.common.locale.LocaleFormatters
import com.erhu.erhu.core.designsystem.component.LocalBottomChromeHeight
import com.erhu.erhu.core.designsystem.foundation.ArtworkSize
import com.erhu.erhu.core.designsystem.foundation.Motion
import com.erhu.erhu.core.designsystem.foundation.TextStyles
import com.erhu.erhu.core.model.PlaylistEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.erhu.erhu.core.common.R as CommonR

/** Gesture ownership lives on the list, so scrolling cannot dispose the active drag detector. */
@Stable
internal class PlaylistReorderState(
    val listState: LazyListState,
    private val latest: State<List<PlaylistEntry>>,
    private val scope: CoroutineScope,
    private val reorder: State<suspend (List<PlaylistEntry>, Int, Int) -> Boolean>,
    rowHeight: Float,
) {
    private val heights = mutableMapOf<Int, Float>()
    val motion =
        ReorderDragMotion<PlaylistEntry>(rowHeight) {
            // Unseen rows use the measured common height, including transition connectors.
            // A hard-coded dp estimate drifts from actual row positions during long auto-scrolls.
            val commonHeight =
                heights.values
                    .groupingBy { it }
                    .eachCount()
                    .maxByOrNull { it.value }
                    ?.key ?: rowHeight
            latest.value.map { heights[it.position] ?: commonHeight }
        }
    val entries: List<PlaylistEntry> get() = motion.rows ?: latest.value
    var armedPosition by mutableStateOf<Int?>(null)
    var pointerY by mutableFloatStateOf(0f)
    var initialTop by mutableFloatStateOf(0f)
    var scrolled by mutableFloatStateOf(0f)

    fun measure(
        position: Int,
        height: Int,
    ) {
        if (height > 0) heights[position] = height.toFloat()
    }

    fun begin(
        index: Int,
        y: Float,
    ) {
        if (motion.index != null) return
        armedPosition = latest.value.getOrNull(index)?.position
        pointerY = y
        val layout = listState.layoutInfo
        initialTop = (layout.visibleItemsInfo.firstOrNull { it.key == armedPosition }?.offset ?: 0) - layout.viewportStartOffset.toFloat()
        scrolled = 0f
        motion.begin(latest.value, index)
    }

    fun finish(commit: Boolean) {
        val snapshot = motion.rows ?: return
        val moved = motion.index != motion.dropIndex()
        scope.launch {
            try {
                motion.finish(commit, { latest.value }) { from, to -> reorder.value(snapshot, from, to) }
            } finally {
                if (moved || !commit) armedPosition = null
            }
        }
    }
}

@Composable
internal fun rememberPlaylistReorderState(
    playlistId: Long,
    entries: List<PlaylistEntry>,
    hasTransitionConnectors: Boolean,
    reorder: suspend (List<PlaylistEntry>, Int, Int) -> Boolean,
): PlaylistReorderState {
    val listState = rememberLazyListState()
    val latest = rememberUpdatedState(entries)
    val latestReorder = rememberUpdatedState(reorder)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val state =
        remember(playlistId, density, hasTransitionConnectors) {
            PlaylistReorderState(listState, latest, scope, latestReorder, with(density) { 72.dp.toPx() })
        }
    LaunchedEffect(state, entries) {
        state.motion.queueChanged(entries)
        if (entries.none { it.position == state.armedPosition }) state.armedPosition = null
    }
    val edge = with(density) { 56.dp.toPx() }
    val maxStep = with(density) { 14.dp.toPx() }
    val bottomChrome = with(density) { LocalBottomChromeHeight.current.toPx() }
    LaunchedEffect(state, state.motion.index, state.motion.settling, bottomChrome) {
        val index = state.motion.index ?: return@LaunchedEffect
        while (!state.motion.settling) {
            val layout = listState.layoutInfo
            val top = layout.viewportStartOffset + layout.beforeContentPadding + edge
            val bottom = layout.viewportEndOffset - bottomChrome - edge
            val amount =
                when {
                    state.pointerY < top -> -maxStep * ((top - state.pointerY) / edge).coerceIn(0f, 1f)
                    state.pointerY > bottom -> maxStep * ((state.pointerY - bottom) / edge).coerceIn(0f, 1f)
                    else -> 0f
                }
            if (amount != 0f) {
                val consumed = listState.scrollBy(amount)
                state.scrolled += consumed
                state.motion.drag(index, consumed)
            }
            delay(16)
        }
    }
    return state
}

/** A long press anywhere on a track, or a direct drag on its already revealed leading handle. */
internal fun Modifier.playlistReorderGestures(
    state: PlaylistReorderState,
    handleStart: Float,
    handleEnd: Float,
    rtl: Boolean,
): Modifier =
    pointerInput(state, handleStart, handleEnd, rtl) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (state.motion.index != null) return@awaitEachGesture
            val layout = state.listState.layoutInfo
            val listY = down.position.y + layout.viewportStartOffset
            val item =
                state.listState.layoutInfo.visibleItemsInfo.firstOrNull {
                    it.key is Int && listY >= it.offset && listY < it.offset + it.size
                } ?: return@awaitEachGesture
            val index = state.entries.indexOfFirst { it.position == item.key }
            if (index < 0) return@awaitEachGesture
            val leadingX = if (rtl) size.width - down.position.x else down.position.x
            val handle = state.armedPosition == item.key && leadingX in handleStart..handleEnd
            var initialDelta = 0f
            val start =
                if (handle) {
                    awaitPlaylistHandleDrag(down) { initialDelta = it }
                } else {
                    awaitLongPressOrCancellation(down.id)
                }
            if (start != null) {
                state.begin(index, start.position.y + layout.viewportStartOffset)
                state.motion.drag(index, initialDelta)
                var completed = false
                try {
                    completed = dragPlaylistEntry(state, index, start)
                } finally {
                    state.finish(completed)
                }
            }
        }
    }

@Composable
internal fun playlistReorderRowModifier(
    state: PlaylistReorderState,
    index: Int,
): Modifier {
    val motion = state.motion
    val dragging = motion.index == index
    val displacement by key(motion.handoff) {
        animateFloatAsState(motion.displacement(index), spring(stiffness = Spring.StiffnessMediumLow), label = "playlistDisplacement")
    }
    val lift by animateFloatAsState(if (dragging && !motion.settling) 1f else 0f, Motion.responsive(), label = "playlistPickup")
    val position = state.entries[index].position
    return Modifier
        .onSizeChanged { state.measure(position, it.height) }
        .zIndex(if (dragging) 2f else 1f)
        .graphicsLayer {
            translationY = if (dragging) motion.offset else displacement
            scaleX = 1f + 0.025f * lift
            scaleY = scaleX
            shadowElevation = 8.dp.toPx() * lift
        }
}

@Composable
internal fun PlaylistTrackNumber(
    state: PlaylistReorderState,
    index: Int,
) {
    val armed = state.armedPosition == state.entries[index].position
    Box(Modifier.size(ArtworkSize.list), contentAlignment = Alignment.Center) {
        Crossfade(targetState = armed, label = "playlistHandle") { showHandle ->
            if (showHandle) {
                Icon(Icons.Default.DragHandle, stringResource(CommonR.string.queue_cd_reorder), tint = MaterialTheme.colorScheme.primary)
            } else {
                Text(
                    LocaleFormatters.formatInteger(index + 1),
                    style = TextStyles.itemTitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** An armed handle owns vertical movement before the list's normal scroll detector. */
private suspend fun AwaitPointerEventScope.awaitPlaylistHandleDrag(
    down: PointerInputChange,
    onSlop: (Float) -> Unit,
): PointerInputChange? {
    var distance = 0f
    var result: PointerInputChange? = null
    var pressed = true
    while (result == null && pressed) {
        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
        pressed = change?.pressed == true
        if (change != null && pressed) {
            distance += change.positionChange().y
            if (kotlin.math.abs(distance) > viewConfiguration.touchSlop) {
                change.consume()
                onSlop(distance - kotlin.math.sign(distance) * viewConfiguration.touchSlop)
                result = change
            }
        }
    }
    return result
}

/** Consume releases as well as movement, so long-press never becomes a track click. */
private suspend fun AwaitPointerEventScope.dragPlaylistEntry(
    state: PlaylistReorderState,
    index: Int,
    start: PointerInputChange,
): Boolean {
    var pressed = true
    var completed = false
    while (pressed) {
        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == start.id }
        if (change == null) {
            pressed = false
        } else {
            state.pointerY = change.position.y + state.listState.layoutInfo.viewportStartOffset
            state.motion.drag(index, change.positionChange().y)
            change.consume()
            pressed = change.pressed
            completed = !pressed
        }
    }
    return completed
}

/** Keep the floating row composed after auto-scroll takes its original slot off screen. */
@Composable
internal fun PlaylistDragOverlay(
    state: PlaylistReorderState,
    content: @Composable (Int) -> Unit,
) {
    val index = state.motion.index ?: return
    val position = state.entries[index].position
    if (state.listState.layoutInfo.visibleItemsInfo
            .any { it.key == position }
    ) {
        return
    }
    Box(Modifier.fillMaxWidth().zIndex(2f).graphicsLayer { translationY = state.initialTop - state.scrolled }) {
        Column(modifier = playlistReorderRowModifier(state, index)) { content(index) }
    }
}
