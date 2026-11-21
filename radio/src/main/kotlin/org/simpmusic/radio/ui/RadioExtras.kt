package org.simpmusic.radio.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.simpmusic.radio.data.IcyReader
import org.simpmusic.radio.data.Podcast
import org.simpmusic.radio.data.PodcastApi
import org.simpmusic.radio.data.PodcastEpisode
import org.simpmusic.radio.data.RadioAlarm
import org.simpmusic.radio.data.RadioBrowserApi
import org.simpmusic.radio.data.RadioNow
import org.simpmusic.radio.data.RadioPlayer
import org.simpmusic.radio.data.RadioStation
import org.simpmusic.radio.data.RadioStore

/** What the playing station says is on air, with a share button. Shown only while a station plays. */
@Composable
internal fun NowPlayingCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val station by RadioNow.station.collectAsState()
    val s = station ?: return
    var title by remember(s.uuid) { mutableStateOf<String?>(null) }
    LaunchedEffect(s.uuid) {
        while (true) {
            if (!s.streamUrl.contains(".m3u8", true)) title = IcyReader.nowPlaying(s.streamUrl) ?: title
            delay(20_000)
        }
    }
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StationArt(s, Modifier.size(52.dp), 12.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("ON AIR · ${s.name}", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(title ?: "Live radio", color = Ink, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text("⇪", color = Ink, fontSize = 22.sp, modifier = Modifier.clip(RoundedCornerShape(50)).clickable { shareStation(context, s) }.padding(10.dp))
    }
}

private val SLEEP_MINUTES = listOf(15, 30, 45, 60, 90)

/** Sleep timer and wake-up alarm. */
@Composable
internal fun ToolsDialog(
    handler: MediaPlayerHandler,
    store: RadioStore,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sleep by handler.sleepTimerState.collectAsState()
    val station = remember { store.lastStation() ?: store.favourites.value.firstOrNull() }
    val existing = remember { RadioAlarm.current(context) }
    var hour by remember { mutableIntStateOf(existing?.hour ?: 7) }
    var minute by remember { mutableIntStateOf(existing?.minute ?: 0) }
    var daily by remember { mutableStateOf(existing?.daily ?: true) }
    var armed by remember { mutableStateOf(existing != null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Sleep timer & alarm") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Sleep timer", fontWeight = FontWeight.SemiBold)
                if (sleep.timeRemaining > 0) Text("Stops in ${sleep.timeRemaining} min", color = Mint)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SLEEP_MINUTES) { m -> Chip("$m min", sleep.timeRemaining == m) { handler.sleepStart(m) } }
                    item { Chip("Off", false) { handler.sleepStop() } }
                }
                Spacer(Modifier.height(6.dp))
                Text("Alarm", fontWeight = FontWeight.SemiBold)
                if (station == null) {
                    Text("Play or favourite a station first. The alarm wakes you with it.", color = Muted, fontSize = 13.sp)
                } else {
                    Text("Wakes you with ${station.name}", color = Muted, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Stepper("%02d".format(hour), { hour = (hour + 23) % 24 }, { hour = (hour + 1) % 24 })
                        Text(":", fontSize = 22.sp)
                        Stepper("%02d".format(minute), { minute = (minute + 55) % 60 }, { minute = (minute + 5) % 60 })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("Every day", daily) { daily = !daily }
                        Chip(if (armed) "Update alarm" else "Set alarm", true) {
                            RadioAlarm.set(context, RadioAlarm.Alarm(hour, minute, daily, station))
                            armed = true
                        }
                        if (armed) Chip("Cancel", false) { RadioAlarm.cancel(context); armed = false }
                    }
                }
            }
        },
    )
}

@Composable
private fun Stepper(
    text: String,
    down: () -> Unit,
    up: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("▲", modifier = Modifier.clickable(onClick = up).padding(8.dp))
        Text(text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Text("▼", modifier = Modifier.clickable(onClick = down).padding(8.dp))
    }
}

@SuppressLint("MissingPermission")
private suspend fun findLocation(context: Context): Location? {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers = lm.getProviders(true)
    val last = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
    if (last != null) return last
    if (Build.VERSION.SDK_INT >= 30 && providers.isNotEmpty()) {
        return suspendCancellableCoroutine { c ->
            val signal = android.os.CancellationSignal()
            c.invokeOnCancellation { signal.cancel() }
            lm.getCurrentLocation(providers.first(), signal, ContextCompat.getMainExecutor(context)) { c.resume(it) }
        }
    }
    return null
}

