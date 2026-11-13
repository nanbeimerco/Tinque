package com.music.bitchord.ui.replay

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.data.model.CARD_ART_PX
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.stats.ArtistFacts
import com.music.bitchord.data.stats.RankedEntry
import com.music.bitchord.data.stats.RankedSong
import com.music.bitchord.data.stats.ReplayPeriod
import com.music.bitchord.data.stats.ReplaySummary
import com.music.bitchord.ui.components.ExpressiveGroup
import com.music.bitchord.ui.components.ExpressiveGroupTitle
import com.music.bitchord.ui.components.ExpressiveShapes
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.PolygonShape
import com.music.bitchord.ui.components.expressiveItem
import com.music.bitchord.ui.components.expressiveItemShape
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.ui.player.MeshGradientBackground
import com.music.bitchord.ui.player.rememberArtworkColors
import java.util.Locale
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

private const val DEFAULT_COLLAPSED_CHART_ROWS = 5
private const val EXPANDED_CHART_ROWS = 10

private val ReplayMosaicShapes: List<Shape> by lazy {
    listOf(
        ExpressiveShapes.cookie,
        RoundedPolygon.star(4, radius = 1f, innerRadius = 0.62f, rounding = CornerRounding(0.42f)).normalized(),
        RoundedPolygon.star(8, radius = 1f, innerRadius = 0.86f, rounding = CornerRounding(0.12f)).normalized(),
        RoundedPolygon.star(6, radius = 1f, innerRadius = 0.72f, rounding = CornerRounding(0.32f)).normalized(),
        ExpressiveShapes.smoothSquare,
    ).map { PolygonShape(it) }
}

private val ReplayBadgeStarShape: Shape by lazy {
    PolygonShape(
        RoundedPolygon.star(8, radius = 1f, innerRadius = 0.84f, rounding = CornerRounding(0.18f)).normalized(),
    )
}

/**
 * The Material 3 Expressive Replay screen: an artwork-first visual retrospective
 * featuring dynamic top-jacket backdrop wash, fanned hero sleeves, soundtrack
 * cover bento, listening timeline & 24h rhythm, interactive highlight cards,
 * artwork-centric charts, and track concentration habits.
 */
@Composable
fun ReplayScreen(
    state: ReplayState,
    holder: String,
    onPeriodChange: (ReplayPeriod) -> Unit,
    onOpenStory: (ReplayStoryPage) -> Unit,
    onPlaySong: (Song) -> Unit,
    onOpenArtist: (String?, String) -> Unit,
    onOpenAlbum: (String?, String, String?, String?) -> Unit,
    onShare: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val context = LocalContext.current
    val summary = state.summary
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val heroArtwork = remember(summary) {
        summary?.songs?.firstOrNull()?.song?.thumbnailUrl
            ?: summary?.albums?.firstOrNull()?.artworkUrl
            ?: summary?.artists?.firstOrNull()?.artworkUrl
    }
    val backdropPalette = rememberArtworkColors(heroArtwork)
    val defaultHolder = stringResource(R.string.default_replay_holder)
    val effectiveHolder = holder.ifBlank { defaultHolder }
    val cards = remember(summary) { summary?.cards(context).orEmpty() }

    Box(modifier = modifier.fillMaxSize()) {
        // Palette wash from the period's leading cover art (one-shot settle only).
        MeshGradientBackground(
            palette = backdropPalette,
            trackKey = heroArtwork,
            animated = false,
            blurRadius = 48.dp,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background.copy(alpha = if (dark) 0.58f else 0.72f),
                            MaterialTheme.colorScheme.background.copy(alpha = if (dark) 0.86f else 0.90f),
                            MaterialTheme.colorScheme.background.copy(alpha = if (dark) 0.95f else 0.97f),
                        ),
                    ),
                ),
        )

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding() + 12.dp,
                bottom = contentPadding.calculateBottomPadding() + 36.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item("header") {
                ReplayHeaderSection(
                    selected = state.period,
                    onSelect = onPeriodChange,
                    onShare = onShare,
                    canShare = summary != null && !summary.isEmpty,
                )
            }

            when {
                state.loading && summary == null -> {
                    item("loading") {
                        ReplayLoadingState()
                    }
                }
                summary == null || summary.isEmpty -> {
                    item("empty") {
                        ReplayEmptyState(period = state.period)
                    }
                }
                else -> {
                    item("hero") {
                        ReplayArtworkHeroCard(
                            summary = summary,
                            onPlaySong = onPlaySong,
                            onOpenStory = { onOpenStory(ReplayStoryPage.INTRO) },
                            onShare = onShare,
                        )
                    }

                    item("soundtrack_bento") {
                        ReplaySoundtrackBento(
                            summary = summary,
                            reduceBlur = reduceDynamicBlur,
                            onOpenAlbum = onOpenAlbum,
                            onOpenStory = { onOpenStory(ReplayStoryPage.ALBUMS) },
                        )
                    }

                    item("timeline") {
                        ReplayTimelineSection(summary = summary)
                    }

                    if (cards.isNotEmpty()) {
                        item("cards") {
                            ReplayCardsCarousel(
                                cards = cards,
                                holder = effectiveHolder,
                                memberSince = state.memberSince,
                                onOpenStory = onOpenStory,
                            )
                        }
                    }

                    if (summary.songs.isNotEmpty()) {
                        item("songs_chart") {
                            ReplayTopSongsSection(
                                songs = summary.songs,
                                onPlaySong = onPlaySong,
                            )
                        }
                    }

                    if (summary.artists.isNotEmpty()) {
                        item("artists_chart") {
                            ReplayTopArtistsSection(
                                summary = summary,
                                onOpenArtist = onOpenArtist,
                            )
                        }
                    }

                    if (summary.albums.isNotEmpty()) {
                        item("albums_chart") {
                            ReplayTopAlbumsCarousel(
                                albums = summary.albums.take(EXPANDED_CHART_ROWS),
                                totalAlbumMs = summary.albums.firstOrNull()?.ms?.coerceAtLeast(1L) ?: 1L,
                                onOpenAlbum = onOpenAlbum,
                            )
                        }
                    }

                    if (summary.genres.isNotEmpty() || ArtistFacts.genresAvailable) {
                        item("genres_chart") {
                            ReplayGenresSection(summary = summary)
                        }
                    }

                    item("concentration_habits") {
                        ReplayConcentrationAndHabitsCard(summary = summary)
                    }

                    item("footer") {
                        Text(
                            text = stringResource(R.string.replay_private_counting),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = PAGE_GUTTER, vertical = 4.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplayHeaderSection(
    selected: ReplayPeriod,
    onSelect: (ReplayPeriod) -> Unit,
    onShare: () -> Unit,
    canShare: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_GUTTER),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.replay),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.replay_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (canShare) {
                FilledIconButton(
                    onClick = onShare,
                    shape = PolygonShape(ExpressiveShapes.smoothSquare),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.IosShare,
                        contentDescription = stringResource(R.string.share),
                    )
                }
            }
        }

        ConnectedPeriodPicker(
            selected = selected,
            onSelect = onSelect,
        )
    }
}

