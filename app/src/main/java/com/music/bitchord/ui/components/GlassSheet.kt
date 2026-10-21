package com.music.bitchord.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Floating Liquid Glass sheets and dialogs.
 *
 * Material's ModalBottomSheet and AlertDialog open in a window of their own,
 * and glass can only refract the page recorded in the app's window — so these
 * are drawn by a [GlassSheetHost] laid over the screen instead, the way the ⋮
 * menus are ([GlassMenuHost]). The content inside stays Material 3 Expressive;
 * only the surface is glass.
 */
enum class GlassSheetKind {
    /** Rises from the bottom, inset from the edges; drag down to close. */
    Sheet,
    /** Centred card (forms, confirmations); stays above the keyboard. */
    Dialog,
}

internal class GlassSheetRequest(
    val id: Long,
    val kind: GlassSheetKind,
    val onDismiss: () -> Unit,
    val content: @Composable () -> Unit,
    /** False for a dialog that must be answered (no scrim tap, no back). */
    val dismissible: Boolean = true,
) {
    var open by mutableStateOf(true)
}

@Stable
class GlassSheetController {
    internal val requests = mutableStateListOf<GlassSheetRequest>()
    private var nextId = 0L

    /** The window the host draws in; a caller in another window cannot use it. */
    internal var hostView: android.view.View? = null

    /** Opens [content] over everything else; returns a token for [dismiss]. */
    fun show(
        kind: GlassSheetKind,
        onDismiss: () -> Unit,
        dismissible: Boolean = true,
        content: @Composable () -> Unit,
    ): Long {
        val id = ++nextId
        requests += GlassSheetRequest(id, kind, onDismiss, content, dismissible)
        return id
    }

    /** Closes the sheet [token] opened (it animates away, then is dropped). */
    fun dismiss(token: Long) {
        val request = requests.firstOrNull { it.id == token && it.open } ?: return
        request.open = false
        request.onDismiss()
    }

    /**
     * Closes the sheet [token] opened without telling its owner: for when the
     * owner itself took it away (a sheet left composition because the screen
     * moved on). Only the user closing it is a dismissal.
     */
    fun close(token: Long) {
        val request = requests.firstOrNull { it.id == token && it.open } ?: return
        request.open = false
    }

    internal fun remove(request: GlassSheetRequest) {
        requests.remove(request)
    }
}

/** The screen's sheet host, or null where there is none (callers then use Material's). */
val LocalGlassSheets = staticCompositionLocalOf<GlassSheetController?> { null }

/** Closes the sheet the composable is inside of. */
val LocalGlassSheetDismiss = staticCompositionLocalOf<() -> Unit> { {} }

private val SHEET_SHAPE = RoundedCornerShape(32.dp)
private val DIALOG_SHAPE = RoundedCornerShape(28.dp)

/**
 * Shows [content] as a glass sheet for as long as this is composed — the
 * glass counterpart of placing a ModalBottomSheet. [onDismiss] runs when the
 * user closes it (scrim tap, drag down, back).
 */
@Composable
fun GlassSheet(
    controller: GlassSheetController,
    kind: GlassSheetKind,
    onDismiss: () -> Unit,
    dismissible: Boolean = true,
    content: @Composable () -> Unit,
) {
    val currentDismiss by rememberUpdatedState(onDismiss)
    val currentContent by rememberUpdatedState(content)
    var token by remember { mutableLongStateOf(-1L) }
    LaunchedEffect(controller) {
        token = controller.show(kind, onDismiss = { currentDismiss() }, dismissible = dismissible) { currentContent() }
    }
    DisposableEffect(controller) {
        onDispose { if (token >= 0) controller.close(token) }
    }
}

/**
 * Draws the open sheets, newest on top. Put it in the screen's root Box,
 * under the menu host.
 */
