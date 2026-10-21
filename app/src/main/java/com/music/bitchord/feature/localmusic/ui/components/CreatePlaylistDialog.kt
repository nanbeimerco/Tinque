package com.music.bitchord.feature.localmusic.ui.components

import com.music.bitchord.ui.components.glassPalette
import com.music.bitchord.ui.components.LocalGlassSheets
import com.music.bitchord.ui.components.LocalGlassSheetDismiss
import com.music.bitchord.ui.components.GlassSheetKind
import com.music.bitchord.ui.components.GlassSheet
import com.music.bitchord.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Dialog to create a new local playlist or rename an existing one.
 */
@Composable
fun CreatePlaylistDialog(
    initialName: String = "",
    title: String = "New Playlist",
    confirmText: String = "Create",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf(initialName) }

    // On glass where the screen has a sheet host (see GlassSheet), Material's
    // AlertDialog anywhere else.
    val sheets = com.music.bitchord.ui.components.glassSheetsHere()
    if (sheets != null) {
        GlassSheet(sheets, GlassSheetKind.Dialog, onDismiss = onDismiss) {
            val palette = glassPalette()
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            Column(Modifier.padding(start = 24.dp, end = 16.dp, top = 22.dp, bottom = 12.dp)) {
                Text(
                    text = if (title == "New Playlist") stringResource(R.string.new_playlist) else title,
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.content,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    placeholder = { Text(stringResource(R.string.playlist_name)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = palette.content,
                        unfocusedTextColor = palette.content,
                        focusedBorderColor = palette.content.copy(alpha = 0.55f),
                        unfocusedBorderColor = palette.divider,
                        cursorColor = palette.content,
                        focusedPlaceholderColor = palette.secondary,
                        unfocusedPlaceholderColor = palette.secondary,
                        focusedContainerColor = palette.content.copy(alpha = 0.06f),
                        unfocusedContainerColor = palette.content.copy(alpha = 0.06f),
                    ),
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .fillMaxWidth()
                        .focusRequester(focus),
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    val dismiss = LocalGlassSheetDismiss.current
                    TextButton(onClick = dismiss) {
                        Text(stringResource(R.string.cancel), color = palette.secondary)
                    }
                    Spacer(Modifier.width(4.dp))
                    Button(
                        onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                        enabled = name.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.content,
                            contentColor = if (palette.content == Color.White) Color.Black else Color.White,
                            disabledContainerColor = palette.content.copy(alpha = 0.12f),
                            disabledContentColor = palette.content.copy(alpha = 0.38f),
                        ),
                    ) {
                        Text(if (confirmText == "Create") stringResource(R.string.create) else confirmText)
                    }
                }
            }
        }
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text(
                    text = "Enter a name for this playlist",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Playlist Name") },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name.trim())
                    }
                },
                enabled = name.isNotBlank(),
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(24.dp),
        modifier = modifier,
    )
}
