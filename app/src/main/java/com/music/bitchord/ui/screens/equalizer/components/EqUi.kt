package com.music.bitchord.ui.screens.equalizer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.ui.components.ExpressiveGroupDefaults
import com.music.bitchord.ui.components.ExpressiveGroupTitle
import com.music.bitchord.ui.components.expressiveItemShape

/** Horizontal margin of every EQ section from the screen edge. */
internal val EqPageGutter = 16.dp

/** Collects the rows of an [EqGroup]. Conditional rows are fine — add them with plain `if`. */
class EqGroupScope internal constructor() {
    internal val rows = mutableListOf<@Composable () -> Unit>()
    fun row(content: @Composable () -> Unit) {
        rows += content
    }
}

/**
 * One Material 3 Expressive grouped container: a large rounded block whose rows
 * are split by thin gaps. Every EQ section is one of these, so the whole screen
 * shares one rhythm.
 */
@Composable
fun EqGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    build: EqGroupScope.() -> Unit,
) {
    val rows = EqGroupScope().apply(build).rows
    val container = ExpressiveGroupDefaults.containerColor()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = EqPageGutter, vertical = 8.dp),
    ) {
        if (title != null) ExpressiveGroupTitle(title)
        Column(verticalArrangement = Arrangement.spacedBy(ExpressiveGroupDefaults.Gap)) {
            rows.forEachIndexed { index, row ->
                val shape = expressiveItemShape(index, rows.size)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(container),
                ) {
                    row()
                }
            }
        }
    }
}

/** Standard padding of content inside a group row. */
@Composable
fun EqRowContent(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .alpha(if (enabled) 1f else 0.45f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** Round tinted tile holding a row's leading icon. */
@Composable
fun EqLeadingIcon(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.onSurface) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** Title + optional supporting line, sized for a phone: wraps cleanly, never squeezed. */
@Composable
fun RowScope.EqTexts(title: String, subtitle: String? = null, titleColor: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = titleColor,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun eqSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    checkedBorderColor = MaterialTheme.colorScheme.primary,
    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
)

@Composable
fun eqSliderColors() = SliderDefaults.colors(
    thumbColor = MaterialTheme.colorScheme.primary,
    activeTrackColor = MaterialTheme.colorScheme.primary,
    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    activeTickColor = Color.Transparent,
    inactiveTickColor = Color.Transparent,
)

/** A row with a leading icon, title/subtitle and a trailing switch; the whole row toggles. */
@Composable
fun EqSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    EqRowContent(onClick = { onCheckedChange(!checked) }, enabled = enabled) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (icon != null) EqLeadingIcon(icon)
            EqTexts(title, subtitle)
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = eqSwitchColors(),
            )
        }
    }
}

/** A tappable action row: icon, title, optional subtitle, chevron. */
@Composable
fun EqActionRow(
    title: String,
    onClick: () -> Unit,
    icon: ImageVector,
    subtitle: String? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    showChevron: Boolean = true,
) {
    EqRowContent(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            EqLeadingIcon(icon, tint = titleColor)
            EqTexts(title, subtitle, titleColor)
            if (showChevron) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Label on the left, value on the right, full-width slider underneath. */
@Composable
fun EqSliderRow(
    label: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true,
    steps: Int = 0,
    icon: ImageVector? = null,
) {
    EqRowContent(enabled = enabled) {
        EqSliderBody(label, valueText, value, onValueChange, valueRange, enabled, steps, icon)
    }
}

/** The body of [EqSliderRow], for use inside a row that holds more than one slider. */
@Composable
fun EqSliderBody(
    label: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true,
    steps: Int = 0,
    icon: ImageVector? = null,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            EqValuePill(valueText)
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            steps = steps,
            colors = eqSliderColors(),
        )
    }
}

/** A small rounded readout for a numeric value. */
@Composable
fun EqValuePill(text: String) {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/** A compact rounded tag. */
@Composable
fun EqTag(text: String, container: Color, content: Color) {
    Box(
        modifier = Modifier
            .background(container, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = content,
            fontSize = 11.sp,
            maxLines = 1,
        )
    }
}
