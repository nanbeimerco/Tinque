/*
 * The bar's structure and its inline/expanded behaviour are
 * EchoMusicApp/Echo-Music's AppFloatingNavBar + FloatingMiniPlayer (GPL-3.0),
 * over the FloatingTabBar vendored in [com.music.bitchord.ui.components.floatingtabbar].
 * BitChord's own tabs, song model, transport and haptics are wired through it in
 * place of Echo's Screens routing and PlayerConnection.
 */
@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.music.bitchord.ui.components

import com.music.bitchord.ui.icons.BitChordIcons
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.layout.Layout
import kotlin.math.roundToInt
import com.music.bitchord.ui.components.floatingtabbar.BarMorph
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.layout
import androidx.compose.runtime.derivedStateOf
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.components.floatingtabbar.FloatingTabBar
import com.music.bitchord.ui.components.floatingtabbar.FloatingTabBarDefaults
import com.music.bitchord.ui.components.floatingtabbar.FloatingTabBarScrollConnection
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.ui.player.LocalPlayerDock
import com.music.bitchord.ui.player.PlayerDock
import com.music.bitchord.ui.player.playerDockArt

/**
 * The liquid glass navigation bar: the iOS 26 shape where the now playing
 * controls and the tabs are one component rather than two stacked bars.
 *
 * Expanded, it is a full width now playing pill sitting over the tab pill and a
 * separate circular Search tab. Scrolling down collapses it inline — the tabs
 * fold down to just the selected one, the now playing controls narrow into the
 * gap between it and Search, and the whole thing becomes a single row the width
 * of the screen. Scrolling back up expands it. The fold is driven by
 * [scrollConnection], which the page's scroll has to be dispatched into for any
 * of this to move; see MainActivity's `nestedScroll`.
 *
 * Every surface here samples the app backdrop through [Modifier.liquidGlass], so
 * this is only ever used where that is supported and switched on — off either,
 * MainActivity draws [MiniPlayer] and [FloatingBottomBar] instead.
 *
 * [song] null means nothing is playing, and the accessory is simply absent: the
 * bar is then the tab pill and Search alone, and the collapse still works.
 */
