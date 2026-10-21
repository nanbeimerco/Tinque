package com.music.bitchord.feature.library.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.data.settings.LibraryViewType
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.ui.components.TabLogoHeader
import com.music.bitchord.ui.components.topBarContentPadding

enum class LibraryFilter {
    ALL,
    FAVORITES,
    PLAYLISTS,
    RECENTLY_PLAYED,
    MOST_PLAYED,
}

/**
 * The top of the Library page: the large "Library" title and the filter chips.
 *
 * It is the first item of the page's own list and scrolls away with it — no
 * fixed band (the top bar shows a title capsule once this has gone). Tapping
 * the Library tab again brings it back.
 *
 * The grid/list toggle only applies to the Playlists filter, so it only shows
 * there. "New playlist" lives on the Playlists section header instead.
 */
@Composable
fun LibraryHeaderBar(
    selectedFilter: LibraryFilter,
    onFilterSelect: (LibraryFilter) -> Unit,
    viewType: LibraryViewType,
    onToggleViewType: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onCreatePlaylist: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()

    Column(modifier = modifier.fillMaxWidth()) {
        // The tab's logo, scrolling with the list. No large "Library" title:
        // like every other tab, the page name is in the top bar's capsule.
        TabLogoHeader(listTopPadding = topBarContentPadding())
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(
                    LibraryFilter.ALL to stringResource(R.string.all_filter_label),
                    LibraryFilter.FAVORITES to stringResource(R.string.favorites),
                    LibraryFilter.PLAYLISTS to stringResource(R.string.playlists),
                    LibraryFilter.RECENTLY_PLAYED to stringResource(R.string.recently_played),
                    LibraryFilter.MOST_PLAYED to stringResource(R.string.most_played),
                ).forEach { (filter, label) ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = {
                            haptics.play(Haptic.Select)
                            onFilterSelect(filter)
                        },
                        label = { Text(label, maxLines = 1) },
                        shape = RoundedCornerShape(50),
                        border = null,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            labelColor = MaterialTheme.colorScheme.onSurface,
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }
            if (selectedFilter == LibraryFilter.PLAYLISTS) {
                Box(
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable {
                            haptics.play(Haptic.Select)
                            onToggleViewType()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (viewType == LibraryViewType.GRID) BitChordIcons.ListView else BitChordIcons.GridView,
                        contentDescription = stringResource(
                            if (viewType == LibraryViewType.GRID) R.string.switch_to_list_view else R.string.switch_to_grid_view,
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}
