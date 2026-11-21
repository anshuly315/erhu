package org.simpmusic.radio.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.simpmusic.radio.local.LocalPlayer
import org.simpmusic.radio.local.LocalSong
import org.simpmusic.radio.vybe.VybeEngine
import org.simpmusic.radio.vybe.VybeStatus
import org.simpmusic.radio.vybe.core.VybeAnswer

/** VYBE: describe what you feel like hearing and your own songs are ranked by how close they are. */
@Composable
internal fun VybePage(
    songs: List<LocalSong>,
    player: LocalPlayer,
    bottomPadding: Dp,
    back: () -> Unit,
) {
    val context = LocalContext.current
    val engine = remember { VybeEngine.get(context) }
    val status by engine.status.collectAsState()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var heading by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<LocalSong>>(emptyList()) }
    val byId = remember(songs) { songs.associateBy { it.id } }
    LaunchedEffect(songs) { engine.sync(songs) }

    fun run() {
        val q = query.trim()
        if (q.isEmpty()) return
        scope.launch {
            searching = true
            val a = engine.search(q)
            searching = false
            val (ids, note) =
                when (a) {
                    null -> emptyList<Long>() to "VYBE isn't ready yet."
                    is VybeAnswer.Named -> (a.songIds + a.similar.map { it.songId }).distinct() to "Matches for “${a.name}”"
                    is VybeAnswer.Described ->
                        a.hits.map { it.songId } to (a.error ?: if (a.weak) "Not a strong match. Songs here carry few genre tags, so the order is a guess." else "Closest to “${a.query}”")
                    is VybeAnswer.References -> a.hits.map { it.songId } to (if (a.hits.isEmpty()) "Couldn't find that in your music." else "Songs like “${a.query}”")
                    is VybeAnswer.SimilarTo -> a.hits.map { it.songId } to "Similar songs"
                }
            results = ids.mapNotNull { byId[it] }
            heading = note
        }
    }

    Column(Modifier.fillMaxSize().padding(top = 48.dp)) {
        PageHeader("VYBE", back)
        when (val s = status) {
            VybeStatus.NoModel ->
                Column(Modifier.fillMaxWidth().padding(24.dp)) {
                    Text("Search your music by how it feels", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "VYBE needs a one-time download of Google's EmbeddingGemma model (about 184 MB). It runs on your phone and nothing about your music is sent anywhere. The model is provided under Google's Gemma Terms of Use.",
                        color = Muted, fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(16.dp))
                    Chip("Download model", true) { engine.downloadModel() }
                }
            is VybeStatus.Downloading ->
                Column(Modifier.fillMaxWidth().padding(24.dp)) {
                    Text(if (s.verifying) "Checking the download…" else "Downloading model… ${s.bytes / 1_000_000} of ${s.total / 1_000_000} MB", color = Ink, fontSize = 15.sp)
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(progress = { (s.bytes.toFloat() / s.total).coerceIn(0f, 1f) }, color = Mint, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    if (!s.verifying) Chip("Cancel", false) { engine.cancelDownload() }
                    Text("Keep the app open until it finishes.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                }
            VybeStatus.LoadingModel ->
                Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = Mint, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp)); Text("Loading the model…", color = Muted)
                }
            is VybeStatus.Failed ->
                Column(Modifier.fillMaxWidth().padding(24.dp)) {
                    Text(s.message, color = Ink, fontSize = 15.sp)
                    Spacer(Modifier.height(12.dp))
                    Chip("Try again", true) { if (java.io.File(context.filesDir, "vybe/embedding_gemma.task").isFile) engine.sync(songs) else engine.downloadModel() }
                }
            else -> {
                Box(Modifier.padding(horizontal = 20.dp)) { SearchBox(query, { query = it }, "Try: late night drive, gym, calm piano") { run() } }
                if (s is VybeStatus.Indexing) {
                    Text("Learning your library… ${s.done} of ${s.total}. Search works on what's done so far.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                }
                if (searching) Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator(color = Mint) }
                heading?.let { Text(it, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
                if (results.isNotEmpty()) {
                    Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("▶ Play all", true) { scope.launch { player.play(results, 0, "VYBE") } }
                        Chip("Shuffle", false) { scope.launch { player.play(results.shuffled(), 0, "VYBE") } }
                    }
                }
                LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = bottomPadding)) {
                    itemsIndexed(results, key = { _, s -> s.id }) { i, song ->
                        Column(Modifier.fillMaxWidth().clickable { scope.launch { player.play(results, i, "VYBE") } }.padding(vertical = 8.dp)) {
                            Text(song.title, color = Ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.artist + (song.genre?.let { " · $it" } ?: ""), color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
