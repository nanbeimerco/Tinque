@file:OptIn(ExperimentalFoundationApi::class)

/*
 * FloatingTabBar v1.0.1 by Elyes Mansour
 * https://github.com/elyesmansour/compose-floating-tab-bar
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Vendored from the v1.0.1 tag: the published AAR is compiled against
 * Compose 1.8 and crashes with NoSuchMethodError on
 * SharedTransitionScope.sharedElement under the Compose version this app uses.
 * Compiling the source here keeps it binary-compatible.
 *
 * Local modifications on top of v1.0.1:
 * - Package renamed.
 * - InlineBar/ExpandedBar use Row/Column instead of ConstraintLayout, and the
 *   tab pill + standalone tab cluster is centered (iOS 26 style) instead of
 *   being pinned to opposite screen edges.
 * - The inline/expanded AnimatedContent and its shared elements are gone:
 *   the bar folds on one spring-driven number instead, each surface laid out
 *   from it on every frame and each part of the fold staged after the other
 *   (see [BarMorph] and [MorphingBar]).
 *
 * Taken from EchoMusicApp/Echo-Music, which carries the three modifications
 * listed above; BitChord's own use of it is in
 * [com.music.bitchord.ui.components.GlassNavBar]. One further change here:
 *
 * - `tabBarContentModifier` is a `@Composable () -> Modifier` factory rather
 *   than a single Modifier value. It is applied to three surfaces of three
 *   different sizes (the tab pill, the circular standalone tab, the accessory),
 *   and a liquid glass Modifier carries a shape-outline cache keyed on the size
 *   it last saw — so one shared instance is invalidated by whichever surface
 *   drew last and re-creates all three outlines every frame. A factory gives
 *   each surface its own.
 * - The expanded tab group's selection pill is a lifted glass lens in motion
 *   (see [GlassSelectionPill]): a tap inflates it past the bar, carries it to
 *   the tab on a spring, squashes it with its acceleration, and sheds the glass
 *   back into a flat fill on landing. Horizontal drags pick it up the same way,
 *   resist at the ends, tick across tab boundaries, and settle on release.
 */

package com.music.bitchord.ui.components.floatingtabbar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.components.GlassSpring
import com.music.bitchord.ui.components.LocalGlassExport
import com.music.bitchord.ui.components.backdrop.backdrops.layerBackdrop
import com.music.bitchord.ui.components.backdrop.backdrops.rememberLayerBackdrop
import com.music.bitchord.ui.components.isGlassSupported
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.Constraints
import kotlin.math.abs
import kotlin.math.roundToInt

/** Hoisted so the default is one stable lambda rather than a new one per call. */
private val DefaultTabBarContentModifier: @Composable () -> Modifier = { Modifier }

/**
 * A floating tab bar that can transition between inline and expanded states.
 *
 * @param isInline controls the FloatingTabBar's inline state
 * @param selectedTabKey the key of the currently selected tab
 * @param modifier the modifier to be applied to the tab bar
 * @param shapes the shapes used by the tab bar components
 * @param sizes the sizes and spacing used by the tab bar components
 * @param elevations the elevation values used by the tab bar components
 * @param tabBarContentModifier modifier applied to the tab bar sections containing the grouped tabs and standalone tab.
 * It is applied after the default styling (background, shadow, clip) but before any content padding.
 * @param accessory the accessory composable (e.g. the now playing controls). It is given the
 * [BarMorph] to fit its content to the fold as it goes.
 * @param contentKey optional key that when changed retriggers the content lambda
 * @param content the content defining the tabs
 */
@Composable
fun FloatingTabBar(
    isInline: Boolean,
    selectedTabKey: Any?,
    modifier: Modifier = Modifier,
    tabBarContentModifier: @Composable () -> Modifier = DefaultTabBarContentModifier,
    accessory: (@Composable (Modifier, BarMorph) -> Unit)? = null,
    colors: FloatingTabBarColors = FloatingTabBarDefaults.colors(),
    shapes: FloatingTabBarShapes = FloatingTabBarDefaults.shapes(),
    sizes: FloatingTabBarSizes = FloatingTabBarDefaults.sizes(),
    elevations: FloatingTabBarElevations = FloatingTabBarDefaults.elevations(),
    contentKey: Any? = null,
    content: FloatingTabBarScope.() -> Unit
) {
    val scrollConnection = rememberFloatingTabBarScrollConnection(
        initialIsInline = isInline,
        inlineBehavior = FloatingTabBarInlineBehavior.Never
    )

    LaunchedEffect(isInline) {
        if (isInline) scrollConnection.inline() else scrollConnection.expand()
    }

    FloatingTabBar(
        selectedTabKey = selectedTabKey,
        scrollConnection = scrollConnection,
        modifier = modifier,
        tabBarContentModifier = tabBarContentModifier,
        accessory = accessory,
        colors = colors,
        shapes = shapes,
        sizes = sizes,
        elevations = elevations,
        contentKey = contentKey,
        content = content
    )
}

/**
 * A floating tab bar that folds between its expanded and inline states as the
 * page scrolls.
 *
 * The fold is one number, [BarMorph.fold] (0 = expanded, 1 = inline), run on a
 * spring. Every surface works out where it is from that number on each frame,
 * each over its own part of the way (see [BarMorph]), so the parts move one
 * after another rather than all at once, and a fold turned around halfway
 * simply heads back from where it is. Before a fold that starts from rest, the
 * whole bar dips slightly towards its centre ([BarMorph.squeeze]).
 *
 * @param selectedTabKey the key of the currently selected tab
 * @param scrollConnection the scroll connection that handles state transitions
 * @param modifier the modifier to be applied to the tab bar
 * @param colors the colors used by the tab bar components
 * @param shapes the shapes used by the tab bar components
 * @param sizes the sizes and spacing used by the tab bar components
 * @param elevations the elevation values used by the tab bar components
 * @param tabBarContentModifier modifier applied to the tab bar sections containing the grouped tabs and standalone tab.
 * It is applied after the default styling (background, shadow, clip) but before any content padding.
 * @param accessory the accessory composable (e.g. the now playing controls)
 * @param contentKey optional key that when changed retriggers the content lambda
 * @param content the content defining the tabs
 */
