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
        inlineAccessory = song?.let { current ->
            { accessoryModifier, _ ->
                GlassNowPlaying(
                    song = current,
                    isInline = true,
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
        expandedAccessory = song?.let { current ->
            { accessoryModifier, _ ->
                GlassNowPlaying(
                    song = current,
                    isInline = false,
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
                    modifier = accessoryModifier.fillMaxWidth().then(surfaceModifier()),
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
 * Two densities of the same row rather than two components, so the shared
 * element carrying it between the bar's states has one thing to interpolate:
 * [isInline] narrows the artwork, drops the artist line and drops the skip
 * button, which is what makes it fit the collapsed row's height.
 */
@Composable
private fun GlassNowPlaying(
    song: Song,
    isInline: Boolean,
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

    val artSize = if (isInline) 32.dp else 40.dp
    val glyphSlot = if (isInline) 32.dp else 40.dp
    // Same proportions as the full player (skip 46 : play 66 ≈ 0.7), so
    // play/pause reads as the main control here too.
    val skipGlyphSize = if (isInline) 17.dp else 21.dp
    val playGlyphSize = if (isInline) 24.dp else 30.dp

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
                .padding(
                    horizontal = if (isInline) 8.dp else 12.dp,
                    vertical = if (isInline) 4.dp else 8.dp,
                ),
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
                            .size(artSize)
                            .then(
                                // Only the playing track is the dock's shared element.
                                if (isCurrent) Modifier.playerDockArt(dock = dock, corner = if (isInline) 6.dp else 8.dp)
                                else Modifier,
                            )
                            .clip(RoundedCornerShape(if (isInline) 6.dp else 8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Spacer(Modifier.width(if (isInline) 8.dp else 10.dp))
                    if (isInline) {
                        val titleStyle = MaterialTheme.typography.titleMedium.centeredLine()
                        Text(
                            text = shownSong.title,
                            style = titleStyle,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).fixedLineHeight(titleStyle),
                        )
                    } else {
                        Column(Modifier.weight(1f)) {
                            val titleStyle = MaterialTheme.typography.titleMedium.centeredLine()
                            val artistStyle = MaterialTheme.typography.bodyMedium.centeredLine()
                            ExplicitSongTitle(
                                song = shownSong,
                                style = titleStyle,
                                color = contentColor,
                                modifier = Modifier.fixedLineHeight(titleStyle),
                            )
                            Text(
                                modifier = Modifier.fixedLineHeight(artistStyle),
                                text = shownSong.artist,
                                style = artistStyle,
                                color = if (useGlass) contentColor.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            if (showPreviousButton) {
                IconButton(
                    onClick = {
                        haptics.play(Haptic.SkipPrevious)
                        onPrevious()
                    },
                    modifier = Modifier.size(glyphSlot),
                ) {
                    Icon(
                        imageVector = BitChordIcons.TransportPrevious,
                        contentDescription = stringResource(R.string.widget_previous),
                        tint = contentColor,
                        modifier = Modifier.size(skipGlyphSize),
                    )
                }
                // Same gap as before Next, so play/pause sits in the middle.
                Spacer(Modifier.width(8.dp))
            }
            if (isLoading) {
                Box(Modifier.size(glyphSlot), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = if (useGlass) contentColor else MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(if (isInline) 18.dp else 22.dp),
                    )
                }
            } else {
                IconButton(
                    onClick = {
                        haptics.play(if (isPlaying) Haptic.Pause else Haptic.Resume)
                        onPlayPause()
                    },
                    modifier = Modifier.size(glyphSlot),
                ) {
                    Icon(
                        imageVector = if (isPlaying) BitChordIcons.TransportPause else BitChordIcons.TransportPlay,
                        contentDescription = stringResource(if (isPlaying) R.string.pause else R.string.play),
                        tint = contentColor,
                        modifier = Modifier.size(playGlyphSize),
                    )
                }
            }
            if (!isInline && !playPauseOnly) {
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        haptics.play(Haptic.SkipNext)
                        onNext()
                    },
                    modifier = Modifier.size(glyphSlot),
                ) {
                    Icon(
                        imageVector = BitChordIcons.TransportNext,
                        contentDescription = stringResource(R.string.widget_next),
                        tint = contentColor,
                        modifier = Modifier.size(skipGlyphSize),
                    )
                }
            }
        }
    }
}
