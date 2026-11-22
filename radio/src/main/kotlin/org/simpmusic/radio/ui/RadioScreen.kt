package org.simpmusic.radio.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.drawBackdrop
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.simpmusic.radio.data.RadioBrowserApi
import org.simpmusic.radio.data.RadioFacet
import org.simpmusic.radio.data.RadioPlayer
import org.simpmusic.radio.data.RadioStation
import org.simpmusic.radio.data.RadioStore
import org.simpmusic.radio.local.LocalMusic
import org.simpmusic.radio.local.LocalPlayer
import org.simpmusic.radio.local.LocalSong

internal val Bg = Color(0xFF0E1110)
internal val Surface = Color(0xFF1D2220)
internal val Mint = Color(0xFFA9E5C0)
internal val OnMint = Color(0xFF0B2A1A)
internal val Muted = Color(0xFF9BA6A0)
internal val Ink = Color(0xFFF2F5F3)

/** The backdrop the glass panels of Radio and Local refract (the ambient page background). */
internal val LocalGlassSource = androidx.compose.runtime.compositionLocalOf<com.kyant.backdrop.backdrops.LayerBackdrop?> { null }

/** Liquid glass like the SimpMusic pages: blur, vibrancy and lens over the backdrop; a flat frost when none is provided. */
@androidx.compose.runtime.Composable
internal fun Modifier.glass(shape: androidx.compose.ui.graphics.Shape): Modifier {
    val backdrop = LocalGlassSource.current
    val rim = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0x66FFFFFF), Color(0x14FFFFFF)))
    if (backdrop == null) {
        return this.clip(shape)
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0x2EFFFFFF), Color(0x12FFFFFF))))
            .border(1.dp, rim, shape)
    }
    return this.drawBackdrop(
        backdrop = backdrop,
        effects = {
            vibrancy()
            colorControls(brightness = 0.05f, contrast = 1f, saturation = 1.5f)
            blur(10f.dp.toPx())
            lens(12f.dp.toPx(), size.minDimension / 2f, true)
        },
        shape = { shape },
        onDrawSurface = { drawRect(Color.White.copy(alpha = 0.08f)) },
    ).border(1.dp, rim, shape)
}

/** The page: ambient glows are the glass source; [content] sits beside it, never inside it. */
@androidx.compose.runtime.Composable
internal fun GlassPage(modifier: Modifier = Modifier, content: @androidx.compose.runtime.Composable () -> Unit) {
    val backdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
    Box(modifier.fillMaxSize()) {
        Box(Modifier.matchParentSize().ambientBackdrop().layerBackdrop(backdrop))
        androidx.compose.runtime.CompositionLocalProvider(LocalGlassSource provides backdrop) { content() }
    }
}

/** The page backdrop: dark base with soft coloured glows so the glass has something to frost. */
internal fun Modifier.ambientBackdrop(): Modifier =
    this.background(Bg).background(
        androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0x3350C8A0), Color.Transparent), center = androidx.compose.ui.geometry.Offset(150f, 250f), radius = 900f),
    ).background(
        androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0x33607CFF), Color.Transparent), center = androidx.compose.ui.geometry.Offset(1000f, 1500f), radius = 1100f),
    )


private enum class StationSort(val label: String) {
    Default("Default order"),
    NameAsc("Name A–Z"),
    NameDesc("Name Z–A"),
    Popular("Most popular"),
    Quality("Highest bitrate"),
    Country("Country"),
    Language("Language"),
}

private fun List<RadioStation>.sortedBy(sort: StationSort): List<RadioStation> =
    when (sort) {
        StationSort.Default -> this
        StationSort.NameAsc -> sortedBy { it.name.trim().lowercase() }
        StationSort.NameDesc -> sortedByDescending { it.name.trim().lowercase() }
        StationSort.Popular -> sortedByDescending { it.votes }
        StationSort.Quality -> sortedByDescending { it.bitrate }
        StationSort.Country -> sortedWith(compareBy({ it.country.isBlank() }, { it.country.lowercase() }, { it.name.lowercase() }))
        StationSort.Language -> sortedWith(compareBy({ it.language.isBlank() }, { it.language.lowercase() }, { it.name.lowercase() }))
    }

/** Plays one random station from the whole directory. */
private fun playRandom(scope: kotlinx.coroutines.CoroutineScope, api: RadioBrowserApi, player: RadioPlayer) {
    scope.launch {
        val s = runCatching { api.random() }.getOrNull() ?: return@launch
        player.play(listOf(s), 0, "Random station")
    }
}

