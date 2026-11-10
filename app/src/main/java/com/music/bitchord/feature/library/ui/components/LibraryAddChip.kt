package com.music.bitchord.feature.library.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.LibraryAddCheck
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.feature.library.data.LibraryEntryStore
import com.music.bitchord.feature.library.data.LibraryEntryType
import com.music.bitchord.feature.library.data.albumKey
import com.music.bitchord.feature.library.data.artistKey
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics

/**
 * "Add to Library" for an album or an artist page — a toggle: once in, it
 * reads as added and takes it back out.
 */
@Composable
fun LibraryAddChip(
    type: LibraryEntryType,
    title: String,
    artist: String?,
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val entries by LibraryEntryStore.entries.collectAsStateWithLifecycle()
    val key = if (type == LibraryEntryType.ALBUM) albumKey(title, artist) else artistKey(title)
    val inLibrary = remember(entries, key) { entries.any { it.type == type && it.key == key } }
    AssistChip(
        onClick = {
            haptics.play(Haptic.Select)
            if (inLibrary) {
                LibraryEntryStore.remove(type, key)
                Toast.makeText(context, R.string.removed_from_library, Toast.LENGTH_SHORT).show()
            } else {
                if (type == LibraryEntryType.ALBUM) {
                    LibraryEntryStore.addAlbum(title, artist, artworkUrl)
                } else {
                    LibraryEntryStore.addArtist(title, artworkUrl)
                }
                Toast.makeText(context, R.string.added_to_library, Toast.LENGTH_SHORT).show()
            }
        },
        label = { Text(stringResource(if (inLibrary) R.string.remove_from_library else R.string.add_to_library)) },
        leadingIcon = {
            Icon(
                if (inLibrary) Icons.Rounded.LibraryAddCheck else Icons.Rounded.LibraryAdd,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
        shape = RoundedCornerShape(50),
        colors = AssistChipDefaults.assistChipColors(
            containerColor = contentColor.copy(alpha = 0.10f),
            labelColor = contentColor,
            leadingIconContentColor = contentColor,
        ),
        border = null,
        modifier = modifier,
    )
}
