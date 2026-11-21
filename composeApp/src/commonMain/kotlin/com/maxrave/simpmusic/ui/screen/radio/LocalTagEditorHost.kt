package com.maxrave.simpmusic.ui.screen.radio

import androidx.compose.runtime.Composable

/** One-file tag and artwork editor for a phone file; Android only. */
@Composable
expect fun LocalTagEditorHost(trackId: String, onDismiss: () -> Unit)
