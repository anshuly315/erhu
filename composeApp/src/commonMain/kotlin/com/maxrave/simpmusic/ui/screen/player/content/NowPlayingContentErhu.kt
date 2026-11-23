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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil3.compose.LocalPlatformContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.maxrave.domain.mediaservice.handler.RepeatState
import com.maxrave.common.Config.MAIN_PLAYER
import com.maxrave.simpmusic.expect.ui.MediaPlayerView
import com.maxrave.simpmusic.expect.ui.MediaPlayerViewWithSubtitle
import com.maxrave.simpmusic.expect.ui.PlatformBackdrop
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.expect.ui.layerBackdrop
import com.maxrave.simpmusic.expect.ui.rememberBackdrop
import com.maxrave.simpmusic.expect.ui.toImageBitmap
import com.maxrave.simpmusic.ui.component.liquidGlass
import com.maxrave.simpmusic.extension.LeftToRight
import com.maxrave.simpmusic.extension.elapsedLabel
import com.maxrave.simpmusic.extension.lengthLabel
import com.maxrave.simpmusic.ui.component.rememberHolderPainter
import com.maxrave.simpmusic.ui.icon.Favorite
import com.maxrave.simpmusic.ui.icon.FavoriteBorder
import com.maxrave.simpmusic.ui.icon.Add
import com.maxrave.simpmusic.ui.icon.Layers
import com.maxrave.simpmusic.ui.icon.ViewCarousel
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
    val colors = rememberErhuColors(state.screenData.bitmap, seed)
    val c = state.controllerState
    var stacked by remember { mutableStateOf(false) }
    val stack = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(stacked) { stack.animateTo(if (stacked) 1f else 0f, androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 300f)) }
    // Glass: the blurred artwork and its scrim are the source the cards and panels refract.
    val backdrop = rememberBackdrop(Color.Black)
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().then(if (glass) Modifier.layerBackdrop(backdrop) else Modifier)) {
        if (glass) {
            AppleMusicArtworkBackdrop(state = state, actions = actions, seedColor = seed)
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    if (glass) {
                        listOf(Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.9f))
                    } else {
                        listOf(colors.gradient[0], colors.gradient[1], if (colors.content.luminance() > 0.5f) Color.Black.copy(alpha = 0.6f).compositeOver(colors.gradient[2]) else colors.gradient[2])
                    },
                ),
            ),
        )
        }
        LeftToRight {
            Column(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp),
            ) {
                // erhu's header: a plain back arrow, "Now Playing" in large type, the menu at the end.
                Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundButton(48.dp, Color.Transparent, onClick = actions.onDismiss) {
                        Icon(state.dismissIcon, contentDescription = "Close player", tint = colors.content, modifier = Modifier.size(28.dp))
                    }
                    Text(
                        text = "Now Playing",
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        color = colors.content,
                        maxLines = 1,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    RoundButton(48.dp, Color.Transparent, onClick = actions.onShowMoreSheet) {
                        Icon(SimpIcons.MoreVert, contentDescription = "More options", tint = colors.content)
                    }
                }

                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val viewport = maxHeight
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Column(Modifier.fillMaxWidth().height(viewport - 56.dp)) {
                            Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp)) { Carousel(state, actions, glass, backdrop, colors, stack.value, !stacked) }

                            // 0:00 · 7 / 930 · 4:10
                            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(state.timelineState.elapsedLabel(state.sliderValue / 100f), color = colors.content.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                if (state.artworkQueue.isNotEmpty()) {
                                    Text("${state.currentOrderIndex + 1} / ${state.artworkQueue.size}", color = colors.content.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium, letterSpacing = 1.5.sp)
                                }
                                Text(state.timelineState.lengthLabel(), color = colors.content.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                            }
                            ErhuSeekBar(
                                progressFraction = state.sliderValue / 100f,
                                isPlaying = c.isPlaying,
                                accent = colors.accent,
                                track = colors.content.copy(alpha = 0.22f),
                                onSliderChange = actions.onSliderChange,
                                onSliderChangeFinished = actions.onSliderChangeFinished,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                            // Five equal columns: shuffle, previous, play / pause, next, repeat.
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Slot { SmallIcon(active = c.isShuffle, onClick = { actions.onUIEvent(UIEvent.Shuffle) }) { Icon(SimpIcons.Shuffle, if (c.isShuffle) "Shuffle on" else "Shuffle off", tint = colors.content, modifier = Modifier.size(22.dp)) } }
                                Slot { RoundButton(52.dp, Color.Transparent, onClick = { actions.onUIEvent(UIEvent.Previous) }) { Icon(SimpIcons.SkipPrevious, "Previous track", tint = colors.content.copy(alpha = if (c.isPreviousAvailable) 1f else 0.4f), modifier = Modifier.size(32.dp)) } }
                                Slot { RoundButton(64.dp, colors.accent, onClick = { actions.onUIEvent(UIEvent.PlayPause) }) { Icon(if (c.isPlaying) SimpIcons.Pause else SimpIcons.PlayArrow, if (c.isPlaying) "Pause" else "Play", tint = colors.onAccent, modifier = Modifier.size(34.dp)) } }
                                Slot { RoundButton(52.dp, Color.Transparent, onClick = { actions.onUIEvent(UIEvent.Next) }) { Icon(SimpIcons.SkipNext, "Next track", tint = colors.content.copy(alpha = if (c.isNextAvailable) 1f else 0.4f), modifier = Modifier.size(32.dp)) } }
                                Slot { SmallIcon(active = c.repeatState !is RepeatState.None, onClick = { actions.onUIEvent(UIEvent.Repeat) }) { Icon(if (c.repeatState is RepeatState.One) SimpIcons.RepeatOne else SimpIcons.Repeat, when (c.repeatState) { is RepeatState.One -> "Repeat one"; is RepeatState.None -> "Repeat off"; else -> "Repeat all" }, tint = colors.content, modifier = Modifier.size(22.dp)) } }
                            }
                            // Add to playlist under shuffle, the stack toggle under play, the queue under repeat.
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Slot { SmallIcon(active = false, onClick = actions.onShowAddToPlaylist) { Icon(SimpIcons.Add, "Add to playlist", tint = colors.content) } }
                                Slot {}
                                Slot {
                                    SmallIcon(active = false, onClick = { stacked = !stacked }) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(SimpIcons.Layers, if (!stacked) "Stack albums" else null, tint = colors.content, modifier = Modifier.graphicsLayer { alpha = 1f - stack.value })
                                            Icon(SimpIcons.ViewCarousel, if (stacked) "Spread albums" else null, tint = colors.content, modifier = Modifier.graphicsLayer { alpha = stack.value })
                                        }
                                    }
                                }
                                Slot {}
                                Slot { SmallIcon(active = false, onClick = actions.onShowQueue) { Icon(SimpIcons.QueueMusic, "Queue", tint = colors.content) } }
                            }
                        }

                        // Lyrics panel: several lines, the current one lit; tap opens the full page.
                        val lines = state.screenData.lyricsData?.lyrics?.lines.orEmpty()
                        val cur = state.currentLyricLineIndex.coerceAtLeast(0)
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .height((viewport * 0.55f).coerceIn(240.dp, 420.dp))
                                .then(if (glass) Modifier.liquidGlass(backdrop, RoundedCornerShape(28.dp), interactive = false) else Modifier)
                                .clip(RoundedCornerShape(28.dp))
                                .erhuCardSurface(colors, RoundedCornerShape(28.dp), 10_000.dp, glass)
                                .clickable(onClick = actions.onShowFullscreenLyrics)
                                .padding(18.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val unsynced = state.screenData.lyricsData?.lyrics?.syncType == "UNSYNCED"
                                Text(if (unsynced) "Lyrics · not synced" else "Lyrics", color = colors.content, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                Icon(SimpIcons.OpenInFull, "Open lyrics", tint = colors.content.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.height(10.dp))
                            if (lines.isEmpty()) {
                                Text("No lyrics yet", color = colors.content.copy(alpha = 0.6f), style = MaterialTheme.typography.titleMedium)
                            } else {
                                lines.drop(cur).take(8).forEachIndexed { i, l ->
                                    Text(
                                        l.words.replace(LyricMarker, "").trim().ifBlank { "♪" },
                                        color = colors.content.copy(alpha = if (i == 0) 1f else 0.55f),
                                        fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Medium,
                                        style = if (i == 0) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(vertical = 4.dp),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.Slot(content: @Composable () -> Unit) {
    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { content() }
}

/** erhu's carousel card: artwork square plus the metadata beneath it on one gradient card; dimmed neighbours tap to select. */
@Composable
private fun Carousel(
    state: NowPlayingContentState,
    actions: NowPlayingContentActions,
    glass: Boolean,
    backdrop: PlatformBackdrop,
    colors: ErhuColors,
    stack: Float,
    selectable: Boolean,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pad = 10.dp
        val showAlbum = maxHeight >= 340.dp
        val metaH = 62.dp + if (showAlbum) 18.dp else 0.dp
        val artSide = minOf(maxWidth * 0.8f - pad * 2, maxHeight - metaH - pad * 2).coerceAtLeast(140.dp)
        val cardW = artSide + pad * 2
        val cardH = artSide + pad * 2 + metaH
        val sidePad = ((maxWidth - cardW) / 2).coerceAtLeast(0.dp)
        // erhu: Radius.large (16dp) on the artwork, plus the card padding on the card around it.
        val shape = RoundedCornerShape(16.dp + pad)
        HorizontalPager(
            state = state.artworkPagerState,
            contentPadding = PaddingValues(horizontal = sidePad),
            pageSpacing = 0.dp,
            beyondViewportPageCount = 2,
            verticalAlignment = Alignment.CenterVertically,
            userScrollEnabled = state.artworkQueue.isNotEmpty(),
            key = { idx -> "erhu_${state.artworkQueue.getOrNull(idx)?.videoId.orEmpty()}_$idx" },
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val signed = (page - state.artworkPagerState.currentPage) - state.artworkPagerState.currentPageOffsetFraction
            val offset = signed.absoluteValue.coerceIn(0f, 1f)
            val isCurrent = page == state.artworkPagerState.currentPage
            val track = state.artworkQueue.getOrNull(page)
            var url by remember(page, state.screenData.thumbnailURL, track?.videoId) {
                mutableStateOf(
                    if (isCurrent) state.screenData.thumbnailURL else track?.thumbnails?.maxByOrNull { it.width * it.height }?.url,
                )
            }
            val title = if (isCurrent) state.screenData.nowPlayingTitle else track?.title.orEmpty()
            val artist = if (isCurrent) state.screenData.artistName else track?.artists?.joinToString { it.name }.orEmpty()
            val album = track?.album?.name.orEmpty()
            Box(
                Modifier
                    .height(cardH)
                    .fillMaxWidth()
                    .zIndex(if (signed.absoluteValue < 0.5f) 1f else 0f)
                    .graphicsLayer { depthCard(signed, stack, hideLeft = page + 1 < state.artworkQueue.size) }
                    .then(if (glass) Modifier.liquidGlass(backdrop, shape, interactive = false) else Modifier)
                    .clip(shape)
                    .erhuCardSurface(colors, shape, artSide + pad * 2, glass)
                    .clickable(enabled = !isCurrent && selectable) { actions.onSeekToQueueIndex(page) },
            ) {
                Column(Modifier.fillMaxSize().padding(pad)) {
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
                        modifier = Modifier.size(artSide).clip(RoundedCornerShape(16.dp)),
                    )
                    Row(
                        Modifier.fillMaxWidth().height(metaH).padding(start = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                            Text(title, color = colors.content, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                artist,
                                color = colors.content.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = if (isCurrent) Modifier.clickable { actions.onNavigateToArtist() } else Modifier,
                            )
                            if (showAlbum && album.isNotBlank()) {
                                Text(album, color = colors.content.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (isCurrent) {
                            RoundButton(44.dp, Color.Transparent, onClick = { actions.onUIEvent(UIEvent.ToggleLike) }) {
                                Icon(
                                    if (state.controllerState.isLiked) SimpIcons.Favorite else SimpIcons.FavoriteBorder,
                                    contentDescription = if (state.controllerState.isLiked) "Remove from liked" else "Like",
                                    tint = if (state.controllerState.isLiked) colors.accent else colors.content,
                                )
                            }
                        }
                    }
                }
                    // The current card plays what the other styles play in place of the cover: the music
                    // video (with lyrics as subtitles) or the track's canvas loop. The cover stays under it,
                    // because its bitmap is what colours the page.
                    if (isCurrent) {
                        val canvasVideo = state.screenData.canvasData?.takeIf { it.isVideo }?.url
                        val showVideo = state.screenData.isVideo && state.shouldShowVideo
                        if (showVideo || canvasVideo != null) {
                            Box(
                                Modifier
                                    .padding(pad)
                                    .size(artSide)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (showVideo) {
                                    MediaPlayerViewWithSubtitle(
                                        playerName = MAIN_PLAYER,
                                        modifier = Modifier.align(Alignment.Center),
                                        shouldShowSubtitle = true,
                                        shouldPip = false,
                                        shouldScaleDownSubtitle = true,
                                        timelineState = state.timelineState,
                                        lyricsData = state.screenData.lyricsData?.lyrics,
                                        translatedLyricsData = state.screenData.lyricsData?.translatedLyrics?.first,
                                        isInPipMode = state.isInPipMode,
                                        mainTextStyle = typo().bodyLarge,
                                        translatedTextStyle = typo().bodyMedium,
                                    )
                                } else if (canvasVideo != null) {
                                    MediaPlayerView(url = canvasVideo, modifier = Modifier.fillMaxSize(), cropToBounds = true)
                                }
                            }
                        }
                    }
                if (offset > 0f) Box(Modifier.fillMaxSize().graphicsLayer { alpha = offset }.background(Color.Black.copy(alpha = 0.45f)))
            }
        }
    }
}

/** Side cards: where they stand (share of a card's width from the centre), how small, how turned. */
private const val SIDE_SPACING = 0.62f
private const val SIDE_SCALE = 0.78f
private const val SIDE_TURN_DEGREES = 32f
private const val STACKED_BEHIND_SCALE = 1.06f
private const val STACKED_HIDE_RATE = 1.5f
private const val VANISH_DISTANCE = 1.6f
private const val CAMERA_DISTANCE = 14f

/**
 * erhu's depth carousel, as a transform on the shell's pager pages: a card [distance] places from
 * the centre moves in to [SIDE_SPACING] of its width, shrinks and turns towards the centre, and
 * fades out past one place. [stack] gathers the neighbours into one slightly larger card behind
 * the current one. The pager lays pages one width apart, so that spacing is taken back out.
 */
private fun androidx.compose.ui.graphics.GraphicsLayerScope.depthCard(
    distance: Float,
    stack: Float,
    hideLeft: Boolean,
) {
    val away = distance.absoluteValue
    val near = away.coerceAtMost(1f)
    val depth = near * near * (3f - 2f * near)
    val sign = kotlin.math.sign(distance)
    val stackedOut = 2f * minOf(near, 1f - near)
    val scale = lerp(1f - (1f - SIDE_SCALE) * depth, 1f + (STACKED_BEHIND_SCALE - 1f) * depth, stack)
    scaleX = scale
    scaleY = scale
    val wanted = lerp(distance * size.width * SIDE_SPACING, sign * size.width * SIDE_SPACING * stackedOut, stack)
    translationX = wanted - distance * size.width
    rotationY = -sign * SIDE_TURN_DEGREES * depth * (1f - stack)
    cameraDistance = CAMERA_DISTANCE * density
    val vanish = (1f - (away - 1f).coerceAtLeast(0f) / (VANISH_DISTANCE - 1f)).coerceIn(0f, 1f)
    val gathered = if (hideLeft) (stack * (-distance).coerceIn(0f, 1f) * STACKED_HIDE_RATE).coerceAtMost(1f) else 0f
    alpha = vanish * (1f - gathered)
}

/** erhu's card material: diagonal gradient body, light along the top, a soft shade under the artwork, lower shade and a rim. */
private fun Modifier.erhuCardSurface(
    colors: ErhuColors,
    shape: androidx.compose.ui.graphics.Shape,
    infoTop: Dp,
    glass: Boolean,
): Modifier =
    drawWithCache {
        // Over real glass the tint stays light so the frost shows; behind the text it is still readable.
        val bodyAlpha = if (glass) 0.45f else 1f
        val body = Brush.linearGradient(colors.card.map { it.copy(alpha = it.alpha * bodyAlpha) }, start = Offset.Zero, end = Offset(size.width, size.height))
        val top = infoTop.toPx()
        val light = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.1f), Color.Transparent), startY = 0f, endY = 22.dp.toPx())
        val step = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.16f), Color.Transparent), startY = top - 1f, endY = top + 14.dp.toPx())
        val lower = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.16f)), startY = size.height * 0.82f, endY = size.height)
        val rim = Brush.verticalGradient(listOf(colors.rim.copy(alpha = 0.5f), colors.rim.copy(alpha = 0.12f), Color.Black.copy(alpha = 0.22f)))
        val outline = shape.createOutline(size, layoutDirection, this)
        onDrawBehind {
            drawRect(body)
            drawRect(light)
            drawRect(step)
            drawRect(lower)
            drawOutline(outline, rim, style = Stroke(width = 2.dp.toPx()))
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
            .background(if (active) Color.Black.copy(alpha = 0.32f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
