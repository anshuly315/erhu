package com.maxrave.simpmusic.ui.screen.player.content

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import kotlin.math.PI
import kotlin.math.sin

// erhu's LinearSeekBar: a thin line whose played part is a slowly drifting "liquid" crest,
// ending in a glowing knob. Ported without the bookmarks and A-B loop (SimpMusic has neither).

private val TRACK_THICKNESS = 4.dp
private val TRACK_THICKNESS_SCRUBBING = 6.dp
private val HEAD_RADIUS = 5.dp
private val KNOB_RADIUS = 7.dp
private val GLOW_RADIUS = 12.dp
private val CONTOUR_WIDTH = 1.5.dp
private val SHADE_EXTRA = 1.5.dp
private const val SHADE_ALPHA = 0.22f
private const val CENTRE_LIFT = 0.7f
private const val CONTOUR_LIFT = 0.5f
private const val CONTOUR_ALPHA = 0.7f
private const val GLOW_INNER = 0.42f
private const val PAUSED_GLOW = 0.5f
private const val TAIL_ALPHA = 0.55f
private const val HEAD_LIFT = 0.28f
private const val GLOW_ALPHA = 0.5f
private const val PASTEL_LIFT = 0.18f
private val LIQUID_RISE = 3.dp
private val LIQUID_WAVE_LONG = 74.dp
private val LIQUID_WAVE_SHORT = 31.dp
private val LIQUID_LEAD_IN = 22.dp
private val LIQUID_STEP = 3.dp
private const val LIQUID_SETTLED = 0.45f

@Composable
fun ErhuSeekBar(
    progressFraction: Float,
    isPlaying: Boolean,
    accent: Color,
    track: Color,
    onSliderChange: (Float) -> Unit,
    onSliderChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var scrubbing by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableIntStateOf(0) }
    val glow by animateFloatAsState(if (isPlaying || scrubbing) 1f else PAUSED_GLOW, tween(420), label = "seekGlow")
    val scrub by animateFloatAsState(if (scrubbing) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 500f), label = "seekScrub")
    val liquid by animateFloatAsState(if (isPlaying) 1f else LIQUID_SETTLED, tween(420), label = "seekLiquid")
    // Drift time in seconds: advances only while playing, and through the infinite-animation clock
    // so a hidden window (Desktop) or a test can pause it.
    val drift = remember { mutableFloatStateOf(0f) }
    val inspection = LocalInspectionMode.current
    LaunchedEffect(isPlaying) {
        if (!isPlaying || inspection) return@LaunchedEffect
        var last = withInfiniteAnimationFrameNanos { it }
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                drift.floatValue += (now - last) / 1_000_000_000f
                last = now
            }
        }
    }
    val shown = { (if (scrubbing) dragFraction else progressFraction).coerceIn(0f, 1f) }
    fun fractionAt(x: Float): Float = if (widthPx <= 0) 0f else (x / widthPx).coerceIn(0f, 1f)

    Box(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    dragFraction = fractionAt(o.x)
                    onSliderChange(dragFraction * 100f)
                    onSliderChangeFinished()
                }
            }.pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o ->
                        scrubbing = true
                        dragFraction = fractionAt(o.x)
                        onSliderChange(dragFraction * 100f)
                    },
                    onDragEnd = {
                        scrubbing = false
                        onSliderChangeFinished()
                    },
                    onDragCancel = {
                        scrubbing = false
                        onSliderChangeFinished()
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragFraction = fractionAt(change.position.x)
                        onSliderChange(dragFraction * 100f)
                    },
                )
            }.drawWithCache {
                val thin = TRACK_THICKNESS.toPx()
                val thick = TRACK_THICKNESS_SCRUBBING.toPx()
                val body = lerp(accent, Color.White, PASTEL_LIFT)
                val tail = body.copy(alpha = body.alpha * TAIL_ALPHA)
                val head = lerp(accent, Color.White, HEAD_LIFT)
                val centre = lerp(head, Color.White, CENTRE_LIFT)
                val contour = lerp(head, Color.White, CONTOUR_LIFT).copy(alpha = CONTOUR_ALPHA)
                val crest = LiquidCrest(LIQUID_RISE.toPx(), LIQUID_WAVE_LONG.toPx(), LIQUID_WAVE_SHORT.toPx(), LIQUID_LEAD_IN.toPx())
                val step = LIQUID_STEP.toPx()
                val path = Path()
                onDrawBehind {
                    val y = size.height / 2
                    val thickness = thin + (thick - thin) * scrub
                    val headX = shown() * size.width
                    drawLine(track, Offset(0f, y), Offset(size.width, y), thickness, StrokeCap.Round)
                    if (headX > 0f) {
                        path.liquidFill(crest, headX, y, thickness / 2, step, drift.floatValue, liquid)
                        drawPath(path, Brush.horizontalGradient(listOf(tail, body, head), startX = 0f, endX = headX))
                    }
                    drawHead(
                        Offset(headX, y),
                        radius = HEAD_RADIUS.toPx() + (KNOB_RADIUS.toPx() - HEAD_RADIUS.toPx()) * scrub,
                        glow = glow,
                        glowRadius = GLOW_RADIUS.toPx(),
                        head = head,
                        centre = centre,
                        contour = contour,
                        contourWidth = CONTOUR_WIDTH.toPx(),
                        shadeExtra = SHADE_EXTRA.toPx(),
                    )
                }
            },
    )
}

