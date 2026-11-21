package org.simpmusic.radio.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.maxrave.domain.mediaservice.automix.TransitionOverrides
import kotlin.math.roundToInt

private enum class Mode { DEFAULT, OFF, CUSTOM }

/**
 * Transition Studio, scaled to the player's own crossfade: this album's fades and the fade from this
 * song into the next can each keep the global setting, turn off, or use their own length. Needs
 * Crossfade switched on in Settings; these settings only change what it does for these songs.
 */
@Composable
fun TransitionStudioDialog(
    trackId: String,
    nextTrackId: String?,
    onDismiss: () -> Unit,
) {
    val collection = remember(trackId) { TransitionOverrides.collectionOf(trackId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transitions") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                if (collection != null) {
                    OverrideEditor(
                        label = "This album",
                        hint = "Fades between songs of this album",
                        current = TransitionOverrides.collectionOverride(collection),
                    ) { TransitionOverrides.setCollection(collection, it) }
                }
                if (nextTrackId != null) {
                    OverrideEditor(
                        label = "This song into the next",
                        hint = "Only this pair; beats the album setting",
                        current = TransitionOverrides.pairOverride(trackId, nextTrackId),
                    ) { TransitionOverrides.setPair(trackId, nextTrackId, it) }
                } else {
                    Text("No next song in the queue to set a transition into.", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "Crossfade must be on in Settings. Auto mode is replaced by a fixed length where you set one.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun OverrideEditor(
    label: String,
    hint: String,
    current: Int?,
    onChange: (Int?) -> Unit,
) {
    var mode by remember {
        mutableStateOf(
            when {
                current == null -> Mode.DEFAULT
                current == TransitionOverrides.OFF -> Mode.OFF
                else -> Mode.CUSTOM
            },
        )
    }
    var seconds by remember { mutableFloatStateOf(if (current != null && current > 0) current / 1000f else 5f) }
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Text(hint, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            FilterChip(selected = mode == Mode.DEFAULT, onClick = { mode = Mode.DEFAULT; onChange(null) }, label = { Text("Default") })
            FilterChip(selected = mode == Mode.OFF, onClick = { mode = Mode.OFF; onChange(TransitionOverrides.OFF) }, label = { Text("No fade") })
            FilterChip(
                selected = mode == Mode.CUSTOM,
                onClick = { mode = Mode.CUSTOM; onChange((seconds * 1000).roundToInt()) },
                label = { Text("Custom") },
            )
        }
        if (mode == Mode.CUSTOM) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = seconds,
                    onValueChange = { seconds = it.roundToInt().coerceIn(1, 15).toFloat() },
                    onValueChangeFinished = { onChange((seconds * 1000).roundToInt()) },
                    valueRange = 1f..15f,
                    steps = 13,
                    modifier = Modifier.weight(1f),
                )
                Text("${seconds.roundToInt()} s", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