@Composable
private fun RandomButton(onClick: () -> Unit) {
    Text("🎲", fontSize = 22.sp, modifier = Modifier.clip(CircleShape).clickable(onClick = onClick).padding(10.dp))
}

@Composable
private fun SortButton(
    sort: StationSort,
    onSort: (StationSort) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.glass(RoundedCornerShape(50)).clickable { open = true }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Sort", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(" ▾", color = Muted, fontSize = 14.sp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            StationSort.values().forEach { o ->
                DropdownMenuItem(
                    text = { Text(if (o == sort) "✓ ${o.label}" else o.label) },
                    onClick = { onSort(o); open = false },
                )
            }
        }
    }
}

private sealed interface Page {
    data object Home : Page
    data class Stations(val title: String, val load: suspend () -> List<RadioStation>) : Page
    data class Facets(val title: String, val kind: String) : Page
    data object Favourites : Page
    data object Local : Page
    data object Near : Page
    data object Podcasts : Page
}

internal sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Failed(val message: String) : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
}

/** The Radio tab: Popular and Trending on the home page, then Countries, Languages and Genres, search, Favourites. */
@Composable
fun RadioScreen(
    api: RadioBrowserApi,
    store: RadioStore,
    player: RadioPlayer,
    localPlayer: LocalPlayer,
    modifier: Modifier = Modifier,
    bottomPadding: androidx.compose.ui.unit.Dp = 120.dp,
) {
    val stack = remember { mutableStateListOf<Page>(Page.Home) }
    val modeState = rememberViewMode(store)
    val page = stack.last()
    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }
    GlassPage(modifier) {
        when (page) {
            Page.Home -> HomePage(api, store, player, bottomPadding) { stack.add(it) }
            is Page.Stations -> StationListPage(page.title, page.load, api, store, player, modeState, bottomPadding) { stack.removeAt(stack.lastIndex) }
            is Page.Facets -> FacetPage(page.title, page.kind, api, bottomPadding, { stack.removeAt(stack.lastIndex) }) { stack.add(it) }
            Page.Favourites -> FavouritesPage(api, store, player, modeState, bottomPadding) { stack.removeAt(stack.lastIndex) }
            Page.Local -> LocalMusicPage(localPlayer, bottomPadding) { stack.removeAt(stack.lastIndex) }
            Page.Near -> NearMePage(api, store, player, modeState, bottomPadding) { stack.removeAt(stack.lastIndex) }
            Page.Podcasts -> PodcastsPage(player, bottomPadding) { stack.removeAt(stack.lastIndex) }
        }
    }
}

@Composable
private fun HomePage(
    api: RadioBrowserApi,
    store: RadioStore,
    player: RadioPlayer,
    bottomPadding: androidx.compose.ui.unit.Dp,
    open: (Page) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var popular by remember { mutableStateOf<Load<List<RadioStation>>>(Load.Loading) }
    var trending by remember { mutableStateOf<Load<List<RadioStation>>>(Load.Loading) }
    val history by store.history.collectAsState()
    var tools by remember { mutableStateOf(false) }
    if (tools) ToolsDialog(player.handler, store) { tools = false }
    LaunchedEffect(Unit) {
        popular = runCatching { Load.Ready(api.popular()) }.getOrElse { Load.Failed(it.message ?: "Offline") }
        trending = runCatching { Load.Ready(api.trending()) }.getOrElse { Load.Failed(it.message ?: "Offline") }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 48.dp, bottom = bottomPadding)) {
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Radio", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                        Text("Live stations from around the world", color = Muted, fontSize = 13.sp)
                    }
                    Text("⏲", color = Ink, fontSize = 22.sp, modifier = Modifier.clip(CircleShape).clickable { tools = true }.padding(10.dp))
                    RandomButton { playRandom(scope, api, player) }
                }
                Spacer(Modifier.height(12.dp))
                NowPlayingCard()
                Spacer(Modifier.height(12.dp))
                SearchBox(query, { query = it }) {
                    if (query.isNotBlank()) open(Page.Stations("\"$query\"") { api.search(query.trim()) })
                }
                Spacer(Modifier.height(14.dp))
            }
            var moreFilters by rememberSaveable { mutableStateOf(false) }
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Chip("Near me", true) { open(Page.Near) } }
                item { Chip("Favourites", false) { open(Page.Favourites) } }
                item { Chip("Trending", false) { open(Page.Stations("Trending now") { api.trending(120) }) } }
                item { MoreChip("Filters", moreFilters) { moreFilters = !moreFilters } }
                if (moreFilters) {
                    item { Chip("Podcasts", false) { open(Page.Podcasts) } }
                    item { Chip("Countries", false) { open(Page.Facets("Countries", "country")) } }
                    item { Chip("Languages", false) { open(Page.Facets("Languages", "language")) } }
                    item { Chip("Genres", false) { open(Page.Facets("Genres", "tag")) } }
                    item { Chip("All stations", false) { open(Page.Stations("All stations") { api.popular(1000) }) } }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        if (history.isNotEmpty()) {
            item {
                SectionTitle("Recently played")
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(history.take(15)) { i, s -> StationTile(s) { scope.launch { player.play(history.take(15), i, "Recently played") } } }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
        item { SectionTitle("Popular now") }
        item { StationRow(popular, scope, player, "Popular") }
        item { Spacer(Modifier.height(20.dp)); SectionTitle("Trending") }
        item { StationRow(trending, scope, player, "Trending") }
    }
}

@Composable
private fun StationRow(
    state: Load<List<RadioStation>>,
    scope: kotlinx.coroutines.CoroutineScope,
    player: RadioPlayer,
    queueName: String,
) {
    when (state) {
        Load.Loading -> Box(Modifier.fillMaxWidth().height(180.dp), Alignment.Center) { CircularProgressIndicator(color = Mint) }
        is Load.Failed -> Text("Couldn't load stations (${state.message}). Check your connection.", color = Muted, modifier = Modifier.padding(20.dp))
        is Load.Ready ->
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(state.value) { i, s -> StationTile(s) { scope.launch { player.play(state.value, i, queueName) } } }
            }
    }
}

