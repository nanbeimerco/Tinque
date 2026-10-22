package com.music.bitchord.feature.localsongactions.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.music.bitchord.R
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.PlayerDetailsAction
import com.music.bitchord.feature.artistimage.util.ArtistSplitter
import com.music.bitchord.playback.SleepTimer
import com.music.bitchord.ui.components.GlassMenuController
import com.music.bitchord.ui.components.GlassMenuItem
import com.music.bitchord.ui.components.glassPalette
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * The player's ⋯ menu, as a Liquid Glass popup by the button — the same
 * items, in the same order and under the same "show in the player menu"
 * setting, as the sheet it replaces.
 *
 * It opens on [controller] (the player's own glass host, so it floats over
 * the player) while this is in composition, and closes when it leaves: an
 * item that moves on to something else (a picker, an editor, another page)
 * only has to change what the caller shows. A tap outside or Back calls
 * [onDismissRequest].
 *
 * Items a song without a file cannot use ([streamed]: a YouTube Music song)
 * stay where they are, dimmed, and say why when tapped.
 */
@Composable
fun PlayerActionsMenu(
    controller: GlassMenuController,
    anchor: Rect,
    song: Song,
    streamed: Boolean,
    onDismissRequest: () -> Unit,
    onGoToAlbum: () -> Unit,
    onGoToArtist: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onEqualizer: () -> Unit,
    onPlaybackTuning: () -> Unit,
    onCastUnavailable: () -> Unit,
    onTagEditor: () -> Unit,
    onEditLyrics: () -> Unit,
    onDetails: () -> Unit,
    onShareFile: () -> Unit,
    onExplainUnavailable: (Int) -> Unit,
) {
    val actions = rememberUpdatedState(
        PlayerMenuActions(
            onGoToAlbum, onGoToArtist, onAddToPlaylist, onEqualizer, onPlaybackTuning,
            onCastUnavailable, onTagEditor, onEditLyrics, onDetails, onShareFile, onExplainUnavailable,
        ),
    )
    val dismiss = rememberUpdatedState(onDismissRequest)
    DisposableEffect(controller, song.videoId) {
        val token = controller.show(anchor, onDismiss = { dismiss.value() }) {
            PlayerActionsMenuContent(song = song, streamed = streamed, actions = actions.value)
        }
        onDispose { controller.close(token) }
    }
}

private class PlayerMenuActions(
    val goToAlbum: () -> Unit,
    val goToArtist: () -> Unit,
    val addToPlaylist: () -> Unit,
    val equalizer: () -> Unit,
    val playbackTuning: () -> Unit,
    val castUnavailable: () -> Unit,
    val tagEditor: () -> Unit,
    val editLyrics: () -> Unit,
    val details: () -> Unit,
    val shareFile: () -> Unit,
    val explain: (Int) -> Unit,
)