/** M3 Expressive connected button group matching Home's HeatTabs interaction grammar. */
@Composable
private fun ConnectedPeriodPicker(
    selected: ReplayPeriod,
    onSelect: (ReplayPeriod) -> Unit,
) {
    val context = LocalContext.current
    val periods = ReplayPeriod.entries
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        periods.forEachIndexed { index, period ->
            val on = selected == period
            val full = 21.dp
            val inner = 6.dp
            val start by animateDpAsState(if (on || index == 0) full else inner, label = "periodStart")
            val end by animateDpAsState(if (on || index == periods.lastIndex) full else inner, label = "periodEnd")
            val shape = RoundedCornerShape(topStart = start, bottomStart = start, topEnd = end, bottomEnd = end)
            val container by animateColorAsState(
                if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                label = "periodBg",
            )
            val content by animateColorAsState(
                if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "periodFg",
            )
            Box(
                modifier = Modifier
                    .weight(if (on) 1.15f else 1f)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(container)
                    .clickable { onSelect(period) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = period.localizedChip(context),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ReplayArtworkHeroCard(
    summary: ReplaySummary,
    onPlaySong: (Song) -> Unit,
    onOpenStory: () -> Unit,
    onShare: () -> Unit,
) {
    val context = LocalContext.current
    val topSong = summary.songs.firstOrNull()
    val leadArtwork = topSong?.song?.thumbnailUrl
        ?: summary.albums.firstOrNull()?.artworkUrl
    val secondArtwork = summary.songs.getOrNull(1)?.song?.thumbnailUrl
        ?: summary.albums.getOrNull(1)?.artworkUrl
    val thirdArtwork = summary.songs.getOrNull(2)?.song?.thumbnailUrl
        ?: summary.albums.getOrNull(2)?.artworkUrl

    val streak = remember(summary.dailyMs) { longestStreakDays(summary.dailyMs) }
    val concentration = remember(summary) { concentrationMetrics(summary) }
    val animatedTopOneShare by animateFloatAsState(
        targetValue = concentration.topOneShare.coerceIn(0.06f, 1f),
        animationSpec = tween(durationMillis = 600),
        label = "heroWavyShare",
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_GUTTER),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.86f),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        shape = CircleShape,
                    ) {
                        Text(
                            text = stringResource(R.string.replay_kicker, summary.localizedLabel(context)),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }

                    Text(
                        text = formatListening(context, summary.totalMs),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HeroStatPill(
                            text = context.replayCount(summary.totalPlays, R.plurals.replay_play_count),
                        )
                        if (streak > 0) {
                            HeroStatPill(
                                text = stringResource(R.string.replay_streak_days_short, streak),
                                icon = Icons.Rounded.LocalFireDepartment,
                            )
                        }
                    }

                    if (topSong != null) {
                        Text(
                            text = "#1 ${topSong.song.title}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Visual Anchor: Cookie-clipped #1 sleeve with fanned #2/#3 thumbnails + play action.
                Box(
                    modifier = Modifier
                        .size(width = 148.dp, height = 148.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!thirdArtwork.isNullOrBlank()) {
                        AsyncImage(
                            model = thirdArtwork.artworkAt(ROW_ART_PX),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(x = 2.dp, y = 10.dp)
                                .rotate(-12f)
                                .size(64.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.5.dp, MaterialTheme.colorScheme.surface.copy(alpha = 0.65f), RoundedCornerShape(16.dp)),
                        )
                    }
                    if (!secondArtwork.isNullOrBlank()) {
                        AsyncImage(
                            model = secondArtwork.artworkAt(ROW_ART_PX),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = (-2).dp, y = 6.dp)
                                .rotate(11f)
                                .size(68.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .border(1.5.dp, MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), RoundedCornerShape(18.dp)),
                        )
                    }

                    val cookieShape = remember { PolygonShape(ExpressiveShapes.cookie) }
                    Box(
                        modifier = Modifier
                            .size(124.dp)
                            // Avoid Android HWUI concave-path shadow tessellation on cookieShape
                            // (SkBaseShadowTessellator::computeConcaveShadow stalls RenderThread).
                            .drawBehind {
                                val center = Offset(size.width * 0.5f, size.height * 0.54f)
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colorStops = arrayOf(
                                            0.0f to Color.Black.copy(alpha = 0.28f),
                                            0.66f to Color.Black.copy(alpha = 0.14f),
                                            1.0f to Color.Transparent,
                                        ),
                                        center = center,
                                        radius = size.maxDimension * 0.56f,
                                    ),
                                    radius = size.maxDimension * 0.56f,
                                    center = center,
                                )
                            }
                            .clip(cookieShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .clickable(enabled = topSong != null) {
                                topSong?.let { onPlaySong(it.song) }
                            },
                    ) {
                        if (!leadArtwork.isNullOrBlank()) {
                            AsyncImage(
                                model = leadArtwork.artworkAt(CARD_ART_PX),
                                contentDescription = topSong?.song?.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            InitialTile(
                                text = topSong?.song?.title ?: summary.label,
                                size = 124.dp,
                                shape = cookieShape,
                            )
                        }
                    }

                    if (topSong != null) {
                        FilledIconButton(
                            onClick = { onPlaySong(topSong.song) },
                            shape = RoundedCornerShape(16.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(42.dp)
                                .shadow(6.dp, RoundedCornerShape(16.dp)),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = topSong.song.title,
                            )
                        }
                    }
                }
            }

            WavyExpressiveProgressBar(
                progress = animatedTopOneShare,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.16f),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onOpenStory,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.replay_story_play),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FilledTonalButton(
                    onClick = onShare,
                    shape = CircleShape,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.IosShare,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.share),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroStatPill(
    text: String,
    icon: ImageVector? = null,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(13.dp),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ReplaySoundtrackBento(
    summary: ReplaySummary,
    reduceBlur: Boolean,
    onOpenAlbum: (String?, String, String?, String?) -> Unit,
    onOpenStory: () -> Unit,
) {
    val context = LocalContext.current
    val topAlbum = summary.albums.firstOrNull()
    val mosaicCovers = remember(summary) {
        buildList {
            val seen = HashSet<String>()
            summary.albums.forEach { entry ->
                val url = entry.artworkUrl
                if (!url.isNullOrBlank() && seen.add(url)) add(url to entry.title)
            }
            summary.songs.forEach { entry ->
                val url = entry.song.thumbnailUrl
                if (!url.isNullOrBlank() && seen.add(url)) add(url to entry.song.title)
            }
            summary.artists.forEach { entry ->
                val url = entry.artworkUrl ?: ArtistFacts.imageFor(entry.title)
                if (!url.isNullOrBlank() && seen.add(url)) add(url to entry.title)
            }
        }.take(6)
    }

    if (topAlbum == null && mosaicCovers.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_GUTTER),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.replay_soundtrack_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.replay_soundtrack_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // A. #1 Album Spotlight Card
        if (topAlbum != null) {
            val smoothShape = remember { PolygonShape(ExpressiveShapes.smoothSquare) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable {
                        onOpenAlbum(topAlbum.browseId, topAlbum.title, topAlbum.subtitle, topAlbum.artworkUrl)
                    },
            ) {
                if (!topAlbum.artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = topAlbum.artworkUrl.artworkAt(CARD_ART_PX),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                            .then(if (!reduceBlur) Modifier.blur(16.dp) else Modifier),
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.72f),
                                        Color.Black.copy(alpha = 0.48f),
                                    ),
                                ),
                            ),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .shadow(8.dp, RoundedCornerShape(26.dp), clip = false)
                            .clip(smoothShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    ) {
                        if (!topAlbum.artworkUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = topAlbum.artworkUrl.artworkAt(CARD_ART_PX),
                                contentDescription = topAlbum.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            InitialTile(topAlbum.title, 88.dp, smoothShape)
                        }
                    }

                    val textPrimary = if (!topAlbum.artworkUrl.isNullOrBlank()) Color.White else MaterialTheme.colorScheme.onSurface
                    val textSecondary = if (!topAlbum.artworkUrl.isNullOrBlank()) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                        ) {
                            Text(
                                text = stringResource(R.string.top_album),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                            )
                        }
                        Text(
                            text = topAlbum.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!topAlbum.subtitle.isNullOrBlank()) {
                            Text(
                                text = topAlbum.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = "${formatListening(context, topAlbum.ms)} · ${context.replayCount(topAlbum.plays, R.plurals.replay_play_count)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = textSecondary,
                        )
                    }
                }
            }
        }

        // B. Visual Sleeve Mosaic + Discovery Counts Pill
        if (mosaicCovers.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenStory),
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        mosaicCovers.take(6).forEachIndexed { idx, (url, title) ->
                            val shape = ReplayMosaicShapes[idx % ReplayMosaicShapes.size]
                            AsyncImage(
                                model = url.artworkAt(ROW_ART_PX),
                                contentDescription = title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(shape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(
                                R.string.replay_discovery_counts,
                                context.replayCount(summary.distinctSongs, R.plurals.replay_song_count),
                                context.replayCount(summary.distinctArtists, R.plurals.replay_artist_count),
                                summary.distinctAlbums,
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

private enum class ReplayTimelineMode {
    TIMELINE,
    HOURS,
}

@Composable
private fun ReplayTimelineSection(summary: ReplaySummary) {
    val context = LocalContext.current
    var mode by rememberSaveable { mutableStateOf(ReplayTimelineMode.TIMELINE) }
    val timelineBars = remember(summary) { buildTimelineBars(summary) }
    val hasTimelineData = timelineBars.any { it.ms > 0L }
    val hourPeak = summary.peakHour
    val hourBars = remember(summary, hourPeak) {
        summary.hourOfDay.mapIndexed { hour, ms ->
            ReplayTimelineBar(
                key = hour.toString(),
                label = hour.toString(),
                ms = ms,
                isPeak = hourPeak == hour && ms > 0L,
            )
        }
    }

    val effectiveMode = if (mode == ReplayTimelineMode.TIMELINE && !hasTimelineData) {
        ReplayTimelineMode.HOURS
    } else {
        mode
    }
    val activeBars = if (effectiveMode == ReplayTimelineMode.TIMELINE) timelineBars else hourBars
    val maxMs = remember(activeBars) { (activeBars.maxOfOrNull { it.ms } ?: 0L).coerceAtLeast(1L) }

    val activeDays = remember(summary.dailyMs) { activeDaysCount(summary.dailyMs) }
    val avgDaily = remember(summary.totalMs, summary.dailyMs) { avgDailyMinutes(summary.totalMs, summary.dailyMs) }
    val streak = remember(summary.dailyMs) { longestStreakDays(summary.dailyMs) }
    val topArtwork = summary.songs.firstOrNull()?.song?.thumbnailUrl

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_GUTTER),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.replay_timeline_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val subtitleRes = when {
                    effectiveMode == ReplayTimelineMode.HOURS -> R.string.replay_timeline_subtitle_hours
                    summary.period == ReplayPeriod.THIS_MONTH -> R.string.replay_timeline_subtitle_month
                    summary.period == ReplayPeriod.THIS_YEAR -> R.string.replay_timeline_subtitle_year
                    else -> R.string.replay_timeline_subtitle_all
                }
                Text(
                    text = stringResource(subtitleRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasTimelineData) {
                    TimelineFilterChip(
                        selected = effectiveMode == ReplayTimelineMode.TIMELINE,
                        label = stringResource(R.string.replay_timeline_tab_timeline),
                        onClick = { mode = ReplayTimelineMode.TIMELINE },
                    )
                }
                TimelineFilterChip(
                    selected = effectiveMode == ReplayTimelineMode.HOURS,
                    label = stringResource(R.string.replay_timeline_tab_hours),
                    onClick = { mode = ReplayTimelineMode.HOURS },
                )
            }

            // Pill Bar Chart
            if (activeBars.isNotEmpty()) {
                val barCount = activeBars.size
                val labelStep = when {
                    barCount <= 12 -> 1
                    barCount <= 24 -> 6
                    else -> 5
                }
                val barSpacing = if (barCount > 20) 3.dp else 6.dp
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(112.dp),
                        horizontalArrangement = Arrangement.spacedBy(barSpacing),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        activeBars.forEachIndexed { idx, bar ->
                            val rawFraction = if (bar.ms <= 0L) 0.05f else (bar.ms.toFloat() / maxMs.toFloat()).coerceIn(0.10f, 1f)
                            val fraction by animateFloatAsState(
                                targetValue = rawFraction,
                                animationSpec = tween(durationMillis = 420),
                                label = "timelineBar$idx",
                            )
                            val barColor = when {
                                bar.ms <= 0L -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                bar.isPeak -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(fraction)
                                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                                    .background(barColor),
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(barSpacing),
                    ) {
                        activeBars.forEachIndexed { idx, bar ->
                            val showLabel = idx == 0 ||
                                idx == activeBars.lastIndex ||
                                (labelStep == 1) ||
                                (idx % labelStep == 0)
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (showLabel) {
                                    Text(
                                        text = bar.label,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        fontWeight = if (bar.isPeak) FontWeight.Bold else FontWeight.Normal,
                                        color = if (bar.isPeak) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Highlight Strip with Top 1 mini art badge
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val miniShape = remember { PolygonShape(ExpressiveShapes.smoothSquare) }
                    if (!topArtwork.isNullOrBlank()) {
                        AsyncImage(
                            model = topArtwork.artworkAt(ROW_ART_PX),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(miniShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        val peakLine = when {
                            effectiveMode == ReplayTimelineMode.HOURS && hourPeak != null ->
                                "${stringResource(R.string.replay_peak_hour)}: ${formatHour(context, hourPeak)}"
                            summary.busiestDay != null ->
                                stringResource(
                                    R.string.replay_timeline_peak_badge,
                                    formatDay(context, summary.busiestDay),
                                    formatListening(context, summary.busiestDayMs),
                                )
                            else -> stringResource(R.string.replay_daily_average_value, avgDaily)
                        }
                        Text(
                            text = peakLine,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val metricsText = buildList {
                            if (avgDaily > 0) add(context.getString(R.string.replay_daily_average_value, avgDaily))
                            if (activeDays > 0) add(context.getString(R.string.replay_active_days_value, activeDays))
                            if (streak > 0) add("${context.getString(R.string.replay_longest_streak)} ${context.getString(R.string.replay_streak_days_short, streak)}")
                        }.joinToString(" · ")
                        if (metricsText.isNotEmpty()) {
                            Text(
                                text = metricsText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineFilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
        },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            labelColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = Color.Transparent,
            selectedBorderColor = Color.Transparent,
            enabled = true,
            selected = selected,
        ),
    )
}

@Composable
private fun ReplayCardsCarousel(
    cards: List<ReplayHeroCard>,
    holder: String,
    memberSince: String?,
    onOpenStory: (ReplayStoryPage) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(modifier = Modifier.padding(horizontal = PAGE_GUTTER)) {
            Text(
                text = stringResource(R.string.replay_cards_section_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.replay_cards_section_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(cards, key = { _, card -> card.page.name }) { _, card ->
                ReplayCreditCard(
                    label = card.label,
                    value = card.value,
                    detail = card.detail,
                    artworkUrl = card.artworkUrl,
                    holder = holder,
                    memberSince = memberSince,
                    onClick = { onOpenStory(card.page) },
                    modifier = Modifier.width(296.dp),
                )
            }
        }
    }
}

@Composable
private fun ReplayTopSongsSection(
    songs: List<RankedSong>,
    onPlaySong: (Song) -> Unit,
) {
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    val limit = if (expanded) EXPANDED_CHART_ROWS else DEFAULT_COLLAPSED_CHART_ROWS
    val visible = songs.take(limit)
    val topOne = visible.firstOrNull() ?: return
    val topMs = topOne.ms.coerceAtLeast(1L)
    val tail = visible.drop(1)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_GUTTER),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ExpressiveGroupTitle(
            text = stringResource(R.string.top_songs),
            modifier = Modifier.padding(horizontal = 0.dp),
        )

        // #1 Podium Card
        val podiumArtShape = remember { PolygonShape(ExpressiveShapes.smoothSquare) }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onPlaySong(topOne.song) },
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.78f),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        if (!topOne.song.thumbnailUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = topOne.song.thumbnailUrl.artworkAt(CARD_ART_PX),
                                contentDescription = topOne.song.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(84.dp)
                                    .clip(podiumArtShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            )
                        } else {
                            InitialTile(topOne.song.title, 84.dp, podiumArtShape)
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(x = (-6).dp, y = (-6).dp)
                                .size(28.dp)
                                .clip(ReplayBadgeStarShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "1",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = topOne.song.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (topOne.song.artist.isNotBlank()) {
                            Text(
                                text = topOne.song.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = "${formatListening(context, topOne.ms)} · ${context.replayCount(topOne.plays, R.plurals.replay_play_count)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    FilledIconButton(
                        onClick = { onPlaySong(topOne.song) },
                        shape = RoundedCornerShape(16.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(
                            imageVector = BitChordIcons.TransportPlay,
                            contentDescription = topOne.song.title,
                        )
                    }
                }

                WavyExpressiveProgressBar(
                    progress = 1f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.14f),
                )
            }
        }

        // #2..#5 (or #10) inside connected ExpressiveGroup
        if (tail.isNotEmpty()) {
            val rowShape = remember { PolygonShape(ExpressiveShapes.smoothSquare) }
            ExpressiveGroup {
                tail.forEachIndexed { idx, rankedSong ->
                    val rank = idx + 2
                    val share = (rankedSong.ms.toFloat() / topMs.toFloat()).coerceIn(0.05f, 1f)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .expressiveItem(
                                index = idx,
                                count = tail.size,
                            )
                            .clickable { onPlaySong(rankedSong.song) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = "$rank",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(22.dp),
                                textAlign = TextAlign.Center,
                            )
                            if (!rankedSong.song.thumbnailUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = rankedSong.song.thumbnailUrl.artworkAt(ROW_ART_PX),
                                    contentDescription = rankedSong.song.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(rowShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                )
                            } else {
                                InitialTile(rankedSong.song.title, 52.dp, rowShape)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = rankedSong.song.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (rankedSong.song.artist.isNotBlank()) {
                                    Text(
                                        text = rankedSong.song.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Text(
                                text = formatListening(context, rankedSong.ms),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 34.dp)
                                .height(5.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(share)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)),
                            )
                        }
                    }
                }
            }
        }

        if (songs.size > DEFAULT_COLLAPSED_CHART_ROWS) {
            ExpandCollapsePill(
                expanded = expanded,
                moreLabel = stringResource(R.string.replay_show_more_songs, minOf(songs.size, EXPANDED_CHART_ROWS)),
                lessLabel = stringResource(R.string.replay_show_less),
                onToggle = { expanded = !expanded },
            )
        }
    }
}

@Composable
private fun ReplayTopArtistsSection(
    summary: ReplaySummary,
    onOpenArtist: (String?, String) -> Unit,
) {
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    val artists = summary.artists.take(if (expanded) EXPANDED_CHART_ROWS else DEFAULT_COLLAPSED_CHART_ROWS)
    val podium = artists.take(3)
    val rest = artists.drop(3)
    val topMs = summary.artists.firstOrNull()?.ms?.coerceAtLeast(1L) ?: 1L

    val fallbackArtworks = remember(summary) {
        buildMap {
            summary.songs.forEach { ranked ->
                val url = ranked.song.thumbnailUrl ?: return@forEach
                val artistKey = ranked.song.artist.trim().lowercase(Locale.ROOT)
                if (artistKey.isNotEmpty() && !containsKey(artistKey)) {
                    put(artistKey, url)
                }
            }
        }
    }

    fun resolveArtistArtwork(entry: RankedEntry): String? =
        entry.artworkUrl
            ?: ArtistFacts.imageFor(entry.title)
            ?: fallbackArtworks[entry.title.trim().lowercase(Locale.ROOT)]

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_GUTTER),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ExpressiveGroupTitle(
            text = stringResource(R.string.top_artists),
            modifier = Modifier.padding(horizontal = 0.dp),
        )

        // Podium #1-#3 Portrait Cards
        val firstPlaceShape: Shape = remember { PolygonShape(ExpressiveShapes.cookie) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            podium.forEachIndexed { idx, entry ->
                val rank = idx + 1
                val avatarSize = if (rank == 1) 82.dp else 68.dp
                val avatarShape: Shape = if (rank == 1) firstPlaceShape else CircleShape
                val artworkUrl = resolveArtistArtwork(entry)
                val share = (entry.ms.toFloat() / topMs.toFloat()).coerceIn(0.08f, 1f)
                val containerColor = when (rank) {
                    1 -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f)
                    2 -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                }
                Surface(
                    modifier = Modifier
                        .weight(if (rank == 1) 1.12f else 1f)
                        .clickable { onOpenArtist(entry.browseId, entry.title) },
                    shape = RoundedCornerShape(24.dp),
                    color = containerColor,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box {
                            if (!artworkUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = artworkUrl.artworkAt(CARD_ART_PX),
                                    contentDescription = entry.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(avatarSize)
                                        .clip(avatarShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                )
                            } else {
                                InitialTile(entry.title, avatarSize, avatarShape)
                            }
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.align(Alignment.TopStart),
                            ) {
                                Text(
                                    text = "#$rank",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                )
                            }
                        }

                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = formatListening(context, entry.ms),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(share)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                    }
                }
            }
        }

        if (rest.isNotEmpty()) {
            ExpressiveGroup {
                rest.forEachIndexed { idx, entry ->
                    val rank = idx + 4
                    val artworkUrl = resolveArtistArtwork(entry)
                    val share = (entry.ms.toFloat() / topMs.toFloat()).coerceIn(0.06f, 1f)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .expressiveItem(
                                index = idx,
                                count = rest.size,
                            )
                            .clickable { onOpenArtist(entry.browseId, entry.title) }
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = "$rank",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(22.dp),
                                textAlign = TextAlign.Center,
                            )
                            if (!artworkUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = artworkUrl.artworkAt(ROW_ART_PX),
                                    contentDescription = entry.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                )
                            } else {
                                InitialTile(entry.title, 48.dp, CircleShape)
                            }
                            Text(
                                text = entry.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = formatListening(context, entry.ms),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 34.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(share)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondary),
                            )
                        }
                    }
                }
            }
        }

        if (summary.artists.size > DEFAULT_COLLAPSED_CHART_ROWS) {
            ExpandCollapsePill(
                expanded = expanded,
                moreLabel = stringResource(R.string.replay_show_more_songs, minOf(summary.artists.size, EXPANDED_CHART_ROWS)),
                lessLabel = stringResource(R.string.replay_show_less),
                onToggle = { expanded = !expanded },
            )
        }
    }
}

