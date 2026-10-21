package com.music.bitchord.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive grouped list: several rows held in one large rounded
 * container, split by thin gaps that read as dividers. The outer corners are
 * large, the corners where two rows meet are small, so the group reads as one
 * block rather than a stack of separate cards.
 */
object ExpressiveGroupDefaults {
    val OuterRadius: Dp = 24.dp
    val InnerRadius: Dp = 4.dp
    val Gap: Dp = 2.dp

    @Composable
    fun containerColor(): Color = MaterialTheme.colorScheme.surfaceContainerHigh

    @Composable
    fun selectedContainerColor(): Color = MaterialTheme.colorScheme.secondaryContainer
}

/**
 * The shape of row [index] out of [count] in a group: large corners on the
 * group's outside edges, small ones between rows.
 */
fun expressiveItemShape(
    index: Int,
    count: Int,
    outer: Dp = ExpressiveGroupDefaults.OuterRadius,
    inner: Dp = ExpressiveGroupDefaults.InnerRadius,
): RoundedCornerShape {
    val top = if (index == 0) outer else inner
    val bottom = if (index == count - 1) outer else inner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/**
 * Clips and fills one row of a group. Use inside a lazy list where each row is
 * its own item; pair it with [ExpressiveGroupDefaults.Gap] spacing.
 */
@Composable
fun Modifier.expressiveItem(
    index: Int,
    count: Int,
    color: Color = ExpressiveGroupDefaults.containerColor(),
): Modifier {
    val shape = expressiveItemShape(index, count)
    return clip(shape).background(color, shape)
}

/**
 * A group of rows in one container, for non-lazy content. Each child is laid
 * out with a thin gap between it and the next; give each child its shape with
 * [expressiveItem].
 */
@Composable
fun ExpressiveGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            ExpressiveGroupTitle(title)
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ExpressiveGroupDefaults.Gap),
            content = content,
        )
    }
}

/** The small label that sits above a group. */
@Composable
fun ExpressiveGroupTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
    )
}
