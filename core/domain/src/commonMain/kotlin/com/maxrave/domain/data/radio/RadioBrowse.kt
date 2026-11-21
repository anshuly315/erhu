package com.maxrave.domain.data.radio

/** A station as Android Auto and other media browsers see it. */
data class RadioItem(
    val uuid: String,
    val name: String,
    val subtitle: String,
    val artUri: String?,
    val streamUrl: String,
)

/** Implemented by the radio module, so the media service can list stations without depending on it. */
interface RadioBrowseProvider {
    suspend fun favourites(): List<RadioItem>

    suspend fun recent(): List<RadioItem>

    suspend fun popular(): List<RadioItem>

    suspend fun search(query: String): List<RadioItem>
}

object RadioBrowse {
    @Volatile
    var provider: RadioBrowseProvider? = null
}
