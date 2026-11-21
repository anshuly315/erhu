package com.maxrave.simpmusic.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

private var _Radio: ImageVector? = null

/** A table radio, for the Radio tab. */
val SimpIcons.Radio: ImageVector
    get() {
        _Radio?.let { return it }
        val icon =
            ImageVector
                .Builder(
                    name = "Radio",
                    defaultWidth = 24.dp,
                    defaultHeight = 24.dp,
                    viewportWidth = 24f,
                    viewportHeight = 24f,
                ).addPath(
                    pathData =
                        addPathNodes(
                            "M3.24 6.15C2.51 6.43 2 7.17 2 8v12c0 1.1.89 2 2 2h16c1.11 0 2-.9 2-2V8c0-1.11-.89-2-2-2H8.3l8.26-3.34L15.88 1 3.24 6.15zM7 20c-1.66 0-3-1.34-3-3s1.34-3 3-3 3 1.34 3 3-1.34 3-3 3zm13-8h-2v-2h-2v2H4V8h16v4z",
                        ),
                    fill = SolidColor(Color.Black),
                ).build()
        _Radio = icon
        return icon
    }
