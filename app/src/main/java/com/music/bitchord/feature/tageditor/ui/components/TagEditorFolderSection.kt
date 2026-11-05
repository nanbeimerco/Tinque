package com.music.bitchord.feature.tageditor.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.feature.tageditor.domain.model.TagField

/**
 * Where a save goes beyond this one song: the switch that takes it to the rest
 * of the folder (or, for a folder opened as a whole, just the subfolders
 * switch), and the changed fields that will be written there, each one a chip
 * that can be unticked.
 *
 * @param batchCount songs being edited together, or null when editing one
 * @param folderCount the folder's other songs, for one song (null: none found)
 * @param subfolderCount the same with its subfolders' songs, when that is more
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagEditorFolderSection(
    batchCount: Int?,
    folderCount: Int?,
    subfolderCount: Int?,
    applyToFolder: Boolean,
    onApplyToFolderChange: (Boolean) -> Unit,
    includeSubfolders: Boolean,
    onIncludeSubfoldersChange: (Boolean) -> Unit,
    changed: Set<TagField>,
    excluded: Set<TagField>,
    onToggleField: (TagField) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (batchCount == null && folderCount == null) return
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 18.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (batchCount != null) {
                Text(stringResource(R.string.tag_batch_title, batchCount), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.tag_batch_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (folderCount != null) {
                SwitchRow(
                    title = stringResource(R.string.tag_apply_folder),
                    subtitle = stringResource(R.string.tag_apply_folder_sub, folderCount),
                    checked = applyToFolder,
                    onCheckedChange = onApplyToFolderChange,
                )
            }
            val showOptions = batchCount != null || applyToFolder
            if (showOptions && subfolderCount != null) {
                SwitchRow(
                    title = stringResource(R.string.tag_include_subfolders, subfolderCount),
                    subtitle = null,
                    checked = includeSubfolders,
                    onCheckedChange = onIncludeSubfoldersChange,
                )
            }
            if (showOptions) {
                Text(stringResource(R.string.tag_fields_to_apply), style = MaterialTheme.typography.labelLarge)
                if (changed.isEmpty()) {
                    Text(
                        stringResource(R.string.tag_fields_none),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        changed.forEach { field ->
                            FilterChip(
                                selected = field !in excluded,
                                onClick = { onToggleField(field) },
                                label = { Text(stringResource(field.label)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
