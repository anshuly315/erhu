package com.maxrave.simpmusic.ui.screen.radio

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.maxrave.simpmusic.ui.component.SettingGroup
import com.maxrave.simpmusic.ui.component.SettingItem
import com.maxrave.simpmusic.ui.component.SettingsScope
import org.simpmusic.radio.data.RadioStore
import org.simpmusic.radio.local.PlayStats

@Composable
actual fun RadioLocalSettingsGroup() {
    val context = LocalContext.current
    val store = remember { RadioStore(context) }
    val favourites by store.favourites.collectAsState()
    val history by store.history.collectAsState()
    val hidden by store.hidden.collectAsState()
    var nowPlaying by remember { mutableStateOf(store.showNowPlaying) }
    var mixes by remember { mutableStateOf(store.showSmartMixes) }
    var hiddenFolders by remember { mutableStateOf(org.simpmusic.radio.local.LocalFolders.excluded(context)) }
    fun toast(m: String) = Toast.makeText(context, m, Toast.LENGTH_SHORT).show()

    SettingGroup(
        title = "Radio",
        scope = SettingsScope.Radio,
        footer = { androidx.compose.material3.Text("Pinch any list to change how it looks. Stations are kept out of your listening history, Most played and Last.fm.") },
    ) {
        SettingItem(
            title = "Show what's playing",
            subtitle = "Show the song a station says is on air, read from the stream's own title tag",
            switch = nowPlaying to { nowPlaying = it; store.showNowPlaying = it },
        )
        SettingItem(
            title = "Clear radio history",
            subtitle = "${history.size} recently played stations",
            isEnable = history.isNotEmpty(),
            onClick = { store.clearHistory(); toast("Radio history cleared") },
        )
        SettingItem(
            title = "Show hidden stations again",
            subtitle = "${hidden.size} hidden stations · ${favourites.size} favourites kept",
            isEnable = hidden.isNotEmpty(),
            onClick = { store.unhideAll(); toast("Hidden stations restored") },
        )
        SettingItem(
            title = "Reset list layouts",
            subtitle = "Stations, songs, albums, artists and playlists back to their default views",
            onClick = { store.resetLayouts(); toast("Layouts reset. Reopen the tab to see it.") },
        )
    }
    SettingGroup(
        title = "Local music",
        scope = SettingsScope.Local,
        footer = { androidx.compose.material3.Text("To fix one song's tags or cover, open it in the player, tap the three dots and choose Edit tags & artwork.") },
    ) {
        SettingItem(
            title = "Smart mixes",
            subtitle = "Show mixes such as recently added, most played and forgotten favourites above your songs",
            switch = mixes to { mixes = it; store.showSmartMixes = it },
        )
        val vybe = remember { org.simpmusic.radio.vybe.VybeEngine.get(context) }
        val vybeStatus by vybe.status.collectAsState()
        SettingItem(
            title = "VYBE model",
            subtitle = if (vybe.isInstalled()) "Installed (about 184 MB). Tap to delete it and free the space." else "Not downloaded. Open VYBE in the Local tab to set it up.",
            isEnable = vybe.isInstalled() || vybeStatus is org.simpmusic.radio.vybe.VybeStatus.Downloading,
            onClick = { vybe.deleteModel(); toast("VYBE model deleted") },
        )
        SettingItem(
            title = "Music folders",
            subtitle =
                if (hiddenFolders.isEmpty()) {
                    "Every folder is shown. Choose folders from the Local tab's Folders button."
                } else {
                    "${hiddenFolders.size} folder${if (hiddenFolders.size == 1) "" else "s"} hidden. Tap to show every folder again."
                },
            isEnable = hiddenFolders.isNotEmpty(),
            onClick = {
                org.simpmusic.radio.local.LocalFolders.setExcluded(context, emptySet())
                hiddenFolders = emptySet()
                toast("All music folders shown")
            },
        )
        SettingItem(
            title = "Reset play counts",
            subtitle = "Clear the counts behind Most played, Recently played and the smart mixes",
            onClick = { PlayStats(context).clear(); toast("Play counts reset") },
        )
    }
}
