/*
 * The glass rendering itself is Kyant0/backdrop (Apache-2.0), vendored at
 * [com.music.bitchord.ui.components.backdrop] — see that package for the
 * upstream attribution. This file is the integration glue, adapted from
 * EchoMusicApp/Echo-Music's GlassEffectConfig/Modifier.liquidGlass
 * (GPL-3.0), cut down from Echo's full per-component/vibrancy-slider config
 * to the single on/off switch BitChord exposes in Settings. It is used by the
 * floating nav bar and, while enabled, the app-wide floating top controls.
 */
package com.music.bitchord.ui.components

import com.music.bitchord.ui.components.backdrop.effects.clearRefractionIndex
import com.music.bitchord.ui.components.backdrop.effects.RimLight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.compositeOver
import com.music.bitchord.ui.components.backdrop.highlight.HighlightStyle
import com.music.bitchord.ui.components.backdrop.effects.opticalLens
import com.music.bitchord.data.settings.GlassRenderStyle
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.components.backdrop.Backdrop
import com.music.bitchord.ui.components.backdrop.BackdropEffectScope
import com.music.bitchord.ui.components.backdrop.backdrops.LayerBackdrop
import com.music.bitchord.ui.components.backdrop.drawBackdrop
import com.music.bitchord.ui.components.backdrop.effects.blur
import com.music.bitchord.ui.components.backdrop.effects.colorControls
import com.music.bitchord.ui.components.backdrop.effects.lens
import com.music.bitchord.ui.components.backdrop.highlight.Highlight
import com.music.bitchord.ui.components.backdrop.highlight.HighlightElement
import com.music.bitchord.ui.components.backdrop.internal.ShapeProvider
import com.music.bitchord.ui.components.backdrop.shadow.Shadow
import androidx.compose.ui.unit.dp

private val SharedDefaultHighlightProvider: () -> Highlight? = { Highlight.Default }
private val SharedDefaultShadowProvider: () -> Shadow? = { Shadow.Default }

/** Whether the liquid glass nav bar is turned on — see [AppSettings.liquidGlass]. */
val LocalLiquidGlassEnabled = staticCompositionLocalOf { false }

/** The backdrop content (app UI) that a liquid glass surface samples from. */
val LocalAppBackdrop = staticCompositionLocalOf<Backdrop> { error("No AppBackdrop provided") }

/**
 * Where a [liquidGlass] surface composed under it also records the glass it
 * draws — the sampled, blurred and tinted backdrop, without its rim or shadow —
 * so a lens above it can refract that surface rather than the page behind it.
 * The tab bar's travelling selection pill is the one reader; see
 * [com.music.bitchord.ui.components.floatingtabbar.GlassSelectionPill].
 */
internal val LocalGlassExport = compositionLocalOf<LayerBackdrop?> { null }

/**
 * The backdrop blur pipeline requires [android.graphics.RenderEffect] on a
 * [android.graphics.RenderNode], available from Android 12 (API 31).
 */
fun isGlassSupported(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt >= Build.VERSION_CODES.S

/** Apple-matched defaults (Echo's GlassEffectConfig()). Blur and opacity are user sliders via AppSettings. */
private const val VIBRANCY = 1f
private const val LENS_HEIGHT = 0.5f
private const val LENS_AMOUNT = 0.5f
private const val LENS_MAX_DP = 48f

/**
 * Resolution fraction the glass surface records and processes its backdrop at.
 *
 * A third, which is what Echo's `glassResolutionScale` comes to at this blur
 * radius. It was briefly raised to a half while the sharp-text bug was being
 * chased — wrongly, as it turned out: the cause was a transparent backdrop, not
 * the resample (see MainActivity's `paintBackdrop`). A third is nine times fewer
 * pixels than full resolution through the colour matrix, the blur and the lens
 * shader, on as many as six surfaces at once in the middle of a fold, and the
 * blur is what hides the upscale.
 */
private const val GLASS_RESOLUTION_SCALE = 0.33f

/**
 * The hairline along a bar's edge, and what stands in for the glass rim
 * wherever the glass itself is not drawn.
 *
 * A surface filled with the theme's own `surface` colour has no edge of its own
 * against a dark page — it is the same near-black the page is. The glass gets
 * its edge from [Highlight], and this is that edge for everything that does not.
 */
internal val GLASS_EDGE_WIDTH = 0.5.dp
internal val GLASS_EDGE_COLOR = Color.White.copy(alpha = 0.10f)

/**
 * Icon and label colour for content sitting on a glass surface.
 *
 * Glass shows whatever is behind it rather than the theme's surface colour, so
 * the usual onSurface greys have nothing dependable to sit against. Pure black
 * or white off the theme's luminance is the only tint that holds against
 * arbitrary artwork, and it is what Echo's own glass nav bar uses.
 */
@Composable
fun glassContentColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) Color.Black else Color.White