@Composable
private fun StationListPage(
    title: String,
    load: suspend () -> List<RadioStation>,
    api: RadioBrowserApi,
    store: RadioStore,
    player: RadioPlayer,
    modeState: androidx.compose.runtime.MutableIntState,
    bottomPadding: androidx.compose.ui.unit.Dp,
    back: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<Load<List<RadioStation>>>(Load.Loading) }
    var sort by remember { mutableStateOf(StationSort.Default) }
    LaunchedEffect(title) { state = runCatching { Load.Ready(load()) }.getOrElse { Load.Failed(it.message ?: "Offline") } }
    Column(Modifier.fillMaxSize().padding(top = 48.dp)) {
        PageHeader(title, back) {
            SortButton(sort) { sort = it }
            RandomButton { playRandom(scope, api, player) }
        }
        when (val s = state) {
            Load.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Mint) }
            is Load.Failed -> Text("Couldn't load stations (${s.message}).", color = Muted, modifier = Modifier.padding(20.dp))
            is Load.Ready ->
                if (s.value.isEmpty()) {
                    Text("No stations found.", color = Muted, modifier = Modifier.padding(20.dp))
                } else {
                    val sorted = remember(s.value, sort) { s.value.sortedBy(sort) }
                    StationCollection(sorted, store, modeState, bottomPadding) { i -> scope.launch { player.play(sorted, i, title) } }
                }
        }
    }
}

@Composable
private fun FavouritesPage(
    api: RadioBrowserApi,
    store: RadioStore,
    player: RadioPlayer,
    modeState: androidx.compose.runtime.MutableIntState,
    bottomPadding: androidx.compose.ui.unit.Dp,
    back: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val favs by store.favourites.collectAsState()
    val history by store.history.collectAsState()
    var showHistory by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(StationSort.Default) }
    val list = remember(showHistory, history, favs, sort) { (if (showHistory) history else favs).sortedBy(sort) }
    Column(Modifier.fillMaxSize().padding(top = 48.dp)) {
        PageHeader("My stations", back) {
            SortButton(sort) { sort = it }
            RandomButton { playRandom(scope, api, player) }
        }
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Favourites", !showHistory) { showHistory = false }
            Chip("Recently played", showHistory) { showHistory = true }
        }
        Spacer(Modifier.height(8.dp))
        if (list.isEmpty()) {
            Text(if (showHistory) "Nothing played yet." else "Tap the heart on a station to keep it here.", color = Muted, modifier = Modifier.padding(20.dp))
        } else {
            StationCollection(list, store, modeState, bottomPadding) { i -> scope.launch { player.play(list, i, if (showHistory) "Recently played" else "Favourites") } }
        }
    }
}

