package com.maxrave.simpmusic.ui.screen.radio

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler
import org.koin.compose.koinInject
import org.simpmusic.radio.data.RadioBrowserApi
import org.simpmusic.radio.data.RadioPlayer
import org.simpmusic.radio.data.RadioStore
import org.simpmusic.radio.ui.RadioScreen

@Composable
actual fun RadioTabScreen(innerPadding: PaddingValues) {
    val context = LocalContext.current
    val handler: MediaPlayerHandler = koinInject()
    val api = remember { RadioBrowserApi() }
    val store = remember { RadioStore(context) }
    val player = remember(handler) { RadioPlayer(context, handler, api, store).also { it.restoreRegistry() } }
    val localPlayer = remember(handler) { org.simpmusic.radio.local.LocalPlayer(context, handler) }
    ProvideRadioTheme {
    RadioScreen(
        localPlayer = localPlayer,
        api = api,
        store = store,
        player = player,
        modifier = Modifier,
        bottomPadding = innerPadding.calculateBottomPadding() + 120.dp,
    )
    }
}

@Composable
actual fun LocalTabScreen(innerPadding: PaddingValues) {
    val context = LocalContext.current
    val handler: MediaPlayerHandler = koinInject()
    val localPlayer = remember(handler) { org.simpmusic.radio.local.LocalPlayer(context, handler) }
    ProvideRadioTheme {
        org.simpmusic.radio.ui.LocalMusicScreen(
            player = localPlayer,
            bottomPadding = innerPadding.calculateBottomPadding() + 120.dp,
        )
    }
}

/** Radio and Local draw in the app's own colours and follow the Liquid glass setting. */
@Composable
private fun ProvideRadioTheme(content: @Composable () -> Unit) {
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    val dark = com.maxrave.simpmusic.ui.theme.LocalIsDarkTheme.current
    val glass = com.maxrave.simpmusic.ui.theme.LocalLiquidGlassEnabled.current
    val strength = com.maxrave.simpmusic.ui.component.GlassTuning.strength
    val theme =
        remember(scheme, dark, glass, strength) {
            org.simpmusic.radio.ui.RadioTheme(
                bg = scheme.background,
                surface = scheme.surfaceContainerHigh,
                accent = scheme.primary,
                onAccent = scheme.onPrimary,
                muted = scheme.onSurfaceVariant,
                ink = scheme.onBackground,
                dark = dark,
                glass = glass,
                glassStrength = strength,
            )
        }
    androidx.compose.runtime.CompositionLocalProvider(org.simpmusic.radio.ui.LocalRadioTheme provides theme, content = content)
}
