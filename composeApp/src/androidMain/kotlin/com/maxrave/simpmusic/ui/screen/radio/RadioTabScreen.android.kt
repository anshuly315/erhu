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
    RadioScreen(
        api = api,
        store = store,
        player = player,
        modifier = Modifier,
        bottomPadding = innerPadding.calculateBottomPadding() + 120.dp,
    )
}
