package org.simpmusic.radio.ui

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.simpmusic.radio.local.LocalTagEditor
import org.simpmusic.radio.local.LocalTags
import org.simpmusic.radio.local.TagSuggestion

/** Edits the tags and cover of the one file [trackId] points at. Nothing here runs on its own or in bulk. */
@Composable
fun LocalTagEditorDialog(
    trackId: String,
    onDismiss: () -> Unit,
) {
    val context: Context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mediaId = remember(trackId) { LocalTagEditor.mediaIdOf(trackId) }
    if (mediaId == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val uri: Uri = remember(mediaId) { LocalTagEditor.uriOf(mediaId) }
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var album by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<TagSuggestion>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var artUrl by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(uri) {
        LocalTagEditor.read(context, uri)?.let { title = it.title; artist = it.artist; album = it.album; genre = it.genre; year = it.year }
        loaded = true
    }

    val writeRef = remember { mutableStateOf<() -> Unit>({}) }
    // Android asks the user before another app's file may change; this is that prompt's answer.
    val consent =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
            if (res.resultCode == Activity.RESULT_OK) writeRef.value() else status = "Permission to edit the file was not given"
        }
    fun doWrite() {
        scope.launch {
            busy = true
            status = null
            val art = artUrl?.let { LocalTagEditor.download(it) }
            if (artUrl != null && art == null) {
                status = "Could not download that cover"
                busy = false
                return@launch
            }
            val r = LocalTagEditor.write(context, uri, LocalTags(title, artist, album, genre, year), art)
            busy = false
            r.onSuccess { Toast(context, "Saved"); onDismiss() }
            r.onFailure {
                val rse = it as? android.app.RecoverableSecurityException
                if (rse != null) consent.launch(IntentSenderRequest.Builder(rse.userAction.actionIntent.intentSender).build())
                else status = "Could not save: ${it.message ?: it.javaClass.simpleName}"
            }
        }
    }

    writeRef.value = ::doWrite
    fun save() {
        scope.launch {
            val sender = if (android.os.Build.VERSION.SDK_INT >= 30) LocalTagEditor.writeRequest(context, uri) else null
            if (sender != null) {
                consent.launch(IntentSenderRequest.Builder(sender).build())
            } else {
                // API 29 raises the prompt from the write itself; try, and ask on that exception.
                doWrite()
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Edit tags & artwork") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!loaded) LinearProgressIndicator(Modifier.fillMaxWidth())
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(artist, { artist = it }, label = { Text("Artist") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(album, { album = it }, label = { Text("Album") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(genre, { genre = it }, label = { Text("Genre") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(year, { year = it.filter(Char::isDigit).take(4) }, label = { Text("Year") }, singleLine = true, modifier = Modifier.width(96.dp))
                }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            searching = true
                            suggestions = LocalTagEditor.suggest(title, artist)
                            searching = false
                            if (suggestions.isEmpty()) status = "No matches found"
                        }
                    },
                    enabled = !searching && !busy && (title.isNotBlank() || artist.isNotBlank()),
                ) { Text(if (searching) "Searching…" else "Find matches online") }
                if (suggestions.isNotEmpty()) {
                    Text("Tap a match to fill the fields and use its cover", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(suggestions) { s ->
                            Column(
                                Modifier.width(120.dp).clip(RoundedCornerShape(8.dp)).clickable {
                                    title = s.title; artist = s.artist; album = s.album
                                    if (s.genre.isNotBlank()) genre = s.genre
                                    if (s.year.isNotBlank()) year = s.year
                                    artUrl = s.artwork
                                },
                            ) {
                                AsyncImage(s.artwork.replace("1200x1200bb", "300x300bb"), null, Modifier.size(120.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                                Text(s.title, maxLines = 1, style = MaterialTheme.typography.labelLarge)
                                Text(s.artist, maxLines = 1, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                if (artUrl != null) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AsyncImage(artUrl!!.replace("1200x1200bb", "300x300bb"), null, Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
                        Text("This cover will be written into the file", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { artUrl = null }) { Text("Remove") }
                    }
                }
                status?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = { save() }, enabled = loaded && !busy) { Text(if (busy) "Saving…" else "Save") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}

private fun Toast(context: Context, msg: String) = android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
