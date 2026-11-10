package com.music.bitchord.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.HomeSection
import com.music.bitchord.data.settings.LibrarySection

@Composable
fun homeSectionLabel(section: HomeSection): String = stringResource(
    when (section) {
        HomeSection.HERO -> R.string.home_hero
        HomeSection.HOT -> R.string.home_hot
        HomeSection.COOLING -> R.string.home_cooling
        HomeSection.NEW_ADDITIONS -> R.string.home_new
        HomeSection.RECENTLY_PLAYED -> R.string.recently_played
        HomeSection.UNPLAYED_ALBUMS -> R.string.home_unplayed_albums
        HomeSection.STATS -> R.string.home_section_stats
        HomeSection.MIXES -> R.string.home_mixes
        HomeSection.RISING -> R.string.home_rising
        HomeSection.TIME_CAPSULE -> R.string.home_time_capsule
        HomeSection.DEEP_CUTS -> R.string.home_deep_cuts
        HomeSection.TIME_OF_DAY -> R.string.home_time_of_day
        HomeSection.COVERAGE -> R.string.home_coverage
    },
)

@Composable
fun librarySectionLabel(section: LibrarySection): String = stringResource(
    when (section) {
        LibrarySection.RECENTLY_ADDED -> R.string.recently_added_to_library
        LibrarySection.FAVORITES -> R.string.favorites
        LibrarySection.PLAYLISTS -> R.string.playlists
        LibrarySection.SONGS -> R.string.songs
        LibrarySection.ALBUMS -> R.string.albums
        LibrarySection.ARTISTS -> R.string.artists
    },
)

/** Home's shelves: order and visibility, edited from the Home tab itself. */
@Composable
fun HomeLayoutSheet(onDismiss: () -> Unit) {
    val order by AppSettings.homeSectionOrder.collectAsStateWithLifecycle()
    val hidden by AppSettings.hiddenHomeSections.collectAsStateWithLifecycle()
    SectionOrderSheet(
        title = stringResource(R.string.home_layout),
        items = order,
        label = { homeSectionLabel(it) },
        isShown = { it !in hidden },
        onShownChange = AppSettings::setHomeSectionVisible,
        onMove = AppSettings::moveHomeSection,
        onDismiss = onDismiss,
    )
}

/** The Library's "All" groups: order and visibility, edited from the Library tab itself. */
@Composable
fun LibraryLayoutSheet(onDismiss: () -> Unit) {
    val order by AppSettings.librarySectionOrder.collectAsStateWithLifecycle()
    val hidden by AppSettings.hiddenLibrarySections.collectAsStateWithLifecycle()
    SectionOrderSheet(
        title = stringResource(R.string.library_layout),
        items = order,
        label = { librarySectionLabel(it) },
        isShown = { it !in hidden },
        onShownChange = AppSettings::setLibrarySectionVisible,
        onMove = AppSettings::moveLibrarySection,
        onDismiss = onDismiss,
    )
}

/** One row per section: a switch, its name, and up/down. Changes apply as they are made. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun <T> SectionOrderSheet(
    title: String,
    items: List<T>,
    label: @Composable (T) -> String,
    isShown: (T) -> Boolean,
    onShownChange: (T, Boolean) -> Unit,
    onMove: (T, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    GlassModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
            }
            Spacer(Modifier.height(8.dp))
            items.forEachIndexed { index, item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                ) {
                    Switch(
                        checked = isShown(item),
                        onCheckedChange = { onShownChange(item, it) },
                        modifier = Modifier.padding(end = 12.dp),
                    )
                    Text(
                        text = label(item),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                    )
                    IconButton(onClick = { onMove(item, -1) }, enabled = index > 0) {
                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = stringResource(R.string.selection_move_up))
                    }
                    IconButton(onClick = { onMove(item, 1) }, enabled = index < items.lastIndex) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.selection_move_down))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** The "Edit layout" button at the foot of Home and the Library. */
@Composable
fun EditLayoutButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
        TextButton(onClick = onClick) {
            Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text(stringResource(R.string.edit_layout))
        }
    }
}
