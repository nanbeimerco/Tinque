package com.music.bitchord.feature.localmusic.ui.components

import androidx.compose.runtime.collectAsState
import com.music.bitchord.data.settings.GridKind
import com.music.bitchord.ui.components.ScaledGridCells
import com.music.bitchord.ui.components.heatRing
import com.music.bitchord.ui.components.pinchToResizeGrid
import com.music.bitchord.ui.components.gridPulse
import com.music.bitchord.ui.components.pressBounce
import com.music.bitchord.ui.components.selectionPlatform
import androidx.compose.foundation.combinedClickable
import com.music.bitchord.ui.components.GridTitleOnArt
import com.music.bitchord.ui.components.reorderDragHost
import com.music.bitchord.ui.components.gridPinchHeader
import com.music.bitchord.ui.components.lightweightLiquidGlass
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Check
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.music.bitchord.ui.components.rememberGridColumns
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import com.music.bitchord.ui.utils.debouncedCombinedClickable
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Reorder
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.data.model.CARD_ART_PX
import com.music.bitchord.data.model.HEADER_ART_PX
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.model.durationMillis
import com.music.bitchord.data.model.isSameTrackAs
import com.music.bitchord.data.settings.LibraryViewType
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.components.FastScroller
import com.music.bitchord.ui.components.SectionIndexer
import com.music.bitchord.feature.artistimage.model.ArtistImage
import com.music.bitchord.ui.components.ExplicitSongTitle
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.ROW_DIVIDER_INSET
import com.music.bitchord.ui.components.SongRow
import com.music.bitchord.ui.components.thumbnailBorder
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.ui.theme.ArtworkPalette
import com.music.bitchord.ui.theme.rememberArtworkPalette
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.abs
import kotlin.math.roundToInt

/** Chooses the more legible monochrome foreground for the row surface. */
private fun Color.highContrastBlackOrWhite(): Color {
    val luminance = luminance()
    val blackContrast = (luminance + 0.05f) / 0.05f
    val whiteContrast = 1.05f / (luminance + 0.05f)
    return if (blackContrast >= whiteContrast) Color.Black else Color.White
}

/**
 * Something a page adds under its header's details — the album and artist
 * pages' "Add to Library". Provided around the page rather than passed down
 * through each header variant.
 */
val LocalDrillDownHeaderExtra = androidx.compose.runtime.compositionLocalOf<(@Composable () -> Unit)?> { null }

@Composable
fun DrillDownHeader(
    label: String,
    artworkUrl: String?,
    songs: List<Song>,
    isArtist: Boolean = false,
    onBack: (() -> Unit)? = null,
    isPlaylist: Boolean = false,
    fullScreenArtwork: Boolean = false,
    artHeight: Dp? = null,
    palette: ArtworkPalette? = null,
    modifier: Modifier = Modifier,
) {
    if (fullScreenArtwork && artHeight != null && palette != null) {
        FullScreenCollectionHeader(
            label = label,
            songs = songs,
            isPlaylist = isPlaylist || label.equals("Favorites", ignoreCase = true) || artworkUrl == "favorites",
            artHeight = artHeight,
            palette = palette,
            modifier = modifier,
        )
    } else {
        CompactDrillDownHeader(
            label = label,
            artworkUrl = artworkUrl,
            songs = songs,
            isArtist = isArtist,
            onBack = onBack,
            modifier = modifier,
        )
    }
}

@Composable
private fun CompactDrillDownHeader(
    label: String,
    artworkUrl: String?,
    songs: List<Song>,
    isArtist: Boolean,
    onBack: (() -> Unit)?,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = PAGE_GUTTER, end = PAGE_GUTTER, top = 8.dp, bottom = 12.dp),
    ) {
        val shape = if (isArtist) CircleShape else RoundedCornerShape(16.dp)
        val isFavorites = label.equals("Favorites", ignoreCase = true) || artworkUrl == "favorites"

        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(188.dp)
                .clip(shape)
                .background(
                    if (isFavorites) {
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFE91E63),
                                Color(0xFF8E24AA),
                                Color(0xFF3F51B5),
                            ),
                        )
                    } else {
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.secondaryContainer,
                                MaterialTheme.colorScheme.secondaryContainer,
                            ),
                        )
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isFavorites) {
                Icon(
                    imageVector = if (com.music.bitchord.data.settings.AppSettings.favoriteUsesStar.collectAsState().value) {
                        BitChordIcons.StarFilled
                    } else {
                        BitChordIcons.HeartFilled
                    },
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.95f),
                    modifier = Modifier.size(76.dp),
                )
            } else {
                Icon(
                    imageVector = if (isArtist) Icons.Rounded.Person else Icons.Rounded.Album,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(64.dp),
                )
                val imageModel: Any? = if (isArtist) {
                    remember(label, artworkUrl) {
                        ArtistImage(
                            name = label,
                            fallbackUrl = artworkUrl ?: songs.firstNotNullOfOrNull { it.thumbnailUrl },
                            isLarge = true,
                        )
                    }
                } else {
                    (artworkUrl ?: songs.firstNotNullOfOrNull { it.thumbnailUrl })?.artworkAt(CARD_ART_PX)
                }

                if (imageModel != null) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(shape)
                            .then(if (isArtist) Modifier.thumbnailBorder(shape) else Modifier),
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PAGE_GUTTER),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = buildString {
                if (isArtist) append("Artist · ")
                append(pluralStringResource(R.plurals.track_count_plural, songs.size, songs.size))
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        rememberHeaderHeat(songs)?.let { level ->
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                com.music.bitchord.ui.components.HeatMeter(
                    level = level,
                    width = 72.dp,
                    trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                )
            }
        }
        LocalDrillDownHeaderExtra.current?.let { extra ->
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { extra() }
        }
    }
}

/**
 * The heat key of the playlist this page shows (pl:<id>, or Favourites'),
 * so its header shows the same heat as the playlist's tile. Null for albums,
 * artists and folders, which use the group's heat on both.
 */