@Composable
fun FloatingTabBar(
    selectedTabKey: Any?,
    scrollConnection: FloatingTabBarScrollConnection,
    modifier: Modifier = Modifier,
    tabBarContentModifier: @Composable () -> Modifier = DefaultTabBarContentModifier,
    accessory: (@Composable (Modifier, BarMorph) -> Unit)? = null,
    colors: FloatingTabBarColors = FloatingTabBarDefaults.colors(),
    shapes: FloatingTabBarShapes = FloatingTabBarDefaults.shapes(),
    sizes: FloatingTabBarSizes = FloatingTabBarDefaults.sizes(),
    elevations: FloatingTabBarElevations = FloatingTabBarDefaults.elevations(),
    contentKey: Any? = null,
    content: FloatingTabBarScope.() -> Unit
) {
    val scope = remember(contentKey) { FloatingTabBarScopeImpl().apply { content() } }
    val reduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()

    val fold = remember { Animatable(if (scrollConnection.isInline) 1f else 0f) }
    val squeeze = remember { Animatable(0f) }
    val morph = remember { BarMorph({ fold.value }, { squeeze.value }) }
    val animationScope = rememberCoroutineScope()
    val currentReduceAnimation by rememberUpdatedState(reduceAnimation)

    LaunchedEffect(scrollConnection) {
        // Waiting out the squeeze before the fold itself starts.
        var pending: Job? = null
        snapshotFlow { scrollConnection.isInline }.collect { inline ->
            val target = if (inline) 1f else 0f
            if (currentReduceAnimation) {
                pending?.cancel()
                fold.snapTo(target)
                squeeze.snapTo(0f)
                return@collect
            }
            val waiting = pending?.isActive == true && !fold.isRunning
            if (waiting) {
                // Turned around before the fold began: drop the dip too.
                pending?.cancel()
                animationScope.launch { squeeze.animateTo(0f, tween(SQUEEZE_OUT_MS)) }
            }
            if (!waiting && !fold.isRunning && abs(fold.value - target) > 0.5f) {
                // From rest: dip first, then fold out of the dip.
                pending = animationScope.launch {
                    launch {
                        squeeze.animateTo(1f, tween(SQUEEZE_IN_MS, easing = LinearOutSlowInEasing))
                        squeeze.animateTo(0f, tween(SQUEEZE_OUT_MS, easing = FastOutSlowInEasing))
                    }
                    delay(SQUEEZE_IN_MS.toLong())
                    fold.animateTo(target, FoldSpring)
                }
            } else {
                // Mid-fold: [Animatable.animateTo] takes the current velocity
                // with it, so the bar turns around without a jolt.
                pending = animationScope.launch { fold.animateTo(target, FoldSpring) }
            }
        }
    }

    val inlineTab = scope.getInlineTab(selectedTabKey)
    val standaloneTab = scope.standaloneTab
    val geometry = remember { BarGeometry() }

    MorphingBar(
        morph = morph,
        sizes = sizes,
        geometry = geometry,
        hasAccessory = accessory != null,
        hasStandalone = standaloneTab != null,
        // A new scope means new tab contents (labels shown or hidden, say),
        // which can change the expanded height.
        groupHeightKeys = scope,
        modifier = modifier,
        tabGroup = {
            if (scope.tabs.isNotEmpty()) {
                TabGroup(
                    scope = scope,
                    selectedTabKey = selectedTabKey,
                    inlineTab = inlineTab,
                    morph = morph,
                    geometry = geometry,
                    onCollapsedClick = {
                        scrollConnection.expand()
                        inlineTab?.onClick?.invoke()
                    },
                    onCollapsedLongClick = { scrollConnection.expand() },
                    shapes = shapes,
                    sizes = sizes,
                    colors = colors,
                    elevations = elevations,
                    tabBarContentModifier = tabBarContentModifier,
                    modifier = Modifier,
                )
            }
        },
        accessory = {
            if (accessory != null) {
                Box {
                    accessory(
                        Modifier
                            .fillMaxSize()
                            .shadow(
                                shape = shapes.accessoryShape,
                                elevation = elevations.expandedElevation
                            )
                            .background(color = colors.accessoryBackgroundColor, shapes.accessoryShape)
                            .clip(shapes.accessoryShape),
                        morph,
                    )
                }
            }
        },
        standalone = {
            if (standaloneTab != null) {
                Tab(
                    icon = standaloneTab.icon,
                    title = standaloneTab.title,
                    isInline = true,
                    isStandalone = true,
                    modifier = Modifier
                        .shadow(
                            shape = shapes.standaloneTabShape,
                            elevation = elevations.expandedElevation
                        )
                        .background(
                            color = colors.backgroundColor,
                            shape = shapes.standaloneTabShape
                        )
                        .clip(shapes.standaloneTabShape)
                        .then(tabBarContentModifier())
                        .clickable(
                            onClick = standaloneTab.onClick,
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        )
                )
            }
        },
    )
}

/**
 * Where the bar is in its fold, for the surfaces and their contents to read
 * while laying out and drawing — never in composition, which would recompose
 * the bar on every frame of every fold.
 *
 * The parts are staged along [fold] so that nothing ever runs into anything
 * else: the tab group and the standalone tab shrink to circles first, which
 * opens room for the accessory beside them; the accessory narrows to fit that
 * room before it drops into the row. Played back the other way, the accessory
 * rises out of the row first and the tab group starts to widen once it is
 * clear of it, before it has reached the top.
 */
@Stable
class BarMorph internal constructor(
    private val foldValue: () -> Float,
    private val squeezeValue: () -> Float,
) {
    /** The whole fold: 0 = expanded, 1 = inline. */
    val fold: Float get() = foldValue().coerceIn(0f, 1f)

    /** The dip before a fold starts from rest: 0 at rest, 1 at its deepest. */
    val squeeze: Float get() = squeezeValue().coerceIn(0f, 1f)

    /** The tab group and the standalone tab shrinking to circles. */
    val tabs: Float get() = smoothStep(segment(fold, 0f, TABS_END))

    /** The tabs other than the one that stays, and the pill, fading out. */
    val tabFade: Float get() = segment(fold, 0f, TAB_FADE_END)

    /** The tab labels fading out. */
    val labelFade: Float get() = segment(fold, 0f, LABEL_FADE_END)

    /** The accessory narrowing to the room between the two circles. */
    val accessoryWidth: Float get() = smoothStep(segment(fold, ACCESSORY_WIDTH_START, ACCESSORY_WIDTH_END))

    /**
     * The accessory dropping into the row. Eased in rather than in and out:
     * on the way up it rises quickly, so it is out of the row early and the
     * tab group can start widening while it still has a little way to go.
     */
    val accessoryDrop: Float get() = segment(fold, ACCESSORY_DROP_START, 1f).let { it * it }
}

private fun segment(value: Float, start: Float, end: Float): Float =
    ((value - start) / (end - start)).coerceIn(0f, 1f)

private fun smoothStep(t: Float): Float = t * t * (3f - 2f * t)

private fun lerp(start: Float, stop: Float, fraction: Float): Float = start + (stop - start) * fraction

// Where each part of the fold runs, along [BarMorph.fold]. Chosen together so
// the accessory never overlaps the tab group or the standalone tab: it is
// narrowed to its inline width (by 0.5) before it reaches the row's height
// (about 0.6), and the circles are finished (by 0.47) before either.
private const val TABS_END = 0.47f
private const val TAB_FADE_END = 0.3f
private const val LABEL_FADE_END = 0.2f
private const val ACCESSORY_WIDTH_START = 0.05f
private const val ACCESSORY_WIDTH_END = 0.5f
private const val ACCESSORY_DROP_START = 0.1f

/** The dip: in over 50ms, then back out while the fold runs. */
private const val SQUEEZE_IN_MS = 50
private const val SQUEEZE_OUT_MS = 220
/** How far the bar dips: to 95% of its size. */
private const val SQUEEZE_SCALE = 0.05f

