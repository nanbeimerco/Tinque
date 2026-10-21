package com.music.bitchord.ui.components

import com.music.bitchord.data.settings.GlassRenderStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.ThemeMode

/**
 * Colours for anything drawn on Liquid Glass: menus, sheets, the selection
 * toolbar, the floating top controls.
 *
 * Glass shows whatever is behind it, so it never takes the artwork theme's
 * colours. It follows the tab bar's rule instead: a neutral tint, white
 * content in the dark and artwork themes, black in the light theme. The tab
 * bar's selected-tab accent is the one coloured thing allowed on glass.
 */
@Immutable
data class GlassPalette(
    /** Glyphs and labels. */
    val content: Color,
    /** Secondary labels. */
    val secondary: Color,
    /** Hairlines between groups. */
    val divider: Color,
    /** Destructive rows (delete, remove). */
    val destructive: Color,
    /** The fill when no glass is drawn (glass off, or nothing recorded behind). */
    val fallbackFill: Color,
    /** The neutral container Mornye-style glass tints from. */
    val container: Color,
    /** Whether the glass is the dark kind. */
    val dark: Boolean,
    /** The artwork theme is on (Mornye glass thins its tint then, as Mornye does). */
    val artworkTheme: Boolean = false,
)

@Composable
fun glassPalette(): GlassPalette {
    val themeMode by AppSettings.themeMode.collectAsStateWithLifecycle()
    val renderStyle by AppSettings.glassRenderStyle.collectAsStateWithLifecycle()
    val clearStyle = renderStyle == GlassRenderStyle.CLEAR
    val artworkTheme = themeMode == ThemeMode.ARTWORK
    val lightSurface = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val darkGlass = !lightSurface
    val content = if (darkGlass || artworkTheme) Color.White else Color.Black
    return GlassPalette(
        content = content,
        secondary = content.copy(alpha = 0.70f),
        divider = content.copy(alpha = 0.14f),
        destructive = if (darkGlass || artworkTheme) Color(0xFFFF453A) else Color(0xFFFF3B30),
        // Mornye's own neutrals for its glass (#2C2C2E / #F2F2F7), Tinque's for its own.
        fallbackFill = when {
            clearStyle -> if (lightSurface) Color(0xFFF2F2F7) else Color(0xFF2C2C2E)
            else -> if (lightSurface) Color(0xFFF2F2F5) else Color(0xFF1C1C1E)
        },
        container = when {
            clearStyle -> if (lightSurface) Color(0xFFF2F2F7) else Color(0xFF2C2C2E)
            else -> if (lightSurface) Color(0xFFEDEDF2) else Color(0xFF1C1C1E)
        },
        artworkTheme = artworkTheme,
        dark = darkGlass,
    )
}
