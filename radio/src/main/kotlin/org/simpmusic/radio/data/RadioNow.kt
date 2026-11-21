package org.simpmusic.radio.data

import kotlinx.coroutines.flow.MutableStateFlow

/** The station that is playing right now (null when something else, such as a local song, is). */
object RadioNow {
    val station = MutableStateFlow<RadioStation?>(null)
}