@Composable
fun GlassNavBar(
    tabs: List<BottomTab>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    scrollConnection: FloatingTabBarScrollConnection,
    song: Song?,
    isPlaying: Boolean,
    isLoading: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onExpand: () -> Unit,
    onDismiss: () -> Unit = {},
    dock: PlayerDock? = null,
    pull: MiniPlayerPull? = null,
    useGlass: Boolean = false,
    modifier: Modifier = Modifier,
    /** The queue's neighbours, previewed while swiping the mini player. */
    previousSong: Song? = null,
    nextSong: Song? = null,
) {
    val pillShape = remember { RoundedCornerShape(percent = 50) }
    val isDark = isSystemInDarkTheme()
    val borderColor = if (isDark) GLASS_EDGE_COLOR else Color.Black.copy(alpha = 0.08f)
    val hideNavigationBarLabels by AppSettings.hideNavigationBarLabels.collectAsStateWithLifecycle()
    val lockBottomBarExpanded by AppSettings.lockBottomBarExpanded.collectAsStateWithLifecycle()
    val integrateStandaloneTab by AppSettings.integrateStandaloneTab.collectAsStateWithLifecycle()
    val shouldIntegrate = lockBottomBarExpanded && integrateStandaloneTab
    val themeMode by AppSettings.themeMode.collectAsStateWithLifecycle()
    val isArtworkTheme = themeMode == com.music.bitchord.data.settings.ThemeMode.ARTWORK
    val liquidGlassOpacity by AppSettings.liquidGlassOpacity.collectAsStateWithLifecycle()

    val contentColor = if (useGlass) {
        if (isDark || isArtworkTheme) Color.White else glassContentColor()
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val artworkColor = MaterialTheme.colorScheme.primary
    val selectedColor = if (useGlass) {
        if (isArtworkTheme) artworkColor else contentColor
    } else {
        artworkColor
    }
    val unselectedColor = if (useGlass) {
        Color.White.copy(alpha = 0.70f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val milkyPillAlpha = (0.14f + liquidGlassOpacity * 0.25f).coerceIn(0.15f, 0.40f)
    val renderStyle by AppSettings.glassRenderStyle.collectAsStateWithLifecycle()
    val indicatorColor = when {
        !useGlass -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        // Mornye's resting pill: a faint wash, not a milky capsule.
        renderStyle == com.music.bitchord.data.settings.GlassRenderStyle.CLEAR ->
            if (isArtworkTheme || isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
        isArtworkTheme || isDark -> Color.White.copy(alpha = milkyPillAlpha)
        else -> Color.Black.copy(alpha = 0.12f)
    }
    val haptics = rememberHaptics()

    val currentOnTabSelected by rememberUpdatedState(onTabSelected)
    val standaloneIndex = tabs.lastIndex

    // A factory, not a value. Each surface gets its own glass or clean background modifier.
    val surfaceModifier: @Composable () -> Modifier = remember(useGlass, pillShape, borderColor) {
        if (useGlass) {
            { Modifier.liquidGlass(shape = pillShape, role = GlassRole.Player) }
        } else {
            {
                Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, pillShape)
                    .border(GLASS_EDGE_WIDTH, borderColor, pillShape)
            }
        }
    }

    FloatingTabBar(
        selectedTabKey = selectedIndex,
        scrollConnection = scrollConnection,
        modifier = modifier
            .windowInsetsPadding(floatingBarInsets)
            .padding(horizontal = PAGE_GUTTER)
            .padding(bottom = 2.dp)
            .fillMaxWidth(),
        tabBarContentModifier = if (useGlass) {
            { Modifier.liquidGlass(shape = pillShape, role = GlassRole.TabBar) }
        } else surfaceModifier,
        accessory = song?.let { current ->
            { accessoryModifier, morph ->
                GlassNowPlaying(
                    song = current,
                    morph = morph,
                    isPlaying = isPlaying,
                    isLoading = isLoading,
                    contentColor = contentColor,
                    onPlayPause = onPlayPause,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onExpand = onExpand,
                    onDismiss = onDismiss,
                    dock = dock,
                    pull = pull,
                    useGlass = useGlass,
                    previousSong = previousSong,
                    nextSong = nextSong,
                    modifier = accessoryModifier.then(surfaceModifier()),
                )
            }
        },
        // Transparent: the surface underneath is the background, and a
        // colour over it would be the thing you saw instead of the backdrop.
        colors = FloatingTabBarDefaults.colors(
            backgroundColor = Color.Transparent,
            accessoryBackgroundColor = Color.Transparent,
            indicatorColor = indicatorColor,
            selectedContentColor = selectedColor,
            artworkColor = artworkColor,
            unselectedContentColor = unselectedColor,
            isArtworkTheme = isArtworkTheme && useGlass,
        ),
        // Flat, because the surface is not. Every surface here already draws its
        // own edge border and shadow pass.
        elevations = FloatingTabBarDefaults.elevations(
            inlineElevation = 0.dp,
            expandedElevation = 0.dp,
        ),
        // Expanded, this is meant to be the plain [FloatingBottomBar] with a
        // different material — same outer width, same pill inset, same tab
        // padding, same 25dp glyph — so the two bars measure identically and the
        // toggle changes the surface rather than the layout.
        sizes = FloatingTabBarDefaults.sizes(
            tabBarContentPadding = PaddingValues(PILL_INSET),
            tabExpandedContentPadding = PaddingValues(vertical = TAB_VERTICAL_PADDING),
            // The inline circle: the 25dp glyph and 10dp around it.
            inlineBarHeight = 45.dp,
        ),
        contentKey = remember(selectedIndex, tabs, contentColor, artworkColor, isArtworkTheme, hideNavigationBarLabels, shouldIntegrate) {
            listOf(selectedIndex, tabs, contentColor, artworkColor, isArtworkTheme, hideNavigationBarLabels, shouldIntegrate)
        },
    ) {
        tabs.forEachIndexed { index, tab ->
            val isSelected = index == selectedIndex
            val defaultTint = if (isSelected) selectedColor else unselectedColor
            val onClick = {
                if (!isSelected) haptics.play(Haptic.Select)
                currentOnTabSelected(index)
            }
            if (index == standaloneIndex && !shouldIntegrate) {
                standaloneTab(
                    key = index,
                    icon = {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = defaultTint,
                            modifier = Modifier.size(25.dp),
                        )
                    },
                    onClick = onClick,
                    indication = null,
                )
            } else {
                tab(
                    key = index,
                    icon = {
                        val currentTint = androidx.compose.material3.LocalContentColor.current
                        val tint = if (currentTint.alpha > 0f) currentTint else defaultTint
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = tint,
                            modifier = Modifier.size(25.dp),
                        )
                    },
                    title = {
                        if (!hideNavigationBarLabels) {
                            val currentTint = androidx.compose.material3.LocalContentColor.current
                            val tint = if (currentTint.alpha > 0f) currentTint else defaultTint
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = tint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = TAB_ICON_LABEL_GAP),
                            )
                        }
                    },
                    onClick = onClick,
                    indication = null,
                )
            }
        }
    }
}

