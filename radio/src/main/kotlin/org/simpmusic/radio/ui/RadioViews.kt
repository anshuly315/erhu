package org.simpmusic.radio.ui

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.simpmusic.radio.data.RadioStation
import org.simpmusic.radio.data.RadioStore

/** Station list layouts, from the largest to the densest. Pinch in to go denser, pinch out to go larger. */
internal enum class ViewMode(val label: String) {
    Cards("Cards"),
    List("List"),
    Grid("Grid"),
    Compact("Compact"),
}

internal fun shareStation(
    context: Context,
    s: RadioStation,
) {
    val link = s.homepage.ifBlank { s.streamUrl }
    val send =
        Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "Listening to ${s.name} on SimpMusic Radio\n$link")
    context.startActivity(Intent.createChooser(send, "Share station").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** The view-mode switch that sits in page headers. */
@Composable
internal fun ViewModeButton(
    mode: ViewMode,
    onChange: (ViewMode) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Text(
            when (mode) {
                ViewMode.Cards -> "▭"
                ViewMode.List -> "☰"
                ViewMode.Grid -> "▦"
                ViewMode.Compact -> "▩"
            },
            color = Ink,
            fontSize = 22.sp,
            modifier = Modifier.clip(CircleShape).combinedClickable(onClick = { open = true }).padding(10.dp),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ViewMode.values().forEach { m ->
                DropdownMenuItem(text = { Text(if (m == mode) "✓ ${m.label}" else m.label) }, onClick = { onChange(m); open = false })
            }
        }
    }
}

/** Remembers the chosen layout across pages and restarts. */
@Composable
internal fun rememberViewMode(store: RadioStore): androidx.compose.runtime.MutableIntState =
    remember { mutableIntStateOf(store.viewMode) }

/**
 * Stations in one of four layouts. Two fingers pinching in or out switch layout, with a fade and
 * scale between them. The pinch listener watches events without consuming them, so scrolling and
 * taps keep working.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun StationCollection(
    stations: List<RadioStation>,
    store: RadioStore,
    modeState: androidx.compose.runtime.MutableIntState,
    bottomPadding: Dp,
    onPlay: (Int) -> Unit,
) {
    val favs by store.favourites.collectAsState()
    val mode = ViewMode.values()[modeState.intValue.coerceIn(0, 3)]
    fun set(i: Int) {
        val n = i.coerceIn(0, 3)
        if (n != modeState.intValue) {
            modeState.intValue = n
            store.viewMode = n
        }
    }
    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var zoom = 1f
                var done = false
                do {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    if (ev.changes.size >= 2 && !done) {
                        zoom *= ev.calculateZoom()
                        if (zoom < 0.8f) { set(modeState.intValue + 1); done = true }
                        else if (zoom > 1.25f) { set(modeState.intValue - 1); done = true }
                    }
                } while (ev.changes.any { it.pressed })
            }
        },
    ) {
        AnimatedContent(
            targetState = mode,
            transitionSpec = {
                (fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.92f)) togetherWith
                    (fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 1.06f))
            },
            label = "stationView",
        ) { m ->
            when (m) {
                ViewMode.Cards ->
                    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding, start = 20.dp, end = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        itemsIndexed(stations) { i, s -> StationItem(m, s, favs.any { it.uuid == s.uuid }, store) { onPlay(i) } }
                    }
                ViewMode.List ->
                    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding)) {
                        itemsIndexed(stations) { i, s -> StationItem(m, s, favs.any { it.uuid == s.uuid }, store) { onPlay(i) } }
                    }
                ViewMode.Grid, ViewMode.Compact ->
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(if (m == ViewMode.Grid) 2 else 3),
                        contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding, start = 16.dp, end = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        itemsIndexed(stations) { i, s -> StationItem(m, s, favs.any { it.uuid == s.uuid }, store) { onPlay(i) } }
                    }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StationItem(
    mode: ViewMode,
    s: RadioStation,
    fav: Boolean,
    store: RadioStore,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val click = Modifier.combinedClickable(onClick = onClick, onLongClick = { menu = true })
    Box {
        when (mode) {
            ViewMode.List ->
                Row(Modifier.fillMaxWidth().then(click).padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StationArt(s, Modifier.size(52.dp), 14.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.name, color = Ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOf(s.subtitle, s.quality).filter { it.isNotBlank() }.joinToString(" · "), color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        if (fav) "♥" else "♡",
                        color = if (fav) Color(0xFFFF8FB1) else Muted,
                        fontSize = 24.sp,
                        modifier = Modifier.clip(CircleShape).combinedClickable(onClick = { store.toggleFavourite(s) }).padding(10.dp),
                    )
                }
            ViewMode.Cards ->
                Column(Modifier.fillMaxWidth().then(click)) {
                    StationArt(s, Modifier.fillMaxWidth().aspectRatio(1.7f), 22.dp)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.name, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOf(s.subtitle, s.quality).filter { it.isNotBlank() }.joinToString(" · "), color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(
                            if (fav) "♥" else "♡",
                            color = if (fav) Color(0xFFFF8FB1) else Muted,
                            fontSize = 26.sp,
                            modifier = Modifier.clip(CircleShape).combinedClickable(onClick = { store.toggleFavourite(s) }).padding(8.dp),
                        )
                    }
                }
            ViewMode.Grid, ViewMode.Compact ->
                Column(Modifier.fillMaxWidth().then(click)) {
                    StationArt(s, Modifier.fillMaxWidth().aspectRatio(1f), if (mode == ViewMode.Grid) 20.dp else 14.dp)
                    Spacer(Modifier.height(6.dp))
                    Text(s.name, color = Ink, fontSize = if (mode == ViewMode.Grid) 14.sp else 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (mode == ViewMode.Grid) Text(s.subtitle, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(if (fav) "Remove from favourites" else "Add to favourites") }, onClick = { store.toggleFavourite(s); menu = false })
            DropdownMenuItem(text = { Text("Share") }, onClick = { shareStation(context, s); menu = false })
            DropdownMenuItem(text = { Text("Hide this station") }, onClick = { store.hide(s.uuid); menu = false })
        }
    }
}
