package com.erhu.erhu.core.designsystem.artwork

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.erhu.erhu.core.designsystem.theme.LocalGlassTokens
import com.erhu.erhu.core.model.Song
import kotlinx.coroutines.delay

/** Tiny source bitmap: upscaling it is itself a soft blur, and it costs almost nothing. */
private const val BACKDROP_SOURCE_PX = 48
private const val DEFAULT_BLUR_RADIUS_DP = 40
private const val DEFAULT_FALLBACK_SCRIM_ALPHA = 0.85f

/**
 * Full-bleed, blurred album art behind the Now Playing screen.
 *
 * - Android 12+: a GPU blur ([Modifier.blur], RenderEffect) on one static image.
 * - Android 8–11: Modifier.blur is a no-op there, so the look comes from
 *   upscaling a 48 px thumbnail with bilinear filtering plus the scrim.
 * When artwork is unavailable, draws the theme background to keep the layer intact.
 */
@Composable
fun GlassArtworkBackdrop(
    song: Song?,
    modifier: Modifier = Modifier,
) {
    val glass = LocalGlassTokens.current
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, song?.uri) {
        value = song?.let { ArtworkLoader.load(context, it.uri, it.artworkUri, BACKDROP_SOURCE_PX) }
    }
    val source = bitmap
    val opaqueBackground = MaterialTheme.colorScheme.background.copy(alpha = 1f)
    val blurRadius = (glass?.artworkBlurRadiusDp ?: DEFAULT_BLUR_RADIUS_DP).dp
    val scrimColor =
        glass?.let { Color(it.artworkScrim) }
            ?: MaterialTheme.colorScheme.background.copy(alpha = DEFAULT_FALLBACK_SCRIM_ALPHA)
    if (source != null) {
        var viewport by remember { mutableStateOf(IntSize.Zero) }
        val cached = rememberBackdropRaster(source, viewport, blurRadius, opaqueBackground, scrimColor)
        Box(modifier = modifier.fillMaxSize().background(opaqueBackground).onSizeChanged { viewport = it }) {
            if (cached != null) {
                Image(bitmap = cached, contentDescription = null, modifier = Modifier.fillMaxSize(), filterQuality = FilterQuality.Low)
            } else {
                Image(
                    bitmap = source.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.Low,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .blur(blurRadius),
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(scrimColor),
                )
            }
        }
    } else {
        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(opaqueBackground),
        )
    }
}

/** Flatten the static blur once, instead of running it again through every glass consumer. */
@Composable
private fun rememberBackdropRaster(
    source: Bitmap,
    viewport: IntSize,
    radius: Dp,
    background: Color,
    scrim: Color,
): ImageBitmap? {
    val graphics = LocalGraphicsContext.current
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val raster by produceState<ImageBitmap?>(null, source, viewport, radius, background, scrim, density, direction) {
        value = null
        // Bound retained memory to 12 MB. Large displays retain the existing rendering path.
        val area = viewport.width.toLong() * viewport.height
        val sizeFits = viewport.width > 0 && viewport.height > 0 && area <= MAX_BACKDROP_PIXELS
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !sizeFits) return@produceState
        // Slider/theme changes cancel superseded captures; the original blur remains visible.
        delay(100)
        val image = source.asImageBitmap()
        val blurred = graphics.createGraphicsLayer()
        val frame = graphics.createGraphicsLayer()
        try {
            val px = with(density) { radius.toPx() }
            blurred.renderEffect = BlurEffect(px, px, TileMode.Decal)
            blurred.clip = true
            blurred.record(density, direction, viewport) {
                val scale = maxOf(viewport.width.toFloat() / image.width, viewport.height.toFloat() / image.height)
                val scaled = IntSize(kotlin.math.round(image.width * scale).toInt(), kotlin.math.round(image.height * scale).toInt())
                clipRect {
                    drawImage(
                        image,
                        dstSize = scaled,
                        dstOffset = IntOffset((viewport.width - scaled.width) / 2, (viewport.height - scaled.height) / 2),
                        filterQuality = FilterQuality.Low,
                    )
                }
            }
            frame.record(density, direction, viewport) {
                drawRect(background)
                drawLayer(blurred)
                drawRect(scrim)
            }
            value = frame.toImageBitmap()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: RuntimeException) {
            // Renderer readback is optional. Keep the original GPU blur on unsupported drivers.
            android.util.Log.w("ArtworkBackdrop", "Backdrop caching unavailable", error)
            value = null
        } finally {
            graphics.releaseGraphicsLayer(blurred)
            graphics.releaseGraphicsLayer(frame)
        }
    }
    return raster
}

private const val MAX_BACKDROP_PIXELS = 3_000_000L
