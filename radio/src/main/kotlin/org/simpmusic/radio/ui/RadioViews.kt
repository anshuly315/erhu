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


internal const val LEVEL_MAX = 5

/** Remembers the pinch level across pages and restarts. */
@Composable
internal fun rememberViewMode(store: RadioStore): androidx.compose.runtime.MutableIntState =
    remember { mutableIntStateOf(store.viewMode.coerceIn(0, LEVEL_MAX)) }

/**
 * One collection, six levels of detail, driven by pinching: 0 two text columns, 1 text list,
 * 2 list with small art, 3 list with large art, 4 grid of three, 5 grid of two with big art.
 * Pinch in for denser, out for larger; every step can repeat within one gesture. The listener
 * watches without consuming, so scrolling and taps still work.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun <T> PinchCollection(
    items: List<T>,
    level: androidx.compose.runtime.MutableIntState,
    bottomPadding: Dp,
    onLevel: (Int) -> Unit,
    title: (T) -> String,
    subtitle: (T) -> String,
    art: @Composable (T, Modifier, Dp) -> Unit,
    trailing: @Composable (T) -> Unit = {},
    menu: @Composable (T, Boolean, () -> Unit) -> Unit = { _, _, _ -> },
    onClick: (Int) -> Unit,
) {
    val lv = level.intValue.coerceIn(0, LEVEL_MAX)
    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var zoom = 1f
                do {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    if (ev.changes.size >= 2) {
                        zoom *= ev.calculateZoom()
                        if (zoom < 0.85f) { onLevel((level.intValue + 1).coerceAtMost(LEVEL_MAX)); zoom = 1f }
                        else if (zoom > 1.18f) { onLevel((level.intValue - 1).coerceAtLeast(0)); zoom = 1f }
                    }
                } while (ev.changes.any { it.pressed })
            }
        },
    ) {
        AnimatedContent(
            targetState = lv,
            transitionSpec = {
                val bigger = targetState > initialState
                (fadeIn(tween(280)) + scaleIn(tween(280), initialScale = if (bigger) 0.9f else 1.1f)) togetherWith
                    (fadeOut(tween(200)) + scaleOut(tween(200), targetScale = if (bigger) 1.1f else 0.9f))
            },
            label = "pinchLevel",
        ) { l ->
            @Composable
            fun cell(i: Int, t: T, artSize: Dp?, corner: Dp, sub: Boolean, row: Boolean) {
                var open by remember { mutableStateOf(false) }
                val click = Modifier.combinedClickable(onClick = { onClick(i) }, onLongClick = { open = true })
                Box {
                    if (row) {
                        Row(Modifier.fillMaxWidth().then(click).padding(horizontal = 16.dp, vertical = if (artSize == null) 6.dp else 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (artSize != null) { art(t, Modifier.size(artSize), corner); Spacer(Modifier.width(12.dp)) }
                            Column(Modifier.weight(1f)) {
                                Text(title(t), color = Ink, fontSize = if (artSize == null) 13.sp else 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (sub) Text(subtitle(t), color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            trailing(t)
                        }
                    } else {
                        Column(Modifier.fillMaxWidth().then(click)) {
                            art(t, Modifier.fillMaxWidth().aspectRatio(1f), corner)
                            Spacer(Modifier.height(6.dp))
                            Text(title(t), color = Ink, fontSize = if (l == 4) 12.sp else 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(subtitle(t), color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    menu(t, open) { open = false }
                }
            }
            when (l) {
                0 ->
                    LazyVerticalGrid(GridCells.Fixed(2), contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding)) {
                        itemsIndexed(items) { i, t -> cell(i, t, null, 0.dp, false, true) }
                    }
                1 ->
                    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding)) {
                        itemsIndexed(items) { i, t -> cell(i, t, null, 0.dp, true, true) }
                    }
                2 ->
                    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding)) {
                        itemsIndexed(items) { i, t -> cell(i, t, 44.dp, 10.dp, true, true) }
                    }
                3 ->
                    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding)) {
                        itemsIndexed(items) { i, t -> cell(i, t, 84.dp, 14.dp, true, true) }
                    }
                else ->
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(if (l == 4) 3 else 2),
                        contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding, start = 12.dp, end = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(items) { i, t -> cell(i, t, null, if (l == 4) 14.dp else 20.dp, true, false) }
                    }
            }
        }
    }
}

/** Stations in the pinch layout, with the station long-press menu. */
@Composable
internal fun StationCollection(
    stations: List<RadioStation>,
    store: RadioStore,
    modeState: androidx.compose.runtime.MutableIntState,
    bottomPadding: Dp,
    onPlay: (Int) -> Unit,
) {
    val context = LocalContext.current
    val favs by store.favourites.collectAsState()
    PinchCollection(
        items = stations,
        level = modeState,
        bottomPadding = bottomPadding,
        onLevel = { modeState.intValue = it; store.viewMode = it },
        title = { it.name },
        subtitle = { listOf(it.subtitle, it.quality).filter { x -> x.isNotBlank() }.joinToString(" · ") },
        art = { s, m, c -> StationArt(s, m, c) },
        trailing = { s ->
            val fav = favs.any { it.uuid == s.uuid }
            Text(
                if (fav) "♥" else "♡",
                color = if (fav) Color(0xFFFF8FB1) else Muted,
                fontSize = 22.sp,
                modifier = Modifier.clip(CircleShape).combinedClickable(onClick = { store.toggleFavourite(s) }).padding(8.dp),
            )
        },
        menu = { s, open, close ->
            val fav = favs.any { it.uuid == s.uuid }
            DropdownMenu(expanded = open, onDismissRequest = close) {
                DropdownMenuItem(text = { Text(if (fav) "Remove from favourites" else "Add to favourites") }, onClick = { store.toggleFavourite(s); close() })
                DropdownMenuItem(text = { Text("Share") }, onClick = { shareStation(context, s); close() })
                DropdownMenuItem(text = { Text("Hide this station") }, onClick = { store.hide(s.uuid); close() })
            }
        },
        onClick = onPlay,
    )
}
