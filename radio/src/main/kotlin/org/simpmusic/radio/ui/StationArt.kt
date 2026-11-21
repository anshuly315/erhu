package org.simpmusic.radio.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import org.simpmusic.radio.data.RadioStation

/**
 * Artwork for a station. Never a flat colour: the station's own logo when it has one, otherwise
 * one of the bundled pictures (res/drawable-nodpi/radio_art_1, radio_art_2, ...) picked from the
 * station id so a station always gets the same one. Drop more radio_art_N.jpg files in to grow the set.
 */
@Composable
fun StationArt(
    station: RadioStation,
    modifier: Modifier = Modifier,
    corner: Dp = 16.dp,
) {
    val context = LocalContext.current
    val fallbackRes = remember(station.uuid) { org.simpmusic.radio.data.FallbackArt.resFor(context, station.uuid, station.tags, station.language) }
    Box(modifier.clip(RoundedCornerShape(corner)).background(Brush.linearGradient(listOf(Color(0xFF2B2F6E), Color(0xFF6A2C7E))))) {
        val fallback: @Composable () -> Unit = {
            if (fallbackRes != 0) {
                Image(painterResource(fallbackRes), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        if (station.favicon.startsWith("http")) {
            SubcomposeAsyncImage(
                model = station.favicon,
                contentDescription = station.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = { fallback() },
                error = { fallback() },
            )
        } else {
            fallback()
        }
    }
}
