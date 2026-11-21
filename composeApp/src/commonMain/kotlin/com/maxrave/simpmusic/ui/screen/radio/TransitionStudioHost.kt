package com.maxrave.simpmusic.ui.screen.radio

import androidx.compose.runtime.Composable

/** Per-album and per-pair crossfade settings for phone files; Android only. */
@Composable
expect fun TransitionStudioHost(trackId: String, nextTrackId: String?, onDismiss: () -> Unit)
