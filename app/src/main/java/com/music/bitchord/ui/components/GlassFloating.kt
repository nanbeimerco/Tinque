package com.music.bitchord.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SheetState
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import com.music.bitchord.ui.components.backdrop.backdrops.layerBackdrop
import com.music.bitchord.ui.components.backdrop.backdrops.rememberLayerBackdrop

/*
 * Drop-in Liquid Glass versions of Material's floating surfaces —
 * [GlassModalBottomSheet], [GlassAlertDialog], [GlassDropdownMenu] — taking the
 * same parameters as the Material ones they replace.
 *
 * Where the caller's window has a glass host (the app's page, or a window
 * wrapped in [GlassWindowScope]), they float as glass and their contents are
 * drawn in the glass's neutral colours ([GlassContentTheme]). Anywhere else —
 * a window with no host — they are exactly the Material component, so nothing
 * ever opens behind another window where it could not be seen.
 */

/** This window's sheet host, or null if the host is in another window (or absent). */
@Composable
fun glassSheetsHere(): GlassSheetController? {
    val sheets = LocalGlassSheets.current ?: return null
    return sheets.takeIf { it.hostView == null || it.hostView === LocalView.current }
}

/** This window's menu host, or null if the host is in another window (or absent). */
@Composable
fun glassMenusHere(): GlassMenuController? {
    val menus = LocalGlassMenu.current ?: return null
    return menus.takeIf { it.hostView == null || it.hostView === LocalView.current }
}

/**
 * Material 3 inside, neutral like the glass outside: on glass the artwork
 * theme's colours would tint text and controls, so the scheme's colours are
 * swapped for [GlassPalette]'s white-or-black set. Shapes and type stay.
 */
@Composable
fun GlassContentTheme(content: @Composable () -> Unit) {
    val palette = glassPalette()
    val base = MaterialTheme.colorScheme
    val fg = palette.content
    val onFg = if (fg == Color.White) Color.Black else Color.White
    val wash = fg.copy(alpha = 0.08f)
    val scheme = base.copy(
        primary = fg,
        onPrimary = onFg,
        primaryContainer = fg.copy(alpha = 0.14f),
        onPrimaryContainer = fg,
        secondary = fg,
        onSecondary = onFg,
        secondaryContainer = fg.copy(alpha = 0.14f),
        onSecondaryContainer = fg,
        tertiary = fg,
        onTertiary = onFg,
        tertiaryContainer = fg.copy(alpha = 0.14f),
        onTertiaryContainer = fg,
        background = Color.Transparent,
        onBackground = fg,
        surface = Color.Transparent,
        onSurface = fg,
        surfaceVariant = wash,
        onSurfaceVariant = palette.secondary,
        surfaceTint = Color.Transparent,
        surfaceContainerLowest = wash,
        surfaceContainerLow = wash,
        surfaceContainer = wash,
        surfaceContainerHigh = fg.copy(alpha = 0.10f),
        surfaceContainerHighest = fg.copy(alpha = 0.12f),
        inverseSurface = fg,
        inverseOnSurface = onFg,
        outline = fg.copy(alpha = 0.32f),
        outlineVariant = palette.divider,
        error = palette.destructive,
        onError = Color.White,
    )
    MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
        CompositionLocalProvider(LocalContentColor provides fg, content = content)
    }
}