@Composable
fun GlassSheetHost(controller: GlassSheetController, glassBackdrop: Boolean, modifier: Modifier = Modifier) {
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect { controller.hostView = view }
    Box(modifier.fillMaxSize()) {
        controller.requests.toList().forEach { request ->
            key(request.id) {
                GlassSheetLayer(request, controller, glassBackdrop)
            }
        }
    }
}

@Composable
private fun GlassSheetLayer(request: GlassSheetRequest, controller: GlassSheetController, glassBackdrop: Boolean) {
    val progress = remember { Animatable(0f) }
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var panelHeight by remember { mutableStateOf(0) }
    val close = { controller.dismiss(request.id) }

    LaunchedEffect(request.open) {
        if (request.open) {
            progress.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow))
        } else {
            progress.animateTo(0f, tween(200))
            controller.remove(request)
        }
    }
    BackHandler(enabled = request.open && controller.requests.lastOrNull { it.open } === request) {
        if (request.dismissible) close()
    }

    Box(Modifier.fillMaxSize()) {
        // Scrim: dims the page and closes the sheet when tapped.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }
                .background(Color.Black.copy(alpha = 0.32f))
                .pointerInput(request.id) { detectTapGestures { if (request.open && request.dismissible) close() } },
        )
        val palette = glassPalette()
        val glassEnabled = LocalLiquidGlassEnabled.current && isGlassSupported()
        val shape = if (request.kind == GlassSheetKind.Sheet) SHEET_SHAPE else DIALOG_SHAPE
        val surface = if (glassBackdrop && glassEnabled) {
            Modifier.liquidGlass(shape = shape, surfaceAlpha = 0.78f, role = GlassRole.Overlay)
        } else {
            Modifier
                .background(palette.fallbackFill.copy(alpha = 0.98f), shape)
                .border(0.5.dp, palette.divider, shape)
        }
        CompositionLocalProvider(LocalGlassSheetDismiss provides close) {
        GlassContentTheme {
            when (request.kind) {
                GlassSheetKind.Sheet -> BoxWithConstraints(Modifier.fillMaxSize()) {
                    val maxPanel = maxHeight * 0.82f
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                            .widthIn(max = 560.dp)
                            .fillMaxWidth()
                            .heightIn(max = maxPanel)
                            .onSizeChanged { panelHeight = it.height }
                            .graphicsLayer {
                                val hidden = (1f - progress.value) * (panelHeight + 40.dp.toPx())
                                translationY = hidden + drag.value
                            }
                            .then(surface)
                            .pointerInput(request.id) {
                                if (!request.dismissible) return@pointerInput
                                detectVerticalDragGestures(
                                    onDragEnd = {
                                        val far = drag.value > with(density) { 120.dp.toPx() }
                                        if (far) close() else scope.launch { drag.animateTo(0f, spring()) }
                                    },
                                    onDragCancel = { scope.launch { drag.animateTo(0f, spring()) } },
                                    onVerticalDrag = { change, amount ->
                                        change.consume()
                                        scope.launch { drag.snapTo((drag.value + amount).coerceAtLeast(0f)) }
                                    },
                                )
                            },
                    ) {
                        Column {
                            // The grab handle.
                            Box(
                                Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .padding(top = 10.dp, bottom = 2.dp)
                                    .size(width = 36.dp, height = 4.dp)
                                    .background(palette.content.copy(alpha = 0.28f), RoundedCornerShape(50)),
                            )
                            request.content()
                        }
                    }
                }
                GlassSheetKind.Dialog -> Box(
                    Modifier
                        .fillMaxSize()
                        .imePadding()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .widthIn(max = 400.dp)
                            .fillMaxWidth()
                            .graphicsLayer {
                                val p = progress.value
                                alpha = p.coerceIn(0f, 1f)
                                val scale = 0.9f + 0.1f * p
                                scaleX = scale
                                scaleY = scale
                            }
                            .then(surface)
                            // Taps inside the card are not taps on the scrim.
                            .pointerInput(Unit) { detectTapGestures { } },
                    ) {
                        request.content()
                    }
                }
            }
        }
        }
    }
}
