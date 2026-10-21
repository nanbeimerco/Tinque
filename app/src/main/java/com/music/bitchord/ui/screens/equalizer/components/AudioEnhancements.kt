package com.music.bitchord.ui.screens.equalizer.components

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.music.bitchord.R

@Composable
fun AudioEnhancements(
    bassBoostStrength: Int,
    virtualizerStrength: Int,
    enabled: Boolean,
    audioSessionId: Int,
    onBassBoostChange: (Int) -> Unit,
    onVirtualizerChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val systemEqLabel = stringResource(R.string.equalizer_subtitle)

    EqGroup(modifier = modifier, title = "エフェクト") {
        row {
            EqSliderRow(
                label = "Bass Boost",
                valueText = "${bassBoostStrength / 10}%",
                value = bassBoostStrength.toFloat(),
                onValueChange = { if (enabled) onBassBoostChange(it.toInt()) },
                valueRange = 0f..1000f,
                enabled = enabled,
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
            )
        }
        row {
            EqSliderRow(
                label = "Virtualizer (3D)",
                valueText = "${virtualizerStrength / 10}%",
                value = virtualizerStrength.toFloat(),
                onValueChange = { if (enabled) onVirtualizerChange(it.toInt()) },
                valueRange = 0f..1000f,
                enabled = enabled,
                icon = Icons.Rounded.SurroundSound,
            )
        }
        row {
            EqActionRow(
                title = systemEqLabel,
                onClick = { openSystemEqualizer(context, audioSessionId) },
                icon = Icons.AutoMirrored.Rounded.OpenInNew,
            )
        }
    }
}

private fun openSystemEqualizer(context: Context, sessionId: Int) {
    val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
        putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
        putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
        putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
    }
    runCatching { context.startActivity(intent) }.onFailure {
        Toast.makeText(context, context.getString(R.string.no_equalizer), Toast.LENGTH_SHORT).show()
    }
}