/** [ModalBottomSheet], floating as glass where it can. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = 0.dp,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.windowInsets },
    properties: ModalBottomSheetProperties = ModalBottomSheetProperties(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheets = glassSheetsHere()
    if (sheets != null) {
        GlassSheet(sheets, GlassSheetKind.Sheet, onDismiss = onDismissRequest) {
            Column(modifier) { content() }
        }
        return
    }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        sheetMaxWidth = sheetMaxWidth,
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        scrimColor = scrimColor,
        dragHandle = dragHandle,
        contentWindowInsets = contentWindowInsets,
        properties = properties,
        content = content,
    )
}

/** [AlertDialog], as a centred glass card where it can. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
) {
    val sheets = glassSheetsHere()
    if (sheets != null) {
        val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.8f).dp
        GlassSheet(
            sheets,
            GlassSheetKind.Dialog,
            onDismiss = onDismissRequest,
            dismissible = properties.dismissOnClickOutside || properties.dismissOnBackPress,
        ) {
            val palette = glassPalette()
            Column(
                modifier = modifier
                    .heightIn(max = maxHeight)
                    .padding(24.dp),
                horizontalAlignment = if (icon != null) Alignment.CenterHorizontally else Alignment.Start,
            ) {
                if (icon != null) {
                    CompositionLocalProvider(LocalContentColor provides palette.secondary) { icon() }
                    Spacer(Modifier.height(16.dp))
                }
                if (title != null) {
                    CompositionLocalProvider(LocalContentColor provides palette.content) {
                        ProvideTextStyle(MaterialTheme.typography.headlineSmall, title)
                    }
                    Spacer(Modifier.height(16.dp))
                }
                if (text != null) {
                    Box(Modifier.weight(1f, fill = false).align(Alignment.Start)) {
                        CompositionLocalProvider(LocalContentColor provides palette.secondary) {
                            ProvideTextStyle(MaterialTheme.typography.bodyMedium, text)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
        return
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        containerColor = containerColor,
        iconContentColor = iconContentColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
        tonalElevation = tonalElevation,
        properties = properties,
    )
}

/** [DropdownMenu], as a glass menu by its anchor where it can. */
@Composable
fun GlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    scrollState: ScrollState = rememberScrollState(),
    properties: PopupProperties = PopupProperties(focusable = true),
    shape: Shape = MenuDefaults.shape,
    containerColor: Color = MenuDefaults.containerColor,
    tonalElevation: Dp = MenuDefaults.TonalElevation,
    shadowElevation: Dp = MenuDefaults.ShadowElevation,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val menus = glassMenusHere()
    if (menus != null) {
        var anchor by remember { mutableStateOf(Rect.Zero) }
        var token by remember { mutableLongStateOf(-1L) }
        val dismiss by rememberUpdatedState(onDismissRequest)
        val currentContent by rememberUpdatedState(content)
        // Zero-size marker: its parent is the anchor, as for Material's menu.
        Spacer(
            Modifier.onGloballyPositioned { coords ->
                anchor = coords.parentLayoutCoordinates?.boundsInRoot() ?: coords.boundsInRoot()
            },
        )
        LaunchedEffect(expanded) {
            if (expanded) {
                token = menus.show(anchor, onDismiss = { dismiss() }) {
                    Column(modifier) { currentContent() }
                }
            } else if (token >= 0) {
                menus.close(token)
            }
        }
        DisposableEffect(menus) {
            onDispose { if (token >= 0) menus.close(token) }
        }
        return
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        scrollState = scrollState,
        properties = properties,
        shape = shape,
        containerColor = containerColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        border = border,
        content = content,
    )
}

/**
 * Gives a window of its own (a full-screen Dialog: the tag editor, the lyrics
 * editor, the equalizer) its own glass: a recorded backdrop of its content and
 * sheet and menu hosts over it, so the Glass* surfaces opened inside it float
 * as glass there too. The content is only recorded while one is open.
 */
@Composable
fun GlassWindowScope(
    modifier: Modifier = Modifier,
    /** The menu host's controller, when the caller opens menus here from outside. */
    menus: GlassMenuController = remember { GlassMenuController() },
    content: @Composable () -> Unit,
) {
    val sheets = remember { GlassSheetController() }
    val windowBackground = MaterialTheme.colorScheme.background
    val paint: ContentDrawScope.() -> Unit = remember(windowBackground) {
        {
            drawRect(windowBackground)
            drawContent()
        }
    }
    val backdrop = rememberLayerBackdrop(onDraw = paint)
    val active = sheets.requests.isNotEmpty() || menus.request != null
    val glassOn = LocalLiquidGlassEnabled.current && isGlassSupported()
    CompositionLocalProvider(
        LocalAppBackdrop provides backdrop,
        LocalGlassMenu provides menus,
        LocalGlassSheets provides sheets,
    ) {
        Box(modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (active && glassOn) Modifier.layerBackdrop(backdrop) else Modifier),
            ) { content() }
            GlassSheetHost(sheets, glassBackdrop = active && glassOn)
            GlassMenuHost(menus, glassBackdrop = active && glassOn)
        }
    }
}

/**
 * Call inside a full-screen [androidx.compose.ui.window.Dialog]: turns off the
 * window's dim-behind.
 *
 * A platform dialog darkens what is under it by default. A full-screen page
 * covers that anyway, so the dim was only ever seen in the gaps: the moment
 * the window appears before the page fades in, and the moment the page has
 * faded out before the window goes. Both read as a dark flash.
 */
@Composable
fun NoDialogDim() {
    val window = (LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
    // Synchronously in composition, so the very first frame is already undimmed.
    remember(window) {
        window?.setDimAmount(0f)
        window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        Unit
    }
}
