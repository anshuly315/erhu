package com.maxrave.simpmusic.ui.screen.player.content

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil3.compose.LocalPlatformContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.maxrave.domain.mediaservice.handler.RepeatState
import com.maxrave.simpmusic.expect.ui.toImageBitmap
import com.maxrave.simpmusic.extension.LeftToRight
import com.maxrave.simpmusic.extension.elapsedLabel
import com.maxrave.simpmusic.extension.lengthLabel
import com.maxrave.simpmusic.ui.component.rememberHolderPainter
import com.maxrave.simpmusic.ui.icon.Favorite
import com.maxrave.simpmusic.ui.icon.FavoriteBorder
import com.maxrave.simpmusic.ui.icon.Add
import com.maxrave.simpmusic.ui.icon.LibraryMusic
import com.maxrave.simpmusic.ui.icon.OpenInFull
import com.maxrave.simpmusic.ui.icon.MoreVert
import com.maxrave.simpmusic.ui.icon.Pause
import com.maxrave.simpmusic.ui.icon.PlayArrow
import com.maxrave.simpmusic.ui.icon.QueueMusic
import com.maxrave.simpmusic.ui.icon.Repeat
import com.maxrave.simpmusic.ui.icon.RepeatOne
import com.maxrave.simpmusic.ui.icon.Shuffle
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.icon.SkipNext
import com.maxrave.simpmusic.ui.icon.SkipPrevious
import com.maxrave.simpmusic.ui.screen.player.content.expressive.WavySeekBar
import com.maxrave.simpmusic.viewModel.UIEvent
import kotlin.math.absoluteValue

private val Pink = Color(0xFFFF4F8B)
private val LyricMarker = Regex("<\\d+:\\d+(\\.\\d+)?>")

/**
 * The erhu player, after the original erhu app's Now Playing: one big rounded card per track with
 * a translucent info panel inside it, neighbours peeking in from both sides, an artwork-tinted
 * gradient behind, then time / queue position, seek line, transport with a round pink play button,
 * a small action row and a Lyrics card at the bottom. The swipe is the shell's artwork pager.
 *
 * [glass] = false: the page is the artwork's own gradient. [glass] = true: the blurred artwork
 * under the same layout, with glass panels.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun NowPlayingContentErhu(
    state: NowPlayingContentState,
    actions: NowPlayingContentActions,
    glass: Boolean,
) {
    val seed = state.startColor.value
    val c = state.controllerState
    val lyricLine =
        state.screenData.lyricsData
            ?.lyrics
            ?.lines
            ?.getOrNull(state.currentLyricLineIndex)
            ?.words
            ?.replace(LyricMarker, "")
            ?.trim()
            .orEmpty()

    Box(Modifier.fillMaxSize()) {
        if (glass) {
            AppleMusicArtworkBackdrop(state = state, actions = actions, seedColor = seed)
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    if (glass) {
                        listOf(Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.9f))
                    } else {
                        listOf(seed, state.endColor.value.copy(alpha = 0.85f), Color(0xFF0B0B0F))
                    },
                ),
            ),
        )
        LeftToRight {
            Column(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundButton(44.dp, Color.White.copy(alpha = 0.14f), onClick = actions.onDismiss) {
                        Icon(state.dismissIcon, contentDescription = "", tint = Color.White)
                    }
                    Text(
                        text = state.screenData.playlistName,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    RoundButton(44.dp, Color.White.copy(alpha = 0.14f), onClick = actions.onShowMoreSheet) {
                        Icon(SimpIcons.MoreVert, contentDescription = "", tint = Color.White)
                    }
                }

                Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 10.dp)) { Carousel(state, actions, glass) }

                // 0:00 · 7 / 930 · 4:10
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(state.timelineState.elapsedLabel(state.sliderValue / 100f), color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    if (state.artworkQueue.isNotEmpty()) {
                        Text("${state.currentOrderIndex + 1} / ${state.artworkQueue.size}", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
                    }
                    Text(state.timelineState.lengthLabel(), color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
                WavySeekBar(
                    progressFraction = state.sliderValue / 100f,
                    isPlaying = c.isPlaying,
                    activeColor = Pink,
                    trackColor = Color.White.copy(alpha = 0.22f),
                    thumbColor = Pink,
                    onSliderChange = actions.onSliderChange,
                    onSliderChangeFinished = actions.onSliderChangeFinished,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SmallIcon(active = c.isShuffle, onClick = { actions.onUIEvent(UIEvent.Shuffle) }) {
                        Icon(SimpIcons.Shuffle, "", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    RoundButton(52.dp, Color.Transparent, onClick = { actions.onUIEvent(UIEvent.Previous) }) {
                        Icon(SimpIcons.SkipPrevious, "", tint = Color.White.copy(alpha = if (c.isPreviousAvailable) 1f else 0.4f), modifier = Modifier.size(32.dp))
                    }
                    RoundButton(68.dp, Pink, onClick = { actions.onUIEvent(UIEvent.PlayPause) }) {
                        Icon(if (c.isPlaying) SimpIcons.Pause else SimpIcons.PlayArrow, "", tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                    RoundButton(52.dp, Color.Transparent, onClick = { actions.onUIEvent(UIEvent.Next) }) {
                        Icon(SimpIcons.SkipNext, "", tint = Color.White.copy(alpha = if (c.isNextAvailable) 1f else 0.4f), modifier = Modifier.size(32.dp))
                    }
                    SmallIcon(active = c.repeatState !is RepeatState.None, onClick = { actions.onUIEvent(UIEvent.Repeat) }) {
                        Icon(if (c.repeatState is RepeatState.One) SimpIcons.RepeatOne else SimpIcons.Repeat, "", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    SmallIcon(active = false, onClick = actions.onShowAddToPlaylist) { Icon(SimpIcons.Add, "", tint = Color.White) }
                    SmallIcon(active = false, onClick = actions.onShowInfo) { Icon(SimpIcons.LibraryMusic, "", tint = Color.White) }
                    SmallIcon(active = false, onClick = actions.onShowQueue) { Icon(SimpIcons.QueueMusic, "", tint = Color.White) }
                }

                // Lyrics card, tap to open the full page
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 12.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (glass) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.35f))
                        .clickable(onClick = actions.onShowFullscreenLyrics)
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Lyrics", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        if (lyricLine.isNotBlank()) {
                            Text(lyricLine, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Icon(SimpIcons.OpenInFull, "", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/** One big card per track with the info panel inside it; the neighbours peek in from the sides. */
