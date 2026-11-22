package org.simpmusic.radio.ui

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import org.simpmusic.radio.local.LocalLibrary
import org.simpmusic.radio.local.LocalSong

/** Asks to permanently delete these songs' files; provided by [ProvideLocalDeleter]. */
internal val LocalDeleter = compositionLocalOf<(List<LocalSong>, String) -> Unit> { { _, _ -> } }

/**
 * Confirmation dialog + the system's delete flow. Android 11+ shows its own "allow deleting" sheet
 * for files we did not create; older versions delete directly (Android 10 may ask per file).
 */
@Composable
internal fun ProvideLocalDeleter(library: LocalLibrary, onDeleted: (Set<Long>) -> Unit, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<Pair<List<LocalSong>, String>?>(null) }
    var inFlight by remember { mutableStateOf<List<LocalSong>>(emptyList()) }

    fun finish(songs: List<LocalSong>) {
        val ids = songs.map { it.id }.toSet()
        library.forget(ids)
        onDeleted(ids)
        inFlight = emptyList()
        Toast.makeText(context, "Deleted ${songs.size} ${if (songs.size == 1) "song" else "songs"} from this phone", Toast.LENGTH_SHORT).show()
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) finish(inFlight) else inFlight = emptyList()
    }

    fun run(songs: List<LocalSong>) {
        val uris = songs.map { ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, it.id) }
        inFlight = songs
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                val sender = MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender
                launcher.launch(IntentSenderRequest.Builder(sender).build())
            } else {
                var done = true
                for (u in uris) {
                    try {
                        context.contentResolver.delete(u, null, null)
                    } catch (e: SecurityException) {
                        val rse = e as? RecoverableSecurityException
                        if (Build.VERSION.SDK_INT >= 29 && rse != null) {
                            launcher.launch(IntentSenderRequest.Builder(rse.userAction.actionIntent.intentSender).build())
                        } else Toast.makeText(context, "Android would not let this app delete that file", Toast.LENGTH_LONG).show()
                        done = false
                        break
                    }
                }
                if (done) finish(songs)
            }
        } catch (e: Exception) {
            inFlight = emptyList()
            Toast.makeText(context, "Couldn't delete: ${e.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
        }
    }

    pending?.let { (songs, what) ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Delete from device?") },
            text = { Text("$what will be deleted from this phone for good. This can't be undone.") },
            confirmButton = { TextButton(onClick = { pending = null; run(songs) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
        )
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalDeleter provides { s, w -> if (s.isNotEmpty()) pending = s to w }) { content() }
}