val LocalDrillDownHeatKey = androidx.compose.runtime.staticCompositionLocalOf<String?> { null }

@Composable
private fun rememberHeaderHeat(songs: List<Song>): Float? {
    val key = LocalDrillDownHeatKey.current
        ?: return com.music.bitchord.ui.components.rememberGroupHeat(songs)
    return com.music.bitchord.ui.components.rememberPlaylistTileHeat(key, songs)?.takeIf { it > 0f }
}

private const val COLLECTION_ART_RATIO = 0.92f
private val COLLECTION_HEADER_DROP = 44.dp

@Composable
private fun FullScreenCollectionHeader(
    label: String,
    songs: List<Song>,
    isPlaylist: Boolean,
    artHeight: Dp,
    palette: ArtworkPalette,
    modifier: Modifier,
) {
    val artists = remember(songs) {
        songs.asSequence()
            .map { it.artist.trim() }
            .filter { it.isNotBlank() && !it.equals("Unknown artist", ignoreCase = true) }
            .distinctBy { it.lowercase() }
            .take(4)
            .joinToString(", ")
    }
    val totalMinutes = remember(songs) {
        songs.sumOf { it.durationMillis().coerceAtLeast(0L) } / 60_000L
    }
    val duration = when {
        totalMinutes >= 60L -> stringResource(
            R.string.collection_duration_hours_minutes,
            (totalMinutes / 60L).toInt(),
            (totalMinutes % 60L).toInt(),
        )
        totalMinutes > 0L -> stringResource(R.string.collection_duration_minutes, totalMinutes.toInt())
        else -> null
    }
    val kind = stringResource(if (isPlaylist) R.string.playlist else R.string.album)
    val count = pluralStringResource(R.plurals.track_count_plural, songs.size, songs.size)
    val metadata = listOfNotNull(kind, count, duration).joinToString(" · ")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(artHeight + COLLECTION_HEADER_DROP),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = PAGE_GUTTER + 8.dp, end = PAGE_GUTTER + 8.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.headlineMedium,
                color = palette.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (artists.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = artists,
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.accent,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = metadata,
                style = MaterialTheme.typography.labelLarge,
                color = palette.onBackgroundVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            rememberHeaderHeat(songs)?.let { level ->
                Spacer(Modifier.height(8.dp))
                com.music.bitchord.ui.components.HeatMeter(
                    level = level,
                    width = 72.dp,
                    trackColor = palette.onBackgroundVariant.copy(alpha = 0.3f),
                )
            }
            LocalDrillDownHeaderExtra.current?.let { extra ->
                Spacer(Modifier.height(10.dp))
                extra()
            }
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongGridCard(
    song: Song,
    selected: Boolean = false,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    /** Extra content over the artwork's corner (the reorder grip). */
    artOverlay: (@Composable androidx.compose.foundation.layout.BoxScope.() -> Unit)? = null,
    /**
     * Guard taps against a double press. Off while songs are being picked: a
     * tap then only picks, and quick taps must all count.
     */
    debounce: Boolean = true,
) {
    val cardCompact = com.music.bitchord.ui.components.rememberCardCompact()
    val titleOnArt = com.music.bitchord.ui.components.rememberGridTitleOverlay()
    val selectionActive = com.music.bitchord.ui.components.LocalSongSelection.current?.active == true
    Column(
        modifier = modifier
            .then(cardCompact.measure)
            .gridPulse()
            .pressBounce()
            .clip(RoundedCornerShape(com.music.bitchord.ui.components.LocalGridSpacing.current.corner))
            .then(
                if (debounce && !selectionActive && !selected) {
                    Modifier.debouncedCombinedClickable(onClick = onClick, onLongClick = onLongPress)
                } else {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongPress)
                },
            )
            .padding(com.music.bitchord.ui.components.LocalGridSpacing.current.cardPadding),
    ) {
        val artScale = com.music.bitchord.ui.components.selectionArtScale(selected)
        val shape = com.music.bitchord.ui.components.rememberArtShape(selected = selected, playing = isCurrent, spinning = isPlaying)
        // The picked tile's platform and its check sit outside the artwork,
        // which shrinks onto the platform (M3 Expressive selected state).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .selectionPlatform(selected),
        ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = artScale
                    scaleY = artScale
                }
                .heatRing(com.music.bitchord.ui.components.rememberSongTileHeat(song), shape, still = isCurrent)
                .clip(shape)
                .thumbnailBorder(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp),
            )
            if (song.thumbnailUrl != null) {
                AsyncImage(
                    model = song.thumbnailUrl.artworkAt(CARD_ART_PX),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (titleOnArt) {
                GridTitleOnArt(
                    title = song.title,
                    subtitle = song.artist.ifBlank { stringResource(R.string.unknown_artist) },
                    tileWidth = cardCompact.width,
                    highlight = isCurrent,
                )
            }
            if (isCurrent) {
                Icon(
                    Icons.Rounded.GraphicEq,
                    contentDescription = stringResource(R.string.now_playing),
                    tint = if (titleOnArt) Color.White else MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(if (titleOnArt) Alignment.TopEnd else Alignment.Center)
                        .padding(if (titleOnArt) 8.dp else 0.dp)
                        .size(if (cardCompact.compact) 20.dp else 28.dp),
                )
            }
            artOverlay?.invoke(this)
        }
            if (selectionActive || selected) {
                com.music.bitchord.ui.components.SelectionMark(
                    selected = selected,
                    size = if (cardCompact.compact) 20.dp else 26.dp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(if (cardCompact.compact) 2.dp else 4.dp),
                )
            }
        }
        if (!titleOnArt) {
        Spacer(Modifier.height(if (cardCompact.compact) 4.dp else 8.dp))
        ExplicitSongTitle(
            song = song,
            style = if (cardCompact.compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,
            color = if (isCurrent || selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
        )
        }
        if (!cardCompact.compact && !titleOnArt) {
            Text(
                text = song.artist.ifBlank { stringResource(R.string.unknown_artist) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun DrillDownSongList(
    label: String,
    artworkUrl: String?,
    songs: List<Song>,
    isArtist: Boolean = false,
    viewType: LibraryViewType = LibraryViewType.LIST,
    onViewTypeToggle: (() -> Unit)? = null,
    selectedIds: Set<String> = emptySet(),
    selectionMode: Boolean = false,
    onSelectionToggle: ((Song) -> Unit)? = null,
    selectionToolbar: (@Composable (Color) -> Unit)? = null,
    currentSong: Song? = null,
    isPlaying: Boolean = false,
    onSongClick: (List<Song>, Int) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onSongMore: ((Song) -> Unit)? = null,
    onSongSwipe: (Song) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onMore: (() -> Unit)? = null,
    isPlaylist: Boolean = false,
    showArtworkInList: Boolean = false,
    fullScreenArtwork: Boolean = false,
    reorderEnabled: Boolean = false,
    onReorderToggle: (() -> Unit)? = null,
    onReorderComplete: ((List<Song>) -> Unit)? = null,
    /** Drops the whole selection (a drag ends it: the picked songs have moved). */
    onClearSelection: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    songDropdownMenu: (@Composable (Song) -> Unit)? = null,
    onArtworkPaletteChanged: ((ArtworkPalette?) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val pageArtworkUrl = remember(fullScreenArtwork, artworkUrl, songs) {
        if (!fullScreenArtwork) {
            null
        } else {
            artworkUrl
                ?.takeIf { it.isNotBlank() && it != "favorites" }
                ?.artworkAt(HEADER_ART_PX)
                ?: songs.firstNotNullOfOrNull { it.artworkAt(HEADER_ART_PX) }
        }
    }
    val artworkPalette = rememberArtworkPalette(
        imageUrl = pageArtworkUrl,
        artPx = HEADER_ART_PX,
    )
    LaunchedEffect(artworkPalette.background) {
        onArtworkPaletteChanged?.invoke(artworkPalette.takeIf { fullScreenArtwork })
    }
    // Detail pages draw their artwork behind the translucent app bar. Keep the
    // normal top inset for every other list, but let eligible collection art
    // begin at the top edge so it fills the space underneath that bar.
    val pageContentPadding = if (fullScreenArtwork) {
        PaddingValues(bottom = contentPadding.calculateBottomPadding())
    } else {
        contentPadding
    }
    // Release pages in BitChord leave rows transparent so the artwork-derived
    // wash continues behind the track list instead of snapping back to black.
    val rowBackground = if (fullScreenArtwork) Color.Transparent else MaterialTheme.colorScheme.background
    val rowSubtitleColor = if (fullScreenArtwork) {
        artworkPalette.onBackgroundVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val rowAccent = if (fullScreenArtwork) artworkPalette.accent else MaterialTheme.colorScheme.primary
    val rowDividerColor = if (fullScreenArtwork) {
        artworkPalette.divider
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }
    val selectionToolbarBackground = if (fullScreenArtwork) {
        artworkPalette.wash
    } else {
        MaterialTheme.colorScheme.background
    }
    val selectionToolbarContentColor = selectionToolbarBackground.highContrastBlackOrWhite()

    // Reordering: only a playlist's songs have an order of their own to change.
    val canReorder = isPlaylist && onReorderComplete != null
    val currentSelectedIds by rememberUpdatedState(selectedIds)
    val currentLongPress by rememberUpdatedState(onSongLongPress)
    fun songKeyOf(song: Song): String = song.localUri ?: song.videoId
    fun isPicked(song: Song): Boolean =
        songKeyOf(song) in currentSelectedIds || song.videoId in currentSelectedIds

    /**
     * A long press that selects (as before) and arms the reorder drag: if the
     * finger then moves, the song is lifted and dragged. A song this press
     * picked out of no selection is let go again after the drag, so moving
     * one song does not leave the page in selection mode; a long press on an
     * already-picked song unpicks it only if it was not dragged.
     */
    fun armedLongPress(arm: com.music.bitchord.ui.components.ReorderArm, song: Song) {
        if (!canReorder) {
            currentLongPress(song)
            return
        }
        arm.arm(songKeyOf(song))
        if (!isPicked(song)) {
            currentLongPress(song)
        } else {
            arm.onNoDrag = { currentLongPress(song) }
        }
    }

    // Both layouts' scroll states live here, so a list/grid switch can carry
    // the place over; and the list/grid icon's animation outlives the button
    // row, which each layout builds anew.
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val viewMorph = remember {
        List(4) { androidx.compose.animation.core.Animatable(if (viewType == LibraryViewType.GRID) 0f else 1f) }
    }
    val switchScope = rememberCoroutineScope()
    val screenWidthPx = with(LocalDensity.current) {
        androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp.toPx()
    }
    val switchColumns = rememberGridColumns(GridKind.COLLECTION_SONGS)
    val currentViewType by rememberUpdatedState(viewType)
    val headerItems = 1 + (if (!selectionMode) 1 else 0) + (if (selectionToolbar != null) 1 else 0)
    val currentHeaderItems by rememberUpdatedState(headerItems)
    val animatedViewToggle: (() -> Unit)? = onViewTypeToggle?.let { toggle ->
        {
            switchScope.launch {
                val fromGrid = currentViewType == LibraryViewType.GRID
                val index = if (fromGrid) gridState.firstVisibleItemIndex else listState.firstVisibleItemIndex
                val offset = if (fromGrid) gridState.firstVisibleItemScrollOffset else listState.firstVisibleItemScrollOffset
                // Within the header (same in both layouts): keep it exactly.
                // In the songs: the song at the top stays at the top.
                val keep = if (index < currentHeaderItems) offset else 0
                com.music.bitchord.ui.components.animateGridSwitch(
                    rippleFrom = Offset(screenWidthPx / 2f, 0f),
                    cellPx = screenWidthPx / switchColumns.coerceAtLeast(1),
                ) {
                    if (fromGrid) listState.requestScrollToItem(index, keep) else gridState.requestScrollToItem(index, keep)
                    toggle()
                }
            }
            Unit
        }
    }

    // Each drag offers "Undo" for that one move (the app shows it in its
    // floating glass layer), and ends the selection.
    var orderBeforeDrag by remember { mutableStateOf<List<Song>?>(null) }
    val currentReorderComplete by rememberUpdatedState(onReorderComplete)
    val currentClearSelection by rememberUpdatedState(onClearSelection)
    fun noteDragStart(current: List<Song>) {
        orderBeforeDrag = current
    }
    fun noteDragEnd() {
        val before = orderBeforeDrag
        orderBeforeDrag = null
        currentClearSelection?.invoke()
        if (before != null) {
            com.music.bitchord.ui.components.ReorderUndo.show { currentReorderComplete?.invoke(before) }
        }
    }
    DisposableEffect(Unit) {
        onDispose { com.music.bitchord.ui.components.ReorderUndo.clear() }
    }

    // The page art lives here, outside both layouts, so a list/grid switch
    // neither rebuilds nor reloads it.
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(if (fullScreenArtwork) artworkPalette.wash else MaterialTheme.colorScheme.background),
    ) {
    val outerArtHeight = (maxWidth / COLLECTION_ART_RATIO).coerceAtMost(maxHeight * 0.6f)
    val outerArtHeightPx = with(LocalDensity.current) { outerArtHeight.toPx() }
    if (fullScreenArtwork) {
        CollectionPageBackground(
            imageUrl = pageArtworkUrl,
            palette = artworkPalette,
            artHeight = outerArtHeight,
            artTopPx = {
                val atTop = if (currentViewType == LibraryViewType.GRID) {
                    gridState.firstVisibleItemIndex == 0
                } else {
                    listState.firstVisibleItemIndex == 0
                }
                val offset = if (currentViewType == LibraryViewType.GRID) {
                    gridState.firstVisibleItemScrollOffset
                } else {
                    listState.firstVisibleItemScrollOffset
                }
                if (atTop) -offset.toFloat() else -outerArtHeightPx * 2f
            },
        )
    }

    if (viewType == LibraryViewType.GRID) {
        var gridSongs by remember(songs) { mutableStateOf(songs) }
        val stack = remember { GroupDragStack() }
        val stackScope = rememberCoroutineScope()
        val tileCoords = remember { mutableMapOf<String, androidx.compose.ui.layout.LayoutCoordinates>() }
        val gridCoords = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
        var groupOrder by remember { mutableStateOf<List<Song>>(emptyList()) }
        val reorder = com.music.bitchord.ui.components.rememberGridReorderState(gridState)
        val gridArm = remember { com.music.bitchord.ui.components.ReorderArm() }
        var groupKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
        reorder.onMove = { fromKey, toKey ->
            val list = gridSongs
            val from = list.indexOfFirst { songKeyOf(it) == fromKey }
            val to = list.indexOfFirst { songKeyOf(it) == toKey }
            if (from >= 0 && to >= 0 && from != to) {
                gridSongs = list.toMutableList().apply { add(to, removeAt(from)) }
            }
        }
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val artHeight = (maxWidth / COLLECTION_ART_RATIO).coerceAtMost(maxHeight * 0.6f)
            val gridCols = rememberGridColumns(GridKind.COLLECTION_SONGS)
            val gridSpacing = com.music.bitchord.ui.components.rememberGridSpacing(gridCols)
            androidx.compose.runtime.CompositionLocalProvider(
                com.music.bitchord.ui.components.LocalGridSpacing provides gridSpacing,
                com.music.bitchord.ui.components.LocalSelectionPlatformColor provides
                    if (fullScreenArtwork) artworkPalette.accent.copy(alpha = 0.38f) else Color.Unspecified,
            ) {
            LazyVerticalGrid(
                columns = ScaledGridCells(gridCols),
                state = gridState,
                modifier = Modifier
                    .fillMaxSize()
                    .onPlaced { gridCoords[0] = it }
                    .then(
                        if (canReorder) {
                            Modifier.reorderDragHost(
                                arm = gridArm,
                                onStart = { key, position ->
                                    val lead = gridSongs.firstOrNull { songKeyOf(it) == key }
                                    groupKeys = if (lead != null && isPicked(lead)) {
                                        gridSongs.filter { isPicked(it) }.map { songKeyOf(it) }.toSet()
                                    } else {
                                        emptySet()
                                    }
                                    noteDragStart(gridSongs)
                                    val started = reorder.start(key, position)
                                    if (started && lead != null && groupKeys.size > 1) {
                                        // The rest of the pick leaves its places (the
                                        // grid closes up) and flies in under the finger.
                                        groupOrder = gridSongs.filter { songKeyOf(it) in groupKeys }
                                        val others = groupOrder.filter { songKeyOf(it) != key }
                                        val starts = others.associate {
                                            songKeyOf(it) to positionIn(gridCoords[0], tileCoords[songKeyOf(it)])
                                        }
                                        val leadCoordinates = tileCoords[key]
                                        val heldStart = positionIn(gridCoords[0], leadCoordinates) ?: position
                                        val size = leadCoordinates?.size ?: androidx.compose.ui.unit.IntSize.Zero
                                        gridSongs = gridSongs.filterNot { songKeyOf(it) in groupKeys && songKeyOf(it) != key }
                                        stackScope.launch {
                                            stack.begin(lead, others, heldStart, size, { starts[it] }, position)
                                        }
                                    }
                                    started
                                },
                                onDrag = { delta, position ->
                                    reorder.drag(delta, position)
                                    if (stack.active) stack.finger = position
                                },
                                onEnd = {
                                    val lead = reorder.draggedKey
                                    reorder.end()
                                    if (lead != null && stack.active && groupOrder.size > 1) {
                                        // The pick goes back in, in its own order, where
                                        // the held song landed; the stack comes apart.
                                        val at = gridSongs.indexOfFirst { songKeyOf(it) == lead }
                                        if (at >= 0) {
                                            gridSongs = gridSongs.take(at) + groupOrder + gridSongs.drop(at + 1)
                                        }
                                        onReorderComplete?.invoke(gridSongs)
                                        stackScope.launch {
                                            stack.drop(
                                                placeOf = { k -> positionIn(gridCoords[0], tileCoords[k]) },
                                                onDone = {
                                                    groupKeys = emptySet()
                                                    groupOrder = emptyList()
                                                },
                                            )
                                        }
                                    } else {
                                        groupKeys = emptySet()
                                        onReorderComplete?.invoke(gridSongs)
                                    }
                                    noteDragEnd()
                                },
                            )
                        } else {
                            Modifier
                        },
                    )
                    .pinchToResizeGrid(GridKind.COLLECTION_SONGS, gridCols),
                contentPadding = PaddingValues(
                    start = PAGE_GUTTER,
                    end = PAGE_GUTTER,
                    top = pageContentPadding.calculateTopPadding(),
                    bottom = pageContentPadding.calculateBottomPadding() + 8.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(gridSpacing.horizontal),
                verticalArrangement = Arrangement.spacedBy(gridSpacing.vertical),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    DrillDownHeader(
                        label = label,
                        artworkUrl = artworkUrl,
                        songs = songs,
                        isArtist = isArtist,
                        onBack = onBack,
                        isPlaylist = isPlaylist,
                        fullScreenArtwork = fullScreenArtwork,
                        artHeight = artHeight,
                        palette = artworkPalette,
                        modifier = Modifier.gridHeaderBleed(gridSpacing.vertical).gridPinchHeader(),
                    )
                }
                if (!selectionMode) item(span = { GridItemSpan(maxLineSpan) }) {
                    DrillDownActionRow(
                        modifier = Modifier
                            .gridHeaderBleed(gridSpacing.vertical, extraBottom = (4.dp - gridSpacing.cardPadding).coerceAtLeast(0.dp))
                            .gridPinchHeader(),
                        pageColor = if (fullScreenArtwork) artworkPalette.wash else MaterialTheme.colorScheme.background,
                        songs = songs,
                        viewType = viewType,
                        onViewTypeToggle = animatedViewToggle,
                        viewMorph = viewMorph,
                        onSongClick = onSongClick,
                        onShuffle = onShuffle,
                        onMore = onMore,
                        reorderEnabled = reorderEnabled,
                        onReorderToggle = onReorderToggle,
                    )
                }
                if (selectionToolbar != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.gridHeaderBleed(gridSpacing.vertical).gridPinchHeader()) {
                            selectionToolbar(selectionToolbarContentColor)
                        }
                    }
                }
                itemsIndexed(gridSongs, key = { _, song -> songKeyOf(song) }) { index, song ->
                    val songKey = songKeyOf(song)
                    val lifted = reorder.draggedKey == songKey
                    val settling = reorder.droppingKey == songKey
                    val lift by animateFloatAsState(
                        if (lifted) 1.06f else 1f,
                        androidx.compose.animation.core.spring(0.6f, 800f),
                        label = "gridLift",
                    )
                    SongGridCard(
                        debounce = !selectionMode && selectedIds.isEmpty(),
                        modifier = Modifier
                            .onPlaced { tileCoords[songKey] = it }
                            .then(if (lifted || settling) Modifier else Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null))
                            .zIndex(if (lifted || settling) 2f else 0f)
                            .graphicsLayer {
                                val offset = reorder.offsetOf(songKey)
                                translationX = offset.x
                                translationY = offset.y
                                scaleX = lift
                                scaleY = lift
                                alpha = if (stack.covers(songKey)) 0f else 1f
                                shadowElevation = ((lift - 1f) / 0.06f).coerceIn(0f, 1f) * 12.dp.toPx()
                                shape = RoundedCornerShape(16.dp)
                            },
                        song = song,
                        selected = song.videoId in selectedIds || songKey in selectedIds,
                        isCurrent = song.isSameTrackAs(currentSong),
                        isPlaying = song.isSameTrackAs(currentSong) && isPlaying,
                        onClick = {
                            if (selectionMode) onSelectionToggle?.invoke(song) else onSongClick(gridSongs, index)
                        },
                        onLongPress = { armedLongPress(gridArm, song) },
                        artOverlay = if (canReorder && reorderEnabled) {
                            { ReorderGrip(onTouch = { gridArm.arm(songKey, immediate = true) }) }
                        } else {
                            null
                        },
                    )
                }
            }
            GroupDragStackOverlay(stack) { song, tileModifier ->
                SongGridCard(
                    modifier = tileModifier,
                    song = song,
                    selected = true,
                    isCurrent = song.isSameTrackAs(currentSong),
                    isPlaying = song.isSameTrackAs(currentSong) && isPlaying,
                    onClick = {},
                )
            }
            }
            FastScroller(
                gridState = gridState,
                itemCount = songs.size,
                headerCount = 2,
                sectionNameForIndex = { index -> SectionIndexer.getSectionName(songs[index].title) },
                contentPadding = PaddingValues(
                    top = pageContentPadding.calculateTopPadding(),
                    bottom = pageContentPadding.calculateBottomPadding() + 8.dp,
                ),
            )
        }
    } else {
        var displayedSongs by remember(songs) { mutableStateOf(songs) }
        val dragState = rememberPlaylistSongDragState(listState, displayedSongs.size) { from, to, draggedKey, insertAfter, groupedKeys ->
            val current = displayedSongs
            if (from !in current.indices || to !in current.indices || from == to) return@rememberPlaylistSongDragState null

            val movingSongs = if (groupedKeys.size > 1 && draggedKey in groupedKeys) {
                current.filter { (it.localUri ?: it.videoId) in groupedKeys }
            } else {
                listOf(current[from])
            }
            if (movingSongs.isEmpty()) return@rememberPlaylistSongDragState null

            val movingKeys = movingSongs.mapTo(mutableSetOf()) { it.localUri ?: it.videoId }
            val targetKey = current[to].localUri ?: current[to].videoId
            if (targetKey in movingKeys) return@rememberPlaylistSongDragState null

            val remaining = current.filterNot { (it.localUri ?: it.videoId) in movingKeys }
            val targetIndex = remaining.indexOfFirst { (it.localUri ?: it.videoId) == targetKey }
            if (targetIndex < 0) return@rememberPlaylistSongDragState null

            val anchorOrder = movingSongs.indexOfFirst { (it.localUri ?: it.videoId) == draggedKey }
                .coerceAtLeast(0)
            val insertionIndex = (targetIndex + if (insertAfter) 1 else 0).coerceIn(0, remaining.size)
            val reordered = remaining.toMutableList().apply { addAll(insertionIndex, movingSongs) }
            if (reordered == current) return@rememberPlaylistSongDragState null

            displayedSongs = reordered
            insertionIndex + anchorOrder
        }
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val artHeight = (maxWidth / COLLECTION_ART_RATIO).coerceAtMost(maxHeight * 0.6f)
            val listArm = remember { com.music.bitchord.ui.components.ReorderArm() }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (canReorder) {
                            Modifier.reorderDragHost(
                                arm = listArm,
                                onStart = { key, _ ->
                                    val index = displayedSongs.indexOfFirst { songKeyOf(it) == key }
                                    if (index < 0) {
                                        false
                                    } else {
                                        val lead = displayedSongs[index]
                                        val group = if (isPicked(lead)) {
                                            displayedSongs.filter { isPicked(it) }.map { songKeyOf(it) }.toSet()
                                        } else {
                                            emptySet()
                                        }
                                        noteDragStart(displayedSongs)
                                        dragState.onDragStart(key, group, index)
                                        true
                                    }
                                },
                                onDrag = { delta, _ -> dragState.onDrag(delta.y) },
                                onEnd = {
                                    if (dragState.isGroupDrag) dragState.finishGroupedDrag()
                                    dragState.onDragEnd()
                                    onReorderComplete?.invoke(displayedSongs)
                                    noteDragEnd()
                                },
                            )
                        } else {
                            Modifier
                        },
                    ),
                contentPadding = pageContentPadding,
            ) {
                item {
                    DrillDownHeader(
                        label = label,
                        artworkUrl = artworkUrl,
                        songs = songs,
                        isArtist = isArtist,
                        onBack = onBack,
                        isPlaylist = isPlaylist,
                        fullScreenArtwork = fullScreenArtwork,
                        artHeight = artHeight,
                        palette = artworkPalette,
                    )
                }
                if (!selectionMode) item {
                    DrillDownActionRow(
                        pageColor = if (fullScreenArtwork) artworkPalette.wash else MaterialTheme.colorScheme.background,
                        songs = songs,
                        viewType = viewType,
                        onViewTypeToggle = animatedViewToggle,
                        viewMorph = viewMorph,
                        onSongClick = onSongClick,
                        onShuffle = onShuffle,
                        onMore = onMore,
                        reorderEnabled = reorderEnabled,
                        onReorderToggle = onReorderToggle,
                    )
                }
                if (selectionToolbar != null) {
                    item { selectionToolbar(selectionToolbarContentColor) }
                }
                itemsIndexed(displayedSongs, key = { _, song -> song.localUri ?: song.videoId }) { index, song ->
                    val trackNumber = if (isPlaylist && showArtworkInList) null else index + 1
                    if (reorderEnabled && isPlaylist) {
                        val songKey = song.localUri ?: song.videoId
                        val isDragging = dragState.draggedKey == songKey
                        val isDragGroupMember = dragState.isDragMember(songKey)
                        val finishDrag by rememberUpdatedState(newValue = { commit: Boolean ->
                            if (dragState.draggedKey == songKey) {
                                if (commit && dragState.isGroupDrag) {
                                    dragState.finishGroupedDrag()
                                }
                                dragState.onDragEnd()
                                onReorderComplete?.invoke(displayedSongs)
                            }
                        })
                        Row(
                            modifier = Modifier
                                .then(com.music.bitchord.ui.components.GridSwitchBlurFeel)
                                .zIndex(if (isDragGroupMember) 1f else 0f)
                                .graphicsLayer {
                                    translationY = if (isDragGroupMember) dragState.renderOffset else 0f
                                }
                                .background(
                                    if (isDragGroupMember) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                    else Color.Transparent,
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.weight(1f)) {
                                SongRow(
                                    song = song,
                                    rowBackground = rowBackground,
                                    subtitleColor = rowSubtitleColor,
                                    downloadedTint = rowAccent,
                                    selected = song.videoId in selectedIds || (song.localUri ?: song.videoId) in selectedIds,
                                    isCurrent = song.isSameTrackAs(currentSong),
                                    isPlaying = song.isSameTrackAs(currentSong) && isPlaying,
                                    activeTint = rowAccent,
                                    trackNumber = trackNumber,
                                    onClick = {
                                        if (selectionMode) onSelectionToggle?.invoke(song)
                                        else onSongClick(displayedSongs, index)
                                    },
                                    onLongPress = { armedLongPress(listArm, song) },
                                    debounce = !selectionMode && selectedIds.isEmpty(),
                                    onMore = onSongMore?.let { more -> { more(song) } },
                                    onSwipeToQueue = null,
                                    dropdownMenu = songDropdownMenu?.let { menu -> { menu(song) } },
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.DragHandle,
                                contentDescription = stringResource(
                                    if (selectionMode && (songKey in selectedIds || song.videoId in selectedIds)) {
                                        R.string.drag_selected_songs_to_reorder
                                    } else {
                                        R.string.drag_to_reorder_song
                                    },
                                ),
                                tint = if (isDragging) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(48.dp)
                                    .padding(end = PAGE_GUTTER)
                                    .pointerInput(songKey, selectionMode, selectedIds) {
                                        detectDragGestures(
                                            onDragStart = {
                                                val draggedIsSelected = songKey in selectedIds || song.videoId in selectedIds
                                                val groupKeys = if (selectionMode && draggedIsSelected) {
                                                    displayedSongs
                                                        .filter { (it.localUri ?: it.videoId) in selectedIds || it.videoId in selectedIds }
                                                        .map { it.localUri ?: it.videoId }
                                                        .toSet()
                                                } else {
                                                    emptySet()
                                                }
                                                dragState.onDragStart(songKey, groupKeys, index)
                                            },
                                            onDragEnd = { finishDrag(true) },
                                            onDragCancel = { finishDrag(false) },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragState.onDrag(dragAmount.y)
                                            },
                                        )
                                    },
                            )
                        }
                    } else {
                        val songKey = song.localUri ?: song.videoId
                        val isDragGroupMember = canReorder && dragState.isDragMember(songKey)
                        Box(
                            modifier = Modifier
                                .then(com.music.bitchord.ui.components.GridSwitchBlurFeel)
                                .zIndex(if (isDragGroupMember) 1f else 0f)
                                .graphicsLayer {
                                    translationY = if (isDragGroupMember) dragState.renderOffset else 0f
                                }
                                .background(
                                    if (isDragGroupMember) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                    else Color.Transparent,
                                ),
                        ) {
                        SongRow(
                            song = song,
                            rowBackground = rowBackground,
                            subtitleColor = rowSubtitleColor,
                            downloadedTint = rowAccent,
                            selected = song.videoId in selectedIds || (song.localUri ?: song.videoId) in selectedIds,
                            isCurrent = song.isSameTrackAs(currentSong),
                            isPlaying = song.isSameTrackAs(currentSong) && isPlaying,
                            activeTint = rowAccent,
                            trackNumber = trackNumber,
                            onClick = {
                                if (selectionMode) onSelectionToggle?.invoke(song) else onSongClick(displayedSongs, index)
                            },
                            onLongPress = { armedLongPress(listArm, song) },
                            debounce = !selectionMode && selectedIds.isEmpty(),
                            onMore = onSongMore?.let { more -> { more(song) } },
                            onSwipeToQueue = if (selectionMode) null else { { onSongSwipe(song) } },
                            dropdownMenu = songDropdownMenu?.let { menu -> { menu(song) } },
                        )
                        }
                    }
                    if (index < songs.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = ROW_DIVIDER_INSET),
                            thickness = 0.5.dp,
                            color = rowDividerColor,
                        )
                    }
                }
            }
            FastScroller(
                listState = listState,
                itemCount = songs.size,
                headerCount = 2,
                sectionNameForIndex = { index -> SectionIndexer.getSectionName(songs[index].title) },
                contentPadding = pageContentPadding,
            )
        }
    }
    }
}

