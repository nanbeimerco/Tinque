package com.music.bitchord.feature.localsongactions.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.PlaylistRemove
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.ui.geometry.Rect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.data.model.Song
import com.music.bitchord.feature.artistimage.util.ArtistSplitter
import com.music.bitchord.ui.components.GlassMenuDivider
import com.music.bitchord.ui.components.GlassMenuItem
import com.music.bitchord.ui.components.LocalGlassMenu
import com.music.bitchord.ui.components.trackBoundsInRoot

/**
 * Opens the lyrics editor for a song; provided by the activity, which owns that
 * dialog. Null where it is not available, and the menu then leaves the row out.
 */
val LocalEditLyricsAction = staticCompositionLocalOf<((Song) -> Unit)?> { null }

/**
 * Material 3 DropdownMenu displaying the 8 local song options matching the reference player.
 */
@Composable
fun LocalSongDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    song: Song,
    onQueueNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onGoToAlbum: ((String) -> Unit)? = null,
    onGoToArtist: ((String) -> Unit)? = null,
    onGoToFolder: ((String) -> Unit)? = null,
    onTagEditor: () -> Unit,
    onShare: () -> Unit,
    onDeleteFromDevice: () -> Unit,
    onDetails: () -> Unit,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
) {
    val glassMenu = com.music.bitchord.ui.components.glassMenusHere()
    val editLyrics = LocalEditLyricsAction.current
    if (glassMenu != null) {
        // Liquid Glass menu, drawn by the screen's host (see GlassMenuHost).
        var anchor by remember { mutableStateOf(Rect.Zero) }
        var token by remember { mutableLongStateOf(-1L) }
        val dismiss by rememberUpdatedState(onDismissRequest)
        Spacer(Modifier.fillMaxSize().trackBoundsInRoot { anchor = it })
        LaunchedEffect(expanded) {
            if (expanded) {
                token = glassMenu.show(anchor, onDismiss = { dismiss() }) {
                    GlassSongMenuContent(
                        song = song,
                        onQueueNext = onQueueNext,
                        onAddToQueue = onAddToQueue,
                        onAddToPlaylist = onAddToPlaylist,
                        onRemoveFromPlaylist = onRemoveFromPlaylist,
                        onGoToAlbum = onGoToAlbum,
                        onGoToArtist = onGoToArtist,
                        onGoToFolder = onGoToFolder,
                        onTagEditor = onTagEditor,
                        onEditLyrics = editLyrics?.let { edit -> { edit(song) } },
                        onShare = onShare,
                        onDeleteFromDevice = onDeleteFromDevice,
                        onDetails = onDetails,
                    )
                }
            } else if (token >= 0) {
                glassMenu.close(token)
            }
        }
        DisposableEffect(glassMenu) {
            onDispose { if (token >= 0) glassMenu.close(token) }
        }
        return
    }

    var showingSubMenu by remember { mutableStateOf(false) }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = {
            showingSubMenu = false
            onDismissRequest()
        },
        offset = offset,
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        if (!showingSubMenu) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.queue_next)) },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.PlaylistPlay,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                },
                onClick = {
                    onDismissRequest()
                    onQueueNext()
                },
            )

            DropdownMenuItem(
                text = { Text(stringResource(R.string.add_to_queue)) },
                leadingIcon = {
                    Icon(
                        Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                },
                onClick = {
                    onDismissRequest()
                    onAddToQueue()
                },
            )

            DropdownMenuItem(
                text = { Text(stringResource(R.string.add_to_playlist)) },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.PlaylistAdd,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                },
                onClick = {
                    onDismissRequest()
                    onAddToPlaylist()
                },
            )

            if (onRemoveFromPlaylist != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.remove_from_playlist)) },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.PlaylistRemove,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    onClick = {
                        onDismissRequest()
                        onRemoveFromPlaylist()
                    },
                )
            }

            val hasGoTo = onGoToAlbum != null || onGoToArtist != null || onGoToFolder != null
            if (hasGoTo) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.go_to)) },
                    trailingIcon = {
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    onClick = {
                        showingSubMenu = true
                    },
                )
            }

            DropdownMenuItem(
                text = { Text(stringResource(R.string.tag_editor)) },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                },
                onClick = {
                    onDismissRequest()
                    onTagEditor()
                },
            )

            DropdownMenuItem(
                text = { Text(stringResource(R.string.share)) },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Share,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                },
                onClick = {
                    onDismissRequest()
                    onShare()
                },
            )

            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete_from_device)) },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                },
                onClick = {
                    onDismissRequest()
                    onDeleteFromDevice()
                },
            )

            DropdownMenuItem(
                text = { Text(stringResource(R.string.details)) },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Info,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                },
                onClick = {
                    onDismissRequest()
                    onDetails()
                },
            )
        } else {
            // "Go to" submenu
            DropdownMenuItem(
                text = { Text(stringResource(R.string.go_to), style = MaterialTheme.typography.titleSmall) },
                leadingIcon = {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        modifier = Modifier.size(20.dp),
                    )
                },
                onClick = {
                    showingSubMenu = false
                },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            if (onGoToAlbum != null && !song.albumName.isNullOrBlank()) {
                DropdownMenuItem(
                    text = { Text(song.albumName) },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.Album,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    onClick = {
                        showingSubMenu = false
                        onDismissRequest()
                        onGoToAlbum(song.albumName)
                    },
                )
            }

            if (onGoToArtist != null && song.artist.isNotBlank()) {
                val artists = ArtistSplitter.split(song.artist)
                if (artists.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text(song.artist) },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Person,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        onClick = {
                            showingSubMenu = false
                            onDismissRequest()
                            onGoToArtist(song.artist)
                        },
                    )
                } else {
                    artists.forEach { artistName ->
                        DropdownMenuItem(
                            text = { Text(artistName) },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            onClick = {
                                showingSubMenu = false
                                onDismissRequest()
                                onGoToArtist(artistName)
                            },
                        )
                    }
                }
            }

            if (onGoToFolder != null && !song.localPath.isNullOrBlank()) {
                val folderPath = song.localPath.substringBeforeLast('/')
                val folderName = folderPath.substringAfterLast('/')
                DropdownMenuItem(
                    text = { Text(folderName) },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    onClick = {
                        showingSubMenu = false
                        onDismissRequest()
                        onGoToFolder(folderPath)
                    },
                )
            }
        }
    }
}