@Composable
private fun ReplayTopAlbumsCarousel(
    albums: List<RankedEntry>,
    totalAlbumMs: Long,
    onOpenAlbum: (String?, String, String?, String?) -> Unit,
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ExpressiveGroupTitle(
            text = stringResource(R.string.top_albums),
            modifier = Modifier.padding(horizontal = PAGE_GUTTER),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(albums, key = { index, entry -> "${entry.title}-${entry.subtitle.orEmpty()}-$index" }) { index, album ->
                val rank = index + 1
                val share = (album.ms.toFloat() / totalAlbumMs.toFloat()).coerceIn(0.08f, 1f)
                Surface(
                    modifier = Modifier
                        .width(168.dp)
                        .clickable {
                            onOpenAlbum(album.browseId, album.title, album.subtitle, album.artworkUrl)
                        },
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .size(168.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        ) {
                            if (!album.artworkUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = album.artworkUrl.artworkAt(CARD_ART_PX),
                                    contentDescription = album.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                InitialTile(album.title, 168.dp, RoundedCornerShape(24.dp))
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(10.dp)
                                    .size(30.dp)
                                    .clip(ReplayBadgeStarShape)
                                    .background(
                                        if (rank == 1) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.72f),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "$rank",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (rank == 1) MaterialTheme.colorScheme.onPrimary else Color.White,
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                text = album.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!album.subtitle.isNullOrBlank()) {
                                Text(
                                    text = album.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                text = formatListening(context, album.ms),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(2.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(share)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplayGenresSection(summary: ReplaySummary) {
    val context = LocalContext.current
    val genres = summary.genres.take(DEFAULT_COLLAPSED_CHART_ROWS)
    val topGenreMs = genres.firstOrNull()?.ms?.coerceAtLeast(1L) ?: 1L

    val genreArtworks = remember(summary) {
        buildMap {
            genres.forEach { genre ->
                val matchedCovers = summary.artists
                    .asSequence()
                    .filter { artist ->
                        ArtistFacts.genresFor(artist.title).any { it.equals(genre.title, ignoreCase = true) }
                    }
                    .mapNotNull { it.artworkUrl ?: ArtistFacts.imageFor(it.title) }
                    .distinct()
                    .take(3)
                    .toList()
                put(genre.title, matchedCovers)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_GUTTER),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ExpressiveGroupTitle(
            text = stringResource(R.string.top_genres),
            modifier = Modifier.padding(horizontal = 0.dp),
        )

        if (genres.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Text(
                    text = stringResource(R.string.genres_pending),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            return
        }

        ExpressiveGroup {
            genres.forEachIndexed { idx, genre ->
                val share = (genre.ms.toFloat() / topGenreMs.toFloat()).coerceIn(0.08f, 1f)
                val covers = genreArtworks[genre.title].orEmpty()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(expressiveItemShape(idx, genres.size))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Stacked circular mini artwork thumbs for artists in this genre
                        if (covers.isNotEmpty()) {
                            Box(
                                modifier = Modifier.width((28 + (covers.size - 1) * 16).dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                covers.forEachIndexed { coverIdx, url ->
                                    AsyncImage(
                                        model = url.artworkAt(ROW_ART_PX),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .offset(x = (coverIdx * 16).dp)
                                            .zIndex((covers.size - coverIdx).toFloat())
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .border(1.5.dp, MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                    )
                                }
                            }
                        } else {
                            InitialTile(genre.title, 28.dp, CircleShape)
                        }

                        Text(
                            text = genre.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )

                        Text(
                            text = formatListening(context, genre.ms),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(share)
                                .clip(CircleShape)
                                .background(
                                    if (idx == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplayConcentrationAndHabitsCard(summary: ReplaySummary) {
    val context = LocalContext.current
    val conc = remember(summary) { concentrationMetrics(summary) }
    val streak = remember(summary.dailyMs) { longestStreakDays(summary.dailyMs) }
    val avgDaily = remember(summary.totalMs, summary.dailyMs) { avgDailyMinutes(summary.totalMs, summary.dailyMs) }
    val topSongArt = summary.songs.firstOrNull()?.song?.thumbnailUrl
    val topThreePct = (conc.topThreeShare * 100f).roundToInt()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_GUTTER),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val cookieShape = remember { PolygonShape(ExpressiveShapes.cookie) }
                if (!topSongArt.isNullOrBlank()) {
                    AsyncImage(
                        model = topSongArt.artworkAt(ROW_ART_PX),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(cookieShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.replay_concentration_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.replay_concentration_subtitle, topThreePct),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 3-segment proportional horizontal bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(CircleShape),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                if (conc.topOneShare > 0.01f) {
                    Box(
                        modifier = Modifier
                            .weight(conc.topOneShare)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
                if (conc.topTwoThreeShare > 0.01f) {
                    Box(
                        modifier = Modifier
                            .weight(conc.topTwoThreeShare)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary),
                    )
                }
                if (conc.othersShare > 0.01f) {
                    Box(
                        modifier = Modifier
                            .weight(conc.othersShare)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LegendPill(
                    color = MaterialTheme.colorScheme.primary,
                    label = "${stringResource(R.string.replay_concentration_top1)} ${(conc.topOneShare * 100f).roundToInt()}%",
                )
                LegendPill(
                    color = MaterialTheme.colorScheme.secondary,
                    label = "${stringResource(R.string.replay_concentration_top23)} ${(conc.topTwoThreeShare * 100f).roundToInt()}%",
                )
                LegendPill(
                    color = MaterialTheme.colorScheme.tertiary,
                    label = "${stringResource(R.string.replay_concentration_others)} ${(conc.othersShare * 100f).roundToInt()}%",
                )
            }

            // 2x2 Habits Grid
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HabitTile(
                        icon = Icons.Rounded.Schedule,
                        label = stringResource(R.string.replay_peak_hour),
                        value = summary.peakHour?.let { formatHour(context, it) } ?: "—",
                        modifier = Modifier.weight(1f),
                    )
                    HabitTile(
                        icon = Icons.Rounded.CalendarMonth,
                        label = stringResource(R.string.replay_busiest_day),
                        value = summary.busiestDay?.let { formatDay(context, it) } ?: "—",
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HabitTile(
                        icon = Icons.Rounded.LocalFireDepartment,
                        label = stringResource(R.string.replay_daily_average),
                        value = if (streak > 0) {
                            "${stringResource(R.string.replay_daily_average_value, avgDaily)} · ${stringResource(R.string.replay_streak_days_short, streak)}"
                        } else {
                            stringResource(R.string.replay_daily_average_value, avgDaily)
                        },
                        modifier = Modifier.weight(1f),
                    )
                    HabitTile(
                        icon = Icons.Rounded.Repeat,
                        label = stringResource(R.string.replay_avg_repeats),
                        value = stringResource(
                            R.string.replay_avg_repeats_value,
                            String.format(Locale.US, "%.1f", conc.avgPlaysPerSong),
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendPill(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun HabitTile(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ExpandCollapsePill(
    expanded: Boolean,
    moreLabel: String,
    lessLabel: String,
    onToggle: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (expanded) lessLabel else moreLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ReplayLoadingState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 96.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ReplayEmptyState(period: ReplayPeriod) {
    val cookieShape = remember { PolygonShape(ExpressiveShapes.cookie) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_GUTTER, vertical = 24.dp),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(cookieShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = BitChordIcons.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(36.dp),
                )
            }
            Text(
                text = stringResource(R.string.not_enough_listening),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = when (period) {
                    ReplayPeriod.THIS_MONTH -> stringResource(R.string.not_enough_this_month)
                    else -> stringResource(R.string.replay_empty_description)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** M3 Expressive wavy linear indicator rendered via Compose Canvas for Material3 1.4.0 compatibility. */
@Composable
private fun WavyExpressiveProgressBar(
    progress: Float,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
) {
    val clamped = progress.coerceIn(0f, 1f)
    Canvas(modifier = modifier) {
        val strokePx = 3.2.dp.toPx().coerceAtMost(size.height * 0.45f)
        val halfStroke = strokePx * 0.5f
        val centerY = size.height * 0.5f
        val usableWidth = (size.width - strokePx).coerceAtLeast(1f)
        val activeWidth = (usableWidth * clamped).coerceAtLeast(0f)
        val amplitude = ((size.height - strokePx) * 0.42f).coerceAtLeast(0.5f)
        val wavelength = 22.dp.toPx().coerceAtLeast(8f)

        val trackStart = if (clamped > 0f) (halfStroke + activeWidth + strokePx * 1.4f) else halfStroke
        if (trackStart < size.width - halfStroke) {
            drawLine(
                color = trackColor,
                start = Offset(trackStart, centerY),
                end = Offset(size.width - halfStroke, centerY),
                strokeWidth = strokePx,
                cap = StrokeCap.Round,
            )
        }

        if (activeWidth > 0f) {
            val path = Path()
            val steps = (activeWidth / 2f).roundToInt().coerceAtLeast(4)
            for (step in 0..steps) {
                val x = halfStroke + (activeWidth * step / steps.toFloat())
                val angle = ((x - halfStroke) / wavelength) * (2f * PI.toFloat())
                val y = centerY + sin(angle) * amplitude
                if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
        }
    }
}
