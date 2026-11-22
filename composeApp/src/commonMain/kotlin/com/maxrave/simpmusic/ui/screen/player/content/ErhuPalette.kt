@file:Suppress("MagicNumber")

package com.maxrave.simpmusic.ui.screen.player.content

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * The erhu player's colours, ported from the erhu app's LyricsPalette: the artwork's strongest hue
 * (pixels vote with their colourfulness) becomes a calm, muted surface; a three-stop gradient and a
 * diagonal card gradient are built around it with every stop kept readable under [content]; the
 * accent is a vivid tint of the same hue that still reads on the weakest stop.
 */
@Immutable
internal class ErhuPalette(
    val gradient: List<Int>,
    val cardGradient: List<Int>,
    val content: Int,
    val accent: Int,
    val rim: Int,
) {
    companion object {
        private const val HUE_BINS = 24
        private const val LIGHT_TEXT = 0xFFF7F3F2.toInt()
        private const val DARK_TEXT = 0xFF17141A.toInt()
        private const val CONTRAST = 4.5

        val FALLBACK: ErhuPalette by lazy { from(345f, 0.38f, 0.3f) }

        fun fromSeed(seed: Color): ErhuPalette {
            val h = hsl(seed.toArgb())
            return from(h[0], h[1], h[2])
        }

        fun fromPixels(pixels: IntArray): ErhuPalette {
            val votes = DoubleArray(HUE_BINS)
            val xs = DoubleArray(HUE_BINS)
            val ys = DoubleArray(HUE_BINS)
            var colourful = 0.0
            var light = 0.0
            var counted = 0
            for (p in pixels) {
                if ((p ushr 24) < 128) continue
                val h = hsl(p)
                val chroma = h[1] * (1f - abs(h[2] * 2f - 1f))
                val bin = (h[0] / (360f / HUE_BINS)).toInt().coerceIn(0, HUE_BINS - 1)
                val a = h[0].toDouble() * kotlin.math.PI / 180.0
                votes[bin] += chroma
                xs[bin] += cos(a) * chroma
                ys[bin] += sin(a) * chroma
                colourful += chroma
                light += h[2]
                counted++
            }
            if (counted == 0) return FALLBACK
            val best = votes.indices.maxBy { votes[it] }
            val band = listOf(best - 1, best, best + 1).map { ((it % HUE_BINS) + HUE_BINS) % HUE_BINS }
            val hue = ((atan2(band.sumOf { ys[it] }, band.sumOf { xs[it] }) * 180.0 / kotlin.math.PI + 360.0) % 360.0).toFloat()
            val second =
                votes.indices
                    .filter { minOf(abs(it - best), HUE_BINS - abs(it - best)) >= 3 }
                    .maxByOrNull { votes[it] }
                    ?.takeIf { votes[it] >= votes[best] * 0.3 }
                    ?.let { ((atan2(ys[it], xs[it]) * 180.0 / kotlin.math.PI + 360.0) % 360.0).toFloat() }
            val sat =
                if (colourful / counted < 0.05) {
                    0f
                } else {
                    val n =
                        pixels.count { p ->
                            val h = hsl(p)
                            (p ushr 24) >= 128 && h[1] > 0f && (h[0] / (360f / HUE_BINS)).toInt().coerceIn(0, HUE_BINS - 1) in band
                        }.coerceAtLeast(1)
                    (band.sumOf { votes[it] } / n).toFloat()
                }
            return from(hue, sat, (light / counted).toFloat(), second)
        }

        private fun from(hue: Float, saturation: Float, artLight: Float, secondHue: Float? = null): ErhuPalette {
            val neutral = saturation < 0.08f
            val s = if (neutral) 0f else (saturation * 1.6f).coerceIn(0.3f, 0.55f)
            val l =
                when {
                    neutral && artLight > 0.8f -> 0.86f
                    neutral -> 0.16f + 0.08f * artLight
                    else -> (0.2f + 0.12f * artLight).coerceAtMost(0.32f)
                }
            val content = readableOn(hslToArgb(hue, s, l))
            val lightText = content == LIGHT_TEXT
            val surface = stop(hue, s, l, content, lightText)
            val gradient =
                listOf(
                    stop(hue - 12f, s * 0.85f, (l + 0.06f).coerceAtMost(0.94f), content, lightText),
                    surface,
                    stop(hue + 26f, s * 0.75f, (l - 0.09f).coerceAtLeast(0.07f), content, lightText),
                )
            val card =
                listOf(
                    stop(hue - 8f, s * 0.95f, (l + 0.08f).coerceAtMost(0.94f), content, lightText),
                    surface,
                    stop(secondHue ?: (hue + 26f), s * 0.8f, (l - 0.1f).coerceAtLeast(0.07f), content, lightText),
                )
            val weakest = (gradient + card).minBy { contrast(content, it) }
            val accent = if (neutral) content else accentOn(weakest, hue, relativeLuminance(weakest) < 0.18)
            val rim = hslToArgb(hue, (s + 0.1f).coerceAtMost(0.55f), if (lightText) 0.74f else 0.38f)
            return ErhuPalette(gradient, card, content, accent, rim)
        }

        fun readableOn(surface: Int): Int = if (contrast(LIGHT_TEXT, surface) >= contrast(DARK_TEXT, surface)) LIGHT_TEXT else DARK_TEXT

        private fun stop(hue: Float, s: Float, l0: Float, content: Int, lightText: Boolean): Int {
            var l = l0
            var c = hslToArgb(hue, s, l)
            while (contrast(content, c) < CONTRAST && l in 0.07f..0.94f) {
                l += if (lightText) -0.02f else 0.02f
                c = hslToArgb(hue, s, l.coerceIn(0f, 1f))
            }
            return c
        }

        private fun accentOn(surface: Int, hue: Float, dark: Boolean): Int {
            var l = if (dark) 0.74f else 0.32f
            repeat(6) {
                val c = hslToArgb(hue, 0.7f, l)
                if (contrast(c, surface) >= CONTRAST) return c
                l += if (dark) 0.04f else -0.04f
            }
            return readableOn(surface)
        }
    }
}

