package com.music.bitchord.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.graphics.shapes.star
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.HomeSection
import com.music.bitchord.data.stats.HeatStore
import com.music.bitchord.data.stats.heatKey
import com.music.bitchord.feature.home.data.HomeContent
import com.music.bitchord.feature.home.data.HomeModel
import com.music.bitchord.feature.home.data.MixKind
import com.music.bitchord.feature.homeai.LocalHomeAi
import com.music.bitchord.feature.homeai.Music2EmoDownloadDialog
import com.music.bitchord.feature.homeai.Music2EmoDownloadErrorDialog
import com.music.bitchord.feature.homeai.Music2EmoDownloadProgressDialog
import com.music.bitchord.feature.localmusic.data.LocalFavoritesStore
import com.music.bitchord.feature.localmusic.data.LocalPlaylistStore
import com.music.bitchord.feature.localsongactions.data.LocalPlayStatsStore
import com.music.bitchord.ui.components.ExpressiveShapes
import com.music.bitchord.ui.components.HeatMeter
import com.music.bitchord.ui.components.heatRing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Home: what the app has noticed about the listening, as against the Library,
 * which is what the listener chose — mixes it made, the heat of tracks,
 * albums, artists and playlists, what is rising, a year ago, the tracks a
 * loved album still has, this time of day's usual, what is new, how much of
 * the library gets heard. Worked out in [HomeModel], off the main thread; this
 * only lays it out, in the order chosen in the layout sheet.
 */
