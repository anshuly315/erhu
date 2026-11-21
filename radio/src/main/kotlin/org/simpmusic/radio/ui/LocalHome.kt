package org.simpmusic.radio.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.simpmusic.radio.local.LocalAlbum
import org.simpmusic.radio.local.LocalArtist
import org.simpmusic.radio.local.LocalLibrary
import org.simpmusic.radio.local.LocalPlayer
import org.simpmusic.radio.local.LocalPlaylist
import org.simpmusic.radio.local.LocalSong
import org.simpmusic.radio.local.PlayStats
import org.simpmusic.radio.local.SmartMix
import org.simpmusic.radio.local.toAlbums
import org.simpmusic.radio.local.toArtists

/** Where the Local tab is. Pages stack, so Back goes up one level. */
internal sealed interface LPage {
    data object Home : LPage
    data object Songs : LPage
    data object Albums : LPage
    data object Artists : LPage
    data object Playlists : LPage
    data object Favourites : LPage
    data object Recent : LPage
    data class Mix(val key: String) : LPage
    data class Album(val id: Long) : LPage
    data class Artist(val name: String) : LPage
    data class Playlist(val id: String) : LPage
}

private fun fmt(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

private fun hueColor(key: String, v: Float = 0.38f) = Color.hsv((key.hashCode().mod(360)).toFloat(), 0.5f, v)

/** The redesigned Local tab: smart-mix hero, shelves, then Songs / Albums / Artists / Playlists / Favourites pages. */
@Composable
internal fun LocalHome(
    all: List<LocalSong>,
    mixes: List<SmartMix>,
    player: LocalPlayer,
    bottomPadding: Dp,
    onVybe: () -> Unit,
    sortLabel: String,
    sorter: (List<LocalSong>) -> List<LocalSong>,
    sortMenu: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val library = remember { LocalLibrary.get(context) }
    val stats = remember { PlayStats(context) }
    val favIds by library.favourites.collectAsState()
    val playlists by library.playlists.collectAsState()
    val stack = remember { mutableStateListOf<LPage>(LPage.Home) }
    val page = stack.last()
    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }
    val byId = remember(all) { all.associateBy { it.id } }
    val albums = remember(all) { all.toAlbums() }
    val artists = remember(all) { all.toArtists() }
    val favourites = remember(all, favIds) { all.filter { it.id in favIds } }
    val recent = remember(all, page) { all.filter { stats.lastPlayed(it.id) > 0 }.sortedByDescending { stats.lastPlayed(it.id) }.take(40) }
    fun play(list: List<LocalSong>, i: Int, name: String) { scope.launch { player.play(list, i, name) } }
    fun go(p: LPage) { stack.add(p) }
    fun back() { stack.removeAt(stack.lastIndex) }
    fun songsOf(p: LocalPlaylist) = p.songIds.mapNotNull { byId[it] }
    var query by remember { mutableStateOf("") }
    val viewPrefs = remember { context.getSharedPreferences("local_view", android.content.Context.MODE_PRIVATE) }
    val level = remember { mutableIntStateOf(viewPrefs.getInt("level", 2).coerceIn(0, LEVEL_MAX)) }
    fun setLevel(l: Int) { level.intValue = l; viewPrefs.edit().putInt("level", l).apply() }

    when (page) {
        LPage.Home -> {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPadding)) {
                item {
                    PageHeader("My music", null) { sortMenu() }
                    Box(Modifier.padding(horizontal = 20.dp)) { SearchBox(query, { query = it }, "Search songs, albums, artists") {} }
                    Spacer(Modifier.height(12.dp))
                }
                if (query.isNotBlank()) {
                    val q = query.trim().lowercase()
                    val hits = all.filter { it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) || it.album.lowercase().contains(q) }
                    item { Heading("${hits.size} songs") }
                    itemsIndexed(hits) { i, s -> SongRow(s, library, favIds, playlists, onClick = { play(hits, i, "Search") }) }
                    return@LazyColumn
                }
                if (mixes.isNotEmpty()) item { MixHero(mixes, onOpen = { go(LPage.Mix(it.key)) }, onPlay = { play(it.songs, 0, it.title) }) }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { Chip("Songs", false) { go(LPage.Songs) } }
                        item { Chip("Albums", false) { go(LPage.Albums) } }
                        item { Chip("Artists", false) { go(LPage.Artists) } }
                        item { Chip("Playlists", false) { go(LPage.Playlists) } }
                        item { Chip("♥ Favourites", false) { go(LPage.Favourites) } }
                        item { Chip("VYBE", false) { onVybe() } }
                    }
                    Spacer(Modifier.height(20.dp))
                }
                if (recent.isNotEmpty()) item {
                    Shelf("Recently played", { go(LPage.Recent) }) {
                        itemsIndexed(recent.take(15)) { i, s ->
                            Tile({ LocalArt(s, Modifier.fillMaxWidth().aspectRatio(1f), 14.dp) }, s.title, s.artist, onClick = { play(recent, i, "Recently played") })
                        }
                    }
                }
                if (albums.isNotEmpty()) item {
                    val newest = albums.sortedByDescending { a -> a.songs.maxOf { it.addedAt } }.take(15)
                    Shelf("Albums", { go(LPage.Albums) }) {
                        items(newest, key = { it.albumId }) { a -> AlbumTile(a, Modifier.width(140.dp)) { go(LPage.Album(a.albumId)) } }
                    }
                }
                item {
                    Shelf("Playlists", { go(LPage.Playlists) }) {
                        item { NewTile(Modifier.width(140.dp)) { go(LPage.Playlists) } }
                        items(playlists, key = { it.id }) { p -> PlaylistTile(p, ::songsOf, Modifier.width(140.dp)) { go(LPage.Playlist(p.id)) } }
                    }
                }
                if (favourites.isNotEmpty()) item {
                    Shelf("Favourites", { go(LPage.Favourites) }) {
                        itemsIndexed(favourites.take(15)) { i, s ->
                            Tile({ LocalArt(s, Modifier.fillMaxWidth().aspectRatio(1f), 14.dp) }, s.title, s.artist, onClick = { play(favourites, i, "Favourites") })
                        }
                    }
                }
                if (artists.isNotEmpty()) item {
                    Shelf("Artists", { go(LPage.Artists) }) {
                        items(artists.take(15), key = { it.name }) { a ->
                            Tile({ LocalArt(a.songs.first(), Modifier.fillMaxWidth().aspectRatio(1f), 70.dp) }, a.name, "${a.songs.size} songs", onClick = { go(LPage.Artist(a.name)) })
                        }
                    }
                }
                val sorted = sorter(all)
                item {
                    Heading("Tracks")
                    TrackActions(sorted, "My music", ::play)
                }
                itemsIndexed(sorted) { i, s -> SongRow(s, library, favIds, playlists, onClick = { play(sorted, i, "My music") }) }
            }
        }
        LPage.Songs -> {
            val sorted = sorter(all)
            SongListPage("Songs", "${all.size} songs", sorted, library, favIds, playlists, level, ::setLevel, bottomPadding, ::back, ::play, "My music")
        }
        LPage.Favourites -> SongListPage("Favourites", "${favourites.size} songs", favourites, library, favIds, playlists, level, ::setLevel, bottomPadding, ::back, ::play, "Favourites", emptyText = "Tap ♡ on a song to keep it here.")
        LPage.Recent -> SongListPage("Recently played", "${recent.size} songs", recent, library, favIds, playlists, level, ::setLevel, bottomPadding, ::back, ::play, "Recently played")
        is LPage.Mix -> {
            val m = mixes.firstOrNull { it.key == page.key }
            SongListPage(m?.title ?: "Mix", "${m?.songs?.size ?: 0} songs", m?.songs.orEmpty(), library, favIds, playlists, level, ::setLevel, bottomPadding, ::back, ::play, m?.title ?: "Mix")
        }
        is LPage.Album -> {
            val a = albums.firstOrNull { it.albumId == page.id }
            val ordered = a?.songs.orEmpty().sortedWith(compareBy({ it.title.lowercase() }))
            SongListPage(a?.title ?: "Album", (a?.artist ?: "") + " · ${ordered.size} songs", ordered, library, favIds, playlists, level, ::setLevel, bottomPadding, ::back, ::play, a?.title ?: "Album", art = a?.songs?.firstOrNull())
        }
        is LPage.Artist -> {
            val a = artists.firstOrNull { it.name == page.name }
            SongListPage(a?.name ?: "Artist", "${a?.songs?.size ?: 0} songs · ${a?.albums ?: 0} albums", a?.songs.orEmpty().sortedBy { it.title.lowercase() }, library, favIds, playlists, level, ::setLevel, bottomPadding, ::back, ::play, a?.name ?: "Artist", art = a?.songs?.firstOrNull())
        }
        is LPage.Playlist -> {
            val p = playlists.firstOrNull { it.id == page.id }
            if (p == null) { LaunchedEffect(Unit) { back() } } else {
                PlaylistPage(p, songsOf(p), library, favIds, playlists, level, ::setLevel, bottomPadding, ::back, ::play)
            }
        }
        LPage.Albums -> Column(Modifier.fillMaxSize()) {
            PageHeader("Albums", ::back)
            LazyVerticalGrid(GridCells.Adaptive(150.dp), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(albums, key = { it.albumId }) { a -> AlbumTile(a, Modifier) { go(LPage.Album(a.albumId)) } }
            }
        }
        LPage.Artists -> Column(Modifier.fillMaxSize()) {
            PageHeader("Artists", ::back)
            LazyColumn(contentPadding = PaddingValues(bottom = bottomPadding)) {
                items(artists, key = { it.name }) { a ->
                    Row(Modifier.fillMaxWidth().clickable { go(LPage.Artist(a.name)) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        LocalArt(a.songs.first(), Modifier.size(52.dp), 26.dp)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(a.name, color = Ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${a.songs.size} songs · ${a.albums} albums", color = Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        LPage.Playlists -> {
            var creating by remember { mutableStateOf(false) }
            if (creating) NameDialog("New playlist", "", "Create", { creating = false }) { n -> val p = library.createPlaylist(n); creating = false; go(LPage.Playlist(p.id)) }
            Column(Modifier.fillMaxSize()) {
                PageHeader("Playlists", ::back)
                LazyVerticalGrid(GridCells.Adaptive(150.dp), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { NewTile(Modifier) { creating = true } }
                    items(playlists, key = { it.id }) { p -> PlaylistTile(p, ::songsOf, Modifier) { go(LPage.Playlist(p.id)) } }
                }
            }
        }
    }
}

@Composable
private fun Heading(text: String, more: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (more != null) Text("See all", color = Mint, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = more).padding(horizontal = 10.dp, vertical = 6.dp))
    }
}

@Composable
private fun Shelf(title: String, onMore: () -> Unit, content: LazyListScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 22.dp)) {
        Heading(title, onMore)
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun Tile(art: @Composable () -> Unit, title: String, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.width(130.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)) {
        art()
        Spacer(Modifier.height(6.dp))
        Text(title, color = Ink, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AlbumTile(a: LocalAlbum, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)) {
        LocalArt(a.songs.first(), Modifier.fillMaxWidth().aspectRatio(1f), 14.dp)
        Spacer(Modifier.height(6.dp))
        Text(a.title, color = Ink, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(a.artist, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PlaylistTile(p: LocalPlaylist, songsOf: (LocalPlaylist) -> List<LocalSong>, modifier: Modifier, onClick: () -> Unit) {
    val songs = remember(p) { songsOf(p) }
    Column(modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)) {
        PlaylistArt(p, songs, Modifier.fillMaxWidth().aspectRatio(1f))
        Spacer(Modifier.height(6.dp))
        Text(p.name, color = Ink, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${p.songIds.size} songs", color = Muted, fontSize = 11.sp)
    }
}

/** 2x2 of covers when the playlist has enough different ones, else the first cover, else a tinted note. */
@Composable
private fun PlaylistArt(p: LocalPlaylist, songs: List<LocalSong>, modifier: Modifier) {
    val covers = songs.distinctBy { it.albumId }.take(4)
    if (covers.size >= 4) {
        Column(modifier.clip(RoundedCornerShape(14.dp))) {
            Row(Modifier.weight(1f)) { LocalArt(covers[0], Modifier.weight(1f).fillMaxSize(), 0.dp); LocalArt(covers[1], Modifier.weight(1f).fillMaxSize(), 0.dp) }
            Row(Modifier.weight(1f)) { LocalArt(covers[2], Modifier.weight(1f).fillMaxSize(), 0.dp); LocalArt(covers[3], Modifier.weight(1f).fillMaxSize(), 0.dp) }
        }
    } else if (covers.isNotEmpty()) {
        LocalArt(covers[0], modifier, 14.dp)
    } else {
        Box(modifier.clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(hueColor(p.id, 0.45f), hueColor(p.id + "x", 0.25f)))), Alignment.Center) {
            Text("♪", color = Ink, fontSize = 32.sp)
        }
    }
}

@Composable
private fun NewTile(modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(Surface), Alignment.Center) { Text("+", color = Mint, fontSize = 36.sp) }
        Spacer(Modifier.height(6.dp))
        Text("New playlist", color = Ink, fontSize = 13.sp)
    }
}

/** The smart mixes as a swipeable banner. */
@Composable
private fun MixHero(mixes: List<SmartMix>, onOpen: (SmartMix) -> Unit, onPlay: (SmartMix) -> Unit) {
    val shown = remember(mixes) { mixes.take(6) }
    val state = rememberPagerState { shown.size }
    Column(Modifier.padding(bottom = 18.dp)) {
        HorizontalPager(state, contentPadding = PaddingValues(horizontal = 20.dp), pageSpacing = 12.dp) { i ->
            val m = shown[i]
            Box(
                Modifier.fillMaxWidth().height(176.dp).clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(listOf(hueColor(m.key, 0.5f), hueColor(m.key + "z", 0.2f))))
                    .clickable { onOpen(m) },
            ) {
                m.songs.firstOrNull()?.let { LocalArt(it, Modifier.align(Alignment.CenterEnd).padding(14.dp).size(148.dp), 16.dp) }
                Column(Modifier.align(Alignment.CenterStart).padding(start = 20.dp).fillMaxWidth(0.52f)) {
                    Text("MIX", color = Ink.copy(alpha = 0.7f), fontSize = 11.sp, letterSpacing = 2.sp)
                    Text(m.title, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                    Text("${m.songs.size} songs", color = Ink.copy(alpha = 0.75f), fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    Chip("▶ Play", true) { onPlay(m) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center) {
            repeat(shown.size) { i ->
                Box(Modifier.padding(3.dp).size(if (i == state.currentPage) 16.dp else 6.dp, 6.dp).clip(CircleShape).background(if (i == state.currentPage) Mint else Muted.copy(alpha = 0.4f)))
            }
        }
    }
}

@Composable
private fun TrackActions(songs: List<LocalSong>, name: String, play: (List<LocalSong>, Int, String) -> Unit) {
    Row(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Chip("▶ Play all", true) { play(songs, 0, name) }
        Chip("Shuffle", false) { play(songs.shuffled(), 0, name) }
        Text("${songs.size} songs", color = Muted, fontSize = 13.sp)
    }
}

/** The song menu with its two dialogs (add to playlist, edit tags). Shared by rows and the pinch layouts. */
@Composable
private fun SongMenu(
    song: LocalSong,
    open: Boolean,
    close: () -> Unit,
    library: LocalLibrary,
    favIds: Set<Long>,
    playlists: List<LocalPlaylist>,
    extra: @Composable (() -> Unit) -> Unit = {},
) {
    var adding by remember { mutableStateOf(false) }
    var tags by remember { mutableStateOf(false) }
    val fav = song.id in favIds
    if (adding) AddToPlaylistDialog(listOf(song.id), library, playlists) { adding = false }
    if (tags) LocalTagEditorDialog(song.trackId) { tags = false }
    DropdownMenu(expanded = open, onDismissRequest = close) {
        DropdownMenuItem(text = { Text(if (fav) "Remove from favourites" else "Add to favourites") }, onClick = { library.toggleFavourite(song.id); close() })
        DropdownMenuItem(text = { Text("Add to playlist") }, onClick = { close(); adding = true })
        DropdownMenuItem(text = { Text("Edit tags & artwork") }, onClick = { close(); tags = true })
        extra(close)
    }
}

/** One song row (Home's track list): tap to play, heart, and the song menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongRow(
    song: LocalSong,
    library: LocalLibrary,
    favIds: Set<Long>,
    playlists: List<LocalPlaylist>,
    onClick: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val fav = song.id in favIds
    Row(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = { menu = true }).padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        LocalArt(song, Modifier.size(46.dp), 10.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, color = Ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist + if (song.album.isNotBlank()) " · ${song.album}" else "", color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(fmt(song.durationMs), color = Muted, fontSize = 12.sp)
        Text(if (fav) "♥" else "♡", color = if (fav) Color(0xFFFF8FB1) else Muted, fontSize = 22.sp, modifier = Modifier.clip(CircleShape).clickable { library.toggleFavourite(song.id) }.padding(8.dp))
        Box {
            Text("⋮", color = Muted, fontSize = 20.sp, modifier = Modifier.clip(CircleShape).clickable { menu = true }.padding(horizontal = 10.dp, vertical = 6.dp))
            SongMenu(song, menu, { menu = false }, library, favIds, playlists)
        }
    }
}

/** Songs in the six-level pinch layout, with heart and the song menu. */
@Composable
private fun SongPinch(
    songs: List<LocalSong>,
    library: LocalLibrary,
    favIds: Set<Long>,
    playlists: List<LocalPlaylist>,
    level: MutableIntState,
    onLevel: (Int) -> Unit,
    bottomPadding: Dp,
    queueName: String,
    play: (List<LocalSong>, Int, String) -> Unit,
    extra: @Composable (LocalSong, () -> Unit) -> Unit = { _, _ -> },
) {
    PinchCollection(
        items = songs,
        level = level,
        bottomPadding = bottomPadding,
        onLevel = onLevel,
        title = { it.title },
        subtitle = { it.artist + if (it.album.isNotBlank()) " · ${it.album}" else "" },
        art = { s, m, c -> LocalArt(s, m, c) },
        trailing = { s ->
            val fav = s.id in favIds
            Text(if (fav) "♥" else "♡", color = if (fav) Color(0xFFFF8FB1) else Muted, fontSize = 22.sp, modifier = Modifier.clip(CircleShape).clickable { library.toggleFavourite(s.id) }.padding(8.dp))
        },
        menu = { s, open, close -> SongMenu(s, open, close, library, favIds, playlists) { c -> extra(s, c) } },
        onClick = { i -> play(songs, i, queueName) },
    )
}

@Composable
private fun SongListPage(
    title: String,
    subtitle: String,
    songs: List<LocalSong>,
    library: LocalLibrary,
    favIds: Set<Long>,
    playlists: List<LocalPlaylist>,
    level: MutableIntState,
    onLevel: (Int) -> Unit,
    bottomPadding: Dp,
    back: () -> Unit,
    play: (List<LocalSong>, Int, String) -> Unit,
    queueName: String,
    art: LocalSong? = null,
    emptyText: String = "Nothing here yet.",
) {
    Column(Modifier.fillMaxSize()) {
        PageHeader(title, back)
        Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (art != null) { LocalArt(art, Modifier.size(72.dp), 14.dp); Spacer(Modifier.width(14.dp)) }
            Text(subtitle, color = Muted, fontSize = 13.sp)
        }
        if (songs.isEmpty()) {
            Text(emptyText, color = Muted, modifier = Modifier.padding(20.dp))
        } else {
            TrackActions(songs, queueName, play)
            Box(Modifier.weight(1f)) { SongPinch(songs, library, favIds, playlists, level, onLevel, bottomPadding, queueName, play) }
        }
    }
}

/** A playlist: play, shuffle, rename, delete, and per-song remove and reorder. */
@Composable
private fun PlaylistPage(
    p: LocalPlaylist,
    songs: List<LocalSong>,
    library: LocalLibrary,
    favIds: Set<Long>,
    playlists: List<LocalPlaylist>,
    level: MutableIntState,
    onLevel: (Int) -> Unit,
    bottomPadding: Dp,
    back: () -> Unit,
    play: (List<LocalSong>, Int, String) -> Unit,
) {
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    if (renaming) NameDialog("Rename playlist", p.name, "Save", { renaming = false }) { library.rename(p.id, it); renaming = false }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete \"${p.name}\"?") },
            text = { Text("The songs stay on your phone.") },
            confirmButton = { TextButton(onClick = { deleting = false; library.delete(p.id) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } },
        )
    }
    Column(Modifier.fillMaxSize()) {
        PageHeader(p.name, back) {
            Box {
                Text("⋮", color = Ink, fontSize = 22.sp, modifier = Modifier.clip(CircleShape).clickable { menu = true }.padding(horizontal = 14.dp, vertical = 8.dp))
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = true })
                    DropdownMenuItem(text = { Text("Delete playlist") }, onClick = { menu = false; deleting = true })
                }
            }
        }
        Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            PlaylistArt(p, songs, Modifier.size(72.dp))
            Spacer(Modifier.width(14.dp))
            Text("${songs.size} songs", color = Muted, fontSize = 13.sp)
        }
        if (songs.isEmpty()) {
            Text("Add songs from any song's ⋮ menu → Add to playlist.", color = Muted, modifier = Modifier.padding(20.dp))
        } else {
            TrackActions(songs, p.name, play)
            Box(Modifier.weight(1f)) {
                SongPinch(songs, library, favIds, playlists, level, onLevel, bottomPadding, p.name, play) { s, close ->
                    val i = songs.indexOfFirst { it.id == s.id }
                    DropdownMenuItem(text = { Text("Remove from playlist") }, onClick = { close(); library.removeSong(p.id, s.id) })
                    if (i > 0) DropdownMenuItem(text = { Text("Move up") }, onClick = { close(); library.move(p.id, i, i - 1) })
                    if (i in 0 until songs.lastIndex) DropdownMenuItem(text = { Text("Move down") }, onClick = { close(); library.move(p.id, i, i + 1) })
                }
            }
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { TextButton(onClick = { onDone(name) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AddToPlaylistDialog(songIds: List<Long>, library: LocalLibrary, playlists: List<LocalPlaylist>, onDismiss: () -> Unit) {
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        NameDialog("New playlist", "", "Create", { creating = false }) { library.createPlaylist(it, songIds); creating = false; onDismiss() }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            LazyColumn {
                item { Text("+ New playlist", color = Mint, modifier = Modifier.fillMaxWidth().clickable { creating = true }.padding(vertical = 12.dp)) }
                items(playlists, key = { it.id }) { p ->
                    Text(p.name + "  ·  ${p.songIds.size}", modifier = Modifier.fillMaxWidth().clickable { library.addSongs(p.id, songIds); onDismiss() }.padding(vertical = 12.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