@Composable
private fun Carousel(
    state: NowPlayingContentState,
    actions: NowPlayingContentActions,
    glass: Boolean,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cardW = maxWidth * 0.80f
        val cardH = minOf(maxHeight, cardW * 1.2f)
        val sidePad = (maxWidth - cardW) / 2
        HorizontalPager(
            state = state.artworkPagerState,
            contentPadding = PaddingValues(horizontal = sidePad),
            pageSpacing = 10.dp,
            beyondViewportPageCount = 1,
            verticalAlignment = Alignment.CenterVertically,
            userScrollEnabled = state.artworkQueue.isNotEmpty(),
            key = { idx -> "erhu_${state.artworkQueue.getOrNull(idx)?.videoId.orEmpty()}_$idx" },
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val offset = ((state.artworkPagerState.currentPage - page) + state.artworkPagerState.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
            val isCurrent = page == state.artworkPagerState.currentPage
            val track = state.artworkQueue.getOrNull(page)
            var url by remember(page, state.screenData.thumbnailURL, track?.videoId) {
                mutableStateOf(
                    if (isCurrent) state.screenData.thumbnailURL else track?.thumbnails?.maxByOrNull { it.width * it.height }?.url,
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(cardH)
                    .graphicsLayer {
                        val s = lerp(1f, 0.9f, offset)
                        scaleX = s
                        scaleY = s
                        alpha = lerp(1f, 0.55f, offset)
                    }.clip(RoundedCornerShape(32.dp))
                    .clickable(enabled = !isCurrent) { actions.onSeekToQueueIndex(page) },
            ) {
                AsyncImage(
                    model =
                        ImageRequest
                            .Builder(LocalPlatformContext.current)
                            .data(url)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .crossfade(300)
                            .build(),
                    contentDescription = track?.title,
                    contentScale = ContentScale.Crop,
                    placeholder = rememberHolderPainter(),
                    error = rememberHolderPainter(),
                    onSuccess = { if (isCurrent) actions.onArtworkBitmap(it.result.image.toImageBitmap()) },
                    onError = {
                        val fallback = url?.replace("maxresdefault", "hqdefault")
                        if (fallback != null && fallback != url) url = fallback
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                // The info panel sits inside the card, on the picture
                val title = if (isCurrent) state.screenData.nowPlayingTitle else track?.title.orEmpty()
                val artist = if (isCurrent) state.screenData.artistName else track?.artists?.joinToString { it.name }.orEmpty()
                val album = track?.album?.name.orEmpty()
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(12.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (glass) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.5f))
                        .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(title, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            artist,
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = if (isCurrent) Modifier.clickable { actions.onNavigateToArtist() } else Modifier,
                        )
                        if (album.isNotBlank()) {
                            Text(album, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (isCurrent) {
                        RoundButton(44.dp, Color.Transparent, onClick = { actions.onUIEvent(UIEvent.ToggleLike) }) {
                            Icon(
                                if (state.controllerState.isLiked) SimpIcons.Favorite else SimpIcons.FavoriteBorder,
                                contentDescription = "",
                                tint = if (state.controllerState.isLiked) Pink else Color.White,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundButton(
    size: Dp,
    fill: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier.size(size).clip(CircleShape).background(fill).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun SmallIcon(
    active: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (active) Color.White.copy(alpha = 0.22f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