@Composable
private fun CollectionPageBackground(
    imageUrl: String?,
    palette: ArtworkPalette,
    artHeight: Dp,
    artTopPx: () -> Float,
) {
    // The sleeve stays sharp with only the wash below it. A blurred copy used
    // to fade in over its lower half ~280ms after opening, which read as the
    // page "going blurry" a beat late; the first frame is the intended look.
    Box(Modifier.fillMaxSize().clipToBounds().then(com.music.bitchord.ui.components.GridPinchBackdropFeel)) {
        if (!imageUrl.isNullOrBlank()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(artHeight)
                    .offset { IntOffset(0, artTopPx().roundToInt()) },
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(palette.elevated),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.45f to Color.Transparent,
                                0.65f to palette.wash.copy(alpha = 0.30f),
                                0.82f to palette.wash.copy(alpha = 0.72f),
                                0.94f to palette.wash.copy(alpha = 0.95f),
                                1.00f to palette.wash,
                            ),
                        ),
                )
            }
        }
    }
}

/** Drag-to-reorder state for the song section below the two static header rows. */
@Composable
private fun rememberPlaylistSongDragState(
    listState: androidx.compose.foundation.lazy.LazyListState,
    songCount: Int,
    onMove: (from: Int, to: Int, draggedKey: String, insertAfter: Boolean, groupedKeys: Set<String>) -> Int?,
): PlaylistSongDragState {
    val state = remember(listState) { PlaylistSongDragState(listState) }
    state.lazyOffset = 2
    state.lazyRange = if (songCount > 0) 2 until songCount + 2 else IntRange.EMPTY
    state.onMove = onMove
    with(LocalDensity.current) {
        state.edgeZone = 40.dp.toPx()
        state.edgeSpeed = 340.dp.toPx()
    }

    val direction = state.autoScrollDir
    LaunchedEffect(state, direction) {
        if (direction == 0) return@LaunchedEffect
        listState.scroll {
            var previous = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val seconds = ((now - previous) / 1_000_000_000f).coerceAtMost(1f / 30f)
                previous = now
                val scrolled = scrollBy(state.autoScrollSpeed * seconds)
                if (scrolled == 0f) break
                state.onScrolled()
            }
        }
    }
    return state
}