@Composable
fun HomeScreen(
    songs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    onPlay: (List<Song>, Int) -> Unit,
    onShufflePlay: (List<Song>) -> Unit,
    onOpenAlbum: (String, String?) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenPlaylist: (String?) -> Unit,
    onOpenReplay: () -> Unit,
    onShowAllRecent: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    /** Pull down to rescan the device for music (the Folders tab's refresh). */
    isRefreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
) {
    val context = LocalContext.current.applicationContext
    val heatOn by AppSettings.heatMeterEnabled.collectAsStateWithLifecycle()
    val heat by HeatStore.info.collectAsStateWithLifecycle()
    val order by AppSettings.homeSectionOrder.collectAsStateWithLifecycle()
    val hidden by AppSettings.hiddenHomeSections.collectAsStateWithLifecycle()
    val playStatsRevision by LocalPlayStatsStore.revision.collectAsStateWithLifecycle()
    val playlists by LocalPlaylistStore.playlists.collectAsStateWithLifecycle()
    val favoriteIds by LocalFavoritesStore.favoriteIds.collectAsStateWithLifecycle()
    val libraryEntries by com.music.bitchord.feature.library.data.LibraryEntryStore.entries.collectAsStateWithLifecycle()
    val favoritesName = stringResource(R.string.favorites)
    val dailyMixBoost by AppSettings.homeHeatDailyMixBoost.collectAsStateWithLifecycle()
    val offlineMode by AppSettings.offlineMode.collectAsStateWithLifecycle()
    val allowModelDownload = remember(context, offlineMode) {
        LocalHomeAi.isDownloadSupported(context) && !offlineMode
    }
    val aiCacheRevision by LocalHomeAi.cacheRevision.collectAsStateWithLifecycle()
    val aiDownloadProgress by LocalHomeAi.downloadProgress.collectAsStateWithLifecycle()
    val aiAnalysisProgress by LocalHomeAi.analysisProgress.collectAsStateWithLifecycle()
    val modelInstalledState by LocalHomeAi.modelInstalled.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var modelInstalled by remember { mutableStateOf(modelInstalledState) }
    var modelPromptDismissed by remember { mutableStateOf(LocalHomeAi.modelDecision(context) == false) }
    var showModelPrompt by remember { mutableStateOf(false) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var todayEpochDay by remember { mutableLongStateOf(java.time.LocalDate.now().toEpochDay()) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val nowDay = java.time.LocalDate.now().toEpochDay()
                if (nowDay != todayEpochDay) todayEpochDay = nowDay
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(context, aiCacheRevision, allowModelDownload, modelInstalledState) {
        modelInstalled = modelInstalledState || withContext(Dispatchers.IO) { LocalHomeAi.isModelInstalled(context) }
        modelPromptDismissed = LocalHomeAi.modelDecision(context) == false
    }

    var content by remember { mutableStateOf<HomeContent?>(null) }
    LaunchedEffect(
        songs,
        heat,
        playlists,
        favoriteIds,
        playStatsRevision,
        libraryEntries,
        todayEpochDay,
        dailyMixBoost,
        aiCacheRevision,
    ) {
        // Heat changes with every listen; gather a burst of them into one pass.
        if (content != null) delay(1_500)
        val day = java.time.LocalDate.ofEpochDay(todayEpochDay)
        // IO: it reads play counts and past months from disk.
        content = withContext(Dispatchers.IO) {
            HomeModel.compute(
                context = context,
                songs = songs,
                heat = heat,
                playlists = playlists,
                favoriteIds = favoriteIds,
                favoritesName = favoritesName,
                libraryEntries = libraryEntries,
                dailyMixBoost = dailyMixBoost,
                today = day,
            )
        }
    }
    val home = content ?: HomeContent()
    fun songHeat(song: Song): Float? = if (heatOn) heat[song.heatKey()]?.level ?: 0f else null

    val newCards = remember(songs) { newAdditions(songs) }
    val sections = order.filter { it !in hidden }
    var editLayout by remember { mutableStateOf(false) }
    var heatTab by rememberSaveable { mutableStateOf(0) }
    val nothingYet = content != null && home.heatSongs.isEmpty() && home.recent.isEmpty() && home.mixes.isEmpty()

    val pullState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { onRefresh?.invoke() },
        state = pullState,
        modifier = modifier.fillMaxSize(),
        indicator = {
            if (onRefresh != null) {
                androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = contentPadding.calculateTopPadding()),
                )
            }
        },
    ) {
    LazyColumn(
        state = rememberLazyListState(),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 80.dp,
        ),
    ) {
        item(key = "home_header") { HomeHeader() }
        if (allowModelDownload && !modelInstalled && !modelPromptDismissed && aiDownloadProgress == null) {
            item(key = "home_ai_opt_in") {
                LocalAiOptInCard(
                    onEnable = { showModelPrompt = true },
                    onDismiss = {
                        LocalHomeAi.setModelDecision(context, false)
                        modelPromptDismissed = true
                    },
                )
            }
        }
        aiAnalysisProgress?.takeIf { !it.finished && !it.lowMemory && it.total > 0 }?.let { progress ->
            item(key = "home_ai_analysis_pill") {
                LocalAiAnalysisBanner(progress = progress)
            }
        }
        if (nothingYet) {
            item(key = "home_empty") {
                Text(
                    text = stringResource(R.string.home_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                )
            }
        }
        sections.forEach { section ->
            when (section) {
                HomeSection.MIXES -> {
                    val mixes = home.mixes.filter { heatOn || it.kind !in HEAT_MIXES }
                    if (mixes.isNotEmpty()) {
                        item(key = "home_mixes_title") {
                            ShelfTitle(stringResource(R.string.home_mixes_title), stringResource(R.string.home_mixes_subtitle))
                        }
                        item(key = "home_mixes") {
                            MixShelf(mixes, home.slot) { mix -> onShufflePlay(mix.songs) }
                        }
                    }
                }
                HomeSection.HERO -> home.hero?.takeIf { heatOn }?.let { hero ->
                    item(key = "home_hero") {
                        HeroCard(hero, heat[hero.heatKey()]?.level ?: 0f, isCurrent = currentSong?.videoId == hero.videoId) {
                            onPlay(listOf(hero), 0)
                        }
                    }
                }
                HomeSection.HOT -> if (heatOn) {
                    item(key = "home_hot_title") { ShelfTitle(stringResource(R.string.home_hot)) }
                    item(key = "home_hot_tabs") { HeatTabs(heatTab) { heatTab = it } }
                    item(key = "home_hot") {
                        when (heatTab) {
                            0 -> if (home.heatSongs.isEmpty()) EmptyShelf() else
                                SongShelf(home.heatSongs, ::songHeat) { i -> onPlay(home.heatSongs, i) }
                            1 -> if (home.heatAlbums.isEmpty()) EmptyShelf() else CardRow(home.heatAlbums, { "ha_${it.title}_${it.subtitle}" }) { g ->
                                Card(g.title, g.subtitle, g.artworkUrl, Icons.Rounded.Album, heat = g.level) { onOpenAlbum(g.title, g.artworkUrl) }
                            }
                            2 -> if (home.heatArtists.isEmpty()) EmptyShelf() else CardRow(home.heatArtists, { "hr_${it.title}" }) { g ->
                                Card(g.title, "", g.artworkUrl, Icons.Rounded.Person, heat = g.level, round = true, size = 112.dp) { onOpenArtist(g.title) }
                            }
                            else -> if (home.heatPlaylists.isEmpty()) EmptyShelf() else CardRow(home.heatPlaylists, { "hp_${it.id}" }) { p ->
                                Card(p.name, stringResource(R.string.home_track_count, p.songs.size), p.artworkUrl,
                                    Icons.AutoMirrored.Rounded.QueueMusic, heat = p.level) { onOpenPlaylist(p.id) }
                            }
                        }
                    }
                }
                HomeSection.RISING -> if (heatOn && home.rising.isNotEmpty()) {
                    item(key = "home_rising_title") {
                        ShelfTitle(stringResource(R.string.home_rising), stringResource(R.string.home_rising_subtitle))
                    }
                    item(key = "home_rising") { SongShelf(home.rising, ::songHeat) { i -> onPlay(home.rising, i) } }
                }
                HomeSection.COOLING -> if (heatOn && home.cooling.isNotEmpty()) {
                    item(key = "home_cool_title") {
                        ShelfTitle(stringResource(R.string.home_cooling), stringResource(R.string.home_cooling_subtitle))
                    }
                    item(key = "home_cool") {
                        SongShelf(home.cooling, ::songHeat, dimmed = true) { i -> onPlay(home.cooling, i) }
                    }
                }
                HomeSection.NEW_ADDITIONS -> {
                    if (newCards.isNotEmpty()) {
                        item(key = "home_new_title") { ShelfTitle(stringResource(R.string.home_new)) }
                        item(key = "home_new") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                itemsIndexed(newCards, key = { i, c -> "new_${i}_${c.title}" }) { _, card ->
                                    when (card) {
                                        is NewCard.Album -> Card(card.title, card.subtitle, card.artworkUrl, Icons.Rounded.Album,
                                            heat = if (heatOn) com.music.bitchord.ui.components.rememberTileHeat(card.songs) else null) {
                                            onOpenAlbum(card.title, card.artworkUrl)
                                        }
                                        is NewCard.One -> Card(card.song.title, card.song.artist, card.song.thumbnailUrl, Icons.Rounded.MusicNote,
                                            heat = songHeat(card.song)) {
                                            onPlay(listOf(card.song), 0)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                HomeSection.RECENTLY_PLAYED -> if (home.recent.isNotEmpty()) {
                    item(key = "home_recent_title") {
                        ShelfTitle(stringResource(R.string.recently_played), action = stringResource(R.string.show_all), onAction = onShowAllRecent)
                    }
                    item(key = "home_recent") { SongShelf(home.recent, ::songHeat) { i -> onPlay(home.recent, i) } }
                }
                HomeSection.TIME_CAPSULE -> home.capsule?.let { capsule ->
                    item(key = "home_capsule_title") {
                        val subtitle = if (capsule.monthsBack == 12) {
                            stringResource(R.string.home_capsule_year, capsule.month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()))
                        } else {
                            stringResource(R.string.home_capsule_months, capsule.monthsBack)
                        }
                        ShelfTitle(stringResource(R.string.home_time_capsule), subtitle)
                    }
                    item(key = "home_capsule") { SongShelf(capsule.songs, ::songHeat) { i -> onPlay(capsule.songs, i) } }
                }
                HomeSection.DEEP_CUTS -> if (heatOn && home.deepCuts.isNotEmpty()) {
                    item(key = "home_deep_title") {
                        ShelfTitle(stringResource(R.string.home_deep_cuts), stringResource(R.string.home_deep_cuts_subtitle))
                    }
                    item(key = "home_deep") {
                        val list = home.deepCuts.map { it.song }
                        CardRow(home.deepCuts, { "deep_${it.song.videoId}" }) { cut ->
                            Card(cut.song.title, stringResource(R.string.home_deep_cut_from, cut.album), cut.song.thumbnailUrl,
                                Icons.Rounded.MusicNote, heat = songHeat(cut.song)) { onPlay(list, list.indexOf(cut.song)) }
                        }
                    }
                }
                HomeSection.TIME_OF_DAY -> if (heatOn && home.timeOfDay.size >= 4) {
                    item(key = "home_time_title") {
                        ShelfTitle(stringResource(slotTitle(home.slot)), stringResource(R.string.home_time_of_day_subtitle))
                    }
                    item(key = "home_time") { SongShelf(home.timeOfDay, ::songHeat) { i -> onPlay(home.timeOfDay, i) } }
                }
                HomeSection.FOR_YOU -> if (home.forYouSongs.size >= 4) {
                    item(key = "home_for_you_title") {
                        ShelfTitle(stringResource(R.string.home_for_you), stringResource(R.string.home_for_you_subtitle))
                    }
                    item(key = "home_for_you") { SongShelf(home.forYouSongs, ::songHeat) { i -> onPlay(home.forYouSongs, i) } }
                }
                HomeSection.UPLIFTING -> if (home.upliftingSongs.size >= 4) {
                    item(key = "home_uplifting_title") {
                        ShelfTitle(stringResource(R.string.home_uplifting), stringResource(R.string.home_uplifting_subtitle))
                    }
                    item(key = "home_uplifting") { SongShelf(home.upliftingSongs, ::songHeat) { i -> onPlay(home.upliftingSongs, i) } }
                }
                HomeSection.CALM -> if (home.calmSongs.size >= 4) {
                    item(key = "home_calm_title") {
                        ShelfTitle(stringResource(R.string.home_calm), stringResource(R.string.home_calm_subtitle))
                    }
                    item(key = "home_calm") { SongShelf(home.calmSongs, ::songHeat) { i -> onPlay(home.calmSongs, i) } }
                }
                HomeSection.UNPLAYED_ALBUMS -> if (home.unplayedAlbums.isNotEmpty()) {
                    item(key = "home_unplayed_title") { ShelfTitle(stringResource(R.string.home_unplayed_albums)) }
                    item(key = "home_unplayed") {
                        CardRow(home.unplayedAlbums, { "unplayed_${it.title}_${it.artist}" }) { a ->
                            Card(a.title, a.artist, a.artworkUrl, Icons.Rounded.Album, heat = if (heatOn) 0f else null) {
                                onOpenAlbum(a.title, a.artworkUrl)
                            }
                        }
                    }
                }
                HomeSection.COVERAGE -> home.coverage?.let { coverage ->
                    item(key = "home_coverage") { CoverageCard(coverage.percent, coverage.stale.isNotEmpty()) { onShufflePlay(coverage.stale) } }
                }
                HomeSection.STATS -> item(key = "home_stats") { WeekCard(home.week, onOpenReplay) }
            }
        }
        // The shelves' order and visibility, edited here rather than in Settings.
        item(key = "home_edit_layout") {
            com.music.bitchord.ui.components.EditLayoutButton(onClick = { editLayout = true })
        }
    }
    }
    if (editLayout) {
        com.music.bitchord.ui.components.HomeLayoutSheet(onDismiss = { editLayout = false })
    }
    if (showModelPrompt) {
        Music2EmoDownloadDialog(
            dismissLabelRes = R.string.home_ai_not_now,
            onConfirm = {
                showModelPrompt = false
                LocalHomeAi.setModelDecision(context, true)
                modelPromptDismissed = false
                scope.launch {
                    try {
                        LocalHomeAi.downloadModels(context)
                        modelInstalled = withContext(Dispatchers.IO) { LocalHomeAi.isModelInstalled(context) }
                    } catch (e: Exception) {
                        downloadError = e.localizedMessage ?: "Download failed"
                    }
                }
            },
            onDismiss = {
                showModelPrompt = false
                LocalHomeAi.setModelDecision(context, false)
                modelPromptDismissed = true
            },
        )
    }
    aiDownloadProgress?.let { dp ->
        Music2EmoDownloadProgressDialog(progress = dp)
    }
    downloadError?.let { err ->
        Music2EmoDownloadErrorDialog(
            error = err,
            onDismiss = { downloadError = null },
        )
    }
}

@Composable
private fun LocalAiOptInCard(
    onEnable: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(com.music.bitchord.ui.components.PolygonShape(ExpressiveShapes.cookie))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_ai_card_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.home_ai_card_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.82f),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.home_ai_not_now))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onEnable,
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Text(stringResource(R.string.home_ai_download_action))
                }
            }
        }
    }
}

