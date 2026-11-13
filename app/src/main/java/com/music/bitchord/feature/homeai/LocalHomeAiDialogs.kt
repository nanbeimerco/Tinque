package com.music.bitchord.feature.homeai

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.ui.components.GlassAlertDialog

@Composable
internal fun Music2EmoDownloadDialog(
    @StringRes dismissLabelRes: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_ai_download_title)) },
        text = { Text(stringResource(R.string.home_ai_download_message)) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.home_ai_download_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(dismissLabelRes))
            }
        },
    )
}

@Composable
internal fun Music2EmoDownloadProgressDialog(progress: LocalHomeAi.DownloadProgress) {
    val totalMb = progress.totalBytes.coerceAtLeast(1L) / (1024f * 1024f)
    val doneMb = progress.bytes / (1024f * 1024f)
    val fraction = (progress.bytes.toFloat() / progress.totalBytes.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
    GlassAlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.home_ai_download_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.home_ai_download_progress, doneMb, totalMb))
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {},
    )
}

@Composable
internal fun Music2EmoDownloadErrorDialog(
    error: String,
    onDismiss: () -> Unit,
) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_ai_download_failed)) },
        text = { Text(error) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
    )
}