/**
 * The ⋮ menu's rows on Liquid Glass. "Go to" slides over to its own page in the
 * same capsule rather than swapping the rows in place.
 */
@Composable
private fun GlassSongMenuContent(
    song: Song,
    onQueueNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onRemoveFromPlaylist: (() -> Unit)?,
    onGoToAlbum: ((String) -> Unit)?,
    onGoToArtist: ((String) -> Unit)?,
    onGoToFolder: ((String) -> Unit)?,
    onTagEditor: () -> Unit,
    onEditLyrics: (() -> Unit)?,
    onShare: () -> Unit,
    onDeleteFromDevice: () -> Unit,
    onDetails: () -> Unit,
) {
    var goTo by remember { mutableStateOf(false) }
    AnimatedContent(
        targetState = goTo,
        transitionSpec = {
            val forward = targetState
            (slideInHorizontally(tween(220)) { if (forward) it / 2 else -it / 2 } + fadeIn(tween(180))) togetherWith
                (slideOutHorizontally(tween(220)) { if (forward) -it / 2 else it / 2 } + fadeOut(tween(120)))
        },
        label = "glassSongMenuPage",
    ) { showGoTo ->
        Column {
            if (!showGoTo) {
                GlassMenuItem(stringResource(R.string.queue_next), onQueueNext, icon = Icons.Rounded.PlaylistPlay)
                GlassMenuItem(stringResource(R.string.add_to_queue), onAddToQueue, icon = Icons.AutoMirrored.Rounded.QueueMusic)
                GlassMenuItem(stringResource(R.string.add_to_playlist), onAddToPlaylist, icon = Icons.Rounded.PlaylistAdd)
                if (onRemoveFromPlaylist != null) {
                    GlassMenuItem(
                        stringResource(R.string.remove_from_playlist),
                        onRemoveFromPlaylist,
                        icon = Icons.Rounded.PlaylistRemove,
                        destructive = true,
                    )
                }
                val hasGoTo = (onGoToAlbum != null && !song.albumName.isNullOrBlank()) ||
                    (onGoToArtist != null && song.artist.isNotBlank()) ||
                    (onGoToFolder != null && !song.localPath.isNullOrBlank())
                if (hasGoTo) {
                    GlassMenuDivider()
                    GlassMenuItem(
                        stringResource(R.string.go_to),
                        onClick = { goTo = true },
                        trailingIcon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        keepOpen = true,
                    )
                }
                GlassMenuDivider()
                GlassMenuItem(stringResource(R.string.tag_editor), onTagEditor, icon = Icons.Rounded.Edit)
                if (onEditLyrics != null) {
                    GlassMenuItem(stringResource(R.string.edit_lyrics), onEditLyrics, icon = Icons.Rounded.Lyrics)
                }
                GlassMenuItem(stringResource(R.string.share), onShare, icon = Icons.Rounded.Share)
                GlassMenuItem(stringResource(R.string.details), onDetails, icon = Icons.Rounded.Info)
                GlassMenuDivider()
                GlassMenuItem(
                    stringResource(R.string.delete_from_device),
                    onDeleteFromDevice,
                    icon = Icons.Rounded.DeleteOutline,
                    destructive = true,
                )
            } else {
                GlassMenuItem(
                    stringResource(R.string.go_to),
                    onClick = { goTo = false },
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    keepOpen = true,
                    emphasized = true,
                )
                GlassMenuDivider()
                val album = song.albumName
                if (onGoToAlbum != null && !album.isNullOrBlank()) {
                    GlassMenuItem(album, { onGoToAlbum(album) }, icon = Icons.Rounded.Album)
                }
                if (onGoToArtist != null && song.artist.isNotBlank()) {
                    val artists = ArtistSplitter.split(song.artist).ifEmpty { listOf(song.artist) }
                    artists.forEach { name ->
                        GlassMenuItem(name, { onGoToArtist(name) }, icon = Icons.Rounded.Person)
                    }
                }
                val path = song.localPath
                if (onGoToFolder != null && !path.isNullOrBlank()) {
                    val folderPath = path.substringBeforeLast('/')
                    GlassMenuItem(folderPath.substringAfterLast('/'), { onGoToFolder(folderPath) }, icon = Icons.Rounded.FolderOpen)
                }
            }
        }
    }
}