/**
 * Selected-tab indicator colour for a glass surface: the inverse of
 * [glassContentColor] rather than the same tint at lower alpha — white in
 * light theme, black in dark theme, so the pill reads as a shaded scrim
 * rather than as more of the same tint already on the glyph and label.
 */
@Composable
fun glassIndicatorColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) Color.White else Color.Black

/**
 * A lightweight visual match for liquid glass over a stable background.
 *
 * This keeps the same translucent tint, directional highlight and hairline as
 * [liquidGlass], but intentionally performs no backdrop capture, blur, lens
 * refraction or shadow rendering. When Liquid Glass is disabled or unsupported,
 * [fallbackColor] preserves the control's existing filled appearance.
 */
@Composable
fun Modifier.lightweightLiquidGlass(
    shape: CornerBasedShape,
    fallbackColor: Color,
): Modifier {
    val useGlass = LocalLiquidGlassEnabled.current && isGlassSupported()
    val surfaceOpacity by AppSettings.liquidGlassOpacity.collectAsStateWithLifecycle()
    val renderStyle by AppSettings.glassRenderStyle.collectAsStateWithLifecycle()
    val clarity by AppSettings.glassClarity.collectAsStateWithLifecycle()
    val lightTheme = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val glassTint = if (lightTheme) {
        Color(0xFFFAFAFA)
    } else {
        Color(0xFF121212)
    }
    // With nothing sampled behind it, Mornye's glass is its fill alone.
    val palette = glassPalette()
    val clearFill = if (renderStyle == GlassRenderStyle.CLEAR) {
        clearGlassFill(palette.container, palette.dark, clarity, opacity = 0.80f, shade = AppSettings.glassShade.collectAsStateWithLifecycle().value)
    } else null
    val shapeProvider = remember(shape) { ShapeProvider { shape } }

    return clip(shape)
        .background(
            color = if (useGlass) clearFill ?: glassTint.copy(alpha = surfaceOpacity) else fallbackColor,
            shape = shape,
        )
        .then(
            if (useGlass) {
                HighlightElement(
                    shapeProvider = shapeProvider,
                    highlight = SharedDefaultHighlightProvider,
                )
            } else {
                Modifier
            },
        )
        .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, shape)
}

/**
 * Renders this composable as a liquid glass surface sampling [LocalAppBackdrop]:
 * vibrancy, blur and lens refraction, then a theme-adaptive surface tint (light
 * glass on light theme, dark on dark). Returns the receiver unchanged on devices
 * without RenderEffect support — callers should still gate on [isGlassSupported]
 * to fall back to the regular Haze treatment there.
 *
 * Under "reduce dynamic blur" the surface is filled solid instead, which is what
 * that setting promises everywhere else in the app. It is checked here rather
 * than at each call site so there is one answer to it: [MainActivity] also stops
 * recording the backdrop layer when it is on, and a surface that still tried to
 * sample would be sampling a layer nothing is drawing into.
 *
 * [shape] is restricted to [CornerBasedShape] because the backdrop's lens effect
 * throws for any other shape type.
 */