/**
 * iOS's spring with a response of 0.3s and a damping fraction of 0.85:
 * stiffness is (2π / response)².
 */
private val FoldSpring = spring(
    dampingRatio = 0.85f,
    stiffness = 438.6f,
    visibilityThreshold = 0.0005f,
)

/** What the outer layout knows that the tab group's inside needs. */
private class BarGeometry {
    /** The tab group's width when expanded, minus its padding. */
    var groupContentWidth = 0
    /** The inline circle's diameter. */
    var inlineSize = 0
}

/**
 * Lays the three surfaces out where the fold says they are.
 *
 * Every surface is measured at its in-between size on every frame, so each
 * one's contents fill it as it changes (the tab group's contents keep their
 * expanded layout inside it, see [TabGroup]). The bar keeps its bottom edge
 * fixed and grows or shrinks at the top.
 */
@Composable
private fun MorphingBar(
    morph: BarMorph,
    sizes: FloatingTabBarSizes,
    geometry: BarGeometry,
    hasAccessory: Boolean,
    hasStandalone: Boolean,
    groupHeightKeys: Any?,
    modifier: Modifier,
    tabGroup: @Composable () -> Unit,
    accessory: @Composable () -> Unit,
    standalone: @Composable () -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    val heights = remember(groupHeightKeys, fontScale) { ExpandedHeights() }
    Layout(
        contents = listOf(tabGroup, accessory, standalone),
        modifier = modifier.graphicsLayer {
            // The whole bar dips towards its own centre, as one piece.
            val s = 1f - SQUEEZE_SCALE * morph.squeeze
            scaleX = s
            scaleY = s
        },
    ) { (groupMeasurables, accessoryMeasurables, standaloneMeasurables), constraints ->
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
        val gap = sizes.componentSpacing.roundToPx()
        val inline = sizes.inlineBarHeight.roundToPx()
        val padding = sizes.tabBarContentPadding
        val padH = padding.calculateLeftPadding(layoutDirection).roundToPx() +
            padding.calculateRightPadding(layoutDirection).roundToPx()
        val group = groupMeasurables.firstOrNull()
        val accessoryM = if (hasAccessory) accessoryMeasurables.firstOrNull() else null
        val standaloneM = if (hasStandalone) standaloneMeasurables.firstOrNull() else null

        // Expanded heights, asked of the contents once per width rather than
        // on every frame.
        if (heights.width != width) {
            heights.width = width
            heights.group = -1
            heights.accessory = -1
        }
        // The standalone circle is as tall as the group, so it takes the
        // group's width; assume a square from a first guess and settle it.
        if (heights.group < 0) {
            val guess = group?.maxIntrinsicHeight(width) ?: inline
            val groupWidth = width - if (standaloneM != null) guess + gap else 0
            geometry.groupContentWidth = (groupWidth - padH).coerceAtLeast(0)
            heights.group = (group?.maxIntrinsicHeight(groupWidth) ?: inline).coerceAtLeast(inline)
        }
        val expandedTabs = heights.group

        val standaloneE = if (standaloneM != null) expandedTabs else 0
        val groupWidthE = width - if (standaloneM != null) standaloneE + gap else 0
        geometry.groupContentWidth = (groupWidthE - padH).coerceAtLeast(0)
        geometry.inlineSize = inline

        // Inline: [tab][accessory][standalone] across the row, or, with no
        // accessory, the two circles packed together in the middle.
        val groupXI: Int
        val standaloneXI: Int
        if (accessoryM != null) {
            groupXI = 0
            standaloneXI = width - inline
        } else {
            val cluster = inline + if (standaloneM != null) gap + inline else 0
            groupXI = (width - cluster) / 2
            standaloneXI = groupXI + inline + gap
        }
        val accessoryXI = inline + gap
        val accessoryWI = (width - accessoryXI - if (standaloneM != null) inline + gap else 0).coerceAtLeast(0)

        val t = morph.tabs
        val groupX = lerp(0f, groupXI.toFloat(), t)
        val groupW = lerp(groupWidthE.toFloat(), inline.toFloat(), t)
        val groupH = lerp(expandedTabs.toFloat(), inline.toFloat(), t)
        val standaloneX = lerp((width - standaloneE).toFloat(), standaloneXI.toFloat(), t)
        val standaloneS = lerp(standaloneE.toFloat(), inline.toFloat(), t)

        val tw = morph.accessoryWidth
        val td = morph.accessoryDrop
        val accessoryX = lerp(0f, accessoryXI.toFloat(), tw)
        val accessoryW = lerp(width.toFloat(), accessoryWI.toFloat(), tw)
        val accessoryWPx = accessoryW.roundToInt().coerceAtLeast(0)
        // Until it starts to drop, the accessory is measured at its own height,
        // which is then its expanded height for the rest of the fold. Not asked
        // for as an intrinsic: the mini player's swipe preview answers that
        // with far more than it takes.
        var accessoryP = if (accessoryM != null && td == 0f) {
            accessoryM.measure(Constraints(minWidth = accessoryWPx, maxWidth = accessoryWPx, minHeight = inline))
                .also { heights.accessory = it.height }
        } else {
            null
        }
        if (accessoryM != null && heights.accessory < 0) {
            // Starting out folded, with no expanded height seen yet.
            heights.accessory = accessoryM.maxIntrinsicHeight(width).coerceAtLeast(inline)
        }
        val expandedAccessory = heights.accessory.coerceAtLeast(0)
        val accessoryH = lerp(expandedAccessory.toFloat(), inline.toFloat(), td)
        // How far the accessory's bottom sits above the bar's bottom.
        val accessoryLift = lerp((expandedTabs + gap).toFloat(), 0f, td)

        val groupP = group?.measure(Constraints.fixed(groupW.roundToInt().coerceAtLeast(0), groupH.roundToInt().coerceAtLeast(0)))
        val standaloneP = standaloneM?.measure(Constraints.fixed(standaloneS.roundToInt().coerceAtLeast(0), standaloneS.roundToInt().coerceAtLeast(0)))
        if (accessoryP == null) {
            accessoryP = accessoryM?.measure(Constraints.fixed(accessoryWPx, accessoryH.roundToInt().coerceAtLeast(0)))
        }

        val tabsH = maxOf(groupP?.height ?: 0, standaloneP?.height ?: 0)
        val height = maxOf(tabsH, if (accessoryP != null) (accessoryLift + accessoryH).roundToInt() else 0)
        layout(width, height) {
            groupP?.placeRelative(groupX.roundToInt(), height - groupP.height)
            standaloneP?.placeRelative(standaloneX.roundToInt(), height - standaloneP.height)
            accessoryP?.placeRelative(accessoryX.roundToInt(), (height - accessoryLift - accessoryH).roundToInt())
        }
    }
}

private class ExpandedHeights {
    var width = -1
    var group = -1
    var accessory = -1
}

