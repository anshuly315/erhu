package com.erhu.erhu.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Embedded
import com.erhu.erhu.core.database.entity.SongEntity

data class SongBrowserRow(
    @Embedded val song: SongEntity,
    @ColumnInfo(name = "browser_plays") val plays: Long,
    @ColumnInfo(name = "browser_last_played") val lastPlayed: Long,
)
