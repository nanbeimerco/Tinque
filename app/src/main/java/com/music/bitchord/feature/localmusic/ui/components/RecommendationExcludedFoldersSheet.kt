package com.music.bitchord.feature.localmusic.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.feature.localmusic.domain.model.LocalFolder
import com.music.bitchord.ui.components.GlassModalBottomSheet
import java.util.Locale

internal data class SelectableRecommendationFolder(
    val path: String,
    val normalizedPath: String,
    val name: String,
    val displayPath: String,
    val songCount: Int,
    val isGroupFolder: Boolean,
    val isDirectlyExcluded: Boolean,
    val isExcludedByParent: Boolean,
)

private val GENERIC_STORAGE_ROOTS = setOf(
    "music",
    "download",
    "downloads",
    "media",
    "audio",
    "documents",
    "dcim",
    "android",
)

internal fun buildSelectableRecommendationFolders(
    discovered: List<LocalFolder>,
    excludedFolders: Set<String>,
): List<SelectableRecommendationFolder> {
    val active = discovered.filterNot { it.isBlacklisted }
    val normalizedExcluded = excludedFolders
        .map { AppSettings.normalizeFolderPath(it) }
        .filter { it.isNotBlank() }
        .toSet()

    val activeByNorm = LinkedHashMap<String, LocalFolder>()
    for (folder in active) {
        val norm = AppSettings.normalizeFolderPath(folder.path)
        if (norm.isNotBlank() && !activeByNorm.containsKey(norm)) {
            activeByNorm[norm] = folder
        }
    }

    val parentDisplayPath = HashMap<String, String>()
    val parentDescendantCount = HashMap<String, Int>()
    val parentSongSum = HashMap<String, Int>()

    for (folder in active) {
        val cleanPath = folder.path.replace('\\', '/').trimEnd('/')
        var cursor = cleanPath
        while (true) {
            val slash = cursor.lastIndexOf('/')
            if (slash <= 0) break
            cursor = cursor.substring(0, slash)
            val normParent = AppSettings.normalizeFolderPath(cursor)
            if (!isAllowedSynthesizedParent(normParent)) continue
            parentDisplayPath.putIfAbsent(normParent, cursor)
            parentDescendantCount[normParent] = (parentDescendantCount[normParent] ?: 0) + 1
            parentSongSum[normParent] = (parentSongSum[normParent] ?: 0) + folder.songCount
        }
    }

    data class RawEntry(
        val path: String,
        val normPath: String,
        val name: String,
        val songCount: Int,
        val isGroup: Boolean,
    )

    val entriesByNorm = LinkedHashMap<String, RawEntry>()
    for ((norm, folder) in activeByNorm) {
        val cleanPath = folder.path.replace('\\', '/').trimEnd('/')
        val descendantSongs = parentSongSum[norm]
        val hasChildFolders = (parentDescendantCount[norm] ?: 0) >= 1
        entriesByNorm[norm] = RawEntry(
            path = cleanPath,
            normPath = norm,
            name = folder.name.ifBlank { cleanPath.substringAfterLast('/') },
            songCount = if (hasChildFolders && descendantSongs != null) {
                maxOf(folder.songCount, descendantSongs)
            } else {
                folder.songCount
            },
            isGroup = hasChildFolders,
        )
    }

    for ((normParent, rawPath) in parentDisplayPath) {
        if (entriesByNorm.containsKey(normParent)) continue
        val count = parentDescendantCount[normParent] ?: 0
        if (count >= 2) {
            entriesByNorm[normParent] = RawEntry(
                path = rawPath,
                normPath = normParent,
                name = rawPath.substringAfterLast('/').ifBlank { rawPath },
                songCount = parentSongSum[normParent] ?: 0,
                isGroup = true,
            )
        }
    }

    for (ex in normalizedExcluded) {
        if (!entriesByNorm.containsKey(ex)) {
            entriesByNorm[ex] = RawEntry(
                path = ex,
                normPath = ex,
                name = ex.substringAfterLast('/').ifBlank { ex },
                songCount = 0,
                isGroup = false,
            )
        }
    }

    return entriesByNorm.values.map { raw ->
        val directlyExcluded = raw.normPath in normalizedExcluded
        val excludedByParent = normalizedExcluded.any { ex ->
            raw.normPath.startsWith("$ex/")
        }
        SelectableRecommendationFolder(
            path = raw.path,
            normalizedPath = raw.normPath,
            name = raw.name,
            displayPath = compactFolderDisplayPath(raw.path),
            songCount = raw.songCount,
            isGroupFolder = raw.isGroup,
            isDirectlyExcluded = directlyExcluded,
            isExcludedByParent = excludedByParent,
        )
    }.sortedWith(
        compareByDescending<SelectableRecommendationFolder> { it.isDirectlyExcluded || it.isExcludedByParent }
            .thenBy { it.displayPath.lowercase(Locale.ROOT) },
    )
}

