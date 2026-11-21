package org.simpmusic.radio.data

import android.content.Context

/**
 * The bundled artwork used when a station has no logo of its own: res/drawable-nodpi/radio_art_1,
 * radio_art_2, ... Drop more numbered images in and they are picked up with no code change.
 */
object FallbackArt {
    private fun count(context: Context): Int =
        generateSequence(1) { it + 1 }
            .takeWhile { context.resources.getIdentifier("radio_art_$it", "drawable", context.packageName) != 0 }
            .count()

    /** The drawable resource for this station, or 0 if the app ships no pictures. */
    fun resFor(
        context: Context,
        uuid: String,
    ): Int {
        val n = count(context)
        if (n == 0) return 0
        val index = (uuid.hashCode() and 0x7fffffff) % n + 1
        return context.resources.getIdentifier("radio_art_$index", "drawable", context.packageName)
    }

    /** Same picture as a URI the notification, lock screen and Android Auto can load. */
    fun uriFor(
        context: Context,
        uuid: String,
    ): String? =
        resFor(context, uuid).takeIf { it != 0 }?.let { "android.resource://${context.packageName}/$it" }
}
