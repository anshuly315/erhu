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
import com.maxrave.simpmusic.ui.icon.Lyrics
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

/**
 * The erhu player. Three cards in a row (previous | current | next) that swipe like the erhu app's
 * track carousel, a wavy progress line, a round white play button and a translucent card under it.
 *
 * [glass] = false is "Cards": the page is the artwork's own gradient. [glass] = true is "Carousel
 * glass": the same carousel over the blurred artwork, with glass cards. The swipe is the shell's
 * artwork pager, so a swipe skips the track exactly as the other styles do.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun NowPlayingContentErhu(
    state: NowPlayingContentState,
    actions: NowPlayingContentActions,
    glass: Boolean,
) {
    val seed = state.startColor.value
    // Shuffle, repeat, lyrics and queue fold away; long-press the ⋮ (or tap the arrow) to show them.
    var extras by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val cardFill = if (glass) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.28f)
    val cardBorder = if (glass) BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)) else null

    Box(Modifier.fillMaxSize()) {
        if (glass) {
            AppleMusicArtworkBackdrop(state = state, actions = actions, seedColor = seed)
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(state.startColor.value, state.endColor.value, PlayerBackdropColor))),
            )
        }
        LeftToRight {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 20.dp),
            ) {
                // Header: close, source label, more
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f))
                            .combinedClickable(onClick = actions.onShowMoreSheet, onLongClick = { extras = !extras }),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(SimpIcons.MoreVert, contentDescription = "", tint = Color.White)
                    }
                }

                Spacer(Modifier.height(20.dp))
                Carousel(state, actions)
                Spacer(Modifier.height(20.dp))

                // The translucent card under the carousel
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(cardFill)
                        .then(if (cardBorder != null) Modifier.border(cardBorder, RoundedCornerShape(28.dp)) else Modifier)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                state.screenData.nowPlayingTitle,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                state.screenData.artistName,
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable { actions.onNavigateToArtist() },
                            )
                        }
                        RoundButton(44.dp, Color.Transparent, onClick = { actions.onUIEvent(UIEvent.ToggleLike) }) {
                            Icon(
                                if (state.controllerState.isLiked) SimpIcons.Favorite else SimpIcons.FavoriteBorder,
                                contentDescription = "",
                                tint = if (state.controllerState.isLiked) Color(0xFFFF5A7A) else Color.White,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    WavySeekBar(
                        progressFraction = state.sliderValue / 100f,
                        isPlaying = state.controllerState.isPlaying,
                        activeColor = Color.White,
                        trackColor = Color.White.copy(alpha = 0.25f),
                        thumbColor = Color.White,
                        onSliderChange = actions.onSliderChange,
                        onSliderChangeFinished = actions.onSliderChangeFinished,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(state.timelineState.elapsedLabel(state.sliderValue / 100f), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                        Text(state.timelineState.lengthLabel(), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RoundButton(52.dp, Color.Transparent, onClick = { actions.onUIEvent(UIEvent.Previous) }) {
                            Icon(SimpIcons.SkipPrevious, "", tint = Color.White.copy(alpha = if (state.controllerState.isPreviousAvailable) 1f else 0.4f), modifier = Modifier.size(32.dp))
                        }
                        RoundButton(72.dp, Color.White, onClick = { actions.onUIEvent(UIEvent.PlayPause) }) {
                            Icon(
                                if (state.controllerState.isPlaying) SimpIcons.Pause else SimpIcons.PlayArrow,
                                "", tint = Color.Black, modifier = Modifier.size(36.dp),
                            )
                        }
                        RoundButton(52.dp, Color.Transparent, onClick = { actions.onUIEvent(UIEvent.Next) }) {
                            Icon(SimpIcons.SkipNext, "", tint = Color.White.copy(alpha = if (state.controllerState.isNextAvailable) 1f else 0.4f), modifier = Modifier.size(32.dp))
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                androidx.compose.animation.AnimatedVisibility(visible = extras) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        SmallIcon(active = state.controllerState.isShuffle, onClick = { actions.onUIEvent(UIEvent.Shuffle) }) {
                            Icon(SimpIcons.Shuffle, "", tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        SmallIcon(active = state.controllerState.repeatState !is RepeatState.None, onClick = { actions.onUIEvent(UIEvent.Repeat) }) {
                            Icon(
                                if (state.controllerState.repeatState is RepeatState.One) SimpIcons.RepeatOne else SimpIcons.Repeat,
                                "", tint = Color.White, modifier = Modifier.size(22.dp),
                            )
                        }
                        SmallIcon(active = false, onClick = actions.onShowFullscreenLyrics) { Icon(SimpIcons.Lyrics, "", tint = Color.White) }
                        SmallIcon(active = false, onClick = actions.onShowQueue) { Icon(SimpIcons.QueueMusic, "", tint = Color.White) }
                    }
                }
                // The hint: this arrow (or a long press on ⋮) shows shuffle, repeat, lyrics and queue.
                Text(
                    if (extras) "▴" else "▾",
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.CenterHorizontally).clip(CircleShape).clickable { extras = !extras }.padding(horizontal = 24.dp, vertical = 6.dp),
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/** previous | current | next, one card each; the neighbours peek in from the sides and shrink. */
@Composable
private fun Carousel(
    state: NowPlayingContentState,
    actions: NowPlayingContentActions,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cardSize = maxWidth * 0.68f
        val sidePad = (maxWidth - cardSize) / 2
        HorizontalPager(
            state = state.artworkPagerState,
            contentPadding = PaddingValues(horizontal = sidePad),
            pageSpacing = 12.dp,
            beyondViewportPageCount = 1,
            userScrollEnabled = state.artworkQueue.isNotEmpty(),
            key = { idx -> "erhu_${state.artworkQueue.getOrNull(idx)?.videoId.orEmpty()}_$idx" },
            modifier = Modifier.fillMaxWidth().height(cardSize + 12.dp),
        ) { page ->
            val offset = ((state.artworkPagerState.currentPage - page) + state.artworkPagerState.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
            val isCurrent = page == state.artworkPagerState.currentPage
            val track = state.artworkQueue.getOrNull(page)
            var url by remember(page, state.screenData.thumbnailURL, track?.videoId) {
                mutableStateOf(
                    if (isCurrent) state.screenData.thumbnailURL else track?.thumbnails?.maxByOrNull { it.width * it.height }?.url,
                )
            }
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
                modifier =
                    Modifier
                        .size(cardSize)
                        .graphicsLayer {
                            val s = lerp(1f, 0.82f, offset)
                            scaleX = s
                            scaleY = s
                            alpha = lerp(1f, 0.6f, offset)
                        }.clip(RoundedCornerShape(28.dp))
                        .clickable(enabled = !isCurrent) { actions.onSeekToQueueIndex(page) },
            )
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