/** Stations close to the phone's location. */
@Composable
internal fun NearMePage(
    api: RadioBrowserApi,
    store: RadioStore,
    player: RadioPlayer,
    modeState: androidx.compose.runtime.MutableIntState,
    bottomPadding: Dp,
    back: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val perm = Manifest.permission.ACCESS_COARSE_LOCATION
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var state by remember { mutableStateOf<Load<List<RadioStation>>>(Load.Loading) }
    LaunchedEffect(granted) {
        if (!granted) return@LaunchedEffect
        state = Load.Loading
        state =
            runCatching {
                val loc = findLocation(context) ?: error("No location yet. Turn on location and try again")
                Load.Ready(api.near(loc.latitude, loc.longitude))
            }.getOrElse { Load.Failed(it.message ?: "Offline") }
    }
    Column(Modifier.fillMaxSize().padding(top = 48.dp)) {
        PageHeader("Near me", back) { if (granted) ViewModeButton(ViewMode.values()[modeState.intValue]) { modeState.intValue = it.ordinal; store.viewMode = it.ordinal } }
        if (!granted) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Allow approximate location to find stations around you. It stays on your phone.", color = Muted, fontSize = 15.sp)
                Spacer(Modifier.height(16.dp))
                Chip("Allow location", true) { launcher.launch(perm) }
            }
            return@Column
        }
        when (val s = state) {
            Load.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Mint) }
            is Load.Failed -> Text(s.message, color = Muted, modifier = Modifier.padding(20.dp))
            is Load.Ready ->
                if (s.value.isEmpty()) Text("No stations found near you.", color = Muted, modifier = Modifier.padding(20.dp))
                else StationCollection(s.value, store, modeState, bottomPadding) { i -> scope.launch { player.play(s.value, i, "Near me") } }
        }
    }
}

private val PODCAST_TOPICS = listOf("News", "Comedy", "Health", "Technology", "Business", "Spirituality", "Music", "Education", "Sports", "History")

/** Search podcasts, pick a show, play its episodes. */
@Composable
internal fun PodcastsPage(
    player: RadioPlayer,
    bottomPadding: Dp,
    back: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val api = remember { PodcastApi() }
    var query by remember { mutableStateOf("") }
    var shows by remember { mutableStateOf<Load<List<Podcast>>?>(null) }
    var show by remember { mutableStateOf<Podcast?>(null) }
    var episodes by remember { mutableStateOf<Load<List<PodcastEpisode>>>(Load.Loading) }
    fun search(term: String) {
        if (term.isBlank()) return
        shows = Load.Loading
        scope.launch { shows = runCatching { Load.Ready(api.search(term.trim())) }.getOrElse { Load.Failed(it.message ?: "Offline") } }
    }
    LaunchedEffect(Unit) { search("popular") }
    LaunchedEffect(show) {
        val p = show ?: return@LaunchedEffect
        episodes = Load.Loading
        episodes = runCatching { Load.Ready(api.episodes(p)) }.getOrElse { Load.Failed(it.message ?: "Couldn't read this podcast") }
    }
    androidx.activity.compose.BackHandler(enabled = show != null) { show = null }
    Column(Modifier.fillMaxSize().padding(top = 48.dp)) {
        PageHeader(show?.name ?: "Podcasts", { if (show != null) show = null else back() })
        val p = show
        if (p == null) {
            Box(Modifier.padding(horizontal = 20.dp)) { SearchBox(query, { query = it }, "Search podcasts") { search(query) } }
            Spacer(Modifier.height(10.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PODCAST_TOPICS) { t -> Chip(t, false) { query = t; search(t) } }
            }
            Spacer(Modifier.height(8.dp))
            when (val s = shows) {
                null, Load.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Mint) }
                is Load.Failed -> Text("Couldn't search (${s.message}).", color = Muted, modifier = Modifier.padding(20.dp))
                is Load.Ready ->
                    LazyColumn(contentPadding = PaddingValues(bottom = bottomPadding)) {
                        items(s.value) { pod ->
                            Row(Modifier.fillMaxWidth().clickable { show = pod }.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(pod.artwork, null, Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(Surface))
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(pod.name, color = Ink, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(pod.author, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
            }
        } else {
            when (val e = episodes) {
                Load.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Mint) }
                is Load.Failed -> Text(e.message, color = Muted, modifier = Modifier.padding(20.dp))
                is Load.Ready ->
                    LazyColumn(contentPadding = PaddingValues(bottom = bottomPadding)) {
                        itemsIndexed(e.value) { i, ep ->
                            Row(Modifier.fillMaxWidth().clickable { scope.launch { player.playEpisodes(e.value, i, p.name) } }.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(ep.title, color = Ink, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    val mins = ep.durationSeconds?.let { "${it / 60} min" }
                                    Text(listOfNotNull(ep.published.ifBlank { null }, mins).joinToString(" · "), color = Muted, fontSize = 12.sp)
                                }
                            }
                        }
                    }
            }
        }
    }
}
