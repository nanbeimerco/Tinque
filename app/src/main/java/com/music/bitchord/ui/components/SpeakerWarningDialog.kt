package com.music.bitchord.ui.components

import android.media.AudioManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.playback.SpeakerWarningBus
import com.music.bitchord.playback.SpeakerWarningTrigger

/**
 * Displays the Liquid Glass confirmation dialog when [PlaybackService] intercepts
 * speaker playback under the user's `warnBeforeSpeakerPlayback` setting.
 */
@Composable
fun SpeakerWarningHost(
    root: Boolean = true,
    active: Boolean = true,
    onConfirmPlay: () -> Unit,
) {
    if (root) {
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            var activeInLifecycle =
                lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (activeInLifecycle) {
                SpeakerWarningBus.onForegroundHostChanged(true)
            }
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> {
                        if (!activeInLifecycle) {
                            activeInLifecycle = true
                            SpeakerWarningBus.onForegroundHostChanged(true)
                        }
                    }
                    Lifecycle.Event.ON_STOP -> {
                        if (activeInLifecycle) {
                            activeInLifecycle = false
                            SpeakerWarningBus.onForegroundHostChanged(false)
                        }
                    }
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                if (activeInLifecycle) {
                    SpeakerWarningBus.onForegroundHostChanged(false)
                }
            }
        }
    }

    if (!active) return

    val pending by SpeakerWarningBus.pendingRequest.collectAsStateWithLifecycle()
    val request = pending ?: return
    val context = LocalContext.current
    val isVolumeRaise = request.trigger == SpeakerWarningTrigger.VOLUME_RAISE_FROM_ZERO

    GlassAlertDialog(
        onDismissRequest = SpeakerWarningBus::dismiss,
        icon = {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                contentDescription = null,
            )
        },
        title = {
            Text(
                stringResource(
                    if (isVolumeRaise) R.string.speaker_volume_warning_title else R.string.speaker_warning_title,
                ),
            )
        },
        text = {
            Text(
                stringResource(
                    if (isVolumeRaise) R.string.speaker_volume_warning_message else R.string.speaker_warning_message,
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    SpeakerWarningBus.confirmAndPlay(
                        onPlayMain = onConfirmPlay,
                        onApplyVolume = { targetVolume, viaHardwareKey ->
                            val audioManager = context.getSystemService(AudioManager::class.java)
                            if (audioManager != null) {
                                if (viaHardwareKey) {
                                    audioManager.adjustStreamVolume(
                                        AudioManager.STREAM_MUSIC,
                                        AudioManager.ADJUST_RAISE,
                                        AudioManager.FLAG_SHOW_UI,
                                    )
                                } else if (targetVolume != null && targetVolume > 0) {
                                    audioManager.setStreamVolume(
                                        AudioManager.STREAM_MUSIC,
                                        targetVolume,
                                        0,
                                    )
                                }
                            }
                        },
                    )
                },
            ) {
                Text(
                    stringResource(
                        if (isVolumeRaise && !request.pausedByService) {
                            R.string.speaker_volume_warning_confirm
                        } else {
                            R.string.speaker_warning_play
                        },
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = SpeakerWarningBus::dismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
