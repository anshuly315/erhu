package com.erhu.erhu.core.datastore

import androidx.datastore.core.DataStore
import com.erhu.erhu.core.datastore.proto.PlaybackStateProto
import com.erhu.erhu.core.common.result.appResultOf
import com.erhu.erhu.core.model.SongsBrowserPreferences
import com.erhu.erhu.core.model.SongsSort
import com.erhu.erhu.core.model.SongsMood
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Singleton
class SongsBrowserPreferencesRepository @Inject constructor(private val store: DataStore<PlaybackStateProto>) {
    fun observe() = store.data.map { state ->
        SongsBrowserPreferences(
            sort = SongsSort.entries.firstOrNull { it.name == state.songsSort } ?: SongsSort.MOST_PLAYED,
            genre = state.songsGenre,
            mood = SongsMood.entries.firstOrNull { it.name == state.songsMood } ?: SongsMood.ALL,
        )
    }.distinctUntilChanged()

    suspend fun setSort(value: SongsSort) = appResultOf {
        store.updateData { it.toBuilder().setSongsSort(value.name).build() }; Unit
    }
    suspend fun setGenre(value: String) = appResultOf {
        store.updateData { it.toBuilder().setSongsGenre(value).build() }; Unit
    }
    suspend fun setMood(value: SongsMood) = appResultOf {
        store.updateData { it.toBuilder().setSongsMood(value.name).build() }; Unit
    }
    suspend fun clear() = appResultOf {
        store.updateData { it.toBuilder().clearSongsGenre().clearSongsMood().build() }; Unit
    }
}
