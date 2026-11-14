package com.erhu.erhu.core.database.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.erhu.erhu.core.common.result.AppResult
import com.erhu.erhu.core.database.ErhuDatabase
import com.erhu.erhu.core.database.entity.SongEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.nio.file.Files

/** A queue saved as a playlist keeps its exact order, duplicates included; adding songs keeps its old rules. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SavedQueuePlaylistTest {
    private val db =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ErhuDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private val artworkDir = Files.createTempDirectory("saved-queue").toFile()
    private var time = 1_000L
    private val playlists = PlaylistRepositoryImpl(db.playlistDao(), db.recentlyPlayedDao(), PlaylistArtworkStore(artworkDir)) { time++ }

    @After
    fun close() {
        db.close()
        artworkDir.deleteRecursively()
    }

    private fun song(id: Long) =
        SongEntity(
            id = id,
            title = "Song $id",
            artist = "A",
            album = "B",
            artistId = 1,
            albumId = 1,
            durationMs = 1,
            uri = "u$id",
            filePath = "/m/$id.mp3",
            trackNumber = 1,
            year = 2020,
            dateModified = 1,
        )

    @Test
    fun `saved queue keeps order and duplicates`() =
        runBlocking {
            db.songDao().upsertAll(listOf(song(1), song(2), song(3)))
            val id = (playlists.createPlaylistWithSongs("  Late drive ", listOf(2L, 1L, 2L, 3L)) as AppResult.Success).data

            assertEquals(listOf(2L, 1L, 2L, 3L), playlists.observePlaylistSongs(id).first().map { it.id })
            val saved = playlists.observePlaylists().first().single()
            assertEquals("Late drive", saved.name)
            assertEquals(4, saved.songCount)

            // Add to playlist still never adds a second copy, and appends after the last entry.
            assertEquals(AppResult.Success(false), playlists.addSong(id, 2L))
            db.songDao().upsertAll(listOf(song(4)))
            assertEquals(AppResult.Success(true), playlists.addSong(id, 4L))
            assertEquals(listOf(2L, 1L, 2L, 3L, 4L), playlists.observePlaylistSongs(id).first().map { it.id })
        }

    @Test
    fun `an empty queue gives an empty playlist and a blank name is refused`() =
        runBlocking {
            val id = (playlists.createPlaylistWithSongs("Empty", emptyList()) as AppResult.Success).data
            assertTrue(playlists.observePlaylistSongs(id).first().isEmpty())
            assertTrue(playlists.createPlaylistWithSongs("   ", listOf(1L)) is AppResult.Error)
            assertEquals(1, playlists.observePlaylists().first().size)
        }

    @Test
    fun `reordering preserves occurrence identities and leaves other playlists unchanged`() = runBlocking {
        db.songDao().upsertAll(listOf(song(1), song(2), song(3), song(4)))
        val id = (playlists.createPlaylistWithSongs("Reorder", listOf(1L, 2L, 1L, 3L)) as AppResult.Success).data
        val other = (playlists.createPlaylistWithSongs("Other", listOf(1L, 2L, 3L)) as AppResult.Success).data
        assertEquals(AppResult.Success(true), playlists.reorderEntries(id, listOf(0, 1, 2, 3), 2, 0))
        val entries = playlists.observePlaylistEntries(id).first()
        assertEquals(listOf(2, 0, 1, 3), entries.map { it.position })
        assertEquals(listOf(1L, 1L, 2L, 3L), entries.map { it.song.id })
        assertEquals(listOf(1L, 2L, 3L), playlists.observePlaylistSongs(other).first().map { it.id })
        assertEquals(listOf(0, 1, 2), playlists.observePlaylistEntries(other).first().map { it.position })

        // First-to-last and last-to-first retain each duplicate's original transition identity.
        assertEquals(AppResult.Success(true), playlists.reorderEntries(id, listOf(2, 0, 1, 3), 0, 3))
        assertEquals(listOf(0, 1, 3, 2), playlists.observePlaylistEntries(id).first().map { it.position })
        assertEquals(AppResult.Success(true), playlists.addSong(id, 4))
        assertEquals(listOf(0, 1, 3, 2, 4), playlists.observePlaylistEntries(id).first().map { it.position })
        assertEquals(listOf(1L, 2L, 3L, 1L, 4L), playlists.observePlaylistSongs(id).first().map { it.id })
    }

    @Test
    fun `stale or invalid drags cannot overwrite additions removals or a newer reorder`() = runBlocking {
        db.songDao().upsertAll(listOf(song(1), song(2), song(3)))
        val id = (playlists.createPlaylistWithSongs("Checked", listOf(1L, 2L)) as AppResult.Success).data
        playlists.addSong(id, 3)
        assertEquals(AppResult.Success(false), playlists.reorderEntries(id, listOf(0, 1), 0, 1))
        assertEquals(AppResult.Success(false), playlists.reorderEntries(id, listOf(0, 1, 2), -1, 2))
        assertEquals(AppResult.Success(false), playlists.reorderEntries(id, listOf(0, 1, 2), 0, 3))
        assertEquals(AppResult.Success(true), playlists.reorderEntries(id, listOf(0, 1, 2), 0, 2))
        assertEquals(AppResult.Success(false), playlists.reorderEntries(id, listOf(0, 1, 2), 2, 0))
        playlists.removeSong(id, 2)
        assertEquals(AppResult.Success(false), playlists.reorderEntries(id, listOf(1, 2, 0), 0, 1))
        assertEquals(listOf(2, 0), playlists.observePlaylistEntries(id).first().map { it.position })
        assertEquals(AppResult.Success(true), playlists.reorderEntries(id, listOf(2, 0), 1, 1))
        assertEquals(listOf(2, 0), playlists.observePlaylistEntries(id).first().map { it.position })
    }

    @Test
    fun `reordered playlist survives closing and reopening the database`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "playlist-reorder-persistence.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, ErhuDatabase::class.java, name).allowMainThreadQueries().build()
        fun repository(database: ErhuDatabase) =
            PlaylistRepositoryImpl(database.playlistDao(), database.recentlyPlayedDao(), PlaylistArtworkStore(artworkDir)) { time++ }
        try {
            val id = open().let { disk ->
                try {
                    disk.songDao().upsertAll(listOf(song(1), song(2), song(3)))
                    val repo = repository(disk)
                    val created = (repo.createPlaylistWithSongs("Persistent", listOf(1L, 2L, 1L, 3L)) as AppResult.Success).data
                    assertEquals(AppResult.Success(true), repo.reorderEntries(created, listOf(0, 1, 2, 3), 3, 0))
                    created
                } finally { disk.close() }
            }
            open().let { disk ->
                try {
                    val loaded = repository(disk).observePlaylistEntries(id).first()
                    assertEquals(listOf(3, 0, 1, 2), loaded.map { it.position })
                    assertEquals(listOf(3L, 1L, 2L, 1L), loaded.map { it.song.id })
                } finally { disk.close() }
            }
        } finally { context.deleteDatabase(name) }
    }
}