@Composable
fun Modifier.liquidGlass(
    shape: CornerBasedShape,
    /** A tint at least this opaque, for glass that has text over busy content (menus). */
    surfaceAlpha: Float? = null,
    /** What the surface is, for the per-part values of Mornye-style glass. */
    role: GlassRole = if (surfaceAlpha != null) GlassRole.Overlay else GlassRole.Control,
): Modifier {
    if (!isGlassSupported()) return this
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    if (reduceDynamicBlur) {
        return background(MaterialTheme.colorScheme.surface, shape)
            .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, shape)
    }
    val backdrop = LocalAppBackdrop.current
    val exportedBackdrop = LocalGlassExport.current
    val renderStyle by AppSettings.glassRenderStyle.collectAsStateWithLifecycle()
    if (renderStyle == GlassRenderStyle.CLEAR) {
        return clearGlass(shape, backdrop, exportedBackdrop, role)
    }
    val blurRadiusDp by AppSettings.liquidGlassBlurDp.collectAsStateWithLifecycle()
    val surfaceOpacity by AppSettings.liquidGlassOpacity.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    // Floating overlays (menus, sheets, the toolbar) carry text over busy pages:
    // they keep the bars' tint, a little denser, and get their legibility from
    // a heavier blur rather than from going dark.
    val overlay = role == GlassRole.Overlay
    val blurPx = with(density) { blurRadiusDp.dp.toPx() } * GLASS_RESOLUTION_SCALE * (if (overlay) 1.5f else 1f)
    val lensHeightPx = with(density) { (LENS_HEIGHT * LENS_MAX_DP).dp.toPx() } * GLASS_RESOLUTION_SCALE
    val lensAmountPx = with(density) { (LENS_AMOUNT * LENS_MAX_DP).dp.toPx() } * GLASS_RESOLUTION_SCALE
    val surfaceTintColor = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        Color(0xFFFAFAFA)
    } else {
        Color(0xFF121212)
    }
    val shapeLambda = remember<() -> Shape>(shape) { { shape } }
    val effectsLambda = remember<BackdropEffectScope.() -> Unit>(blurPx, lensHeightPx, lensAmountPx) {
        {
            colorControls(saturation = 1f + 0.5f * VIBRANCY)
            blur(blurPx)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                lens(
                    refractionHeight = lensHeightPx,
                    refractionAmount = lensAmountPx,
                    depthEffect = true,
                    chromaticAberration = true,
                )
            }
        }
    }
    val tintedSurfaceColor = surfaceTintColor.copy(
        alpha = if (overlay) (surfaceOpacity + 0.15f).coerceAtMost(0.85f) else surfaceOpacity,
    )
    val onDrawSurfaceLambda = remember<DrawScope.() -> Unit>(tintedSurfaceColor) {
        {
            drawRect(color = tintedSurfaceColor, size = size)
        }
    }

    return drawBackdrop(
        backdrop = backdrop,
        shape = shapeLambda,
        effects = effectsLambda,
        highlight = SharedDefaultHighlightProvider,
        shadow = SharedDefaultShadowProvider,
        onDrawSurface = onDrawSurfaceLambda,
        exportedBackdrop = exportedBackdrop,
        backdropScale = GLASS_RESOLUTION_SCALE,
    )
}

/** What a glass surface is. Only Mornye-style glass tells them apart. */
enum class GlassRole {
    /** The tab bar capsule. */
    TabBar,
    /** The mini player. */
    Player,
    /** Floating top buttons and the title capsule. */
    Control,
    /** Menus, sheets and the selection toolbar: text over busy content. */
    Overlay,
}

/*
 * Mornye's glass (Settings → Liquid Glass → Rendering), after the Mornye
 * player's `MornyeGlass` + `MornyeLiquidBackdrop` over liquid_glass_easy 5.0.0:
 * a light blur set by one clarity value, a lens that bends only a band along
 * the edge (see [opticalLens]), and a fill painted over it. Mornye's bars and
 * menus all use its "strong tint": a fixed opacity per part, the theme's
 * container darkened under dark glass, thinned a little as clarity rises.
 */

/** Mornye samples the backdrop at full size, and so does this. */
private const val CLEAR_GLASS_RESOLUTION_SCALE = 1f

/** MornyeLiquidBackdrop's defaults: a 20 px band, light travel of 70 px. */
private const val CLEAR_GLASS_REFRACTION_WIDTH = 20f
private const val CLEAR_GLASS_TRAVEL_DP = 70f

/** Widest colour split at full clarity (Mornye: 1.32 x clarity px), and the package's floor. */
private const val CLEAR_GLASS_DISPERSION_DP = 1.32f
private const val CLEAR_GLASS_DISPERSION_MIN_DP = 0.66f

/** Mornye's refraction depth per part. */
private fun GlassRole.clearDepth(): Float = when (this) {
    GlassRole.TabBar -> 0.45f
    else -> 0.30f
}

/** Mornye's fill opacity per part (MornyeTheme.navigationOpacity / chromeOpacity / overlay). */
private fun GlassRole.clearTintOpacity(dark: Boolean, artworkTheme: Boolean): Float = when (this) {
    // Under an artwork theme Mornye thins its bars (navigationOpacity 0.24, chromeOpacity 0.36).
    GlassRole.TabBar, GlassRole.Control -> if (artworkTheme) 0.24f else if (dark) 0.36f else 0.49f
    GlassRole.Player -> if (artworkTheme) 0.36f else if (dark) 0.60f else 0.70f
    GlassRole.Overlay -> 0.78f
}

/**
 * Mornye's quantised blur: σ 2 for clear glass, 6 in the middle, 16 when it
 * is mostly tint. Returned as a blur radius in dp (Skia's σ = 0.577·r + 0.5).
 */
internal fun clearBlurRadiusDp(clarity: Float): Float {
    val sigma = when {
        clarity >= 0.9f -> 2f
        clarity >= 0.6f -> 6f
        else -> 16f
    }
    return ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0f)
}

/**
 * Mornye's strong-tint fill: [opacity] of the container (darkened by 35% black
 * under dark glass), with a further veil of the container that fades out as
 * clarity rises — (1 − clarity)².
 */
