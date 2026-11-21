package com.maxrave.simpmusic.ui.screen.radio

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable

/** The Radio tab. Real on Android; the desktop build shows a short notice. */
@Composable
expect fun RadioTabScreen(innerPadding: PaddingValues)

/** The Local tab: music files stored on the phone. Real on Android; the desktop build shows a short notice. */
@Composable
expect fun LocalTabScreen(innerPadding: PaddingValues)
