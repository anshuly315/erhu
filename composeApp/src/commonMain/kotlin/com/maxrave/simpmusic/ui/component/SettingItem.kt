package com.maxrave.simpmusic.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.maxrave.simpmusic.Platform
import com.maxrave.simpmusic.extension.greyScale
import com.maxrave.simpmusic.getPlatform
import com.maxrave.simpmusic.ui.theme.LocalIsDarkTheme
import com.maxrave.simpmusic.ui.theme.typo

@Composable
fun SettingItem(
    title: String = "Title",
    subtitle: String = "Subtitle",
    smallSubtitle: Boolean = false,
    isEnable: Boolean = true,
    onClick: (() -> Unit)? = null,
    switch: Pair<Boolean, ((Boolean) -> Unit)>? = null,
    otherView: @Composable (() -> Unit)? = null,
) {
    // isEnable only greys the row out; it deliberately writes nothing.
    //
    // There used to be an onDisable callback fired from a LaunchedEffect(isEnable) here, to clear a
    // login-gated child flag when its gate went false (#2157, #2064). It was wrong twice over. The
    // gate is a StateFlow filled asynchronously from DataStore, so the FIRST composition always sees
    // its "not loaded yet" value and read it as "signed out" — sync_follow_to_youtube was erased on
    // every visit to Settings. And it only ever ran if the user opened Settings and scrolled to the
    // row at all, so it could not do the job it existed for. Clearing a child flag now belongs to the
    // logout that invalidates it (SettingsViewModel: setSpotifyLogIn, logOutDiscord, logOutLastfm,
    // setUsedAccount, logOutAllYouTube, setAIApiKey), which is a real event and cannot misfire.
    // Settings search: while a query is typed, a row that does not match draws nothing, and a
    // matching one tells its group so the group can show itself.
    val query = SettingsSearch.query.trim()
    val group = LocalGroupSearch.current
    if (query.isNotEmpty()) {
        val hit = group?.titleMatches == true || title.contains(query, true) || subtitle.contains(query, true)
        DisposableEffect(hit, title) {
            if (hit) group?.matched?.put(title, Unit)
            onDispose { group?.matched?.remove(title) }
        }
        if (!hit) return
    }
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    // The disabled colour the sheets use (SurfaceDarkColors.disabled). TextStyle.greyScale() alone
    // left a disabled title as bright as an enabled one, because the title passes its own colour,
    // and an enabled subtitle is already grey.
    val disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    Box(
        Modifier
            // Rows separate themselves: a hairline along the top, from where the text starts to the
            // card's far edge (mirrored right-to-left). SettingGroup leaves the first row's undrawn,
            // so a row hidden by a condition never leaves a stray line at the top of the card.
            .drawBehind {
                val y = SettingDividerThickness.toPx() / 2
                val inset = SettingRowPaddingHorizontal.toPx()
                val rtl = layoutDirection == LayoutDirection.Rtl
                drawLine(
                    color = dividerColor,
                    start = Offset(if (rtl) 0f else inset, y),
                    end = Offset(if (rtl) size.width - inset else size.width, y),
                    strokeWidth = SettingDividerThickness.toPx(),
                )
            }.then(
                if (onClick != null && isEnable) {
                    Modifier.clickable { onClick.invoke() }
                } else {
                    Modifier
                },
            ).then(
                if (!isEnable) {
                    Modifier.greyScale()
                } else {
                    Modifier
                },
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        vertical = 12.dp,
                        horizontal = SettingRowPaddingHorizontal,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = title,
                    style = typo().labelMedium,
                    color = if (isEnable) MaterialTheme.colorScheme.onSurface else disabledContentColor,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = if (smallSubtitle) typo().bodySmall else typo().bodyMedium,
                    color = if (isEnable) Color.Unspecified else disabledContentColor,
                    // No maxLines: with a cap and no overflow set, Compose defaults to Clip and cut
                    // the second line through the middle of the glyphs, with no ellipsis to show
                    // anything was missing. A settings list scrolls vertically anyway, and these
                    // descriptions are translated — German and Vietnamese run longer than the
                    // English they were sized against, so any fixed cap just moves the problem to
                    // another language.
                )

                otherView?.let {
                    Spacer(Modifier.height(16.dp))
                    it.invoke()
                }
            }
            if (switch != null) {
                Spacer(Modifier.width(10.dp))
                Switch(
                    modifier = Modifier.wrapContentWidth(),
                    checked = switch.first,
                    onCheckedChange = {
                        switch.second.invoke(it)
                    },
                    enabled = isEnable,
                )
            }
        }
    }
}

/**
 * One group of the Settings screen, laid out the way Apple Music groups its settings: a small
 * header above, the rows on one rounded card, and an optional note under the card ([footer]).
 */
