package org.simpmusic.radio.data

import android.content.Context
import com.maxrave.domain.data.radio.RadioBrowse
import com.maxrave.domain.data.radio.RadioBrowseProvider
import com.maxrave.domain.data.radio.RadioItem

/**
 * Lets Android Auto (and any other media browser) list favourites, recent and popular stations.
 * Call [install] once at app start; the media service then finds the provider on its own.
 */
object RadioAuto {
    fun install(context: Context) {
        val app = context.applicationContext
        val store = RadioStore(app)
        val api = RadioBrowserApi()
        // Saved stations get their stream URLs back at once, so a queue restored after a restart
        // plays from the mini player before the Radio tab was ever opened.
        com.maxrave.domain.data.radio.RadioStreams.registerAll(
            (store.favourites.value + store.history.value).associate { com.maxrave.domain.data.radio.RadioStreams.idFor(it.uuid) to it.streamUrl },
        )

        fun RadioStation.item() = RadioItem(uuid, name, subtitle, favicon.takeIf { it.startsWith("http") } ?: FallbackArt.uriFor(app, uuid, tags, language), streamUrl)
        RadioBrowse.provider =
            object : RadioBrowseProvider {
                override suspend fun favourites() = store.favourites.value.map { it.item() }

                override suspend fun recent() = store.history.value.take(30).map { it.item() }

                override suspend fun popular() = runCatching { api.popular(40).map { it.item() } }.getOrDefault(emptyList())

                override suspend fun search(query: String) = runCatching { api.search(query, 30).map { it.item() } }.getOrDefault(emptyList())
            }
    }
}
