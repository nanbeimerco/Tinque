package com.music.bitchord.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import com.music.bitchord.BuildConfig
import com.music.bitchord.R
import com.music.bitchord.data.model.Account
import com.music.bitchord.data.settings.AppSettings

/**
 * The bar's own height, above whatever inset it is sitting under.
 *
 * The single source of truth for it: the bar lays itself out to this, and
 * everything that has to clear the bar — page content padding, [TopFadeBlur]'s
 * ramp, fixed headers that sit directly beneath it — measures from here rather
 * than from a copy of the number.
 */
val TopBarContentHeight = 52.dp

/**
 * The breathing room between the bar's bottom edge and the first thing under
 * it, so content rests below the glass instead of against it.
 */
val TopBarContentGap = 12.dp

/**
 * How far down the window the bar actually ends: the status bar inset it is
 * pinned under, plus its own height.
 *
 * This has to be read at composition rather than baked in as a constant — the
 * inset is a property of the device and of the window, not of the app. A phone
 * with a cutout, one without, and a freeform window with no status bar at all
 * are all different numbers, and a fixed guess is wrong on all but one of them:
 * too tight and content is clipped under the bar, too loose and every page
 * opens on a band of empty space.
 */
@Composable
fun topBarHeight(): Dp = topSafeInset() + TopBarContentHeight

/**
 * How far below the top edge floating controls must start.
 *
 * The status bar inset alone is 0 while the bar is hidden (quiet mode, or
 * "show status bar" off), which pushed the buttons against the screen edge
 * and the camera hole. So it is the larger of the status bar and the display
 * cutout, and never less than [MinTopSafeInset].
 */
@Composable
fun topSafeInset(): Dp {
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val cutout = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
    return maxOf(statusBar, cutout, MinTopSafeInset)
}

/** The least room left above floating top controls, even with no status bar or cutout. */
val MinTopSafeInset = 16.dp

/**
 * Where page content should start: clear of the bar, plus [TopBarContentGap].
 */
@Composable
fun topBarContentPadding(): Dp = topBarHeight() + TopBarContentGap

/**
 * The top bar's content — title, back affordance, actions — over no backdrop
 * of its own.
 *
 * The glass behind it is [TopFadeBlur]'s, drawn underneath: a blur that starts
 * full at the status bar and ramps to nothing below, so the bar has no bottom
 * edge to draw a line across the page with. A uniform pane would put that line
 * back, which is the one thing every surface here is built to avoid.
 *
 * The exception is Reduce dynamic blur, where there is no fade to sit on and
 * the bar fills itself solid instead — title over raw scrolling content is
 * unreadable, so something has to carry it.
 *
 * Apple Music behaviour: the big in-list header owns the title at rest;
 * once the list scrolls, the small centered title fades in.
 */
@Composable
fun FrostedTopBar(
    title: String,
    scrolled: Boolean,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    refreshing: Boolean = false,
    // A lambda, not a value: the drag changes every frame, and reading it in
    // the caller would recompose the whole app on each one.
    pullFraction: () -> Float = { 0f },
    actions: @Composable () -> Unit = {},
    alwaysShowTitle: Boolean = true,
    artworkTint: Color? = null,
    /** Draws only floating navigation/actions over collection artwork. */
    floatingOnly: Boolean = false,
    /**
     * Whether the page under the bar is being recorded for the glass to
     * sample. Only then can the round buttons use the full refracting glass;
     * otherwise they fall back to the lightweight tint.
     */
    glassBackdrop: Boolean = false,
    /** The start button's glyph: back, or a close while picking tracks. */
    backIcon: ImageVector = Icons.AutoMirrored.Rounded.ArrowBack,
    backDescription: String? = null,
) {
    // Design rule: Liquid Glass only floats directly over moving content —
    // never inside a fixed band, where it would have nothing to refract. So
    // the bar paints no backdrop at all: the buttons and the title capsule are
    // each their own glass, floating over the page.
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = topSafeInset())
                .height(TopBarContentHeight),
        ) {
            CompositionLocalProvider(LocalTopBarGlassBackdrop provides glassBackdrop) {
            // Pages with a large in-page title show the capsule only once that
            // title has scrolled away; others show it all the time.
            androidx.compose.animation.AnimatedVisibility(
                visible = !floatingOnly && (alwaysShowTitle || scrolled),
                enter = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.92f),
                exit = fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.92f),
                modifier = Modifier
                    .align(Alignment.Center)
                    // Room for the back button and up to two actions.
                    .padding(horizontal = 112.dp),
            ) {
                TopBarTitleCapsule(title)
            }
            if (onBack != null) {
                TopBarGlassButton(
                    onClick = onBack,
                    icon = backIcon,
                    contentDescription = backDescription ?: stringResource(R.string.back),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 12.dp),
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                actions()
            }
            }
        }
        // The loader line sits under the controls; the box only grows to the
        // line's height while a refresh is actually showing.
        Box(Modifier.fillMaxWidth()) {
            RefreshLine(
                refreshing = refreshing,
                pullFraction = pullFraction,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** Set by [FrostedTopBar] for the buttons inside it — see its `glassBackdrop`. */
val LocalTopBarGlassBackdrop = staticCompositionLocalOf { false }

/**
 * The app logo for a tab's top-left corner, placed as the first item of the
 * tab's own list so it scrolls away with the content (unlike back / settings,
 * which float in place).
 *
 * It takes no height in the list: it draws itself up in the top bar's row
 * (the space above [listTopPadding]), so at rest it sits exactly where the
 * logo always was, beside the title capsule and the settings button.
 *
 * @param listTopPadding the list's top content padding — how far below the
 * list's top edge its first item starts.
 */
@Composable
fun TabLogoHeader(
    listTopPadding: Dp,
    modifier: Modifier = Modifier,
    /** The list's own start padding, undone so the logo lines up with every other tab's. */
    horizontalInset: Dp = 0.dp,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val rise = with(density) { (listTopPadding - topSafeInset()).roundToPx() }
    val shift = with(density) { horizontalInset.roundToPx() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minHeight = 0))
                // Zero height in the list; drawn up in the bar row.
                layout(constraints.maxWidth, 0) { placeable.place(-shift, -rise) }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(TopBarContentHeight),
            contentAlignment = Alignment.CenterStart,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                modifier = Modifier
                    .padding(start = 20.dp)
                    .height(18.dp),
            )
        }
    }
}