@Composable
private fun FacetPage(
    title: String,
    kind: String,
    api: RadioBrowserApi,
    bottomPadding: androidx.compose.ui.unit.Dp,
    back: () -> Unit,
    open: (Page) -> Unit,
) {
    var state by remember { mutableStateOf<Load<List<RadioFacet>>>(Load.Loading) }
    var filter by remember { mutableStateOf("") }
    LaunchedEffect(kind) {
        state = runCatching {
            Load.Ready(
                when (kind) {
                    "country" -> api.countries()
                    "language" -> api.languages()
                    else -> api.tags()
                },
            )
        }.getOrElse { Load.Failed(it.message ?: "Offline") }
    }
    Column(Modifier.fillMaxSize().padding(top = 48.dp)) {
        PageHeader(title, back)
        Box(Modifier.padding(horizontal = 20.dp)) { SearchBox(filter, { filter = it }, hint = "Filter $title") {} }
        when (val s = state) {
            Load.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Mint) }
            is Load.Failed -> Text("Couldn't load the list (${s.message}).", color = Muted, modifier = Modifier.padding(20.dp))
            is Load.Ready ->
                LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding)) {
                    items(s.value.filter { it.name.contains(filter, true) }) { f ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                open(
                                    Page.Stations(f.name) {
                                        when (kind) {
                                            "country" -> api.byCountry(f.code)
                                            "language" -> api.byLanguage(f.code)
                                            else -> api.byTag(f.code)
                                        }
                                    },
                                )
                            }.padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (kind == "country") {
                                Box(Modifier.size(36.dp).glass(CircleShape), Alignment.Center) {
                                    Text(f.code.take(2).uppercase(), color = Ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(Modifier.width(12.dp))
                            }
                            Text(f.name.replaceFirstChar { it.uppercase() }, color = Ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            Text("${f.count}", color = Muted, fontSize = 13.sp)
                        }
                    }
                }
        }
    }
}

