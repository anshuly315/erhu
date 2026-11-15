package com.erhu.erhu.core.database.repository

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.erhu.erhu.core.common.result.appResultOf
import com.erhu.erhu.core.database.dao.SongDao
import com.erhu.erhu.core.database.mapper.toDomainModel
import com.erhu.erhu.core.model.SongBrowserEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class SongsBrowserRepository
    @Inject
    constructor(
        private val songs: SongDao,
        @ApplicationContext private val context: Context,
    ) {
        fun observe() =
            songs
                .observeBrowserSongs()
                .map { rows ->
                    rows.map {
                        SongBrowserEntry(
                            it.song.toDomainModel(),
                            it.song.dateAddedMs.coerceAtLeast(0),
                            it.song.year,
                            it.plays,
                            it.lastPlayed,
                        )
                    }
                }.flowOn(Dispatchers.Default)

        private val backfillMutex = Mutex()

        /** Caller checks audio permission. No scan, enrichment, tag write, or queue changes. */
        suspend fun backfillAddedDates() =
            appResultOf {
                withContext(Dispatchers.IO) {
                    backfillMutex.withLock {
                        songs.getAllSongsOnce().filter { it.dateAddedMs == 0L }.forEach { song ->
                            coroutineContext.ensureActive()
                            val seconds =
                                try {
                                    context.contentResolver
                                        .query(
                                            Uri.parse(song.uri),
                                            arrayOf(MediaStore.Audio.Media.DATE_ADDED),
                                            null,
                                            null,
                                            null,
                                        )?.use { cursor ->
                                            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else -1L
                                        } ?: -1L
                                } catch (_: IllegalArgumentException) {
                                    // A removed or non-MediaStore source has no indexed date.
                                    -1L
                                }
                            // -1 means checked but unavailable; 0 stays reserved for unexamined rows.
                            songs.backfillAddedDate(
                                song.id,
                                if (seconds > 0 &&
                                    seconds <= Long.MAX_VALUE / MILLIS_PER_SECOND
                                ) {
                                    seconds * MILLIS_PER_SECOND
                                } else {
                                    -1
                                },
                            )
                        }
                    }
                }
            }
    }

private const val MILLIS_PER_SECOND = 1000L