/** Which part of the app a settings group belongs to. */
enum class SettingsScope(val label: String) {
    General("General"),
    YouTube("Home & accounts"),
    Radio("Radio"),
    Local("Local"),
}

/** What the Settings screen is filtering by: the typed query and the chosen part of the app. */
object SettingsSearch {
    var query by mutableStateOf("")
    var scope by mutableStateOf<SettingsScope?>(null)
}

internal class GroupSearch(val titleMatches: Boolean) {
    val matched = mutableStateMapOf<String, Unit>()
}

internal val LocalGroupSearch = compositionLocalOf<GroupSearch?> { null }

/** The search box and the Home / Radio / Local filter at the top of Settings. */
@Composable
fun SettingsSearchBar(modifier: Modifier = Modifier) {
    DisposableEffect(Unit) {
        onDispose {
            SettingsSearch.query = ""
            SettingsSearch.scope = null
        }
    }
    Column(modifier.fillMaxWidth().padding(top = 8.dp)) {
        OutlinedTextField(
            value = SettingsSearch.query,
            onValueChange = { SettingsSearch.query = it },
            placeholder = { Text("Search settings") },
            singleLine = true,
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                if (SettingsSearch.query.isNotEmpty()) {
                    Text(
                        "✕",
                        modifier = Modifier.clickable { SettingsSearch.query = "" }.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            },
        )
        LazyRow(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            item {
                FilterChip(
                    selected = SettingsSearch.scope == null,
                    onClick = { SettingsSearch.scope = null },
                    label = { Text("All") },
                )
            }
            SettingsScope.values().forEach { sc ->
                item {
                    FilterChip(
                        selected = SettingsSearch.scope == sc,
                        onClick = { SettingsSearch.scope = if (SettingsSearch.scope == sc) null else sc },
                        label = { Text(sc.label) },
                    )
                }
            }
        }
    }
}

/**
 * One group of the Settings screen, laid out the way Apple Music groups its settings: a small
 * header above, the rows on one rounded card, and an optional note under the card ([footer]).
 * The header folds the group away (tap it); a search opens every group that has a match.
 */
@Composable
fun SettingGroup(
    title: String,
    modifier: Modifier = Modifier,
    scope: SettingsScope = SettingsScope.General,
    collapsible: Boolean = true,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cardColor = settingGroupColor()
    val query = SettingsSearch.query.trim()
    val searching = query.isNotEmpty()
    val chosen = SettingsSearch.scope
    val groupSearch = remember(query, title) { GroupSearch(title.contains(query, true)) }
    var expanded by rememberSaveable(title) { mutableStateOf(!collapsible) }
    val open = expanded || searching
    val inScope = chosen == null || chosen == scope
    if (!inScope) return
    val hide = searching && !groupSearch.titleMatches && groupSearch.matched.isEmpty()
    Column(
        modifier
            .fillMaxWidth()
            .then(
                if (hide) {
                    Modifier.layout { measurable, constraints ->
                        measurable.measure(constraints)
                        layout(0, 0) {}
                    }
                } else {
                    Modifier.padding(top = 24.dp)
                },
            ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = !searching && collapsible) { expanded = !expanded }
                .padding(start = SettingRowPaddingHorizontal, end = SettingRowPaddingHorizontal, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title.toUpperCase(Locale.current),
                // The headers' size from before the groups; only the weight drops, so they step back.
                style = typo().labelMedium.copy(fontWeight = FontWeight.Normal),
                modifier = Modifier.weight(1f),
            )
            if (!searching && collapsible) Text(if (open) "▾" else "▸", style = typo().labelMedium)
        }
        if (open) {
            CompositionLocalProvider(LocalGroupSearch provides groupSearch) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(SettingGroupShape)
                            .background(cardColor.copy(alpha = 0.5f))
                            .drawWithContent {
                                // The first row's hairline would sit on the card's own edge, so that strip is
                                // left undrawn. Clipped rather than painted over: the card is translucent.
                                clipRect(top = SettingDividerThickness.toPx()) { this@drawWithContent.drawContent() }
                            },
                    content = content,
                )
            }
            if (footer != null && !searching) {
                Box(Modifier.padding(start = SettingRowPaddingHorizontal, end = SettingRowPaddingHorizontal, top = 8.dp)) {
                    footer()
                }
            }
        }
    }
}

// One step off the page the card sits on. Android's page is the theme background (black, or
// #FAFAFA); Desktop's is a panel, and its light panel is already surfaceContainer — the card's
// own colour — so there the card lifts to white instead.
@Composable
private fun settingGroupColor(): Color =
    if (getPlatform() == Platform.Desktop && !LocalIsDarkTheme.current) {
        MaterialTheme.colorScheme.surfaceContainerLowest
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

private val SettingGroupShape = RoundedCornerShape(20.dp)
private val SettingRowPaddingHorizontal = 16.dp
private val SettingDividerThickness = 0.5.dp