private fun isAllowedSynthesizedParent(normalizedPath: String): Boolean {
    val parts = normalizedPath.trim('/').split('/').filter { it.isNotEmpty() }
    val rootLen = when {
        parts.size >= 3 && parts[0] == "storage" && parts[1] == "emulated" && parts[2] == "0" -> 3
        parts.size >= 2 && parts[0] == "storage" -> 2
        else -> 1
    }
    val relParts = parts.drop(rootLen)
    if (relParts.isEmpty()) return false
    if (relParts.size == 1 && relParts[0] in GENERIC_STORAGE_ROOTS) return false
    return true
}

private fun compactFolderDisplayPath(path: String): String {
    val clean = path.replace('\\', '/').trimEnd('/')
    return when {
        clean.startsWith("/storage/emulated/0/", ignoreCase = true) ->
            clean.substring("/storage/emulated/0/".length)
        clean.startsWith("/storage/", ignoreCase = true) ->
            clean.substring("/storage/".length)
        else -> clean
    }
}

/**
 * Bottom sheet for choosing folders whose tracks are hidden from AutoPlay, Home mixes/AI shelves,
 * Home Heat, New Additions, and Music2Emo analysis while staying playable in Library and
 * visible in Recently Played.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendationExcludedFoldersSheet(
    folders: List<LocalFolder>,
    excludedFolders: Set<String>,
    onToggleExcluded: (String, Boolean) -> Unit,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = remember(folders, excludedFolders) {
        buildSelectableRecommendationFolders(folders, excludedFolders)
    }
    var searchQuery by remember { mutableStateOf("") }
    val filteredItems = remember(items, searchQuery) {
        val q = searchQuery.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) items else {
            items.filter {
                it.name.lowercase(Locale.ROOT).contains(q) ||
                    it.displayPath.lowercase(Locale.ROOT).contains(q)
            }
        }
    }

    GlassModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.recommendation_excluded_folders),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.recommendation_excluded_folders_sheet_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.close),
                    )
                }
            }

            if (items.size > 6) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(imageVector = Icons.Rounded.Search, contentDescription = null)
                    },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(imageVector = Icons.Rounded.Close, contentDescription = null)
                            }
                        }
                    } else null,
                    placeholder = {
                        Text(stringResource(R.string.recommendation_excluded_folders_search_hint))
                    },
                    shape = RoundedCornerShape(14.dp),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(8.dp))

            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.recommendation_excluded_folders_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(filteredItems, key = { it.normalizedPath }) { item ->
                        val effectiveExcluded = item.isDirectlyExcluded || item.isExcludedByParent
                        val countLabel = if (item.isGroupFolder) {
                            stringResource(R.string.recommendation_excluded_folders_group_tracks, item.songCount)
                        } else {
                            stringResource(R.string.recommendation_excluded_folders_tracks, item.songCount)
                        }
                        val subLabel = buildString {
                            if (item.songCount > 0) {
                                append(countLabel)
                                append(" • ")
                            }
                            append(item.displayPath)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(enabled = !item.isExcludedByParent) {
                                    onToggleExcluded(item.path, !item.isDirectlyExcluded)
                                }
                                .padding(vertical = 8.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = when {
                                    effectiveExcluded -> Icons.Rounded.FolderOff
                                    item.isGroupFolder -> Icons.Rounded.FolderOpen
                                    else -> Icons.Rounded.Folder
                                },
                                contentDescription = null,
                                tint = if (effectiveExcluded) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                modifier = Modifier.size(30.dp),
                            )

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (effectiveExcluded) {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = subLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (item.isExcludedByParent) {
                                    Text(
                                        text = stringResource(R.string.recommendation_excluded_folders_via_parent),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Switch(
                                checked = effectiveExcluded,
                                enabled = !item.isExcludedByParent,
                                onCheckedChange = { excluded ->
                                    onToggleExcluded(item.path, excluded)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = MaterialTheme.colorScheme.error,
                                    checkedBorderColor = MaterialTheme.colorScheme.error,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