@Composable
private fun LocalAiAnalysisBanner(
    progress: LocalHomeAi.AnalysisProgress,
) {
    val total = progress.total.coerceAtLeast(1)
    val completed = progress.completed.coerceIn(0, total)
    val fraction = (completed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    val percent = (fraction * 100f).toInt().coerceIn(0, 100)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(com.music.bitchord.ui.components.PolygonShape(ExpressiveShapes.cookie))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                        trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.24f),
                    )
                }
                Text(
                    text = stringResource(R.string.home_ai_analysis_progress, completed, progress.total),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                ) {
                    Text(
                        text = "$percent%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.14f),
            )
        }
    }
}

/** Mixes made from heat: hidden with the meter off. */
private val HEAT_MIXES = setOf(MixKind.HOT, MixKind.DEEP_CUTS, MixKind.TIME)

private fun slotTitle(slot: Int): Int = when (slot) {
    0 -> R.string.mix_morning
    1 -> R.string.mix_daytime
    2 -> R.string.mix_evening
    else -> R.string.mix_night
}

private const val DAY_SECONDS = 24 * 60 * 60

private sealed interface NewCard {
    val title: String
    data class Album(override val title: String, val subtitle: String, val artworkUrl: String?, val songs: List<Song>) : NewCard
    data class One(val song: Song) : NewCard { override val title: String get() = song.title }
}