/**
 * A [NestedScrollConnection] that handles scroll events to transition between inline and expanded states.
 *
 * @param initialIsInline Initial state of the tab bar (inline or expanded).
 * @param scrollThresholdPx The minimum scroll distance in pixels required to trigger a state change.
 * @param inlineBehavior Defines when the tab bar should transition to inline state.
 */
class FloatingTabBarScrollConnection(
    initialIsInline: Boolean = false,
    private val scrollThresholdPx: Float,
    private val inlineBehavior: FloatingTabBarInlineBehavior = FloatingTabBarInlineBehavior.OnScrollDown
) : NestedScrollConnection {
    var isInline by mutableStateOf(initialIsInline)
        private set

    private var accumulatedScroll = 0f

    fun expand() {
        isInline = false
        accumulatedScroll = 0f
    }

    fun inline() {
        isInline = true
        accumulatedScroll = 0f
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        // If behavior is Never, don't change state
        if (inlineBehavior == FloatingTabBarInlineBehavior.Never) {
            return Offset.Zero
        }

        val scrollDelta = available.y

        // Reset accumulated scroll if changing direction
        if ((accumulatedScroll > 0 && scrollDelta < 0) || (accumulatedScroll < 0 && scrollDelta > 0)) {
            accumulatedScroll = 0f
        }

        // Accumulate scroll
        accumulatedScroll += scrollDelta

        when (inlineBehavior) {
            FloatingTabBarInlineBehavior.OnScrollDown -> {
                // Check if we've scrolled enough to trigger state change
                if (accumulatedScroll <= -scrollThresholdPx && !isInline) {
                    // Scrolling down enough - transition to inline mode
                    isInline = true
                    accumulatedScroll = 0f // Reset after state change
                } else if (accumulatedScroll >= scrollThresholdPx && isInline) {
                    // Scrolling up enough - transition to expanded mode
                    isInline = false
                    accumulatedScroll = 0f // Reset after state change
                }
            }

            FloatingTabBarInlineBehavior.OnScrollUp -> {
                // Check if we've scrolled enough to trigger state change
                if (accumulatedScroll >= scrollThresholdPx && !isInline) {
                    // Scrolling up enough - transition to inline mode
                    isInline = true
                    accumulatedScroll = 0f // Reset after state change
                } else if (accumulatedScroll <= -scrollThresholdPx && isInline) {
                    // Scrolling down enough - transition to expanded mode
                    isInline = false
                    accumulatedScroll = 0f // Reset after state change
                }
            }

            FloatingTabBarInlineBehavior.Never -> {
                // Already handled above, but included for completeness
            }
        }

        return Offset.Zero // Don't consume the scroll, let it pass through
    }
}

/**
 * Creates and remembers a [FloatingTabBarScrollConnection] instance.
 *
 * @param initialIsInline Initial state of the tab bar (inline or expanded). Default is false.
 * @param scrollThreshold The minimum scroll distance required to trigger a state change. Default is 24.dp,
 * so the bar answers a short scroll rather than waiting for a long one.
 * @param inlineBehavior Defines when the tab bar should transition to inline state. Default is [FloatingTabBarInlineBehavior.OnScrollDown].
 * @return A remembered [FloatingTabBarScrollConnection] instance.
 */
@Composable
fun rememberFloatingTabBarScrollConnection(
    initialIsInline: Boolean = false,
    scrollThreshold: Dp = 24.dp,
    inlineBehavior: FloatingTabBarInlineBehavior = FloatingTabBarInlineBehavior.OnScrollDown
): FloatingTabBarScrollConnection = with(LocalDensity.current) {
    val scrollThresholdPx = scrollThreshold.toPx()
    remember(scrollThresholdPx, inlineBehavior, initialIsInline) {
        FloatingTabBarScrollConnection(initialIsInline, scrollThresholdPx, inlineBehavior)
    }
}

/**
 * Defines when the floating tab bar should transition to inline state.
 */
enum class FloatingTabBarInlineBehavior {
    /** Never transition to inline - it stays in expanded state */
    Never,

    /** Transition to inline when scrolling down */
    OnScrollDown,

    /** Transition to inline when scrolling up */
    OnScrollUp
}

interface FloatingTabBarScope {
    /**
     * Adds a regular tab to the floating tab bar.
     *
     * @param key Unique identifier for the tab
     * @param title Composable content for the tab title
     * @param icon Composable content for the tab icon
     * @param onClick Callback invoked when the tab is clicked
     * @param indication Optional indication provider for touch feedback, defaults to LocalIndication.current
     */
    fun tab(
        key: Any,
        title: @Composable () -> Unit,
        icon: @Composable () -> Unit,
        onClick: () -> Unit,
        indication: (@Composable () -> Indication)? = { LocalIndication.current }
    )

    /**
     * Adds a standalone tab to the floating tab bar.
     *
     * Note: Calling this method more than once will override the previous standalone tab value.
     *
     * @param key Unique identifier for the standalone tab
     * @param icon Composable content for the tab icon
     * @param onClick Callback invoked when the tab is clicked
     * @param indication Optional indication provider for touch feedback, defaults to LocalIndication.current
     */
    fun standaloneTab(
        key: Any,
        icon: @Composable () -> Unit,
        onClick: () -> Unit,
        indication: (@Composable () -> Indication)? = { LocalIndication.current }
    )
}