internal fun clearGlassFill(
    container: Color,
    dark: Boolean,
    clarity: Float,
    opacity: Float,
    /** Share of Mornye's dark underlay and density to keep (Settings → Darkness); 1 = as Mornye. */
    shade: Float = 1f,
): Color {
    val source = if (dark) container else Color.White
    val density = opacity * (0.7f + 0.3f * shade)
    var baseTint = source.copy(alpha = if (dark) density * 0.82f else (density * 0.88f).coerceIn(0.5f, 1f))
    if (dark) baseTint = baseTint.compositeOver(Color.Black.copy(alpha = 0.35f * shade))
    val tintStrength = (1f - clarity) * (1f - clarity)
    return container.copy(alpha = tintStrength).compositeOver(baseTint)
}

/** Mornye's rim: a 4 px specular on the side arcs, and a faint 22 px glow. */
internal fun clearSpecular(dark: Boolean, density: Float, scale: Float, strength: Float = 1f): RimLight =
    RimLight(
        thickness = 0.75f * 5.4f * density * scale,
        angleDegrees = 0f,
        spread = 0.2f,
        intensity = (if (dark) 0.25f else 0.12f) * strength,
        red = 1f,
        green = 1f,
        blue = 1f,
        colorAlpha = if (dark) 1f else 0x88 / 255f,
    )

internal fun clearGlow(density: Float, scale: Float, strength: Float = 1f): RimLight =
    RimLight(
        thickness = 0.75f * 29.4f * density * scale,
        angleDegrees = 39f,
        spread = 0.9f,
        intensity = 0.05f * strength,
    )

private val ClearShadowDarkProvider: () -> Shadow? = {
    Shadow(radius = 8.dp, offset = DpOffset(0.dp, 2.dp), color = Color.Black.copy(alpha = 0.14f))
}
private val ClearShadowLightProvider: () -> Shadow? = {
    Shadow(radius = 8.dp, offset = DpOffset(0.dp, 2.dp), color = Color.Black.copy(alpha = 0.08f))
}
private val NoHighlight: () -> Highlight? = { null }

@Composable
private fun Modifier.clearGlass(
    shape: CornerBasedShape,
    backdrop: Backdrop,
    exportedBackdrop: LayerBackdrop?,
    role: GlassRole,
): Modifier {
    val clarity by AppSettings.glassClarity.collectAsStateWithLifecycle()
    val shade by AppSettings.glassShade.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val palette = glassPalette()
    val dark = palette.dark
    val scale = CLEAR_GLASS_RESOLUTION_SCALE
    val blurPx = with(density) { clearBlurRadiusDp(clarity).dp.toPx() } * scale
    val bandPx = with(density) { CLEAR_GLASS_REFRACTION_WIDTH.dp.toPx() } * scale
    val travelPx = with(density) { CLEAR_GLASS_TRAVEL_DP.dp.toPx() } * scale
    val dispersionPx = with(density) { (CLEAR_GLASS_DISPERSION_DP * clarity).dp.toPx() } * scale
    val dispersionMinPx = with(density) { CLEAR_GLASS_DISPERSION_MIN_DP.dp.toPx() } * scale
    val index = clearRefractionIndex(CLEAR_GLASS_REFRACTION_WIDTH, role.clearDepth())
    val specular = clearSpecular(dark, density.density, scale)
    val glow = clearGlow(density.density, scale)
    // Neutral, as every glass is (see [GlassPalette]) — never the artwork theme's container.
    val fill = clearGlassFill(palette.container, dark, clarity, role.clearTintOpacity(dark, palette.artworkTheme), shade)

    val shapeLambda = remember<() -> Shape>(shape) { { shape } }
    val effectsLambda = remember<BackdropEffectScope.() -> Unit>(blurPx, bandPx, travelPx, index, dispersionPx, dark) {
        {
            if (blurPx > 0f) blur(blurPx)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                opticalLens(
                    band = bandPx,
                    refractionIndex = index,
                    travel = travelPx,
                    dispersion = dispersionPx,
                    dispersionMin = dispersionMinPx,
                    specular = specular,
                    glow = glow,
                )
            }
        }
    }
    val onDrawSurfaceLambda = remember<DrawScope.() -> Unit>(fill) {
        { drawRect(color = fill, size = size) }
    }
    return drawBackdrop(
        backdrop = backdrop,
        shape = shapeLambda,
        effects = effectsLambda,
        // The lens draws its own rim; Mornye adds no second one over a lens.
        highlight = NoHighlight,
        shadow = if (dark) ClearShadowDarkProvider else ClearShadowLightProvider,
        onDrawSurface = onDrawSurfaceLambda,
        exportedBackdrop = exportedBackdrop,
        backdropScale = scale,
    )
}
