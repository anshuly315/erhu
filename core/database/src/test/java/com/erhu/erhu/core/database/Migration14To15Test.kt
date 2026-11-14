package com.erhu.erhu.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class Migration14To15Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), ErhuDatabase::class.java)

    @Test
    fun preservesDuplicateOccurrencesGapsAndIndependentPlaylistOrder() {
        helper.createDatabase("playlist-order-migration", 14).use { db ->
            db.execSQL("INSERT INTO playlist_song_cross_ref (playlist_id, song_id, position) VALUES (1, 10, 0), (1, 20, 3), (1, 10, 7), (2, 10, 1), (2, 30, 4)")
        }
        helper.runMigrationsAndValidate("playlist-order-migration", 15, true, MIGRATION_14_15).use { db ->
            db.query("SELECT playlist_id, song_id, position, sort_order FROM playlist_song_cross_ref ORDER BY playlist_id, sort_order").use { cursor ->
                val actual = buildList {
                    while (cursor.moveToNext()) add(List(4) { cursor.getInt(it) })
                }
                assertEquals(listOf(listOf(1, 10, 0, 0), listOf(1, 20, 3, 3), listOf(1, 10, 7, 7), listOf(2, 10, 1, 1), listOf(2, 30, 4, 4)), actual)
            }
        }
    }

    @Test
    fun emptyDatabaseMigratesWithoutInventingEntries() {
        helper.createDatabase("empty-playlist-order-migration", 14).close()
        helper.runMigrationsAndValidate("empty-playlist-order-migration", 15, true, MIGRATION_14_15).use { db ->
            db.query("SELECT COUNT(*) FROM playlist_song_cross_ref").use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
            }
        }
    }
}
