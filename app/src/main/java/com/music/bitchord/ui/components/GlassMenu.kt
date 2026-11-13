package com.music.bitchord.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import com.music.bitchord.ui.haptics.Haptic
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.music.bitchord.ui.haptics.rememberHaptics

/**
 * A menu drawn as Liquid Glass, opened from a button (the rows' ⋮).
 *
 * It is not a [androidx.compose.ui.window.Popup]: a popup is its own window,
 * and the glass can only refract the app's recorded page inside the app's own
 * window. So the menu is drawn by a [GlassMenuHost] laid over the whole screen,
 * and whatever wants a menu asks the [LocalGlassMenu] controller for one,
 * passing where its button is.
 */
@Stable
class GlassMenuController {
    internal var request by mutableStateOf<GlassMenuRequest?>(null)
        private set
    private var nextId = 0L

    /**
     * Whether a menu is on screen, closing ones included: true from the open
     * until the close animation has finished. A window that records its page
     * for the glass only while a menu is up keeps recording until then, or
     * the closing menu would lose its glass and flash as an opaque fill.
     */
    var visible by mutableStateOf(false)
        internal set

    /** The open/close clock, in [PopupMotion] milliseconds. */
    internal val clock = Animatable(0f)

    /** The button the open menu came from (null for a wide strip), for [glassMenuAnchor]. */
    internal var motionAnchor by mutableStateOf<Rect?>(null)

    /** From that button toward the menu's centre, unit length. */
    internal var motionDirection by mutableStateOf(Offset.Zero)

    /** Reduced motion: the menu only fades. */
    internal var motionReduced by mutableStateOf(false)

    /** The window the host draws in; a caller in another window cannot use it. */
    internal var hostView: android.view.View? = null

    /**
     * Opens [content] by [anchor] (root coordinates); returns a token for [dismiss].
     * [wide]: the full width of the screen (less its margins) and above the
     * anchor rather than below it — a strip of controls over a bar, not a menu.
     */
    fun show(anchor: Rect, onDismiss: () -> Unit, wide: Boolean = false, content: @Composable () -> Unit): Long {
        request?.onDismiss?.invoke()
        val id = ++nextId
        request = GlassMenuRequest(id, anchor, onDismiss, content, wide)
        return id
    }

    /** Closes the menu [token] opened without telling its owner (the owner closed it). */
    fun close(token: Long) {
        if (request?.id == token) request = null
    }

    /** Closes the menu: any menu, or only the one [token] opened. */
    fun dismiss(token: Long? = null) {
        val current = request ?: return
        if (token != null && current.id != token) return
        request = null
        current.onDismiss()
    }
}

internal class GlassMenuRequest(
    val id: Long,
    val anchor: Rect,
    val onDismiss: () -> Unit,
    val content: @Composable () -> Unit,
    val wide: Boolean = false,
)

/** The screen's menu host, or null where there is none (menus then fall back to M3). */
val LocalGlassMenu = staticCompositionLocalOf<GlassMenuController?> { null }

/** Closes the menu the composable is inside of. */
val LocalGlassMenuDismiss = staticCompositionLocalOf<() -> Unit> { {} }

private val MENU_SHAPE = RoundedCornerShape(24.dp)
private val MENU_MARGIN = 12.dp
private val MENU_GAP = 6.dp
private val MENU_MAX_WIDTH = 288.dp
private val MENU_MIN_WIDTH = 200.dp

/**
 * Draws the open menu, if any. Put it last in the screen's root Box, so it sits
 * over the page, the bars and the floating controls.
 *
 * @param glassBackdrop whether the page is being recorded, so the full
 * refracting glass can be drawn; otherwise a near-opaque frosted fill is used.
 */
