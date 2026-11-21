package org.simpmusic.radio.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.simpmusic.radio.local.LocalSong

/** Pick which folders of the phone count as "My music". Unticked folders are hidden everywhere in the Local tab. */
@Composable
internal fun LocalFolderDialog(
    all: List<LocalSong>,
    excluded: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit,
) {
    val folders = remember(all) { all.groupBy { it.folder }.map { (f, s) -> f to s.size }.sortedBy { it.first.lowercase() } }
    var off by remember { mutableStateOf(excluded) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Music folders") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { off = emptySet() }) { Text("All") }
                    TextButton(onClick = { off = folders.map { it.first }.toSet() }) { Text("None") }
                }
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(folders, key = { it.first }) { (folder, count) ->
                        val on = folder !in off
                        Row(
                            Modifier.fillMaxWidth().clickable { off = if (on) off + folder else off - folder }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = on, onCheckedChange = { off = if (it) off - folder else off + folder })
                            Column(Modifier.weight(1f)) {
                                Text(folder.ifBlank { "Other" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("$count songs", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(off) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