@Composable
private fun TabGroup(
    scope: FloatingTabBarScopeImpl,
    selectedTabKey: Any?,
    inlineTab: FloatingTabBarTab?,
    morph: BarMorph,
    geometry: BarGeometry,
    onCollapsedClick: () -> Unit,
    onCollapsedLongClick: () -> Unit,
    shapes: FloatingTabBarShapes,
    sizes: FloatingTabBarSizes,
    colors: FloatingTabBarColors,
    elevations: FloatingTabBarElevations,
    modifier: Modifier,
    tabBarContentModifier: @Composable () -> Modifier
) {
    // Past halfway the bar acts as the inline circle: one tap expands it.
    // Read through derivedStateOf, so this recomposes twice a fold, not on
    // every frame of it.
    val collapsed by remember(morph) { derivedStateOf { morph.fold >= 0.5f } }
    val collapsedInteraction = remember { MutableInteractionSource() }
    val selectedTabIndex = scope.tabs.indexOfFirst { it.key == selectedTabKey }
    val currentSelectedTabIndex by rememberUpdatedState(selectedTabIndex)
    val reduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val selectionSpec: AnimationSpec<Float> = if (reduceAnimation) {
        snap()
    } else {
        GlassSpring
    }
    val haptics = rememberHaptics()
    // Each label's height, for the icon above it to come down by half of it
    // into the middle of the inline circle. Written on layout, read on draw.
    val labelHeights = remember(scope.tabs.size) { FloatArray(scope.tabs.size) }
    val blurMaxPx = with(LocalDensity.current) { TAB_FOLD_BLUR.toPx() }
    // The inline circle's icon colour: the selected tab's own, unless the
    // standalone tab is the selected one and this is only the last tab.
    val inlineColor = if (inlineTab == null || inlineTab.key != selectedTabKey) {
        colors.unselectedContentColor
    } else if (colors.selectedContentColor != Color.Unspecified) {
        colors.selectedContentColor
    } else if (colors.isArtworkTheme && colors.artworkColor != Color.Unspecified) {
        colors.artworkColor
    } else {
        colors.unselectedContentColor
    }

    // Back from the standalone tab there is no tab for the pill to travel
    // from, so it grows in place instead — on the same spring the icon scales
    // on, so the two start and settle together, as they do between tabs.
    val pillPop = remember { androidx.compose.animation.core.Animatable(1f) }
    // A tap on the tab that is already selected: a small press in the icon,
    // since nothing else on the bar moves.
    val reselectPress = remember { androidx.compose.animation.core.Animatable(1f) }

    val pillMotionStyle by AppSettings.pillMotionStyle.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val pill = remember { GlassPillMotion(coroutineScope, selectedTabIndex.coerceAtLeast(0)) }
    pill.reduceMotion = reduceAnimation
    pill.motionStyle = pillMotionStyle
    // The lens samples the recorded page, and "reduce dynamic blur" stops that recording.
    val glassPill = isGlassSupported() && !reduceDynamicBlur
    val barGlass = rememberLayerBackdrop()
    val tabRow = rememberLayerBackdrop()

    var rowSize by remember { mutableStateOf(IntSize.Zero) }
    var lastHapticTab by remember { mutableIntStateOf(selectedTabIndex) }
    var lastSelectedTabIndex by remember { mutableIntStateOf(selectedTabIndex) }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val tabCount = scope.tabs.size
    val tabSpacingPx = with(density) { sizes.tabSpacing.toPx() }
    val tabWidthPx = if (rowSize.width > 0 && tabCount > 0) {
        (rowSize.width - tabSpacingPx * (tabCount - 1)) / tabCount
    } else {
        0f
    }
    val tabStepPx = if (rowSize.width > 0 && tabCount > 0) {
        (rowSize.width + tabSpacingPx) / tabCount
    } else {
        0f
    }
    pill.stepDp = tabStepPx / density.density

    // The pill rests in its tab's cell and lifts to stand [PILL_GROW_HEIGHT]
    // taller than the whole bar, keeping the cell's proportions as it grows.
    val contentPadding = sizes.tabBarContentPadding
    val padLeftPx = with(density) { contentPadding.calculateLeftPadding(layoutDirection).toPx() }
    val padTopPx = with(density) { contentPadding.calculateTopPadding().toPx() }
    val padBottomPx = with(density) { contentPadding.calculateBottomPadding().toPx() }
    val rowHeightPx = rowSize.height.toFloat()
    val restSize = Size(tabWidthPx, rowHeightPx)
    val liftedHeightPx = rowHeightPx + padTopPx + padBottomPx +
        with(density) { PILL_GROW_HEIGHT.toPx() }
    val liftedSize = if (rowHeightPx > 0f) {
        Size(liftedHeightPx * tabWidthPx / rowHeightPx, liftedHeightPx)
    } else {
        restSize
    }
    // Where the pill's centre is in the row, for a position in tab steps.
    val pillCenterInRow: (Float) -> Offset = { position ->
        val x = position * tabStepPx + tabWidthPx / 2f
        Offset(if (isRtl) rowSize.width - x else x, rowHeightPx / 2f)
    }
    val showPill = selectedTabIndex >= 0 && tabWidthPx > 0f
    // The pill as actually drawn — position, and its live size while lifted
    // and stretching — in tab-row coordinates. Read at draw time.
    val rowCoords = remember { CoordsHolder() }
    val pillRectInRow: () -> Rect = {
        val center = pillCenterInRow(pill.position)
        val size = if (!pill.isFlat) pill.liveSize(restSize, liftedSize) else restSize
        Rect(Offset(center.x - size.width / 2f, center.y - size.height / 2f), size)
    }

    LaunchedEffect(selectedTabIndex) {
        if (selectedTabIndex >= 0) {
            // Back from the standalone tab there is no tab to travel from.
            if (lastSelectedTabIndex < 0) {
                pill.snapTo(selectedTabIndex)
                if (!reduceAnimation) {
                    launch {
                        pillPop.snapTo(0f)
                        pillPop.animateTo(1f, selectionSpec)
                    }
                }
            } else {
                pillPop.snapTo(1f)
                if (!pill.isDragging) pill.animateTo(selectedTabIndex)
            }
        }
        lastSelectedTabIndex = selectedTabIndex
        lastHapticTab = selectedTabIndex
    }

    // Unclipped: the lifted pill stands past the bar's edges, so it is drawn
    // here, over the bar, rather than inside the bar's clip.
    Box(
        modifier = modifier
    ) {
        // The bar's glass is exported for the lens to refract only while the
        // glass selection pill is lifted/travelling.
        val activeGlassExport = barGlass.takeIf { glassPill && showPill && !pill.isFlat }
        CompositionLocalProvider(LocalGlassExport provides activeGlassExport) {
            Box(
                modifier = Modifier
                    .shadow(
                        shape = shapes.tabBarShape,
                        elevation = elevations.expandedElevation
                    )
                    .background(
                        color = colors.backgroundColor,
                        shape = shapes.tabBarShape
                    )
                    .clip(shapes.tabBarShape)
                    .then(tabBarContentModifier())
                    .then(
                        if (collapsed) {
                            Modifier.combinedClickable(
                                onClick = onCollapsedClick,
                                onLongClick = onCollapsedLongClick,
                                indication = null,
                                interactionSource = collapsedInteraction,
                            )
                        } else {
                            Modifier
                        }
                    )
                    .padding(contentPadding)
            ) {
              // The tabs keep their expanded layout while the surface around
              // them shrinks to the inline circle; see [expandedContentFrame].
              Box(Modifier.expandedContentFrame(geometry)) {
                if (showPill && (pill.isFlat || !glassPill)) {
                    FlatSelectionPill(
                        color = colors.indicatorColor,
                        isLifted = !pill.isFlat,
                        modifier = Modifier
                            .matchParentSize()
                            // Sized from the surface as it is this frame, not
                            // from the row's last reported size, so while the
                            // bar morphs the pill always fits inside it.
                            .layout { measurable, constraints ->
                                // The bar's height is worked out by asking for its
                                // intrinsic height, which measures this with no
                                // height limit (after a rotation or an unfold). The
                                // pill takes no room of its own, so it says so —
                                // building fixed constraints from an unbounded
                                // height crashed the app.
                                if (!constraints.hasBoundedWidth || !constraints.hasBoundedHeight) {
                                    return@layout layout(constraints.minWidth, constraints.minHeight) {}
                                }
                                val w = constraints.maxWidth.toFloat()
                                val h = constraints.maxHeight
                                val cell = if (tabCount > 0) {
                                    ((w - tabSpacingPx * (tabCount - 1)) / tabCount).coerceAtLeast(1f)
                                } else {
                                    1f
                                }
                                val placeable = measurable.measure(
                                    Constraints.fixed(cell.roundToInt().coerceAtLeast(1), h.coerceAtLeast(1)),
                                )
                                layout(constraints.maxWidth, h) {
                                    val x = pill.position * (cell + tabSpacingPx) + cell / 2f
                                    val cx = if (isRtl) w - x else x
                                    placeable.place((cx - cell / 2f).roundToInt(), 0)
                                }
                            }
                            .graphicsLayer {
                                val pop = pillPop.value
                                alpha = (1f - morph.tabFade) * pop.coerceIn(0f, 1f)
                                val s = 0.3f + 0.7f * pop
                                scaleX = s
                                scaleY = s
                            },
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(sizes.tabSpacing),
                    // Tabs keep their own size while the surface morphs; centred,
                    // they ride its middle up instead of hanging off its top.
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { rowSize = it }
                        .onGloballyPositioned { rowCoords.coords = it }
                        .layerBackdrop(tabRow)
                        .then(if (collapsed) Modifier else Modifier.pointerInput(tabCount, tabStepPx, isRtl) {
                            if (tabCount <= 0 || tabStepPx <= 0f) return@pointerInput

                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val downX = down.position.x
                                val touchedTab = if (isRtl) {
                                    ((rowSize.width - downX - tabWidthPx / 2f) / tabStepPx).roundToInt()
                                } else {
                                    ((downX - tabWidthPx / 2f) / tabStepPx).roundToInt()
                                }.coerceIn(0, scope.tabs.lastIndex)

                                var activeTab = touchedTab
                                val tabChanged = activeTab != currentSelectedTabIndex
                                if (tabChanged) {
                                    haptics.play(Haptic.Select)
                                    scope.tabs[activeTab].onClick()
                                } else {
                                    haptics.play(Haptic.Tick)
                                }

                                if (currentSelectedTabIndex < 0) {
                                    pill.snapTo(activeTab)
                                } else {
                                    pill.animateTo(activeTab)
                                }

                                val pointerId = down.id
                                val touchSlop = viewConfiguration.touchSlop
                                var isHolding = false

                                while (true) {
                                    val event = withTimeoutOrNull(180L) {
                                        awaitPointerEvent()
                                    }

                                    if (event == null) {
                                        if (!isHolding) {
                                            isHolding = true
                                            pill.startDrag()
                                            val currentDragPos = if (isRtl) {
                                                ((rowSize.width - downX - tabWidthPx / 2f) / tabStepPx)
                                            } else {
                                                ((downX - tabWidthPx / 2f) / tabStepPx)
                                            }.coerceIn(0f, scope.tabs.lastIndex.toFloat())
                                            pill.dragTo(currentDragPos)
                                        }
                                        continue
                                    }

                                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break

                                    if (!change.pressed) {
                                        if (isHolding) {
                                            pill.release(activeTab)
                                            if (activeTab != currentSelectedTabIndex) {
                                                scope.tabs[activeTab].onClick()
                                            }
                                        } else if (!tabChanged) {
                                            // A plain tap on the tab that is already
                                            // selected still goes to the app: it closes
                                            // whatever is open over the tab (settings,
                                            // the equalizer) and takes it back to its top.
                                            // Sent on release, so starting to drag the
                                            // pill never triggers it.
                                            scope.tabs[activeTab].onClick()
                                            if (!reduceAnimation) {
                                                coroutineScope.launch {
                                                    reselectPress.animateTo(0.86f, androidx.compose.animation.core.tween(90))
                                                    reselectPress.animateTo(1f, selectionSpec)
                                                }
                                            }
                                        }
                                        break
                                    }

                                    val currentX = change.position.x
                                    val deltaX = currentX - downX

                                    if (!isHolding && abs(deltaX) > touchSlop) {
                                        isHolding = true
                                        pill.startDrag()
                                    }

                                    if (isHolding) {
                                        val currentDragPos = if (isRtl) {
                                            ((rowSize.width - currentX - tabWidthPx / 2f) / tabStepPx)
                                        } else {
                                            ((currentX - tabWidthPx / 2f) / tabStepPx)
                                        }.coerceIn(0f, scope.tabs.lastIndex.toFloat())

                                        pill.dragTo(currentDragPos)

                                        val approximateTab = currentDragPos.roundToInt().coerceIn(0, scope.tabs.lastIndex)
                                        if (approximateTab != activeTab) {
                                            activeTab = approximateTab
                                            haptics.play(Haptic.Tick)
                                        }
                                        change.consume()
                                    }
                                }
                            }
                        })
                ) {
                    scope.tabs.forEachIndexed { tabIndex, tab ->
                        val isSelected = tab.key == selectedTabKey
                        // The tab that stays as the inline circle: it travels to
                        // the circle's centre while the others fade out around it.
                        val isInlineTab = tab.key == inlineTab?.key
                        val iconScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.08f else 1f,
                            animationSpec = selectionSpec,
                            label = "glassTabScale"
                        )
                        Tab(
                            icon = {
                                Box(
                                    modifier = Modifier.graphicsLayer {
                                        val pop = if (isSelected) reselectPress.value else 1f
                                        // The selected tab's slight enlargement goes
                                        // as it becomes the inline circle's icon.
                                        val s = lerp(iconScale, 1f, morph.tabs) * pop
                                        scaleX = s
                                        scaleY = s
                                        if (!isInlineTab) alpha = 1f - morph.tabFade
                                        val blur = if (isInlineTab) {
                                            morph.squeeze * blurMaxPx * 0.5f
                                        } else {
                                            maxOf(morph.squeeze, sinHump(morph.tabFade)) * blurMaxPx
                                        }
                                        // Null at rest: a layer holding any render
                                        // effect is drawn offscreen for as long as it does.
                                        renderEffect = if (blur > 0.05f) BlurEffect(blur, blur) else null
                                    }
                                ) {
                                    val hasArtwork = selectedTabIndex >= 0 && colors.isArtworkTheme && colors.artworkColor != Color.Unspecified

                                    // Normal colour outside the pill, accent inside — split
                                    // along the pill's real capsule outline (see [pillSplit]).
                                    Box(
                                        modifier = Modifier.pillSplit(
                                            enabled = hasArtwork,
                                            inside = false,
                                            row = rowCoords,
                                            pillRect = pillRectInRow,
                                                    fallbackInside = isSelected,
                                        )
                                    ) {
                                        CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides colors.unselectedContentColor) {
                                            tab.icon()
                                        }
                                    }

                                    if (hasArtwork) {
                                        Box(
                                            modifier = Modifier
                                                .matchParentSize()
                                                .pillSplit(
                                                    enabled = true,
                                                    inside = true,
                                                    row = rowCoords,
                                                    pillRect = pillRectInRow,
                                                    fallbackInside = isSelected,
                                                )
                                        ) {
                                            CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides colors.artworkColor) {
                                                tab.icon()
                                            }
                                        }
                                    }

                                    // The inline circle shows the icon in a single colour
                                    // rather than split along a pill it has left; it
                                    // takes over from the split as the tab folds in.
                                    if (isInlineTab) {
                                        Box(
                                            Modifier
                                                .matchParentSize()
                                                .graphicsLayer { alpha = morph.tabs }
                                        ) {
                                            CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides inlineColor) {
                                                tab.icon()
                                            }
                                        }
                                    }
                                }
                            },
                            title = {
                                Box(
                                    Modifier
                                        .onSizeChanged { labelHeights[tabIndex] = it.height.toFloat() }
                                        .graphicsLayer {
                                            alpha = 1f - morph.labelFade
                                            val blur = maxOf(morph.squeeze, sinHump(morph.labelFade)) * blurMaxPx
                                            renderEffect = if (blur > 0.05f) BlurEffect(blur, blur) else null
                                        }
                                ) {
                                    val hasArtwork = selectedTabIndex >= 0 && colors.isArtworkTheme && colors.artworkColor != Color.Unspecified

                                    Box(
                                        modifier = Modifier.pillSplit(
                                            enabled = hasArtwork,
                                            inside = false,
                                            row = rowCoords,
                                            pillRect = pillRectInRow,
                                                    fallbackInside = isSelected,
                                        )
                                    ) {
                                        CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides colors.unselectedContentColor) {
                                            tab.title()
                                        }
                                    }

                                    if (hasArtwork) {
                                        Box(
                                            modifier = Modifier
                                                .matchParentSize()
                                                .pillSplit(
                                                    enabled = true,
                                                    inside = true,
                                                    row = rowCoords,
                                                    pillRect = pillRectInRow,
                                                    fallbackInside = isSelected,
                                                )
                                        ) {
                                            CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides colors.artworkColor) {
                                                tab.title()
                                            }
                                        }
                                    }
                                }
                            },
                            isInline = false,
                            modifier = Modifier
                                .weight(1f)
                                .graphicsLayer {
                                    // Each tab heads for the inline circle's centre;
                                    // the one that stays ends up exactly there, with
                                    // its icon centred once the label below it is gone.
                                    val t = morph.tabs
                                    if (t > 0f && tabWidthPx > 0f) {
                                        val cellCenter = tabIndex * tabStepPx + tabWidthPx / 2f
                                        val target = geometry.inlineSize / 2f - padLeftPx
                                        val dx = target - cellCenter
                                        translationX = t * (if (isRtl) -dx else dx)
                                        translationY = t * labelHeights[tabIndex] / 2f
                                    } else {
                                        translationX = 0f
                                        translationY = 0f
                                    }
                                }
                                .clip(shapes.tabShape)
                                .padding(sizes.tabExpandedContentPadding)
                        )
                    }
                }
              }
            }
        }

        if (glassPill && showPill && !pill.isFlat) {
            GlassSelectionPill(
                motion = pill,
                fillColor = colors.indicatorColor,
                barGlass = barGlass,
                tabRow = tabRow,
                modifier = Modifier
                    .matchParentSize()
                    .pillPlacement(
                        center = { pillCenterInRow(pill.position) + Offset(padLeftPx, padTopPx) },
                        size = { pill.liveSize(restSize, liftedSize) },
                    ),
            )
        }
    }
}

