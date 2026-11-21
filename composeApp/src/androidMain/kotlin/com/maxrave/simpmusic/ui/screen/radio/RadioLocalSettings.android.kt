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
    fun toast(m: String) = Toast.makeText(context, m, Toast.LENGTH_SHORT).show()

    SettingGroup(
        title = "Radio",
        footer = { androidx.compose.material3.Text("Pinch any station or song list to change how it looks.") },
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
            subtitle = "Return radio and local lists to the default pinch level",
            onClick = { store.resetLayouts(); toast("Layouts reset. Reopen the tab to see it.") },
        )
    }
    SettingGroup(
        title = "Local music",
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
            title = "Reset play counts",
            subtitle = "Clear the counts the smart mixes are built from",
            onClick = { PlayStats(context).clear(); toast("Play counts reset") },
        )
    }
}