@Composable
fun GlassMenuHost(controller: GlassMenuController, glassBackdrop: Boolean, modifier: Modifier = Modifier) {
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect { controller.hostView = view }
    val request = controller.request
    var shown by remember { mutableStateOf<GlassMenuRequest?>(null) }
    val reduced by com.music.bitchord.data.settings.AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    androidx.compose.runtime.SideEffect { controller.motionReduced = reduced }
    val clock = controller.clock
    var hostOrigin by remember { mutableStateOf(Offset.Zero) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val stretch = remember(density) { MenuStretch(scope, with(density) { PopupMotion.STRETCH.toPx() }) }
    val safeDrawing = WindowInsets.safeDrawing
    val minTopPx = with(density) { topInset.roundToPx() }
    val safeTopInset = safeDrawing.getTop(density)
    val safeBottomInset = safeDrawing.getBottom(density)

    // One clock drives the whole open (see PopupMotion); a close plays it back.
    LaunchedEffect(request) {
        if (request != null) {
            if (shown?.id != request.id) {
                clock.snapTo(0f)
                stretch.reset()
            }
            shown = request
            controller.visible = true
            controller.motionAnchor = if (request.wide) null else request.anchor
            val left = (PopupMotion.END - clock.value).coerceAtLeast(1f)
            clock.animateTo(PopupMotion.END, tween(left.toInt().coerceAtLeast(1), easing = LinearEasing))
        } else if (shown != null) {
            val from = minOf(clock.value, PopupMotion.CLOSE_FROM)
            clock.snapTo(from)
            clock.animateTo(0f, tween(from.toInt().coerceAtLeast(1), easing = LinearEasing))
            shown = null
            controller.motionAnchor = null
            controller.visible = false
        }
    }
    BackHandler(enabled = request != null) { controller.dismiss() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { hostOrigin = it.positionInRoot() },
    ) {
        val menu = shown ?: return@Box
        // Catches the tap outside that closes the menu.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(menu.id) { detectTapGestures { controller.dismiss() } },
        )
        val anchor = menu.anchor.translate(-hostOrigin)
        val frame = remember(menu.id) { MenuFrame() }
        var menuSize by remember(menu.id) { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
        val shape = remember(frame) { RoundedCornerShape(LiveCornerSize { frame.radius }) }
        val lens = remember(menu.id) { MenuLens { PopupMotion.frame(clock.value, controller.motionReduced).warp } }
        Box(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val margin = MENU_MARGIN.roundToPx()
                    val gap = MENU_GAP.roundToPx()
                    val topBound = maxOf(minTopPx, safeTopInset + margin)
                    val bottomBound = (constraints.maxHeight - maxOf(margin, safeBottomInset + margin)).coerceAtLeast(topBound)
                    val maxW = (constraints.maxWidth - margin * 2).coerceAtLeast(0)
                    frame.minWidth = if (menu.wide) maxW else MENU_MIN_WIDTH.roundToPx().coerceAtMost(maxW)
                    frame.maxWidth = if (menu.wide) maxW else MENU_MAX_WIDTH.roundToPx().coerceAtMost(maxW)
                    frame.maxHeight = (bottomBound - topBound).coerceAtLeast(0)
                    // The full menu's size, once its rows have been measured.
                    val w = if (menuSize.width > 0) menuSize.width else frame.minWidth
                    val h = menuSize.height.coerceAtLeast(1)
                    // Right edge on the button's right edge. Vertically, smoothly pivot from
                    // downward at top anchors -> balanced centered mid-screen -> upward below.
                    val x = if (menu.wide) margin else (anchor.right.toInt() - w).coerceIn(margin, (constraints.maxWidth - w - margin).coerceAtLeast(margin))
                    val y = GlassMenuPlacement.verticalPositionPx(
                        wide = menu.wide,
                        anchorTop = anchor.top.toInt(),
                        anchorBottom = anchor.bottom.toInt(),
                        anchorCenterY = anchor.center.y.toInt(),
                        menuHeight = h,
                        containerHeight = constraints.maxHeight,
                        topBound = topBound,
                        bottomBound = bottomBound,
                        gap = gap,
                        acrossLift = PopupMotion.ACROSS_LIFT.roundToPx(),
                    ).y

                    val f = PopupMotion.frame(clock.value, controller.motionReduced)
                    val start = PopupMotion.START_SIZE.toPx()
                    val stretchPx = stretch.offset.value
                    val winW = PopupMotion.lerp(start, w.toFloat(), f.width)
                    val winH = PopupMotion.lerp(start, h.toFloat(), f.height) + kotlin.math.abs(stretchPx) * 0.3f
                    // The window grows around its centre, which travels from the
                    // button's centre to the menu's.
                    val cx = PopupMotion.lerp(anchor.center.x - x, w / 2f, f.travel)
                    val cy = PopupMotion.lerp(anchor.center.y - y, h / 2f, f.travel) + stretchPx * 0.35f
                    frame.radius = PopupMotion.cornerPx(winW, winH, f.round, PopupMotion.CORNER.toPx())
                    val toCentre = Offset(x + w / 2f, y + h / 2f) - anchor.center
                    val length = toCentre.getDistance()
                    val direction = if (length > 0f) toCentre / length else Offset.Zero
                    if (controller.motionDirection != direction) controller.motionDirection = direction
                    val placeable = measurable.measure(
                        Constraints.fixed(winW.toInt().coerceAtLeast(1), winH.toInt().coerceAtLeast(1)),
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(x + (cx - winW / 2f).toInt(), y + (cy - winH / 2f).toInt())
                    }
                }
                .graphicsLayer {
                    alpha = PopupMotion.frame(clock.value, controller.motionReduced).glassAlpha
                },
        ) {
            Box(Modifier.glassMenuSurface(glassBackdrop, shape).clip(shape)) {
                CompositionLocalProvider(LocalGlassMenuDismiss provides { controller.dismiss(menu.id) }) {
                    Box(
                        Modifier
                            // The rows at the menu's full size, centred in the
                            // window and seen through it.
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(
                                    Constraints(
                                        minWidth = frame.minWidth,
                                        maxWidth = frame.maxWidth.coerceAtLeast(frame.minWidth),
                                        maxHeight = frame.maxHeight,
                                    ),
                                )
                                if (placeable.width != menuSize.width || placeable.height != menuSize.height) {
                                    menuSize = androidx.compose.ui.unit.IntSize(placeable.width, placeable.height)
                                }
                                layout(constraints.maxWidth, constraints.maxHeight) {
                                    placeable.place(
                                        (constraints.maxWidth - placeable.width) / 2,
                                        (constraints.maxHeight - placeable.height) / 2,
                                    )
                                }
                            }
                            .graphicsLayer {
                                val f = PopupMotion.frame(clock.value, controller.motionReduced)
                                alpha = f.contentAlpha
                                scaleX = f.contentScale
                                scaleY = f.contentScale
                                val blur = f.blur * PopupMotion.BLUR.toPx()
                                renderEffect = if (blur > 0.5f && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                    androidx.compose.ui.graphics.BlurEffect(blur, blur)
                                } else null
                            },
                    ) {
                        Column(
                            modifier = Modifier
                                .onGloballyPositioned {
                                    lens.top = it.positionInRoot().y
                                    lens.height = it.size.height.toFloat()
                                }
                                .heightIn(max = 520.dp)
                                .nestedScroll(stretch)
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 8.dp),
                        ) {
                            androidx.compose.runtime.key(menu.id) {
                                CompositionLocalProvider(LocalMenuLens provides lens) {
                                    GlassContentTheme { menu.content() }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Where the open menu is, written while laying it out and read while drawing it. */
private class MenuFrame {
    var minWidth = 0
    var maxWidth = 0
    var maxHeight = 0
    var radius = 0f
}

/**
 * The open menu's lens: how strong it is now, and where the menu's rows span
 * on screen, so each row can stretch by where it sits (the edge rows most).
 */
internal class MenuLens(val warp: () -> Float) {
    var top = 0f
    var height = 0f
}

internal val LocalMenuLens = staticCompositionLocalOf<MenuLens?> { null }

private val topInset = 24.dp

internal data class VerticalPlacementResult(
    val y: Int,
    val pivot: Float,
)

/**
 * Pure placement calculator for vertical popup anchoring and safe-area clamping.
 */
internal object GlassMenuPlacement {
    fun verticalPositionPx(
        wide: Boolean,
        anchorTop: Int,
        anchorBottom: Int,
        anchorCenterY: Int,
        menuHeight: Int,
        containerHeight: Int,
        topBound: Int,
        bottomBound: Int,
        gap: Int,
        acrossLift: Int,
    ): VerticalPlacementResult {
        val h = menuHeight.coerceAtLeast(1)
        val effectiveTop = topBound.coerceIn(0, containerHeight)
        val effectiveBottom = bottomBound.coerceIn(effectiveTop, containerHeight)
        val maxY = (effectiveBottom - h).coerceAtLeast(effectiveTop)
        if (wide) {
            val below = anchorBottom + gap
            val above = anchorTop - gap - h
            val fitsAbove = above >= effectiveTop
            return VerticalPlacementResult(
                y = (if (fitsAbove) above else below).coerceIn(effectiveTop, maxY),
                pivot = if (fitsAbove) 1f else 0f,
            )
        }
        val span = (effectiveBottom - effectiveTop).coerceAtLeast(1)
        val anchorFrac = ((anchorCenterY - effectiveTop).toFloat() / span.toFloat()).coerceIn(0f, 1f)
        val pivot = PopupMotion.smooth(0.14f, 0.76f, anchorFrac)
        val anchorInset = minOf(acrossLift, h / 2)
        val yTopAnchor = anchorCenterY - anchorInset
        val yBottomAnchor = anchorCenterY + anchorInset - h
        val rawY = PopupMotion.lerp(yTopAnchor.toFloat(), yBottomAnchor.toFloat(), pivot)
        val y = kotlin.math.round(rawY).toInt().coerceIn(effectiveTop, maxY)
        return VerticalPlacementResult(y = y, pivot = pivot)
    }
}

@Composable
private fun Modifier.glassMenuSurface(glassBackdrop: Boolean, shape: androidx.compose.foundation.shape.CornerBasedShape = MENU_SHAPE): Modifier {
    val glassEnabled = LocalLiquidGlassEnabled.current && isGlassSupported()
    return if (glassBackdrop && glassEnabled) {
        // Denser tint than the bars: a menu sits over text and has to stay readable.
        liquidGlass(shape = shape, surfaceAlpha = 0.72f)
    } else {
        // Neutral like the glass, never the artwork theme's container.
        val palette = glassPalette()
        this
            .background(palette.fallbackFill.copy(alpha = 0.97f), shape)
            .border(0.5.dp, palette.divider, shape)
    }
}

/** One row of a glass menu. Closes the menu after [onClick] unless [keepOpen]. */
@Composable
fun GlassMenuItem(
    text: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    destructive: Boolean = false,
    keepOpen: Boolean = false,
    emphasized: Boolean = false,
    /** Off: drawn dimmed, and a tap only calls [onDisabledClick] (to say why). */
    enabled: Boolean = true,
    onDisabledClick: (() -> Unit)? = null,
    /** A short value at the row's end (the sleep timer's time left). */
    value: String? = null,
) {
    val dismiss = LocalGlassMenuDismiss.current
    val haptics = rememberHaptics()
    val palette = glassPalette()
    val lens = LocalMenuLens.current
    var rowCentre by remember { mutableStateOf(Float.NaN) }
    val color = (if (destructive) palette.destructive else palette.content)
        .let { if (enabled) it else it.copy(alpha = 0.38f) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (lens != null) Modifier.menuLens(lens) { rowCentre }.onGloballyPositioned { rowCentre = it.positionInRoot().y + it.size.height / 2f } else Modifier)
            .padding(horizontal = 6.dp)
            .heightIn(min = 48.dp)
            .clip16()
            .clickable {
                if (!enabled) {
                    onDisabledClick?.invoke()
                    return@clickable
                }
                haptics.play(Haptic.Select)
                if (!keepOpen) dismiss()
                onClick()
            }
            .padding(horizontal = 14.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
        }
        Text(
            text = text,
            style = if (emphasized) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyLarge,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.secondary,
                maxLines = 1,
            )
        }
        if (trailingIcon != null) {
            Spacer(Modifier.width(8.dp))
            Icon(trailingIcon, contentDescription = null, tint = color.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
        }
    }
}

/** The thin break between groups of a glass menu (M3 Expressive menu groups). */
@Composable
fun GlassMenuDivider() {
    Box(
        Modifier
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(glassPalette().divider),
    )
}


private fun Modifier.clip16(): Modifier = clip(RoundedCornerShape(16.dp))

/**
 * The lens on one row while the menu opens: rows toward the top and bottom
 * edges are pulled outward along the edge and stretched, the outermost most.
 */
private fun Modifier.menuLens(lens: MenuLens, centre: () -> Float): Modifier = graphicsLayer {
    val w = lens.warp()
    val c = centre()
    if (w <= 0.001f || lens.height <= 0f || c.isNaN()) return@graphicsLayer
    val d = (((c - lens.top) / lens.height) * 2f - 1f).coerceIn(-1f, 1f)
    val edge = ((kotlin.math.abs(d) - 0.7f) / 0.3f).coerceIn(0f, 1f)
    translationY = d * w * 4.dp.toPx() + kotlin.math.sign(d) * edge * w * 2.dp.toPx()
    scaleY = 1f + kotlin.math.abs(d) * w * 0.08f + edge * w * 0.3f
    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, if (d < 0f) 1f else 0f)
}

/** Records where this composable is on screen, for [GlassMenuController.show]. */
fun Modifier.trackBoundsInRoot(onBounds: (Rect) -> Unit): Modifier =
    onGloballyPositioned { onBounds(it.boundsInRoot()) }

/** Monotonic token holder, so a menu only closes the request it opened. */
@Stable
class GlassMenuToken {
    var value by mutableLongStateOf(-1L)
}