@Composable
private fun Tab(
    icon: @Composable () -> Unit,
    title: @Composable () -> Unit,
    isInline: Boolean,
    modifier: Modifier = Modifier,
    isStandalone: Boolean = false
) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        icon()
        if (!isStandalone && !isInline) {
            title()
        }
    }
}


private class FloatingTabBarScopeImpl : FloatingTabBarScope {
    val tabs = mutableStateListOf<FloatingTabBarTab>()
    var standaloneTab: FloatingTabBarTab? by mutableStateOf(null)
        private set
    private var inlineTab: FloatingTabBarTab? = null

    fun getInlineTab(selectedTabKey: Any?): FloatingTabBarTab? {
        return if (selectedTabKey != standaloneTab?.key) {
            val selectedTab = tabs.find { it.key == selectedTabKey }
            if (selectedTab != null) {
                inlineTab = selectedTab
                selectedTab
            } else {
                inlineTab ?: tabs.firstOrNull()
            }
        } else {
            inlineTab ?: tabs.firstOrNull()
        }
    }

    override fun tab(
        key: Any,
        title: @Composable () -> Unit,
        icon: @Composable () -> Unit,
        onClick: () -> Unit,
        indication: (@Composable () -> Indication)?
    ) {
        tabs.add(
            FloatingTabBarTab(
                key = key,
                title = title,
                icon = icon,
                onClick = onClick,
                indication = indication
            )
        )
    }
    