/**
 * What is new on the device, newest first. Songs of one album that arrived
 * within a day of each other become that album's card when there are three or
 * more of them, or at least half the album — so copying in a whole album is one
 * card, not a shelf of its tracks.
 */
private fun newAdditions(songs: List<Song>): List<NewCard> {
    val dated = songs.filter { (it.localDateAddedSeconds ?: 0L) > 0L }
        .sortedByDescending { it.localDateAddedSeconds }
        .take(300)
    if (dated.isEmpty()) return emptyList()
    val albumSizes = songs.groupingBy { it.albumName?.trim()?.lowercase(Locale.ROOT).orEmpty() }.eachCount()
    val used = HashSet<String>()
    val out = mutableListOf<NewCard>()
    for (song in dated) {
        if (out.size >= 16) break
        if (song.videoId in used) continue
        val album = song.albumName?.trim().orEmpty()
        val key = album.lowercase(Locale.ROOT)
        val at = song.localDateAddedSeconds ?: 0L
        val batch = if (album.isEmpty()) listOf(song) else dated.filter {
            it.videoId !in used &&
                it.albumName?.trim()?.lowercase(Locale.ROOT) == key &&
                kotlin.math.abs((it.localDateAddedSeconds ?: 0L) - at) <= DAY_SECONDS
        }
        val total = albumSizes[key] ?: batch.size
        if (album.isNotEmpty() && (batch.size >= 3 || batch.size * 2 >= total && batch.size > 1)) {
            batch.forEach { used += it.videoId }
            out += NewCard.Album(
                title = album,
                subtitle = song.albumArtist?.takeIf(String::isNotBlank) ?: song.artist,
                artworkUrl = batch.firstNotNullOfOrNull { it.thumbnailUrl?.takeIf(String::isNotBlank) },
                songs = batch,
            )
        } else {
            used += song.videoId
            out += NewCard.One(song)
        }
    }
    return out
}

