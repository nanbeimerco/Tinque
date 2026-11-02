package com.music.bitchord.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import kotlinx.coroutines.delay

/** One offer to take back the move just made. */
class ReorderUndoOffer(val id: Int, val undo: () -> Unit)

/**
 * "Undo" for the last reorder, offered for a few seconds after each drop.
 * Pages put the offer here; the app shows it in its floating layer (see
 * [ReorderUndoHost]), the one place real Liquid Glass can be drawn — glass
 * inside a page would have to show the page it is part of.
 */
object ReorderUndo {
    var offer by mutableStateOf<ReorderUndoOffer?>(null)
        private set
    private var next = 1

    fun show(undo: () -> Unit) {
        offer = ReorderUndoOffer(next++, undo)
    }

    fun clear() {
        offer = null
    }
}

/** How long the offer stays. */
private const val UNDO_SHOWN_MS = 4_000L

/** The floating glass "Undo" pill, over the bottom bars. */
@Composable
fun ReorderUndoHost(glassBackdrop: Boolean, modifier: Modifier = Modifier) {
    val offer = ReorderUndo.offer
    LaunchedEffect(offer?.id) {
        if (offer == null) return@LaunchedEffect
        delay(UNDO_SHOWN_MS)
        if (ReorderUndo.offer?.id == offer.id) ReorderUndo.clear()
    }
    // Keep the last offer's content while the pill animates out.
    var shown by remember { mutableStateOf<ReorderUndoOffer?>(null) }
    if (offer != null) shown = offer
    AnimatedVisibility(
        visible = offer != null,
        modifier = modifier,
        enter = fadeIn() +
            scaleIn(spring(0.6f, 800f), initialScale = 0.85f) +
            slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { it / 2 },
        exit = fadeOut(tween(140)) + scaleOut(tween(160), targetScale = 0.9f),
    ) {
        val shape = RoundedCornerShape(percent = 50)
        val palette = glassPalette()
        val glassEnabled = LocalLiquidGlassEnabled.current && isGlassSupported()
        val surface = if (glassBackdrop && glassEnabled) {
            Modifier.liquidGlass(shape = shape, surfaceAlpha = 0.55f)
        } else {
            Modifier.lightweightLiquidGlass(shape = shape, fallbackColor = palette.fallbackFill)
        }
        val ink = if (glassEnabled) glassContentColor() else MaterialTheme.colorScheme.onSurface
        Row(
            modifier = Modifier
                .then(surface)
                .clip(shape)
                .clickable {
                    shown?.undo?.invoke()
                    ReorderUndo.clear()
                }
                .height(48.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null, tint = ink, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.reorder_bar_undo),
                style = MaterialTheme.typography.labelLarge,
                color = ink,
            )
        }
    }
}