/** Two overlapping swells drifting in opposite directions, always above the line, easing in from its start. */
private class LiquidCrest(
    private val rise: Float,
    longWave: Float,
    shortWave: Float,
    private val leadIn: Float,
) {
    private val longK = (2 * PI / longWave).toFloat()
    private val shortK = (2 * PI / shortWave).toFloat()

    fun heightAt(
        x: Float,
        time: Float,
        strength: Float,
    ): Float {
        val swell = 0.62f * sin(x * longK - time * 0.9f) + 0.38f * sin(x * shortK + time * 1.6f + 1.3f)
        val wave = 0.25f + 0.75f * (swell + 1f) / 2f
        val t = (x / leadIn).coerceIn(0f, 1f)
        return rise * strength * wave * t * t * (3f - 2f * t)
    }
}

private fun Path.liquidFill(
    crest: LiquidCrest,
    headX: Float,
    y: Float,
    half: Float,
    step: Float,
    time: Float,
    strength: Float,
) {
    rewind()
    moveTo(0f, y + half)
    arcTo(Rect(-half, y - half, half, y + half), 90f, 180f, false)
    var x = 0f
    while (x < headX) {
        lineTo(x, y - half - crest.heightAt(x, time, strength))
        x += step
    }
    lineTo(headX, y - half - crest.heightAt(headX, time, strength))
    lineTo(headX, y + half)
    close()
}

private fun DrawScope.drawHead(
    at: Offset,
    radius: Float,
    glow: Float,
    glowRadius: Float,
    head: Color,
    centre: Color,
    contour: Color,
    contourWidth: Float,
    shadeExtra: Float,
) {
    drawCircle(
        brush =
            Brush.radialGradient(
                0f to head.copy(alpha = GLOW_ALPHA * glow),
                GLOW_INNER to head.copy(alpha = GLOW_ALPHA * glow * 0.5f),
                1f to Color.Transparent,
                center = at,
                radius = glowRadius,
            ),
        radius = glowRadius,
        center = at,
    )
    drawCircle(Color.Black.copy(alpha = SHADE_ALPHA), radius + shadeExtra, at)
    drawCircle(Brush.radialGradient(listOf(centre, head), center = at, radius = radius), radius, at)
    drawCircle(contour, radius - contourWidth / 2, at, style = Stroke(contourWidth))
}