private fun formatDuration(context: android.content.Context, ms: Long): String {
    val minutes = (ms / 60_000L).toInt()
    return if (minutes >= 60) {
        context.getString(R.string.hours_minutes_short, minutes / 60, minutes % 60)
    } else {
        context.getString(R.string.minutes_short, minutes)
    }
}

@Composable
private fun HomeHeader() {
    // Like every other tab: the logo scrolling with the list, and the page's
    // name left to the glass title capsule (no big "Home" of its own). The
    // listening numbers that sat here moved to Replay (2.6.3).
    com.music.bitchord.ui.components.TabLogoHeader(
        listTopPadding = com.music.bitchord.ui.components.topBarContentPadding(),
    )
}

/** Each mix's hue, and the M3 Expressive shape its collage is cut to. */
private fun mixHue(kind: MixKind): Float = when (kind) {
    MixKind.HOT -> 12f
    MixKind.REDISCOVER -> 230f
    MixKind.NEW -> 168f
    MixKind.DEEP_CUTS -> 38f
    MixKind.TIME -> 268f
    MixKind.FOR_YOU -> 295f
    MixKind.UPLIFTING -> 46f
    MixKind.CALM -> 188f
    MixKind.UNPLAYED -> 335f
}

private val MixShapes: List<Shape> by lazy {
    listOf(
        com.music.bitchord.ui.components.ExpressiveShapes.cookie,
        androidx.graphics.shapes.RoundedPolygon.star(4, radius = 1f, innerRadius = 0.62f, rounding = androidx.graphics.shapes.CornerRounding(0.42f)).normalized(),
        androidx.graphics.shapes.RoundedPolygon.star(8, radius = 1f, innerRadius = 0.86f, rounding = androidx.graphics.shapes.CornerRounding(0.12f)).normalized(),
        androidx.graphics.shapes.RoundedPolygon.star(6, radius = 1f, innerRadius = 0.72f, rounding = androidx.graphics.shapes.CornerRounding(0.32f)).normalized(),
    ).map { com.music.bitchord.ui.components.PolygonShape(it) }
}

