package com.music.bitchord.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Glyphs for the player's bottom control row, and only for it.
 *
 * Shapes from Tabler Icons (https://tabler.io/icons, MIT License,
 * Copyright (c) 2020-2024 Paweł Kuna). Other screens keep their own icons:
 * the same picture means different things elsewhere in the app (the queue
 * glyph also stands in for playlist art, for one), so these are kept apart
 * rather than swapped in everywhere.
 */
object PlayerControlIcons {

    /** Tabler draws at 2 on a 24 grid; 2.1 holds up at the row's 22dp. */
    private const val STROKE = 2.1f
    private val ink = SolidColor(Color.Black)

    private fun glyph(name: String, vararg paths: String, filled: Set<Int> = emptySet()): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            paths.forEachIndexed { index, d ->
                addPath(
                    pathData = addPathNodes(d),
                    stroke = ink,
                    strokeLineWidth = STROKE,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                    fill = if (index in filled) ink else null,
                )
            }
        }.build()

    val Shuffle: ImageVector by lazy {
        glyph(
            "pc_shuffle",
            "M18 4l3 3l-3 3",
            "M18 20l3 -3l-3 -3",
            "M3 7h3a5 5 0 0 1 5 5a5 5 0 0 0 5 5h5",
            "M21 7h-5a4.978 4.978 0 0 0 -3 1m-4 8a4.984 4.984 0 0 1 -3 1h-3",
        )
    }

    private val repeatLoop = arrayOf(
        "M4 12v-3a3 3 0 0 1 3 -3h13m-3 -3l3 3l-3 3",
        "M20 12v3a3 3 0 0 1 -3 3h-13m3 3l-3 -3l3 -3",
    )

    val Repeat: ImageVector by lazy { glyph("pc_repeat", *repeatLoop) }

    val RepeatOne: ImageVector by lazy { glyph("pc_repeat_one", *repeatLoop, "M11 11l1 -1v4") }

    val Lyrics: ImageVector by lazy {
        glyph(
            "pc_lyrics",
            "M8 9h8",
            "M8 13h6",
            "M9 18h-3a3 3 0 0 1 -3 -3v-8a3 3 0 0 1 3 -3h12a3 3 0 0 1 3 3v8a3 3 0 0 1 -3 3h-3l-3 3l-3 -3z",
        )
    }

    private const val HEART = "M19.5 12.572l-7.5 7.428l-7.5 -7.428a5 5 0 1 1 7.5 -6.566a5 5 0 1 1 7.5 6.572"
    private const val STAR =
        "M12 17.75l-6.172 3.245l1.179 -6.873l-5 -4.867l6.9 -1l3.086 -6.253l3.086 6.253l6.9 1l-5 4.867l1.179 6.873z"

    val Heart: ImageVector by lazy { glyph("pc_heart", HEART) }
    val HeartFilled: ImageVector by lazy { glyph("pc_heart_filled", HEART, filled = setOf(0)) }
    val Star: ImageVector by lazy { glyph("pc_star", STAR) }
    val StarFilled: ImageVector by lazy { glyph("pc_star_filled", STAR, filled = setOf(0)) }

    val Queue: ImageVector by lazy {
        glyph(
            "pc_queue",
            "M9 6h11", "M9 12h11", "M9 18h11",
            "M5 6v0.01", "M5 12v0.01", "M5 18v0.01",
        )
    }

    val Playlists: ImageVector by lazy {
        glyph(
            "pc_playlists",
            "M11 17a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
            "M17 17v-13h4",
            "M13 5h-10",
            "M3 9h10",
            "M9 13h-6",
        )
    }

    val Search: ImageVector by lazy {
        glyph(
            "pc_search",
            "M3 10a7 7 0 1 0 14 0a7 7 0 1 0 -14 0",
            "M21 21l-6 -6",
        )
    }
}
