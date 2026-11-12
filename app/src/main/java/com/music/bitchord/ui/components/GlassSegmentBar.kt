package com.music.bitchord.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics

/** Height of [GlassSegmentBar]: the same as the top bar's glass buttons. */
val GlassSegmentBarHeight: Dp = TopBarGlassButtonSize

/**
 * A few choices on one floating Glass capsule, the chosen one marked by a pill
 * that slides between them — the bottom tab bar's structure, for a page's own
 * switch (an artist's Songs / Albums / Folders). It floats over the page like
 * the top bar's buttons, so the list runs on beneath it rather than stopping
 * at a band.
 */
@Composable
fun GlassSegmentBar(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val capsule = remember { RoundedCornerShape(percent = 50) }
    val glassEnabled = LocalLiquidGlassEnabled.current && isGlassSupported()
    val idle = if (glassEnabled) glassContentColor() else MaterialTheme.colorScheme.onSurface
    // The bottom tab bar's selection: its pill fill and its label colours.
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isArtworkTheme = AppSettings.themeMode.collectAsStateWithLifecycle().value ==
        com.music.bitchord.data.settings.ThemeMode.ARTWORK
    val chosen = when {
        !glassEnabled -> MaterialTheme.colorScheme.primary
        isArtworkTheme -> MaterialTheme.colorScheme.primary
        isDark -> Color.White
        else -> glassContentColor()
    }
    val unchosen = when {
        !glassEnabled -> idle
        isDark || isArtworkTheme -> Color.White.copy(alpha = 0.70f)
        else -> glassContentColor().copy(alpha = 0.70f)
    }
    val pillColor = if (glassEnabled) glassSelectionPillColor() else MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    BoxWithConstraints(
        modifier
            .height(GlassSegmentBarHeight)
            .then(topBarGlass(capsule))
            .padding(4.dp),
    ) {
        val count = labels.size.coerceAtLeast(1)
        val segment = maxWidth / count
        val pillX by animateDpAsState(
            segment * selectedIndex.coerceIn(0, count - 1),
            spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
            label = "segment pill",
        )
        Box(
            Modifier
                .offset(x = pillX)
                .width(segment)
                .fillMaxHeight()
                .background(pillColor, capsule),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            labels.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val color by animateColorAsState(if (selected) chosen else unchosen, label = "segment label")
                Box(
                    Modifier
                        .width(segment)
                        .fillMaxHeight()
                        .clip(capsule)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                        ) {
                            if (!selected) {
                                haptics.play(Haptic.Select)
                                onSelect(index)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = color,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}