@Composable
private fun PlayerActionsMenuContent(song: Song, streamed: Boolean, actions: PlayerMenuActions) {
    val hidden by AppSettings.hiddenPlayerDetailsActions.collectAsStateWithLifecycle()
    var pickingSleepTimer by remember { mutableStateOf(false) }
    val artists = remember(song.artist) {
        val split = ArtistSplitter.split(song.artist)
        if (split.isEmpty() && song.artist.isNotBlank()) listOf(song.artist) else split
    }
    val afterTrack by SleepTimer.afterTrack.collectAsStateWithLifecycle()
    val deadline by SleepTimer.deadline.collectAsStateWithLifecycle()
    val remaining by produceState<Long?>(initialValue = SleepTimer.remainingMs(), deadline) {
        while (true) {
            value = SleepTimer.remainingMs()
            delay(1000)
        }
    }
    val sleepTimerStatus = remaining?.let { ms ->
        val totalSeconds = (ms + 999) / 1000
        "%d:%02d".format(Locale.ROOT, totalSeconds / 60, totalSeconds % 60)
    } ?: stringResource(R.string.after_this_song).takeIf { afterTrack }

    // Every item keeps the menu open itself: the caller closes it by moving
    // on (see [PlayerActionsMenu]), and the sleep timer swaps in its picker.
    AnimatedContent(
        targetState = pickingSleepTimer,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "playerMenuContent",
    ) { picking ->
        if (picking) {
            CompositionLocalProvider(LocalContentColor provides glassPalette().content) {
                LocalSleepTimerPicker(onBack = { pickingSleepTimer = false })
            }
        } else {
            Column {
                if (PlayerDetailsAction.ALBUM !in hidden) {
                    GlassMenuItem(stringResource(R.string.go_to_album), actions.goToAlbum, icon = Icons.Rounded.Album, keepOpen = true)
                }
                if (PlayerDetailsAction.ARTIST !in hidden) {
                    GlassMenuItem(
                        stringResource(R.string.go_to_artist),
                        actions.goToArtist,
                        icon = Icons.Rounded.Person,
                        trailingIcon = if (artists.size > 1) Icons.AutoMirrored.Rounded.KeyboardArrowRight else null,
                        keepOpen = true,
                    )
                }
                if (PlayerDetailsAction.ADD_TO_PLAYLIST !in hidden) {
                    GlassMenuItem(
                        stringResource(R.string.add_to_playlist),
                        actions.addToPlaylist,
                        icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                        keepOpen = true,
                        enabled = !streamed,
                        onDisabledClick = { actions.explain(R.string.streamed_song_no_playlist) },
                    )
                }
                if (PlayerDetailsAction.EQUALIZER !in hidden) {
                    GlassMenuItem(stringResource(R.string.equalizer), actions.equalizer, icon = Icons.Rounded.GraphicEq, keepOpen = true)
                }
                if (PlayerDetailsAction.CHROMECAST !in hidden) {
                    GlassCastMenuItem(onUnavailable = actions.castUnavailable)
                }
                if (PlayerDetailsAction.PLAYBACK_TUNING !in hidden) {
                    GlassMenuItem(stringResource(R.string.playback_tuning), actions.playbackTuning, icon = Icons.Rounded.Speed, keepOpen = true)
                }
                if (PlayerDetailsAction.SLEEP_TIMER !in hidden) {
                    GlassMenuItem(
                        stringResource(R.string.sleep_timer),
                        { pickingSleepTimer = true },
                        icon = Icons.Rounded.Bedtime,
                        keepOpen = true,
                        value = sleepTimerStatus,
                    )
                }
                if (PlayerDetailsAction.TAG_EDITOR !in hidden) {
                    GlassMenuItem(
                        stringResource(R.string.tag_editor),
                        actions.tagEditor,
                        icon = Icons.Rounded.Edit,
                        keepOpen = true,
                        enabled = !streamed,
                        onDisabledClick = { actions.explain(R.string.streamed_song_no_file) },
                    )
                }
                if (PlayerDetailsAction.EDIT_LYRICS !in hidden) {
                    GlassMenuItem(stringResource(R.string.edit_lyrics), actions.editLyrics, icon = Icons.AutoMirrored.Rounded.Notes, keepOpen = true)
                }
                if (PlayerDetailsAction.DETAILS !in hidden) {
                    GlassMenuItem(stringResource(R.string.details), actions.details, icon = Icons.Rounded.Info, keepOpen = true)
                }
                if (PlayerDetailsAction.SHARE_FILE !in hidden) {
                    GlassMenuItem(
                        stringResource(R.string.share_file),
                        actions.shareFile,
                        icon = Icons.Rounded.Share,
                        keepOpen = true,
                        enabled = !streamed,
                        onDisabledClick = { actions.explain(R.string.streamed_song_no_file) },
                    )
                }
            }
        }
    }
}

/** Chromecast, as a glass menu row: the route picker where casting is set up. */
@Composable
private fun GlassCastMenuItem(onUnavailable: () -> Unit) {
    val context = LocalContext.current
    val castContext = remember(context) { runCatching { CastContext.getSharedInstance(context) }.getOrNull() }
    if (castContext == null) {
        GlassMenuItem(stringResource(R.string.chromecast), onUnavailable, icon = Icons.Rounded.Cast, keepOpen = true)
        return
    }
    val palette = glassPalette()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(start = 20.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Cast, contentDescription = null, tint = palette.content, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            text = stringResource(R.string.chromecast),
            style = MaterialTheme.typography.bodyLarge,
            color = palette.content,
            modifier = Modifier.weight(1f),
        )
        AndroidView(
            factory = { viewContext ->
                MediaRouteButton(viewContext).also { CastButtonFactory.setUpMediaRouteButton(viewContext, it) }
            },
            modifier = Modifier.size(48.dp),
        )
    }
}