/**
 * The now playing controls docked into [GlassNavBar] as its accessory.
 *
 * One row that follows the bar's fold ([morph]) as it goes: the artwork,
 * buttons and padding shrink to the inline sizes as it drops into the row,
 * and the artist line and skip button fade and fold away as it narrows. All
 * of it is read while laying out and drawing, so the fold does not recompose
 * this row on every frame.
 */
@Composable
private fun GlassNowPlaying(
    song: Song,
    morph: BarMorph,
    isPlaying: Boolean,
    isLoading: Boolean,
    contentColor: Color,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onExpand: () -> Unit,
    onDismiss: () -> Unit = {},
    dock: PlayerDock?,
    pull: MiniPlayerPull?,
    useGlass: Boolean = false,
    previousSong: Song? = null,
    nextSong: Song? = null,
    modifier: Modifier = Modifier,
) {
    val previousButtonSetting by AppSettings.miniPlayerPreviousButton.collectAsStateWithLifecycle()
    val playPauseOnly by AppSettings.miniPlayerPlayPauseOnly.collectAsStateWithLifecycle()
    val showPreviousButton = previousButtonSetting && !playPauseOnly
    val fullBleedArt by AppSettings.fullBleedArtwork.collectAsStateWithLifecycle()
    val keepOriginalArtworkAspectRatio by AppSettings.keepOriginalArtworkAspectRatio.collectAsStateWithLifecycle()
    val preserveSleeveArtworkShape = keepOriginalArtworkAspectRatio && !fullBleedArt
    val miniArtContentScale = if (preserveSleeveArtworkShape) ContentScale.Fit else ContentScale.Crop
    val haptics = rememberHaptics()
    val pressSource = remember { MutableInteractionSource() }
    val isPressed by pressSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 1.04f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "accessoryPressScale",
    )

    // Sizes go with the drop into the row; what disappears goes with the
    // narrowing, which comes first, so it is gone before the room is.
    val drop = remember(morph) { { morph.accessoryDrop } }
    val narrow = remember(morph) { { (morph.accessoryWidth / 0.5f).coerceIn(0f, 1f) } }
    // Past halfway it is the inline row: the dock's corner and the swipe's
    // bleed switch then, not on every frame.
    val isInline by remember(morph) { derivedStateOf { morph.accessoryDrop >= 0.5f } }
    val artSize = Modifier.morphSize(40.dp, 32.dp, drop)
    // Folded, the row has play/pause alone, whatever the buttons setting:
    // the skip buttons fade out where they stand, early in the fold, and
    // stop taking taps once they are mostly gone.
    val showNextButton = !playPauseOnly
    val buttonsGone = remember(morph) { { (morph.accessoryWidth / BUTTONS_FADE_END).coerceIn(0f, 1f) } }
    val skipsTappable by remember(morph) { derivedStateOf { morph.accessoryWidth < BUTTONS_FADE_END * 0.5f } }
    val buttons = remember(morph, showPreviousButton, showNextButton) {
        MiniPlayerButtonPlacement(morph, showPreviousButton, showNextButton)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .miniPlayerGestures(
                pull = pull,
                // Track changes are the artwork/title area's own swipe (with preview).
                horizontalSwipe = false,
                onNext = {
                    haptics.play(Haptic.SkipNext)
                    onNext()
                },
                onPrevious = {
                    haptics.play(Haptic.SkipPrevious)
                    onPrevious()
                },
            )
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .then(modifier)
            .clickable(
                interactionSource = pressSource,
                indication = null,
                onClick = onExpand,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .morphPadding(12.dp, 8.dp, 8.dp, 4.dp, drop),
        ) {
            MiniPlayerTrackSwipe(
                current = song,
                previous = previousSong,
                next = nextSong,
                onPrevious = onPrevious,
                onNext = onNext,
                modifier = Modifier.weight(1f),
                startBleed = if (isInline) 8.dp else 12.dp,
            ) { shownSong, isCurrent ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = shownSong.artworkAt(ROW_ART_PX),
                        contentDescription = null,
                        contentScale = miniArtContentScale,
                        modifier = Modifier
                            .then(artSize)
                            .then(
                                // Only the playing track is the dock's shared element.
                                if (isCurrent) Modifier.playerDockArt(dock = dock, corner = if (isInline) 6.dp else 8.dp)
                                else Modifier,
                            )
                            .clip(RoundedCornerShape(if (isInline) 6.dp else 8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Spacer(Modifier.morphSize(10.dp, 8.dp, drop, height = false))
                    Column(Modifier.weight(1f)) {
                        val titleStyle = MaterialTheme.typography.titleMedium.centeredLine()
                        val artistStyle = MaterialTheme.typography.bodyMedium.centeredLine()
                        ExplicitSongTitle(
                            song = shownSong,
                            style = titleStyle,
                            color = contentColor,
                            modifier = Modifier.fixedLineHeight(titleStyle),
                        )
                        // Folds its height away as well as fading, so the
                        // title alone is centred in the inline row.
                        Text(
                            modifier = Modifier
                                .fixedLineHeight(artistStyle)
                                .foldAway(narrow, horizontal = false),
                            text = shownSong.artist,
                            style = artistStyle,
                            color = if (useGlass) contentColor.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            // Room kept clear for the buttons, which are placed over this end
            // of the row (below) rather than laid out in it.
            Spacer(
                Modifier.layout { measurable, _ ->
                    val width = buttons.reservedWidth(this, drop()).roundToInt().coerceAtLeast(0)
                    val placeable = measurable.measure(Constraints.fixed(width, 0))
                    layout(placeable.width, 0) { placeable.place(0, 0) }
                },
            )
        }

        // The buttons, placed from the accessory's right edge. Play/pause
        // drops into the folded row almost straight down — its distance from
        // the right edge closes by about as much as the edge moves in — and
        // shrinks as a drawing rather than being laid out smaller every
        // frame. The skip buttons stay where they are on screen and fade.
        // Laid out in the row instead, the play button was pushed right by
        // the skip button folding away while the edge pulled it left, on two
        // different timings, and went back and forth.
        Layout(
            modifier = Modifier.matchParentSize(),
            content = {
                if (showPreviousButton) {
                    IconButton(
                        onClick = {
                            haptics.play(Haptic.SkipPrevious)
                            onPrevious()
                        },
                        enabled = skipsTappable,
                        modifier = Modifier.graphicsLayer { alpha = 1f - buttonsGone() },
                    ) {
                        Icon(
                            imageVector = BitChordIcons.TransportPrevious,
                            contentDescription = stringResource(R.string.widget_previous),
                            tint = contentColor,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }
                val playScale = Modifier.graphicsLayer {
                    val scale = 1f + (PLAY_FOLDED_SCALE - 1f) * drop()
                    scaleX = scale
                    scaleY = scale
                }
                if (isLoading) {
                    Box(playScale, contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = if (useGlass) contentColor else MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            haptics.play(if (isPlaying) Haptic.Pause else Haptic.Resume)
                            onPlayPause()
                        },
                        modifier = playScale,
                    ) {
                        Icon(
                            imageVector = if (isPlaying) BitChordIcons.TransportPause else BitChordIcons.TransportPlay,
                            contentDescription = stringResource(if (isPlaying) R.string.pause else R.string.play),
                            tint = contentColor,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
                if (showNextButton) {
                    IconButton(
                        onClick = {
                            haptics.play(Haptic.SkipNext)
                            onNext()
                        },
                        enabled = skipsTappable,
                        modifier = Modifier.graphicsLayer { alpha = 1f - buttonsGone() },
                    ) {
                        Icon(
                            imageVector = BitChordIcons.TransportNext,
                            contentDescription = stringResource(R.string.widget_next),
                            tint = contentColor,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }
            },
        ) { measurables, constraints ->
            val slot = BUTTON_SLOT.roundToPx()
            val placeables = measurables.map { it.measure(Constraints.fixed(slot, slot)) }
            // Unbounded when asked for an intrinsic size (the bar does that
            // for a mini player that appears while folded): a size of
            // "infinity" is not a size, and reporting it crashed the app.
            val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
            val height = if (constraints.hasBoundedHeight) constraints.maxHeight else maxOf(constraints.minHeight, slot)
            layout(width, height) {
                val y = (height - slot) / 2
                val centers = buttons.centersFromRight(this@Layout)
                placeables.forEachIndexed { index, placeable ->
                    val fromRight = centers.getOrNull(index) ?: return@forEachIndexed
                    placeable.place((width - fromRight - slot / 2f).roundToInt(), y)
                }
            }
        }
    }
}

/** How far into the narrowing the skip buttons are gone. */
private const val BUTTONS_FADE_END = 0.35f

/** Play/pause folded: the 24dp glyph of the inline row, drawn from the 30dp one. */
private const val PLAY_FOLDED_SCALE = 0.8f

/** The touch target of every button on the row. */
private val BUTTON_SLOT = 40.dp

/**
 * Where [GlassNowPlaying]'s buttons go, as distances from the accessory's
 * right edge to each button's centre, in the order they are composed
 * (previous, play/pause, next).
 */
private class MiniPlayerButtonPlacement(
    private val morph: BarMorph,
    private val hasPrevious: Boolean,
    private val hasNext: Boolean,
) {
    private val slot = BUTTON_SLOT
    private val gap = 8.dp

    private fun Density.playExpanded(): Float =
        (12.dp + (if (hasNext) slot + gap else 0.dp) + slot / 2).toPx()

    private fun Density.playFolded(): Float = (8.dp + 16.dp).toPx()

    private fun Density.play(): Float {
        val t = morph.accessoryWidth
        return playExpanded() + (playFolded() - playExpanded()) * t
    }

    // The skip buttons hold their place on screen: as the right edge comes
    // in, they sit that much closer to it.
    private fun Density.next(): Float = (12.dp + slot / 2).toPx() - morph.accessoryRightInset

    private fun Density.previous(): Float =
        playExpanded() + (slot + gap).toPx() - morph.accessoryRightInset

    fun centersFromRight(density: Density): List<Float> = with(density) {
        buildList {
            if (hasPrevious) add(previous())
            add(play())
            if (hasNext) add(next())
        }
    }

    /**
     * The room the row leaves at its end for the buttons, past its own end
     * padding: up to the leftmost button that is showing, closing in on
     * play/pause as the others fade.
     */
    fun reservedWidth(density: Density, drop: Float): Float = with(density) {
        val half = (slot / 2).toPx()
        val leftmostExpanded = (if (hasPrevious) previous() else play()) + half
        val playLeft = play() + half * (1f + (PLAY_FOLDED_SCALE - 1f) * drop)
        val gone = (morph.accessoryWidth / BUTTONS_FADE_END).coerceIn(0f, 1f)
        val edge = leftmostExpanded + (playLeft - leftmostExpanded) * gone
        val rowEndPadding = (12.dp + (8.dp - 12.dp) * drop).toPx()
        edge + 4.dp.toPx() - rowEndPadding
    }
}


/** A size between [from] (at 0) and [to] (at 1), read when laid out. */
private fun Modifier.morphSize(from: Dp, to: Dp, fraction: () -> Float, height: Boolean = true): Modifier =
    layout { measurable, _ ->
        val f = fraction()
        val px = (from.toPx() + (to.toPx() - from.toPx()) * f).roundToInt()
        val placeable = measurable.measure(
            if (height) Constraints.fixed(px, px) else Constraints.fixed(px, 0),
        )
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

/** Padding between two sets ([fromH]/[fromV] at 0, [toH]/[toV] at 1), read when laid out. */
private fun Modifier.morphPadding(fromH: Dp, toH: Dp, fromV: Dp, toV: Dp, fraction: () -> Float): Modifier =
    layout { measurable, constraints ->
        val f = fraction()
        val h = (fromH.toPx() + (toH.toPx() - fromH.toPx()) * f).roundToInt()
        val v = (fromV.toPx() + (toV.toPx() - fromV.toPx()) * f).roundToInt()
        val placeable = measurable.measure(constraints.offset(-2 * h, -2 * v))
        val width = constraints.constrainWidth(placeable.width + 2 * h)
        val height = constraints.constrainHeight(placeable.height + 2 * v)
        layout(width, height) { placeable.place(h, v) }
    }

/**
 * Fades out and gives up its width ([horizontal]) or its height as
 * [fraction] goes to 1, drawn clipped to what is left of it.
 */
private fun Modifier.foldAway(fraction: () -> Float, horizontal: Boolean): Modifier =
    this
        .layout { measurable, constraints ->
            val placeable = measurable.measure(
                if (horizontal) constraints.copy(minWidth = 0) else constraints.copy(minHeight = 0),
            )
            val keep = 1f - fraction()
            val width = if (horizontal) (placeable.width * keep).roundToInt() else placeable.width
            val height = if (horizontal) placeable.height else (placeable.height * keep).roundToInt()
            layout(width, height) { placeable.place(0, 0) }
        }
        .graphicsLayer {
            alpha = 1f - fraction()
            clip = true
        }