/** Holds the grabbed song under the finger, swaps crossed rows, and scrolls at list edges. */
private class PlaylistSongDragState(
    private val listState: androidx.compose.foundation.lazy.LazyListState,
) {
    var lazyRange: IntRange = IntRange.EMPTY
    var lazyOffset: Int = 2
    var onMove: (from: Int, to: Int, draggedKey: String, insertAfter: Boolean, groupedKeys: Set<String>) -> Int? =
        { _, _, _, _, _ -> null }
    var edgeZone: Float = 0f
    var edgeSpeed: Float = 0f

    var draggedKey by mutableStateOf<Any?>(null)
        private set
    private var groupedKeys: Set<String> = emptySet()
    var renderOffset by mutableFloatStateOf(0f)
        private set
    var autoScrollDir by mutableIntStateOf(0)
        private set
    var autoScrollSpeed: Float = 0f
        private set
    private var heldCenter: Float = Float.NaN
    private var awaiting: Int? = null
    private var draggedDataIndex: Int = -1
    private var groupDropTargetIndex: Int? = null
    private var groupDropAfter: Boolean = false

    val isGroupDrag: Boolean get() = groupedKeys.size > 1

    fun isDragMember(key: String): Boolean =
        draggedKey == key || (isGroupDrag && key in groupedKeys)

    fun onDragStart(key: Any, groupedKeys: Set<String> = emptySet(), dataIndex: Int = -1) {
        draggedKey = key
        this.groupedKeys = groupedKeys
        draggedDataIndex = dataIndex
        heldCenter = Float.NaN
        renderOffset = 0f
        awaiting = null
        groupDropTargetIndex = null
        groupDropAfter = false
        setAutoScroll(0f)
    }

    fun onDrag(deltaY: Float) = settle(deltaY)
    fun onScrolled() = settle(0f)

    fun onDragEnd() {
        draggedKey = null
        groupedKeys = emptySet()
        heldCenter = Float.NaN
        renderOffset = 0f
        awaiting = null
        draggedDataIndex = -1
        groupDropTargetIndex = null
        groupDropAfter = false
        setAutoScroll(0f)
    }

    fun finishGroupedDrag() {
        val key = draggedKey as? String ?: return
        val targetIndex = groupDropTargetIndex ?: return
        if (draggedDataIndex < 0 || targetIndex < 0) return
        onMove(draggedDataIndex, targetIndex, key, groupDropAfter, groupedKeys)
    }

    private fun settle(deltaY: Float) {
        val key = draggedKey ?: return
        val items = listState.layoutInfo.visibleItemsInfo
        val dragged = items.firstOrNull { it.key == key } ?: run {
            setAutoScroll(0f)
            return
        }
        val half = dragged.size / 2f
        if (heldCenter.isNaN()) heldCenter = dragged.offset + half
        heldCenter += deltaY
        holdToSongRange(items, dragged)
        val top = heldCenter - half
        aimAutoScroll(top, dragged)
        renderOffset = insideViewport(top, dragged.size) - dragged.offset

        if (!isGroupDrag) {
            awaiting?.let { targetIndex ->
                if (dragged.index != targetIndex) return
                awaiting = null
            }
        }
        val target = items
            .filter { item ->
                val itemKey = item.key as? String
                item.index in lazyRange && item.index != dragged.index &&
                    (!isGroupDrag || itemKey == null || itemKey !in groupedKeys)
            }
            .minByOrNull { abs((it.offset + it.size / 2f) - heldCenter ) }
            ?: return
        if (abs(heldCenter - (target.offset + target.size / 2f)) > target.size / 2f) return
        if (target.index == listState.firstVisibleItemIndex && listState.canScrollBackward) return
        if (isGroupDrag) {
            groupDropTargetIndex = target.index - lazyOffset
            groupDropAfter = heldCenter > target.offset + target.size / 2f
            return
        }
        val expectedDraggedIndex = onMove(
            dragged.index - lazyOffset,
            target.index - lazyOffset,
            key as? String ?: return,
            heldCenter > target.offset + target.size / 2f,
            groupedKeys,
        ) ?: return
        awaiting = expectedDraggedIndex + lazyOffset
    }

    private fun aimAutoScroll(top: Float, dragged: androidx.compose.foundation.lazy.LazyListItemInfo) {
        val info = listState.layoutInfo
        val bottom = top + dragged.size
        val speed = when {
            top < info.viewportStartOffset + edgeZone -> {
                -edgeSpeed * ((info.viewportStartOffset + edgeZone - top) / edgeZone).coerceIn(0f, 1f)
            }
            bottom > info.viewportEndOffset - edgeZone -> {
                edgeSpeed * ((bottom - (info.viewportEndOffset - edgeZone)) / edgeZone).coerceIn(0f, 1f)
            }
            else -> 0f
        }
        val blocked = when {
            speed < 0f -> dragged.index <= lazyRange.first || !listState.canScrollBackward
            speed > 0f -> dragged.index >= lazyRange.last || !listState.canScrollForward
            else -> true
        }
        setAutoScroll(if (blocked) 0f else speed)
    }

    private fun holdToSongRange(items: List<androidx.compose.foundation.lazy.LazyListItemInfo>, dragged: androidx.compose.foundation.lazy.LazyListItemInfo) {
        val half = dragged.size / 2f
        items.firstOrNull { it.index == lazyRange.first }?.let {
            heldCenter = heldCenter.coerceAtLeast(it.offset + half)
        }
        items.firstOrNull { it.index == lazyRange.last }?.let {
            heldCenter = heldCenter.coerceAtMost(it.offset + it.size - half)
        }
    }

    private fun insideViewport(top: Float, size: Int): Float {
        val info = listState.layoutInfo
        val minTop = info.viewportStartOffset.toFloat()
        val maxTop = (info.viewportEndOffset - size).toFloat().coerceAtLeast(minTop)
        return top.coerceIn(minTop, maxTop)
    }

    private fun setAutoScroll(speed: Float) {
        autoScrollSpeed = speed
        val direction = when {
            speed > 0f -> 1
            speed < 0f -> -1
            else -> 0
        }
        if (autoScrollDir != direction) autoScrollDir = direction
    }
}


/**
 * The grip a grid tile shows while Move is on: hold it and drag straight
 * away, no long press. Everywhere else on the tile still scrolls the grid.
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.ReorderGrip(onTouch: () -> Unit) {
    val touch by rememberUpdatedState(onTouch)
    Box(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(5.dp)
            .size(30.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    touch()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.DragHandle,
            contentDescription = stringResource(R.string.drag_to_reorder_song),
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp),
        )
    }
}


/**
 * In the grid: a header row (title, buttons, toolbar) laid out exactly as in
 * the list — out past the grid's side padding to the full page width, and
 * with the grid's row gap under it taken back — so switching views moves
 * none of it.
 */
private fun Modifier.gridHeaderBleed(gap: Dp, extraBottom: Dp = 0.dp): Modifier = this.layout { measurable, constraints ->
    val extra = (PAGE_GUTTER * 2).roundToPx()
    val width = constraints.maxWidth + extra
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    // [extraBottom]: tiles carry their own inset, smaller in dense grids; this
    // tops it up so the first artwork sits as far below as the list's does.
    val height = (placeable.height - gap.roundToPx() + extraBottom.roundToPx()).coerceAtLeast(0)
    layout(constraints.maxWidth, height) { placeable.place(-extra / 2, 0) }
}