    override fun standaloneTab(
        key: Any,
        icon: @Composable () -> Unit,
        onClick: () -> Unit,
        indication: (@Composable () -> Indication)?
    ) {
        standaloneTab = FloatingTabBarTab(
            key = key,
            title = {},
            icon = icon,
            onClick = onClick,
            indication = indication
        )
    }
}

private data class FloatingTabBarTab(
    val key: Any,
    val title: @Composable () -> Unit,
    val icon: @Composable () -> Unit,
    val onClick: () -> Unit,
    val indication: (@Composable () -> Indication)?
)

/**
 * Represents the colors used in [FloatingTabBar].
 */
@Immutable
data class FloatingTabBarColors(
    val backgroundColor: Color,
    val accessoryBackgroundColor: Color,
    val indicatorColor: Color,
    val selectedContentColor: Color = Color.Unspecified,
    val artworkColor: Color = Color.Unspecified,
    val unselectedContentColor: Color = Color.Unspecified,
    val isArtworkTheme: Boolean = false,
)

/**
 * Represents the shapes used in [FloatingTabBar].
 */
@Immutable
data class FloatingTabBarShapes(
    val tabBarShape: Shape,
    val tabShape: Shape,
    val standaloneTabShape: Shape,
    val accessoryShape: Shape,
)

/**
 * Represents the elevations used in [FloatingTabBar].
 */
@Immutable
data class FloatingTabBarElevations(
    val inlineElevation: Dp,
    val expandedElevation: Dp,
)

/**
 * Represents the sizes and spacing used in [FloatingTabBar].
 */
@Immutable
data class FloatingTabBarSizes(
    val tabBarContentPadding: PaddingValues,
    val tabInlineContentPadding: PaddingValues,
    val tabExpandedContentPadding: PaddingValues,
    val componentSpacing: Dp,
    val tabSpacing: Dp,
    /** The inline bar's height: the diameter of its two circles. */
    val inlineBarHeight: Dp = 48.dp,
)

/**
 * Contains the default values used by [FloatingTabBar].
 */
