package com.maxrave.simpmusic.ui.screen.radio

import androidx.compose.runtime.Composable
import org.simpmusic.radio.ui.LocalTagEditorDialog

@Composable
actual fun LocalTagEditorHost(trackId: String, onDismiss: () -> Unit) = LocalTagEditorDialog(trackId, onDismiss)