@Immutable
internal class ErhuColors(
    val gradient: List<Color>,
    val card: List<Color>,
    val content: Color,
    val accent: Color,
    val onAccent: Color,
    val rim: Color,
)

/** The artwork's palette, sampled small once per artwork, with the colours cross-fading on a track change. */
@Composable
internal fun rememberErhuColors(
    bitmap: ImageBitmap?,
    seed: Color,
): ErhuColors {
    val palette = remember(bitmap, seed) { bitmap?.samplePixels()?.let { ErhuPalette.fromPixels(it) } ?: ErhuPalette.fromSeed(seed) }
    val spec = tween<Color>(600)
    val g0 by animateColorAsState(Color(palette.gradient[0]), spec, label = "g0")
    val g1 by animateColorAsState(Color(palette.gradient[1]), spec, label = "g1")
    val g2 by animateColorAsState(Color(palette.gradient[2]), spec, label = "g2")
    val c0 by animateColorAsState(Color(palette.cardGradient[0]), spec, label = "c0")
    val c1 by animateColorAsState(Color(palette.cardGradient[1]), spec, label = "c1")
    val c2 by animateColorAsState(Color(palette.cardGradient[2]), spec, label = "c2")
    val content by animateColorAsState(Color(palette.content), spec, label = "content")
    val accent by animateColorAsState(Color(palette.accent), spec, label = "accent")
    val rim by animateColorAsState(Color(palette.rim), spec, label = "rim")
    return ErhuColors(listOf(g0, g1, g2), listOf(c0, c1, c2), content, accent, Color(ErhuPalette.readableOn(accent.toArgb())), rim)
}

private fun ImageBitmap.samplePixels(size: Int = 32): IntArray? =
    try {
        val small = ImageBitmap(size, size)
        Canvas(small).drawImageRect(
            this, IntOffset.Zero, IntSize(width, height), IntOffset.Zero, IntSize(size, size),
            Paint().apply { filterQuality = FilterQuality.Low },
        )
        IntArray(size * size).also { small.readPixels(it) }
    } catch (_: Exception) {
        null
    }

internal fun contrast(a: Int, b: Int): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
}

private fun relativeLuminance(argb: Int): Double {
    fun ch(shift: Int): Double {
        val c = ((argb shr shift) and 0xFF) / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * ch(16) + 0.7152 * ch(8) + 0.0722 * ch(0)
}

private fun hsl(argb: Int): FloatArray {
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val hi = max(r, max(g, b))
    val lo = min(r, min(g, b))
    val l = (hi + lo) / 2f
    if (hi == lo) return floatArrayOf(0f, 0f, l)
    val d = hi - lo
    val s = if (l > 0.5f) d / (2f - hi - lo) else d / (hi + lo)
    val h =
        when (hi) {
            r -> ((g - b) / d + (if (g < b) 6f else 0f))
            g -> ((b - r) / d + 2f)
            else -> ((r - g) / d + 4f)
        } * 60f
    return floatArrayOf(h, s, l)
}

private fun hslToArgb(hue: Float, saturation: Float, lightness: Float): Int {
    val c = (1f - abs(2f * lightness - 1f)) * saturation
    val hp = ((hue % 360f) + 360f) % 360f / 60f
    val x = c * (1f - abs(hp % 2f - 1f))
    val (r, g, b) =
        when {
            hp < 1f -> Triple(c, x, 0f)
            hp < 2f -> Triple(x, c, 0f)
            hp < 3f -> Triple(0f, c, x)
            hp < 4f -> Triple(0f, x, c)
            hp < 5f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
    val m = lightness - c / 2f
    fun byte(v: Float) = ((v + m).coerceIn(0f, 1f) * 255f).toInt()
    return (0xFF shl 24) or (byte(r) shl 16) or (byte(g) shl 8) or byte(b)
}