@Composable
private fun StationTile(
    s: RadioStation,
    onClick: () -> Unit,
) {
    Column(Modifier.width(148.dp).clickable(onClick = onClick)) {
        StationArt(s, Modifier.size(148.dp), 20.dp)
        Spacer(Modifier.height(8.dp))
        Text(s.name, color = Ink, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(s.subtitle, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 20.dp, bottom = 12.dp))
}

@Composable
internal fun PageHeader(
    title: String,
    back: (() -> Unit)?,
    trailing: @Composable () -> Unit = {},
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (back != null) Text("←", color = Ink, fontSize = 24.sp, modifier = Modifier.clip(CircleShape).clickable(onClick = back).padding(12.dp))
        else Spacer(Modifier.width(8.dp))
        Text(title.replaceFirstChar { it.uppercase() }, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
internal fun Chip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (selected) Mint else Color.Transparent)
            .then(if (selected) Modifier else Modifier.background(Color(0x14FFFFFF)))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(text, color = if (selected) OnMint else Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/** The "more" chip: tap or long-press to fold the extra chips in or out. The arrow is the hint. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun MoreChip(
    label: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Box(
        Modifier.glass(RoundedCornerShape(50))
            .combinedClickable(onClick = onToggle, onLongClick = onToggle).padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(label + if (expanded) " ▴" else " ▾", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun SearchBox(
    value: String,
    onChange: (String) -> Unit,
    hint: String = "Search stations, countries, genres",
    onSearch: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(48.dp).glass(RoundedCornerShape(24.dp)).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("⌕", color = Muted, fontSize = 20.sp)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(hint, color = Muted, fontSize = 14.sp)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = Ink, fontSize = 14.sp),
                cursorBrush = SolidColor(Mint),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}


private enum class LocalSort(val label: String) {
    Title("Title A–Z"),
    Artist("Artist"),
    Album("Album"),
    Newest("Recently added"),
    Longest("Longest first"),
}

@Composable
private fun LocalMusicPage(
    player: LocalPlayer,
    bottomPadding: androidx.compose.ui.unit.Dp,
    back: (() -> Unit)?,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val permission =
        if (android.os.Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_AUDIO else android.Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember {
        mutableStateOf(androidx.core.content.ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted = it }
    var songs by remember { mutableStateOf<List<LocalSong>?>(null) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(LocalSort.Title) }
    var sortOpen by remember { mutableStateOf(false) }
    var mixKey by remember { mutableStateOf<String?>(null) }
    val seed = remember { System.currentTimeMillis() / 3_600_000L }
    val stats = remember { org.simpmusic.radio.local.PlayStats(context) }
    var scanned by remember { mutableStateOf<List<LocalSong>?>(null) }
    var excluded by remember { mutableStateOf(org.simpmusic.radio.local.LocalFolders.excluded(context)) }
    var foldersOpen by remember { mutableStateOf(false) }
    LaunchedEffect(granted) { if (granted) scanned = LocalMusic.scan(context) }
    LaunchedEffect(scanned, excluded) { songs = scanned?.filter { it.folder !in excluded } }
    var showVybe by remember { mutableStateOf(false) }
    val loadedSongs = songs
    if (showVybe && loadedSongs != null) {
        androidx.activity.compose.BackHandler { showVybe = false }
        VybePage(loadedSongs, player, bottomPadding) { showVybe = false }
        return
    }
    Column(Modifier.fillMaxSize().padding(top = 48.dp)) {
        if (!granted) {
            PageHeader("My music", back)
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Allow access to the music on your phone to play it here.", color = Muted, fontSize = 15.sp)
                Spacer(Modifier.height(16.dp))
                Chip("Allow access", true) { launcher.launch(permission) }
            }
            return@Column
        }
        val all = songs
        if (all == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Mint) }
            return@Column
        }
        if (all.isEmpty()) {
            PageHeader("My music", back)
            Text(
                if (scanned.isNullOrEmpty()) "No music files found on this phone." else "Every folder is switched off.",
                color = Muted, modifier = Modifier.padding(20.dp),
            )
            if (!scanned.isNullOrEmpty()) Box(Modifier.padding(horizontal = 20.dp)) { Chip("Choose folders", true) { foldersOpen = true } }
            val every0 = scanned
            if (foldersOpen && every0 != null) {
                LocalFolderDialog(every0, excluded, { foldersOpen = false }) { chosen ->
                    org.simpmusic.radio.local.LocalFolders.setExcluded(context, chosen)
                    excluded = chosen
                    foldersOpen = false
                }
            }
            return@Column
        }
        val showMixes = remember { RadioStore(context).showSmartMixes }
        val mixes = remember(all, seed, showMixes) { if (showMixes) org.simpmusic.radio.local.SmartMixes.build(all, stats, seed) else emptyList() }
        LocalHome(
            all = all,
            mixes = mixes,
            player = player,
            bottomPadding = bottomPadding,
            onVybe = { showVybe = true },
            onDeleted = { ids -> scanned = scanned?.filterNot { it.id in ids } },
            sortLabel = sort.label,
            sorter = { list ->
                when (sort) {
                    LocalSort.Title -> list.sortedBy { it.title.lowercase() }
                    LocalSort.Artist -> list.sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
                    LocalSort.Album -> list.sortedWith(compareBy({ it.album.lowercase() }, { it.title.lowercase() }))
                    LocalSort.Newest -> list.sortedByDescending { it.addedAt }
                    LocalSort.Longest -> list.sortedByDescending { it.durationMs }
                }
            },
            sortMenu = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Folders", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.glass(RoundedCornerShape(50)).clickable { foldersOpen = true }.padding(horizontal = 14.dp, vertical = 8.dp),
                )
                Spacer(Modifier.width(8.dp))
                Box {
                    Row(
                        Modifier.glass(RoundedCornerShape(50)).clickable { sortOpen = true }.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Sort", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(" ▾", color = Muted, fontSize = 14.sp)
                    }
                    DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                        LocalSort.values().forEach { o ->
                            DropdownMenuItem(text = { Text(if (o == sort) "✓ ${o.label}" else o.label) }, onClick = { sort = o; sortOpen = false })
                        }
                    }
                }
                }
            },
        )
        val every = scanned
        if (foldersOpen && every != null) {
            LocalFolderDialog(every, excluded, { foldersOpen = false }) { chosen ->
                org.simpmusic.radio.local.LocalFolders.setExcluded(context, chosen)
                excluded = chosen
                foldersOpen = false
            }
        }
    }
}

@Composable
internal fun LocalArt(
    song: LocalSong,
    modifier: Modifier,
    corner: androidx.compose.ui.unit.Dp = 14.dp,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val res = remember(song.id) { org.simpmusic.radio.data.FallbackArt.resFor(context, song.id.toString()) }
    Box(modifier.clip(RoundedCornerShape(corner)).background(Surface)) {
        val fallback: @Composable () -> Unit = {
            if (res != 0) androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(res), null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        }
        coil3.compose.SubcomposeAsyncImage(
            model = song.albumArt,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            loading = { fallback() },
            error = { fallback() },
        )
    }
}

/** The Local tab: music files on the phone. */
@Composable
fun LocalMusicScreen(
    player: LocalPlayer,
    bottomPadding: androidx.compose.ui.unit.Dp = 120.dp,
    modifier: Modifier = Modifier,
) {
    GlassPage(modifier) { LocalMusicPage(player, bottomPadding, null) }
}
