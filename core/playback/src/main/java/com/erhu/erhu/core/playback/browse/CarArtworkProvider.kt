package com.erhu.erhu.core.playback.browse

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.erhu.erhu.core.common.artwork.ArtworkLoader
import com.erhu.erhu.core.common.permission.PermissionManager
import com.erhu.erhu.core.database.repository.LibraryRepository
import com.erhu.erhu.core.database.repository.PlaylistRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException

/** Read-only, explicitly granted artwork URIs. Never exposes audio or arbitrary filesystem paths. */
class CarArtworkProvider : ContentProvider() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun library(): LibraryRepository

        fun playlists(): PlaylistRepository

        fun permissions(): PermissionManager
    }

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "image/png"

    @Suppress("CyclomaticComplexMethod")
    override fun openFile(
        uri: Uri,
        mode: String,
    ): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Artwork is read-only")
        val app = context ?: throw FileNotFoundException("Artwork unavailable")
        val parts = uri.pathSegments
        if (uri.authority != "${app.packageName}.car.artwork" || parts.size != 2) throw FileNotFoundException("Invalid artwork URI")
        val id = parts[1].toLongOrNull() ?: throw FileNotFoundException("Invalid artwork ID")
        val dependencies = EntryPointAccessors.fromApplication(app, Dependencies::class.java)
        if (!dependencies.permissions().hasAudioPermission()) throw FileNotFoundException("Music access required")
        val data =
            runBlocking(Dispatchers.IO) {
                val library = dependencies.library()
                val bitmap =
                    when (parts[0]) {
                        "song" -> library.getSongById(id).first()?.let { ArtworkLoader.load(app, it.uri, it.artworkUri, ARTWORK_SIZE) }
                        "album" ->
                            library
                                .getSongsByAlbumId(
                                    id,
                                ).first()
                                .firstOrNull()
                                ?.let { ArtworkLoader.load(app, it.uri, it.artworkUri, ARTWORK_SIZE) }
                        "artist" -> image(app, library.getArtistById(id).first()?.artworkUri)
                        "playlist" ->
                            image(
                                app,
                                dependencies
                                    .playlists()
                                    .observePlaylist(id)
                                    .first()
                                    ?.artworkUri,
                            )
                        else -> null
                    } ?: throw FileNotFoundException("No artwork")
                val largest = maxOf(bitmap.width, bitmap.height)
                val bounded =
                    if (largest > ARTWORK_SIZE) {
                        val scale = ARTWORK_SIZE.toFloat() / largest
                        Bitmap.createScaledBitmap(
                            bitmap,
                            (bitmap.width * scale).toInt().coerceAtLeast(1),
                            (bitmap.height * scale).toInt().coerceAtLeast(1),
                            true,
                        )
                    } else {
                        bitmap
                    }
                ByteArrayOutputStream().use { output ->
                    bounded.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, output)
                    output.toByteArray()
                }
            }
        return openPipeHelper(uri, "image/png", null, data) { output, _, _, _, bytes ->
            ParcelFileDescriptor.AutoCloseOutputStream(output).use { it.write(bytes) }
        }
    }

    private suspend fun image(
        context: Context,
        uri: String?,
    ): Bitmap? = uri?.let { ArtworkLoader.load(context, it, it, ARTWORK_SIZE) }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = throw UnsupportedOperationException("Artwork is read-only")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("Artwork is read-only")

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("Artwork is read-only")

    companion object {
        private const val ARTWORK_SIZE = 512

        fun uri(
            context: Context,
            kind: String,
            id: Long,
        ): Uri =
            Uri
                .Builder()
                .scheme("content")
                .authority("${context.packageName}.car.artwork")
                .appendPath(kind)
                .appendPath(id.toString())
                .build()

        fun rootUri(context: Context): Uri =
            Uri
                .Builder()
                .scheme("content")
                .authority("${context.packageName}.car.artwork")
                .build()
    }
}

private const val PNG_QUALITY = 100
