package com.music.bitchord.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.fillMaxSize
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
import com.music.bitchord.ui.haptics.Haptic
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

    /** The window the host draws in; a caller in another window cannot use it. */
    internal var hostView: android.view.View? = null

    /** Opens [content] by [anchor] (root coordinates); returns a token for [dismiss]. */
    fun show(anchor: Rect, onDismiss: () -> Unit, content: @Composable () -> Unit): Long {
        request?.onDismiss?.invoke()
        val id = ++nextId
        request = GlassMenuRequest(id, anchor, onDismiss, content)
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
    val progress = remember { Animatable(0f) }
    var hostOrigin by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(request) {
        if (request != null) {
            if (shown?.id != request.id) progress.snapTo(0f)
            shown = request
            progress.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow))
        } else if (shown != null) {
            progress.animateTo(0f, tween(130))
            shown = null
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
        var pivot by remember(menu.id) { mutableStateOf(TransformOrigin(1f, 0f)) }
        Box(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val margin = MENU_MARGIN.roundToPx()
                    val gap = MENU_GAP.roundToPx()
                    val top = topInset.roundToPx()
                    val maxW = (constraints.maxWidth - margin * 2).coerceAtLeast(0)
                    val placeable = measurable.measure(
                        Constraints(
                            minWidth = MENU_MIN_WIDTH.roundToPx().coerceAtMost(maxW),
                            maxWidth = MENU_MAX_WIDTH.roundToPx().coerceAtMost(maxW),
                            maxHeight = (constraints.maxHeight - top - margin * 2).coerceAtLeast(0),
                        ),
                    )
                    val w = placeable.width
                    val h = placeable.height
                    // Right edge on the button's right edge, below it if it fits, else above.
                    val x = (anchor.right.toInt() - w).coerceIn(margin, (constraints.maxWidth - w - margin).coerceAtLeast(margin))
                    val below = anchor.bottom.toInt() + gap
                    val fitsBelow = below + h <= constraints.maxHeight - margin
                    val y = (if (fitsBelow) below else anchor.top.toInt() - gap - h)
                        .coerceIn(top, (constraints.maxHeight - h - margin).coerceAtLeast(top))
                    pivot = TransformOrigin(
                        ((anchor.center.x - x) / w.coerceAtLeast(1)).coerceIn(0f, 1f),
                        ((anchor.center.y - y) / h.coerceAtLeast(1)).coerceIn(0f, 1f),
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(x, y) }
                }
                .graphicsLayer {
                    val p = progress.value
                    alpha = p.coerceIn(0f, 1f)
                    val scale = 0.82f + 0.18f * p
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = pivot
                },
        ) {
            Box(Modifier.glassMenuSurface(glassBackdrop)) {
                CompositionLocalProvider(LocalGlassMenuDismiss provides { controller.dismiss(menu.id) }) {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 520.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 8.dp),
                    ) { androidx.compose.runtime.key(menu.id) { GlassContentTheme { menu.content() } } }
                }
            }
        }
    }
}

private val topInset = 24.dp

@Composable
private fun Modifier.glassMenuSurface(glassBackdrop: Boolean): Modifier {
    val glassEnabled = LocalLiquidGlassEnabled.current && isGlassSupported()
    return if (glassBackdrop && glassEnabled) {
        // Denser tint than the bars: a menu sits over text and has to stay readable.
        liquidGlass(shape = MENU_SHAPE, surfaceAlpha = 0.72f)
    } else {
        // Neutral like the glass, never the artwork theme's container.
        val palette = glassPalette()
        this
            .background(palette.fallbackFill.copy(alpha = 0.97f), MENU_SHAPE)
            .border(0.5.dp, palette.divider, MENU_SHAPE)
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
    val color = (if (destructive) palette.destructive else palette.content)
        .let { if (enabled) it else it.copy(alpha = 0.38f) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
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

/** Records where this composable is on screen, for [GlassMenuController.show]. */
fun Modifier.trackBoundsInRoot(onBounds: (Rect) -> Unit): Modifier =
    onGloballyPositioned { onBounds(it.boundsInRoot()) }

/** Monotonic token holder, so a menu only closes the request it opened. */
@Stable
class GlassMenuToken {
    var value by mutableLongStateOf(-1L)
}
