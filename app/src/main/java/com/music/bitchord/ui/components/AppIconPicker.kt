package com.music.bitchord.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.LauncherIcon
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/*
 * The Tinque mark (point-symmetric version): four strokes on a 100-unit
 * square, the third the long one bent at both ends. The same numbers as the
 * launcher vectors (res/drawable/ic_launcher_fg_*.xml) and ic_logo.
 */

/** Tinque's orange: the mark's third line. */
val TinqueOrange = Color(0xFFFF5A36)

// Point-symmetric "C" (2.6.3): tails 4 shorter and tucked in, so a round
// mask no longer crowds them; the long line's top bend is shortened as much,
// so it still turns about (58, 49.5).
private fun markPaths(): List<Path> = listOf(
    Path().apply { moveTo(22f, 32f); lineTo(22f, 68f); quadraticBezierTo(22f, 76f, 17f, 81f) },
    Path().apply { moveTo(40f, 32f); lineTo(40f, 68f); quadraticBezierTo(40f, 76f, 35f, 81f) },
    Path().apply { moveTo(63f, 18f); quadraticBezierTo(58f, 23f, 58f, 31f); lineTo(58f, 68f); quadraticBezierTo(58f, 76f, 53f, 81f) },
    Path().apply { moveTo(75f, 54f); lineTo(75f, 68f); quadraticBezierTo(75f, 76f, 80f, 81f) },
)

private val MARK = markPaths()

/**
 * Draws the four strokes into a [box]-unit square at [origin], lines in [lines],
 * the third in [third]. [shift]: extra units to the right (the beta centring;
 * the mark's weight sits left of its bounds' centre).
 */
private fun DrawScope.drawMarkStrokes(origin: Offset, box: Float, lines: Color, third: Color, shift: Int = 0) {
    val k = box / 100f
    val stroke = Stroke(width = 13f * k, cap = StrokeCap.Butt, join = StrokeJoin.Round)
    MARK.forEachIndexed { i, unit ->
        val p = Path().apply { addPath(unit) }
        p.transform(androidx.compose.ui.graphics.Matrix().apply {
            translate(origin.x + (1.5f + shift) * k, origin.y)
            scale(k, k)
        })
        drawPath(p, if (i == 2) third else lines, style = stroke)
    }
}

/** A launcher variant as its icon looks (a rounded square, as the original artwork). */
@Composable
fun TinqueIcon(icon: LauncherIcon, size: Dp, modifier: Modifier = Modifier) {
    val shift by AppSettings.iconCenterShift.collectAsStateWithLifecycle()
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val corner = CornerRadius(s * 0.22f, s * 0.22f)
        val colors = icon.background.map { Color(it) }
        if (colors.size > 1) {
            drawRoundRect(Brush.linearGradient(colors, Offset.Zero, Offset(s, s)), cornerRadius = corner)
        } else {
            drawRoundRect(colors.first(), cornerRadius = corner)
        }
        if (icon == LauncherIcon.WHITE) {
            drawRoundRect(Color(0xFFD3D1C7), cornerRadius = corner, style = Stroke(width = s / 100f))
        }
        // As the launcher draws it: 93% of the icon, lifted 4.
        drawMarkStrokes(Offset(s * 0.035f, s * (0.035f - 0.04f)), s * 0.93f, Color(icon.lines), Color(icon.third), shift)
    }
}

/**
 * The mark alone, cropped to its strokes (so no centring applies): [ink] for
 * the lines, [accent] for the third — the top bar draws it in one colour.
 */
@Composable
fun TinqueLogo(height: Dp, ink: Color, modifier: Modifier = Modifier, accent: Color = ink) {
    // The strokes span x 13.9..85.9 and y 13.4..85.5 of their 100-unit box.
    Canvas(modifier.size(height, height)) {
        val box = this.size.height / 0.721f
        drawMarkStrokes(Offset(-0.139f * box, -0.134f * box), box, ink, accent)
    }
}

@Composable
fun launcherIconLabel(icon: LauncherIcon): String = stringResource(
    when (icon) {
        LauncherIcon.PINK -> R.string.app_icon_pink
        LauncherIcon.BLACK -> R.string.app_icon_black
        LauncherIcon.BLACK_ORANGE -> R.string.app_icon_black_orange
        LauncherIcon.ORANGE -> R.string.app_icon_orange
        LauncherIcon.CREAM -> R.string.app_icon_cream
        LauncherIcon.WHITE -> R.string.app_icon_white
        LauncherIcon.RED_ORANGE -> R.string.app_icon_red_orange
        LauncherIcon.HEAT_PINK -> R.string.app_icon_heat_pink
        LauncherIcon.NAVY -> R.string.app_icon_navy
        LauncherIcon.GREEN -> R.string.app_icon_green
        LauncherIcon.SUNSET -> R.string.app_icon_sunset
    },
)

/**
 * Beta: nudge the mark right, +0 to +3 (of 100). Only what the app draws
 * follows it for now — these previews; the home-screen icon, the notification
 * icon and the top bar's logo stay at +0.
 */
@Composable
private fun IconCenterShiftRow() {
    val shift by AppSettings.iconCenterShift.collectAsStateWithLifecycle()
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.icon_center_shift),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.beta),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
    Text(
        stringResource(R.string.icon_center_shift_subtitle),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (0..3).forEach { v ->
            FilterChip(
                selected = shift == v,
                onClick = { AppSettings.setIconCenterShift(v) },
                label = { Text("+$v") },
            )
        }
    }
}

/** The app icon chooser: every variant as itself, three to a row. Kept out of SettingsScreen (method size). */
@Composable
fun AppIconPicker(selected: LauncherIcon, onPick: (LauncherIcon) -> Unit, onDismiss: () -> Unit) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_icon)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.app_icon_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                LauncherIcon.entries.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { icon ->
                            val on = icon == selected
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { onPick(icon); onDismiss() }
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    TinqueIcon(
                                        icon,
                                        56.dp,
                                        Modifier.then(
                                            if (on) Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)).padding(4.dp)
                                            else Modifier.padding(4.dp),
                                        ),
                                    )
                                    if (on) {
                                        Icon(
                                            Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(20.dp)
                                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                                                .padding(2.dp),
                                        )
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    launcherIconLabel(icon),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                IconCenterShiftRow()
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
        },
    )
}