/**
 * "Mixes for you" (design A of the mix mock): each mix a collage of four of
 * its sleeves cut to an M3 Expressive shape, a play button on it, and the
 * word "Mix" over its name so it is not taken for a playlist. A tap shuffles
 * it straight away.
 */
@Composable
private fun MixShelf(
    mixes: List<com.music.bitchord.feature.home.data.HomeMix>,
    slot: Int,
    onPlay: (com.music.bitchord.feature.home.data.HomeMix) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(mixes, key = { _, m -> m.kind.name }) { index, mix ->
            MixCard(mix, slot, MixShapes[index % MixShapes.size]) { onPlay(mix) }
        }
    }
}

@Composable
private fun MixCard(mix: com.music.bitchord.feature.home.data.HomeMix, slot: Int, shape: Shape, onClick: () -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val hue = mixHue(mix.kind)
    val accent = Color.hsl(hue, if (dark) 0.85f else 0.70f, if (dark) 0.70f else 0.45f)
    val onAccent = if (dark) Color.hsl(hue, 0.6f, 0.14f) else Color.White
    val (name, desc) = when (mix.kind) {
        MixKind.HOT -> R.string.mix_hot to R.string.mix_hot_desc
        MixKind.REDISCOVER -> R.string.mix_rediscover to R.string.mix_rediscover_desc
        MixKind.NEW -> R.string.mix_new to R.string.mix_new_desc
        MixKind.DEEP_CUTS -> R.string.mix_deep_cuts to R.string.mix_deep_cuts_desc
        MixKind.TIME -> slotTitle(slot) to R.string.mix_time_desc
        MixKind.FOR_YOU -> R.string.home_for_you to R.string.mix_for_you_desc
        MixKind.UPLIFTING -> R.string.home_uplifting to R.string.mix_uplifting_desc
        MixKind.CALM -> R.string.home_calm to R.string.mix_calm_desc
        MixKind.UNPLAYED -> R.string.mix_unplayed to R.string.mix_unplayed_desc
    }
    // Four different sleeves where there are four; repeated where there are fewer.
    val arts = remember(mix.songs) {
        mix.songs.mapNotNull { it.thumbnailUrl?.takeIf(String::isNotBlank) }.distinct().take(4)
    }
    Column(
        Modifier
            .width(156.dp)
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.size(156.dp)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(Color.hsl(hue, 0.35f, if (dark) 0.24f else 0.88f)),
            ) {
                for (row in 0..1) {
                    Row(Modifier.weight(1f)) {
                        for (col in 0..1) {
                            val art = arts.getOrNull((row * 2 + col) % arts.size.coerceAtLeast(1))
                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                if (art != null) {
                                    AsyncImage(
                                        model = art,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
                    .size(48.dp)
                    .shadow(6.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    com.music.bitchord.ui.icons.BitChordIcons.TransportPlay,
                    contentDescription = stringResource(R.string.shuffle),
                    tint = onAccent,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.home_mix_kicker),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
        Text(
            stringResource(name),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            stringResource(desc) + " · " + stringResource(R.string.home_track_count, mix.songs.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The Heat shelf's switch as an M3 Expressive connected button group: the
 * buttons touch, inner corners small; the chosen one rounds fully and fills.
 */
@Composable
private fun HeatTabs(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf(R.string.songs, R.string.albums, R.string.artists, R.string.playlists)
    // Each button as wide as its label (sharing the width cut "Artists" and
    // "Playlists" short), scrolling when they do not fit; room below before
    // the sleeves.
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 14.dp)
            .height(40.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val on = selected == i
            val full = 20.dp
            val inner = 6.dp
            val start by androidx.compose.animation.core.animateDpAsState(if (on || i == 0) full else inner, label = "heatTabStart")
            val end by androidx.compose.animation.core.animateDpAsState(if (on || i == labels.lastIndex) full else inner, label = "heatTabEnd")
            val shape = RoundedCornerShape(topStart = start, bottomStart = start, topEnd = end, bottomEnd = end)
            val container by androidx.compose.animation.animateColorAsState(
                if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                label = "heatTabColor",
            )
            // The chosen one widens a little, as M3 Expressive's selected button does.
            val sidePad by androidx.compose.animation.core.animateDpAsState(if (on) 22.dp else 16.dp, label = "heatTabPad")
            Box(
                Modifier
                    .fillMaxHeight()
                    .clip(shape)
                    .background(container)
                    .clickable { onSelect(i) }
                    .padding(horizontal = sidePad),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun EmptyShelf() {
    Text(
        text = stringResource(R.string.library_empty_section),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
    )
}

@Composable
private fun ShelfTitle(title: String, subtitle: String? = null, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 22.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

/** M3 Expressive's nine-scallop "cookie", as a shape for the hero's artwork. */
private object CookieShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = minOf(cx, cy)
        val steps = 180
        for (i in 0..steps) {
            val a = i.toFloat() / steps * 2f * PI.toFloat()
            val rr = r * (0.9f + 0.1f * cos(9f * a))
            val x = cx + rr * cos(a)
            val y = cy + rr * sin(a)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

@Composable
private fun HeroCard(song: Song, level: Float, isCurrent: Boolean, onClick: () -> Unit) {
    // The artwork is still, on purpose: it used to turn forever, and Home sits
    // under the player and the glass bars — an endless animation here redrew
    // the whole page every frame. The meter flows on the shared heat clock,
    // which holds still while the player is raised.
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(112.dp).clip(CookieShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
            AsyncImage(
                model = song.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.home_hero),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                song.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            HeatMeter(level = level, width = 80.dp, trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
        }
    }
}

@Composable
private fun <T> CardRow(items: List<T>, key: (T) -> Any, card: @Composable (T) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = key) { card(it) }
    }
}

@Composable
private fun SongShelf(songs: List<Song>, heatOf: (Song) -> Float?, dimmed: Boolean = false, onClick: (Int) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(songs, key = { _, s -> s.videoId }) { index, song ->
            Card(
                title = song.title,
                subtitle = song.artist,
                artworkUrl = song.thumbnailUrl,
                fallback = Icons.Rounded.MusicNote,
                dimmed = dimmed,
                heat = heatOf(song),
            ) { onClick(index) }
        }
    }
}

/** A tile: artwork with its heat ring (none with heat off), then two lines. */
@Composable
private fun Card(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    fallback: ImageVector,
    dimmed: Boolean = false,
    heat: Float? = null,
    round: Boolean = false,
    size: Dp = 132.dp,
    onClick: () -> Unit,
) {
    val shape = if (round) CircleShape else RoundedCornerShape(20.dp)
    // Only the artwork is rounded: clipping the whole card shaved the start
    // of the second line off at the bottom corner.
    Column(
        Modifier
            .width(size)
            .clickable(onClick = onClick),
        horizontalAlignment = if (round) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Box(
            Modifier
                .size(size)
                .heatRing(heat, shape)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(fallback, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer { if (dimmed) alpha = 0.7f },
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle.isNotEmpty()) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CoverageCard(percent: Int, canShuffle: Boolean, onShuffle: () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.LibraryMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.home_coverage), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.home_coverage_text, percent), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(10.dp))
        // The share heard, as one bar.
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Box(
                Modifier
                    .fillMaxWidth((percent / 100f).coerceIn(0.02f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        if (canShuffle) {
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onShuffle, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.home_coverage_action))
            }
        }
    }
}

@Composable
private fun WeekCard(week: List<Pair<java.time.LocalDate, Long>>, onOpenReplay: () -> Unit) {
    if (week.isEmpty()) return
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 18.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onOpenReplay)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.home_stats), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.home_stats_open) + " ›", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(12.dp))
        val max = week.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
        Row(
            Modifier.fillMaxWidth().height(84.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            week.forEach { (_, ms) ->
                val top = ms == max && ms > 0
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight((ms.toFloat() / max).coerceIn(0.06f, 1f))
                        .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                        .background(if (top) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            week.forEach { (day, _) ->
                Text(
                    day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}