object FloatingTabBarDefaults {
    /**
     * Creates a [FloatingTabBarColors] that represents the default colors used in a [FloatingTabBar].
     *
     * @param backgroundColor the color used for the tab bar background
     * @param accessoryBackgroundColor the color used for the accessory background
     * @param indicatorColor the color used behind the selected tab in expanded state
     */
    @Composable
    fun colors(
        backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
        accessoryBackgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
        indicatorColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        selectedContentColor: Color = Color.Unspecified,
        artworkColor: Color = Color.Unspecified,
        unselectedContentColor: Color = Color.Unspecified,
        isArtworkTheme: Boolean = false,
    ): FloatingTabBarColors = FloatingTabBarColors(
        backgroundColor = backgroundColor,
        accessoryBackgroundColor = accessoryBackgroundColor,
        indicatorColor = indicatorColor,
        selectedContentColor = selectedContentColor,
        artworkColor = artworkColor,
        unselectedContentColor = unselectedContentColor,
        isArtworkTheme = isArtworkTheme,
    )

    /**
     * Creates a [FloatingTabBarShapes] that represents the default shapes used in a [FloatingTabBar].
     *
     * @param tabBarShape the shape used to clip the tab bar
     * @param tabShape the shape used to clip individual tabs. Can be useful for example to control the click ripple effect shape
     * @param standaloneTabShape the shape used to clip the standalone tab
     * @param accessoryShape the shape used to clip the accessory container
     */
    @Composable
    fun shapes(
        tabBarShape: Shape = RoundedCornerShape(100),
        tabShape: Shape = RoundedCornerShape(100),
        standaloneTabShape: Shape = CircleShape,
        accessoryShape: Shape = RoundedCornerShape(100),
    ): FloatingTabBarShapes = FloatingTabBarShapes(
        tabBarShape = tabBarShape,
        tabShape = tabShape,
        standaloneTabShape = standaloneTabShape,
        accessoryShape = accessoryShape,
    )

    /**
     * Creates a [FloatingTabBarSizes] that represents the default sizes used in a [FloatingTabBar].
     *
     * @param tabBarContentPadding the padding applied to the tab bar content. This also applies to the standalone tab content.
     * @param tabInlineContentPadding the padding applied to tabs in inline state
     * @param tabExpandedContentPadding the padding applied to tabs in expanded state
     * @param componentSpacing the spacing between components
     * @param tabSpacing the spacing between tabs in expanded state
     */
    @Composable
    fun sizes(
        tabBarContentPadding: PaddingValues = PaddingValues(vertical = 4.dp, horizontal = 4.dp),
        tabInlineContentPadding: PaddingValues = PaddingValues(10.dp),
        tabExpandedContentPadding: PaddingValues = PaddingValues(vertical = 6.dp, horizontal = 20.dp),
        componentSpacing: Dp = 8.dp,
        tabSpacing: Dp = 0.dp,
        inlineBarHeight: Dp = 48.dp,
    ): FloatingTabBarSizes = FloatingTabBarSizes(
        tabBarContentPadding = tabBarContentPadding,
        tabInlineContentPadding = tabInlineContentPadding,
        tabExpandedContentPadding = tabExpandedContentPadding,
        componentSpacing = componentSpacing,
        tabSpacing = tabSpacing,
        inlineBarHeight = inlineBarHeight,
    )

    /**
     * Creates a [FloatingTabBarElevations] that represents the default elevations used in a [FloatingTabBar].
     *
     * @param inlineElevation the elevation used for tabs in inline state
     * @param expandedElevation the elevation used for tabs in expanded state
     */
    @Composable
    fun elevations(
        inlineElevation: Dp = 6.dp,
        expandedElevation: Dp = 12.dp,
    ): FloatingTabBarElevations = FloatingTabBarElevations(
        inlineElevation = inlineElevation,
        expandedElevation = expandedElevation,
    )
}

/** Holds a node's latest [LayoutCoordinates] for draw-time maths, without recomposing. */
private class CoordsHolder {
    var coords: LayoutCoordinates? = null
}

/**
 * Draws this element only inside ([inside] = true) or only outside the
 * selection pill — clipped to the pill's real capsule outline.
 *
 * The split used to be a rectangle from the pill's left edge to its right
 * edge, the full height of the element. The pill's ends are round, so
 * everything above and below those curves still fell "inside" and changed
 * colour before the pill actually reached it. Here the pill rectangle is
 * mapped into this element's own coordinates (through any scale on the way)
 * and clipped as a rounded rect with radius half its shorter side.
 *
 * When [enabled] is false the element draws normally (no split).
 */
@Composable
private fun Modifier.pillSplit(
    enabled: Boolean,
    inside: Boolean,
    row: CoordsHolder,
    pillRect: () -> Rect,
    /**
     * Which half draws while the pill's place is not known yet (the first
     * frames of a freshly expanded bar, before the row has a size): the
     * selected tab stays in its accent colour rather than flashing white.
     */
    fallbackInside: Boolean = false,
): Modifier {
    val mine = remember { CoordsHolder() }
    if (!enabled) return this
    return this
        .onGloballyPositioned { mine.coords = it }
        .drawWithContent {
            val rowCoords = row.coords
            val myCoords = mine.coords
            val rect = pillRect()
            if (rowCoords == null || myCoords == null || !rowCoords.isAttached || !myCoords.isAttached ||
                rect.width <= 0f || rect.height <= 0f
            ) {
                if (inside == fallbackInside) drawContent()
                return@drawWithContent
            }
            val topLeft = myCoords.localPositionOf(rowCoords, rect.topLeft)
            val bottomRight = myCoords.localPositionOf(rowCoords, rect.bottomRight)
            val local = Rect(topLeft, bottomRight)
            val radius = minOf(local.width, local.height) / 2f
            val capsule = androidx.compose.ui.graphics.Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        local,
                        androidx.compose.ui.geometry.CornerRadius(radius, radius),
                    ),
                )
            }
            clipPath(
                capsule,
                clipOp = if (inside) {
                    androidx.compose.ui.graphics.ClipOp.Intersect
                } else {
                    androidx.compose.ui.graphics.ClipOp.Difference
                },
            ) {
                this@drawWithContent.drawContent()
            }
        }
}


/** How strongly the tabs blur as they fade in or out with a fold, at most. */
private val TAB_FOLD_BLUR = 3.dp

/** 0 at both ends, 1 in the middle: a blur that is only there mid-fade. */
private fun sinHump(t: Float): Float = kotlin.math.sin(PI_F * t.coerceIn(0f, 1f))

private const val PI_F = 3.1415927f

/**
 * Lays the tab group's contents out at the group's expanded width whatever
 * the surface around them is doing, so the tabs do not reflow into the
 * shrinking surface; each moves on its own instead (see [TabGroup]). They are
 * centred in the surface's height, and whatever is past its edge is cut off by
 * the surface's clip.
 */
private fun Modifier.expandedContentFrame(geometry: BarGeometry): Modifier = layout { measurable, constraints ->
    val width = geometry.groupContentWidth.takeIf { it > 0 }
        ?: if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
    val placeable = measurable.measure(Constraints(minWidth = width, maxWidth = width))
    val reportedWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else placeable.width
    val reportedHeight = if (constraints.hasBoundedHeight) constraints.maxHeight else placeable.height
    layout(reportedWidth, reportedHeight) {
        placeable.placeRelative(0, (reportedHeight - placeable.height) / 2)
    }
}