/** Diameter of the round glass behind each top-bar button. */
val TopBarGlassButtonSize = 42.dp

/** The glass behind floating top controls, full refraction when the page is being recorded. */
@Composable
private fun topBarGlass(shape: androidx.compose.foundation.shape.CornerBasedShape): Modifier {
    val glassEnabled = LocalLiquidGlassEnabled.current && isGlassSupported()
    return if (LocalTopBarGlassBackdrop.current && glassEnabled) {
        Modifier.liquidGlass(shape = shape)
    } else {
        Modifier.lightweightLiquidGlass(
            shape = shape,
            fallbackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

/** The page name in a small floating glass capsule, centred between the buttons. */
@Composable
fun TopBarTitleCapsule(title: String, modifier: Modifier = Modifier) {
    val glassEnabled = LocalLiquidGlassEnabled.current && isGlassSupported()
    val capsule = androidx.compose.foundation.shape.RoundedCornerShape(percent = 50)
    Box(
        modifier = modifier
            .height(TopBarGlassButtonSize)
            .then(topBarGlass(capsule))
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = if (glassEnabled) glassContentColor() else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * A top-bar button on its own round Liquid Glass — the same material as the
 * floating bottom bar. Used for back, settings and every other bar action so
 * they look the same on a plain page, over artwork, and on a pushed album or
 * folder.
 */
@Composable
fun TopBarGlassButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val glassEnabled = LocalLiquidGlassEnabled.current && isGlassSupported()
    val tint = if (glassEnabled) glassContentColor() else MaterialTheme.colorScheme.onSurface
    val glass = topBarGlass(CircleShape)
    Box(
        modifier = modifier
            .size(TopBarGlassButtonSize)
            .then(glass)
            .clip(CircleShape)
            .clickable(onClick = onClick, role = Role.Button),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * The account affordance at the right end of the bar.
 *
 * It is the signed-in Google account's own photo — the same one YouTube Music
 * shows there — and tapping it opens Settings, where the account lives. Signed
 * out, or before the account menu has come back, it falls back to a person
 * glyph on a filled circle so the tap target never disappears.
 *
 * The hairline ring is what keeps a photo with light edges from dissolving into
 * the bar's glass; it is the same one thumbnails elsewhere carry.
 */
@Composable
fun TopBarAccountButton(
    account: Account?,
    onClick: () -> Unit,
    onSwipeProfile: ((forward: Boolean) -> Boolean)? = null,
    modifier: Modifier = Modifier,
) {
    val translation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    // Wrapped in an IconButton so it keeps the 48dp target, the ripple and the
    // spacing every other action in this bar has.
    IconButton(
        onClick = onClick,
        modifier = modifier
            .graphicsLayer { translationY = translation.value }
            .pointerInput(onSwipeProfile) {
                if (onSwipeProfile == null) return@pointerInput
                var drag = 0f
                detectVerticalDragGestures(
                    onVerticalDrag = { change, amount -> change.consume(); drag += amount },
                    onDragEnd = {
                        if (kotlin.math.abs(drag) < 28f) return@detectVerticalDragGestures
                        if (!onSwipeProfile.invoke(drag > 0f)) scope.launch {
                            translation.snapTo(if (drag > 0f) 9f else -9f)
                            translation.animateTo(0f, spring())
                        }
                    },
                )
            },
    ) {
        val photo = account?.thumbnailUrl
        if (photo != null) {
            AsyncImage(
                model = photo,
                contentDescription = stringResource(R.string.switch_account),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(AVATAR_SIZE)
                    .clip(CircleShape)
                    .thumbnailBorder(CircleShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(AVATAR_SIZE)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .thumbnailBorder(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Person,
                    contentDescription = stringResource(R.string.switch_account),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * The refresh indicator: a line along the bottom of the bar, directly under the
 * status bar. It tracks the drag on the way down — filling left to right as the
 * pull approaches the threshold — then sweeps indefinitely once the refresh is
 * away, so the two phases read as one continuous gesture.
 */
@Composable
private fun RefreshLine(refreshing: Boolean, pullFraction: () -> Float, modifier: Modifier = Modifier) {
    val fraction = pullFraction()
    val pulling = fraction > 0.01f
    AnimatedVisibility(
        visible = refreshing || pulling,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(220)),
        modifier = modifier,
    ) {
        val lineModifier = Modifier
            .fillMaxWidth()
            .height(LINE_HEIGHT)
        if (refreshing) {
            LinearProgressIndicator(
                modifier = lineModifier,
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Butt,
                gapSize = 0.dp,
            )
        } else {
            LinearProgressIndicator(
                progress = { fraction.coerceIn(0f, 1f) },
                modifier = lineModifier,
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Butt,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}

private val LINE_HEIGHT = 2.5.dp

/**
 * The account photo's diameter.
 *
 * Smaller than an icon's 24dp box: a filled circle carries more weight than a
 * glyph does, and at 24 it sat heavier in the bar than the wordmark opposite it.
 */
private val AVATAR_SIZE = 28.dp
