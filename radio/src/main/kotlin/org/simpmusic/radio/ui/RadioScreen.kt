package org.simpmusic.radio.ui

import androidx.activity.compose.BackHandler
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

private val Bg = Color(0xFF0E1110)
private val Surface = Color(0xFF1D2220)
private val Mint = Color(0xFFA9E5C0)
private val OnMint = Color(0xFF0B2A1A)
private val Muted = Color(0xFF9BA6A0)
private val Ink = Color(0xFFF2F5F3)

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
            Modifier.clip(RoundedCornerShape(50)).background(Color(0x14FFFFFF)).clickable { open = true }
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
}

private sealed interface Load<out T> {
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
    modifier: Modifier = Modifier,
    bottomPadding: androidx.compose.ui.unit.Dp = 120.dp,
) {
    val stack = remember { mutableStateListOf<Page>(Page.Home) }
    val page = stack.last()
    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }
    Box(modifier.fillMaxSize().background(Bg)) {
        when (page) {
            Page.Home -> HomePage(api, store, player, bottomPadding) { stack.add(it) }
            is Page.Stations -> StationListPage(page.title, page.load, api, store, player, bottomPadding) { stack.removeAt(stack.lastIndex) }
            is Page.Facets -> FacetPage(page.title, page.kind, api, bottomPadding, { stack.removeAt(stack.lastIndex) }) { stack.add(it) }
            Page.Favourites -> FavouritesPage(api, store, player, bottomPadding) { stack.removeAt(stack.lastIndex) }
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
                    RandomButton { playRandom(scope, api, player) }
                }
                Spacer(Modifier.height(16.dp))
                SearchBox(query, { query = it }) {
                    if (query.isNotBlank()) open(Page.Stations("\"$query\"") { api.search(query.trim()) })
                }
                Spacer(Modifier.height(14.dp))
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Chip("Favourites", true) { open(Page.Favourites) } }
                item { Chip("Countries", false) { open(Page.Facets("Countries", "country")) } }
                item { Chip("Languages", false) { open(Page.Facets("Languages", "language")) } }
                item { Chip("Genres", false) { open(Page.Facets("Genres", "tag")) } }
                item { Chip("All stations", false) { open(Page.Stations("All stations") { api.popular(1000) }) } }
                item { Chip("Trending", false) { open(Page.Stations("Trending now") { api.trending(120) }) } }
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
                    StationList(sorted, store, bottomPadding) { i -> scope.launch { player.play(sorted, i, title) } }
                }
        }
    }
}

@Composable
private fun FavouritesPage(
    api: RadioBrowserApi,
    store: RadioStore,
    player: RadioPlayer,
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
            StationList(list, store, bottomPadding) { i -> scope.launch { player.play(list, i, if (showHistory) "Recently played" else "Favourites") } }
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
                                Box(Modifier.size(36.dp).clip(CircleShape).background(Surface), Alignment.Center) {
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
private fun StationList(
    stations: List<RadioStation>,
    store: RadioStore,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onPlay: (Int) -> Unit,
) {
    val favs by store.favourites.collectAsState()
    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding)) {
        itemsIndexed(stations) { i, s ->
            Row(Modifier.fillMaxWidth().clickable { onPlay(i) }.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                StationArt(s, Modifier.size(52.dp), 14.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.name, color = Ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOf(s.subtitle, s.quality).filter { it.isNotBlank() }.joinToString(" · "), color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val fav = favs.any { it.uuid == s.uuid }
                Text(
                    if (fav) "♥" else "♡",
                    color = if (fav) Color(0xFFFF8FB1) else Muted,
                    fontSize = 24.sp,
                    modifier = Modifier.clip(CircleShape).clickable { store.toggleFavourite(s) }.padding(10.dp),
                )
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
private fun PageHeader(
    title: String,
    back: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("←", color = Ink, fontSize = 24.sp, modifier = Modifier.clip(CircleShape).clickable(onClick = back).padding(12.dp))
        Text(title.replaceFirstChar { it.uppercase() }, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun Chip(
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

@Composable
private fun SearchBox(
    value: String,
    onChange: (String) -> Unit,
    hint: String = "Search stations, countries, genres",
    onSearch: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(Surface).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
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
