package com.maxrave.simpmusic.ui.screen.radio

import androidx.compose.runtime.Composable
import org.simpmusic.radio.ui.TransitionStudioDialog

@Composable
actual fun TransitionStudioHost(trackId: String, nextTrackId: String?, onDismiss: () -> Unit) =
    TransitionStudioDialog(trackId, nextTrackId, onDismiss)
