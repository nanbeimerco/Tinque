package com.music.bitchord.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Thick-stroke, round-capped icons in the spirit of Telegram's modern icon set.
 * Drawn as strokes (no fills) so the 2.2px weight + round joins read as a
 * single polished family. Tint is applied by [androidx.compose.material3.Icon].
 */
object BitChordIcons {

    private const val STROKE = 2.2f
    private val stroke = SolidColor(Color.Black)

    // ---- Now Playing transport ----
    //
    // Filled outlines, drawn as true straight lines and circular arcs (the
    // play tip is its own eased curve, tighter-looking than the left corners
    // on purpose). Each sits where the old stroke-rounded glyph sat in the
    // 24 box: same centre, the same visual size (play and pause by area; the
    // skips, flatter now, by width). Every glyph is mirror-symmetric top to
    // bottom, and previous is next mirrored.

    private fun transportIcon(name: String, draw: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply { path(fill = stroke, pathBuilder = draw) }.build()

    /** Play: rounded triangle; its tip rounds tighter than its left corners. */
    val TransportPlay: ImageVector by lazy {
        transportIcon("bc_transport_play") {
            moveTo(7.381f, 3.823f)
            lineTo(18.143f, 9.259f)
            curveTo(20.859f, 10.631f, 21.417f, 10.938f, 21.417f, 12f)
            curveTo(21.417f, 13.062f, 20.859f, 13.369f, 18.143f, 14.741f)
            lineTo(7.381f, 20.177f)
            arcTo(1.79f, 1.79f, 0f, isMoreThanHalf = false, isPositiveArc = true, 4.783f, 18.579f)
            lineTo(4.783f, 5.421f)
            arcTo(1.79f, 1.79f, 0f, isMoreThanHalf = false, isPositiveArc = true, 7.381f, 3.823f)
            close()
        }
    }

    /** Pause: two bars with rounded corners. */
    val TransportPause: ImageVector by lazy {
        transportIcon("bc_transport_pause") {
            moveTo(7f, 3.91f)
            lineTo(9.281f, 3.91f)
            arcTo(1.625f, 1.625f, 0f, isMoreThanHalf = false, isPositiveArc = true, 10.906f, 5.535f)
            lineTo(10.906f, 18.465f)
            arcTo(1.625f, 1.625f, 0f, isMoreThanHalf = false, isPositiveArc = true, 9.281f, 20.09f)
            lineTo(7f, 20.09f)
            arcTo(1.625f, 1.625f, 0f, isMoreThanHalf = false, isPositiveArc = true, 5.374f, 18.465f)
            lineTo(5.374f, 5.535f)
            arcTo(1.625f, 1.625f, 0f, isMoreThanHalf = false, isPositiveArc = true, 7f, 3.91f)
            close()
            moveTo(14.719f, 3.91f)
            lineTo(17f, 3.91f)
            arcTo(1.625f, 1.625f, 0f, isMoreThanHalf = false, isPositiveArc = true, 18.626f, 5.535f)
            lineTo(18.626f, 18.465f)
            arcTo(1.625f, 1.625f, 0f, isMoreThanHalf = false, isPositiveArc = true, 17f, 20.09f)
            lineTo(14.719f, 20.09f)
            arcTo(1.625f, 1.625f, 0f, isMoreThanHalf = false, isPositiveArc = true, 13.094f, 18.465f)
            lineTo(13.094f, 5.535f)
            arcTo(1.625f, 1.625f, 0f, isMoreThanHalf = false, isPositiveArc = true, 14.719f, 3.91f)
            close()
        }
    }

    /** Skip forward: two 60-degree triangles meeting in a sharp notch. */
    val TransportNext: ImageVector by lazy {
        transportIcon("bc_transport_next") {
            moveTo(13.627f, 5.537f)
            arcTo(1.222f, 1.222f, 0f, isMoreThanHalf = false, isPositiveArc = false, 11.794f, 6.595f)
            lineTo(11.794f, 11.172f)
            lineTo(2.033f, 5.537f)
            arcTo(1.222f, 1.222f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.2f, 6.595f)
            lineTo(0.2f, 17.405f)
            arcTo(1.222f, 1.222f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2.033f, 18.463f)
            lineTo(11.794f, 12.828f)
            lineTo(11.794f, 17.405f)
            arcTo(1.222f, 1.222f, 0f, isMoreThanHalf = false, isPositiveArc = false, 13.627f, 18.463f)
            lineTo(22.989f, 13.058f)
            arcTo(1.222f, 1.222f, 0f, isMoreThanHalf = false, isPositiveArc = false, 22.989f, 10.942f)
            close()
        }
    }

    /** Skip back: [TransportNext] mirrored. */
    val TransportPrevious: ImageVector by lazy {
        transportIcon("bc_transport_previous") {
            moveTo(10.373f, 5.537f)
            arcTo(1.222f, 1.222f, 0f, isMoreThanHalf = false, isPositiveArc = true, 12.206f, 6.595f)
            lineTo(12.206f, 11.172f)
            lineTo(21.967f, 5.537f)
            arcTo(1.222f, 1.222f, 0f, isMoreThanHalf = false, isPositiveArc = true, 23.8f, 6.595f)
            lineTo(23.8f, 17.405f)
            arcTo(1.222f, 1.222f, 0f, isMoreThanHalf = false, isPositiveArc = true, 21.967f, 18.463f)
            lineTo(12.206f, 12.828f)
            lineTo(12.206f, 17.405f)
            arcTo(1.222f, 1.222f, 0f, isMoreThanHalf = false, isPositiveArc = true, 10.373f, 18.463f)
            lineTo(1.011f, 13.058f)
            arcTo(1.222f, 1.222f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.011f, 10.942f)
            close()
        }
    }

    val Search: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_search",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Lens (full circle from two arcs)
                moveTo(4.6f, 11f)
                arcToRelative(6.4f, 6.4f, 0f, isMoreThanHalf = true, isPositiveArc = true, 12.8f, 0f)
                arcToRelative(6.4f, 6.4f, 0f, isMoreThanHalf = true, isPositiveArc = true, -12.8f, 0f)
                // Handle
                moveTo(15.9f, 15.9f)
                lineTo(20.4f, 20.4f)
            }
        }.build()
    }

    /**
     * "Play next", for the selection toolbar beside Material's QueueMusic and
     * PlaylistAdd. The three lines are QueueMusic's exactly (same lengths,
     * heights and weight); the play mark sits in the space the short third
     * line leaves, its top level with that line's top and its bottom level
     * with the bottoms of QueueMusic's note and PlaylistAdd's plus. Material's
     * own PlaylistPlay has a much smaller mark and read a size smaller.
     */
    val PlayNext: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_play_next",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(4f, 7f); horizontalLineTo(14f)
                moveTo(4f, 11f); horizontalLineTo(14f)
                moveTo(4f, 15f); horizontalLineTo(10f)
            }
            // Outline 13..18.4 x 14..20 once the 1.2 rounding stroke is added.
            path(
                fill = stroke,
                stroke = stroke,
                strokeLineWidth = 1.2f,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(13.6f, 14.6f)
                lineTo(17.8f, 17f)
                lineTo(13.6f, 19.4f)
                close()
            }
        }.build()
    }

    val Explore: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_explore",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Compass dial
                moveTo(3.4f, 12f)
                arcToRelative(8.6f, 8.6f, 0f, isMoreThanHalf = true, isPositiveArc = true, 17.2f, 0f)
                arcToRelative(8.6f, 8.6f, 0f, isMoreThanHalf = true, isPositiveArc = true, -17.2f, 0f)
                // Needle
                moveTo(15.4f, 8.6f)
                lineTo(13.6f, 13.6f)
                lineTo(8.6f, 15.4f)
                lineTo(10.4f, 10.4f)
                close()
            }
        }.build()
    }

    val Shuffle: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_shuffle",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Strand that crosses downwards, with its arrow head
                moveTo(3.4f, 7.4f); lineTo(7f, 7.4f); lineTo(16.6f, 16.6f); lineTo(20.6f, 16.6f)
                moveTo(18.1f, 14.1f); lineTo(20.6f, 16.6f); lineTo(18.1f, 19.1f)
                // Strand that crosses upwards, broken around the intersection
                moveTo(3.4f, 16.6f); lineTo(7f, 16.6f); lineTo(9.8f, 13.9f)
                moveTo(13.9f, 10.1f); lineTo(16.6f, 7.4f); lineTo(20.6f, 7.4f)
                moveTo(18.1f, 4.9f); lineTo(20.6f, 7.4f); lineTo(18.1f, 9.9f)
            }
        }.build()
    }

    val Repeat: ImageVector by lazy { repeatLoop("bc_repeat") }

    /**
     * Two straight runs joined by semicircles, with the arrow heads lying flat
     * at the ends of the straights. Putting them on the curves instead — as a
     * first pass did — makes the glyph read as a refresh/sync symbol.
     */
    private fun repeatLoop(name: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(8.6f, 7.6f)
                lineTo(15.4f, 7.6f)
                arcToRelative(4.4f, 4.4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 8.8f)
                lineTo(8.6f, 16.4f)
                arcToRelative(4.4f, 4.4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -8.8f)
                close()
                // Direction of travel: right along the top, left along the bottom.
                moveTo(13.5f, 5.7f); lineTo(15.4f, 7.6f); lineTo(13.5f, 9.5f)
                moveTo(10.5f, 14.5f); lineTo(8.6f, 16.4f); lineTo(10.5f, 18.3f)
            }
        }.build()

    /** AutoPlay's lemniscate. */
    val Infinity: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_infinity",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 12f)
                curveTo(10.1f, 9.1f, 8.7f, 8f, 7.1f, 8f)
                arcToRelative(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 8f)
                curveTo(8.7f, 16f, 10.1f, 14.9f, 12f, 12f)
                curveTo(13.9f, 9.1f, 15.3f, 8f, 16.9f, 8f)
                arcToRelative(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 8f)
                curveTo(15.3f, 16f, 13.9f, 14.9f, 12f, 12f)
            }
        }.build()
    }

    /** Beamed pair of notes, for instrumental stretches in the lyrics. */
    val MusicNote: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_music_note",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            // Heads are solid; stems and beam keep the family's stroke weight.
            path(fill = stroke) {
                moveTo(4.2f, 17.7f)
                arcToRelative(2.9f, 2.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 5.8f, 0f)
                arcToRelative(2.9f, 2.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, -5.8f, 0f)
                close()
                moveTo(14.2f, 15.9f)
                arcToRelative(2.9f, 2.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 5.8f, 0f)
                arcToRelative(2.9f, 2.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, -5.8f, 0f)
                close()
            }
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(10f, 17.7f); lineTo(10f, 6.7f)
                moveTo(20f, 15.9f); lineTo(20f, 4.9f)
                moveTo(10f, 6.7f); lineTo(20f, 4.9f)
            }
        }.build()
    }

    /** Speech bubble with two lines of words. */
    val Lyrics: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_lyrics",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(6.2f, 4.6f)
                lineTo(17.8f, 4.6f)
                arcToRelative(2.8f, 2.8f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2.8f, 2.8f)
                lineTo(20.6f, 13.6f)
                arcToRelative(2.8f, 2.8f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2.8f, 2.8f)
                lineTo(10.6f, 16.4f)
                lineTo(6.8f, 19.6f)
                lineTo(6.8f, 16.4f)
                lineTo(6.2f, 16.4f)
                arcToRelative(2.8f, 2.8f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2.8f, -2.8f)
                lineTo(3.4f, 7.4f)
                arcToRelative(2.8f, 2.8f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2.8f, -2.8f)
                close()
                moveTo(7.6f, 9f); lineTo(16.4f, 9f)
                moveTo(7.6f, 12.1f); lineTo(13.2f, 12.1f)
            }
        }.build()
    }

    /** Plain chevron — a disclosure hint, not a directional arrow. */
    val ChevronRight: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_chevron_right",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(9.5f, 6.2f)
                lineTo(15.3f, 12f)
                lineTo(9.5f, 17.8f)
            }
        }.build()
    }

    /**
     * The player's like control, in two weights.
     *
     * Filled rather than merely tinted when set: the player draws every glyph
     * white on artwork, where a colour change alone is the one signal the
     * backdrop can swallow. A shape change survives any album cover.
     */
    val Heart: ImageVector by lazy { heart("bc_heart", filled = false) }

    val HeartFilled: ImageVector by lazy { heart("bc_heart_filled", filled = true) }

    val Star: ImageVector by lazy { star("bc_star", filled = false) }

    val StarFilled: ImageVector by lazy { star("bc_star_filled", filled = true) }

    private fun star(name: String, filled: Boolean): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                fill = if (filled) stroke else null,
            ) {
                moveTo(12f, 3.8f)
                lineTo(14.55f, 9.05f)
                lineTo(20.35f, 9.9f)
                lineTo(16.15f, 13.95f)
                lineTo(17.15f, 19.7f)
                lineTo(12f, 16.98f)
                lineTo(6.85f, 19.7f)
                lineTo(7.85f, 13.95f)
                lineTo(3.65f, 9.9f)
                lineTo(9.45f, 9.05f)
                close()
            }
        }.build()

    private fun heart(name: String, filled: Boolean): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                fill = if (filled) stroke else null,
            ) {
                // Two lobes meeting at the top notch, falling to a single point.
                moveTo(12f, 20f)
                curveTo(12f, 20f, 3.2f, 14.6f, 3.2f, 8.9f)
                arcToRelative(4.5f, 4.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 8.8f, -1.5f)
                arcToRelative(4.5f, 4.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 8.8f, 1.5f)
                curveTo(20.8f, 14.6f, 12f, 20f, 12f, 20f)
                close()
            }
        }.build()

    /** Adding something — a new playlist, on the library shelf. */
    val Plus: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_plus",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 5f); lineTo(12f, 19f)
                moveTo(5f, 12f); lineTo(19f, 12f)
            }
        }.build()
    }

    /**
     * A tick — the other half of [Plus]. Saving something to the library swaps
     * one for the other in place, so the two are drawn on the same 14-unit span
     * and at the same weight; a tick sized to its own bounding box would jump.
     */
    val Check: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_check",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5f, 12.8f); lineTo(9.6f, 17.4f); lineTo(19f, 6.9f)
            }
        }.build()
    }

    /** Arrow pointing down into a tray — offline download. */
    val Download: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_download",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Vertical stem
                moveTo(12f, 4.5f); lineTo(12f, 15.5f)
                // Arrow head
                moveTo(7.5f, 11f); lineTo(12f, 15.5f); lineTo(16.5f, 11f)
                // Tray base
                moveTo(4.5f, 18f); lineTo(19.5f, 18f)
            }
        }.build()
    }

    /**
     * Clock face with two hands — a download asked for but not yet on disk.
     *
     * The dial is the same 8.6 radius as [Explore]'s, so the two sit at the same
     * optical weight when they appear in the same row of header buttons. The
     * hands are one polyline through the centre rather than two strokes: the
     * round join is then the pivot, which is what stops the middle reading as a
     * pair of lines that happen to cross.
     */
    val Clock: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_clock",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Dial (full circle from two arcs)
                moveTo(3.4f, 12f)
                arcToRelative(8.6f, 8.6f, 0f, isMoreThanHalf = true, isPositiveArc = true, 17.2f, 0f)
                arcToRelative(8.6f, 8.6f, 0f, isMoreThanHalf = true, isPositiveArc = true, -17.2f, 0f)
                // Minute hand up, hour hand down to the right
                moveTo(12f, 7.4f); lineTo(12f, 12f); lineTo(15.4f, 13.8f)
            }
        }.build()
    }

    /**
     * A pushpin, not a map marker: a round head with a straight needle
     * kicked out to one side, the way it sits once it's stuck into something.
     * A teardrop reads as "location" the moment it's this small — the two
     * disjoint strokes here are what keep it read as "pinned" instead.
     */
    val Pin: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_pin",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Round head.
                moveTo(9.6f, 8.4f)
                arcToRelative(3.4f, 3.4f, 0f, isMoreThanHalf = true, isPositiveArc = true, 6.8f, 0f)
                arcToRelative(3.4f, 3.4f, 0f, isMoreThanHalf = true, isPositiveArc = true, -6.8f, 0f)
                // Needle, kicked out from the underside of the head to a point.
                moveTo(10.8f, 10.8f)
                lineTo(5.6f, 19.6f)
            }
        }.build()
    }

    /** Speed gauge used by high-performance display settings. */
    val Performance: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_performance",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Open gauge, leaving room for the base line.
                moveTo(4.1f, 16.8f)
                arcToRelative(8.4f, 8.4f, 0f, isMoreThanHalf = true, isPositiveArc = true, 15.8f, 0f)
                // Needle and hub.
                moveTo(12f, 15.8f)
                lineTo(16.7f, 9.3f)
                moveTo(10.9f, 16.9f)
                arcToRelative(1.1f, 1.1f, 0f, isMoreThanHalf = true, isPositiveArc = true, 2.2f, 0f)
                arcToRelative(1.1f, 1.1f, 0f, isMoreThanHalf = true, isPositiveArc = true, -2.2f, 0f)
                // Stable baseline.
                moveTo(5.4f, 19.5f)
                lineTo(18.6f, 19.5f)
            }
        }.build()
    }

    /** Four frame-time bars, used by refresh-rate selection. */
    val FrameRate: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_frame_rate",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(5f, 15.5f); lineTo(5f, 8.5f)
                moveTo(9.7f, 18.5f); lineTo(9.7f, 5.5f)
                moveTo(14.3f, 16.5f); lineTo(14.3f, 7.5f)
                moveTo(19f, 14.5f); lineTo(19f, 9.5f)
            }
        }.build()
    }

    val Library: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_library",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Three upright spines + one leaning — Apple Music's library glyph, thickened
                moveTo(4.6f, 4.8f); lineTo(4.6f, 19.2f)
                moveTo(9.2f, 4.8f); lineTo(9.2f, 19.2f)
                moveTo(13.8f, 4.8f); lineTo(13.8f, 19.2f)
                moveTo(17.2f, 5.6f); lineTo(20.6f, 18.9f)
            }
        }.build()
    }

    /** 2x2 grid icon (4 rounded squares) for switching layout to grid view. */
    val GridView: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_grid_view",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Top-left
                moveTo(6.5f, 4.5f)
                lineTo(8.5f, 4.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 2f)
                lineTo(10.5f, 8.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, 2f)
                lineTo(6.5f, 10.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, -2f)
                lineTo(4.5f, 6.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, -2f)
                close()

                // Top-right
                moveTo(15.5f, 4.5f)
                lineTo(17.5f, 4.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 2f)
                lineTo(19.5f, 8.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, 2f)
                lineTo(15.5f, 10.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, -2f)
                lineTo(13.5f, 6.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, -2f)
                close()

                // Bottom-left
                moveTo(6.5f, 13.5f)
                lineTo(8.5f, 13.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 2f)
                lineTo(10.5f, 17.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, 2f)
                lineTo(6.5f, 19.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, -2f)
                lineTo(4.5f, 15.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, -2f)
                close()

                // Bottom-right
                moveTo(15.5f, 13.5f)
                lineTo(17.5f, 13.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 2f)
                lineTo(19.5f, 17.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, 2f)
                lineTo(15.5f, 19.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, -2f)
                lineTo(13.5f, 15.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, -2f)
                close()
            }
        }.build()
    }

    /** List view icon (horizontal rows) for switching layout to list view. */
    val ListView: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_list_view",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Three horizontal lines with bullet markers
                moveTo(4.5f, 6.5f); lineTo(5.5f, 6.5f)
                moveTo(9.5f, 6.5f); lineTo(19.5f, 6.5f)

                moveTo(4.5f, 12f); lineTo(5.5f, 12f)
                moveTo(9.5f, 12f); lineTo(19.5f, 12f)

                moveTo(4.5f, 17.5f); lineTo(5.5f, 17.5f)
                moveTo(9.5f, 17.5f); lineTo(19.5f, 17.5f)
            }
        }.build()
    }

    // ---- Lucide Tab Bar Icons (optional M3 replacement: outline idle + filled selected) ----
    // Scaled by LUCIDE_TAB_SCALE inside the 24x24 box so optical bounds match Material 3 (~18-20dp)
    // without touching the 25dp/18dp layout geometry of navigation bars.

    private const val LUCIDE_TAB_SCALE = 0.90f

    private fun lucideTabIcon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            group(
                scaleX = LUCIDE_TAB_SCALE,
                scaleY = LUCIDE_TAB_SCALE,
                pivotX = 12f,
                pivotY = 12f,
            ) {
                block()
            }
        }.build()

    private fun PathBuilder.circleSubpath(cx: Float, cy: Float, r: Float, clockwise: Boolean = true) {
        moveTo(cx - r, cy)
        arcToRelative(r, r, 0f, isMoreThanHalf = true, isPositiveArc = clockwise, dx1 = r * 2f, dy1 = 0f)
        arcToRelative(r, r, 0f, isMoreThanHalf = true, isPositiveArc = clockwise, dx1 = -r * 2f, dy1 = 0f)
        close()
    }

    private fun PathBuilder.roundRectSubpath(x: Float, y: Float, w: Float, h: Float, r: Float) {
        moveTo(x + r, y)
        lineTo(x + w - r, y)
        arcToRelative(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = r, dy1 = r)
        lineTo(x + w, y + h - r)
        arcToRelative(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -r, dy1 = r)
        lineTo(x + r, y + h)
        arcToRelative(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -r, dy1 = -r)
        lineTo(x, y + r)
        arcToRelative(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = r, dy1 = -r)
        close()
    }

    private fun ImageVector.Builder.lucideStroke(draw: PathBuilder.() -> Unit) {
        path(
            stroke = stroke,
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = draw,
        )
    }

    private fun ImageVector.Builder.lucideFillAndStroke(draw: PathBuilder.() -> Unit) {
        path(
            fill = stroke,
            stroke = stroke,
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = draw,
        )
    }

    /** ① Home: birdhouse (outline) */
    val TabHomeOutline: ImageVector by lazy {
        lucideTabIcon("bc_tab_home_outline") {
            lucideStroke {
                moveTo(12f, 18f); lineTo(12f, 22f)
                moveTo(17f, 18f); lineTo(18.956f, 6.532f)
                moveTo(3f, 8f); lineTo(10.82f, 2.385f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2.36f, 0f)
                lineTo(21f, 8f)
                moveTo(4f, 18f); lineTo(20f, 18f)
                moveTo(7f, 18f); lineTo(5.044f, 6.532f)
                circleSubpath(12f, 10f, 2f)
            }
        }
    }

    /** ① Home: birdhouse (filled + transparent entrance hole cutout + roof/base/stand strokes) */
    val TabHomeFilled: ImageVector by lazy {
        lucideTabIcon("bc_tab_home_filled") {
            path(fill = stroke, pathFillType = PathFillType.EvenOdd) {
                moveTo(4.4f, 7.1f)
                lineTo(12f, 2.05f)
                lineTo(19.6f, 7.1f)
                lineTo(18.08f, 18f)
                lineTo(5.92f, 18f)
                close()
                circleSubpath(12f, 10f, 2.1f)
            }
            lucideStroke {
                moveTo(12f, 18f); lineTo(12f, 22f)
                moveTo(3f, 8f); lineTo(10.82f, 2.385f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2.36f, 0f)
                lineTo(21f, 8f)
                moveTo(4f, 18f); lineTo(20f, 18f)
            }
        }
    }

    /** ② Library: gallery-vertical-end (outline) */
    val TabLibraryOutline: ImageVector by lazy {
        lucideTabIcon("bc_tab_library_outline") {
            lucideStroke {
                moveTo(7f, 2f); lineTo(17f, 2f)
                moveTo(5f, 6f); lineTo(19f, 6f)
                roundRectSubpath(3f, 10f, 18f, 12f, 2f)
            }
        }
    }

    /** ② Library: gallery-vertical-end (filled card with 2.2 stroke + top stacked lines) */
    val TabLibraryFilled: ImageVector by lazy {
        lucideTabIcon("bc_tab_library_filled") {
            lucideFillAndStroke {
                roundRectSubpath(3f, 10f, 18f, 12f, 2f)
            }
            lucideStroke {
                moveTo(7f, 2f); lineTo(17f, 2f)
                moveTo(5f, 6f); lineTo(19f, 6f)
            }
        }
    }

    /** ③ Songs: music (outline) */
    val TabSongsOutline: ImageVector by lazy {
        lucideTabIcon("bc_tab_songs_outline") {
            lucideStroke {
                moveTo(9f, 18f); lineTo(9f, 5f); lineTo(21f, 3f); lineTo(21f, 16f)
                circleSubpath(6f, 18f, 3f)
                circleSubpath(18f, 16f, 3f)
            }
        }
    }

    /** ③ Songs: music (filled note heads retaining 2.2 stroke so stems connect seamlessly) */
    val TabSongsFilled: ImageVector by lazy {
        lucideTabIcon("bc_tab_songs_filled") {
            lucideFillAndStroke {
                circleSubpath(6f, 18f, 3f)
                circleSubpath(18f, 16f, 3f)
            }
            lucideStroke {
                moveTo(9f, 18f); lineTo(9f, 5f); lineTo(21f, 3f); lineTo(21f, 16f)
            }
        }
    }

    /** ④ Albums: disc-3 (outline) */
    val TabAlbumsOutline: ImageVector by lazy {
        lucideTabIcon("bc_tab_albums_outline") {
            lucideStroke {
                circleSubpath(12f, 12f, 10f)
                circleSubpath(12f, 12f, 2f)
                moveTo(6f, 12f); curveTo(6f, 10.3f, 6.7f, 8.8f, 7.8f, 7.8f)
                moveTo(18f, 12f); curveTo(18f, 13.7f, 17.3f, 15.2f, 16.2f, 16.2f)
            }
        }
    }

    /** ④ Albums: disc-3 (expanded +1.1 outer silhouette + true transparent spindle hole & highlight arcs) */
    val TabAlbumsFilled: ImageVector by lazy {
        lucideTabIcon("bc_tab_albums_filled") {
            path(fill = stroke, pathFillType = PathFillType.EvenOdd) {
                circleSubpath(12f, 12f, 11.1f)
                circleSubpath(12f, 12f, 2.1f)
                // Top-left 45deg rounded capsule groove (radius 6 +/- 0.9)
                moveTo(5.1f, 12f)
                arcToRelative(6.9f, 6.9f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2.021f, -4.879f)
                arcToRelative(0.9f, 0.9f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.273f, 1.273f)
                arcToRelative(5.1f, 5.1f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.494f, 3.606f)
                arcToRelative(0.9f, 0.9f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.8f, 0f)
                close()
                // Bottom-right 45deg rounded capsule groove (radius 6 +/- 0.9)
                moveTo(18.9f, 12f)
                arcToRelative(6.9f, 6.9f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2.021f, 4.879f)
                arcToRelative(0.9f, 0.9f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.273f, -1.273f)
                arcToRelative(5.1f, 5.1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 1.494f, -3.606f)
                arcToRelative(0.9f, 0.9f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.8f, 0f)
                close()
            }
        }
    }

    /** ⑤ Artists: user-round (outline) */
    val TabArtistsOutline: ImageVector by lazy {
        lucideTabIcon("bc_tab_artists_outline") {
            lucideStroke {
                circleSubpath(12f, 8f, 5f)
                moveTo(20f, 21f)
                arcToRelative(8f, 8f, 0f, isMoreThanHalf = false, isPositiveArc = false, -16f, 0f)
            }
        }
    }

    /** ⑤ Artists: user-round (shoulder expanded +1.1 & carved by r=7.5 head gap + filled head on top) */
    val TabArtistsFilled: ImageVector by lazy {
        lucideTabIcon("bc_tab_artists_filled") {
            // Lower shoulder contour clipped cleanly by Circle(12, 8, r = 7.5)
            path(fill = stroke) {
                moveTo(17.122f, 13.478f)
                arcTo(9.1f, 9.1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 21.1f, 21f)
                arcTo(1.1f, 1.1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20f, 22.1f)
                lineTo(4f, 22.1f)
                arcTo(1.1f, 1.1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2.9f, 21f)
                arcTo(9.1f, 9.1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 6.878f, 13.478f)
                arcTo(7.5f, 7.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, 17.122f, 13.478f)
                close()
            }
            // Head rendered on top
            lucideFillAndStroke {
                circleSubpath(12f, 8f, 5f)
            }
        }
    }

    /** ⑥ Album Artists: users (outline) */
    val TabAlbumArtistsOutline: ImageVector by lazy {
        lucideTabIcon("bc_tab_album_artists_outline") {
            lucideStroke {
                moveTo(16f, 21f); lineTo(16f, 19f)
                arcToRelative(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = false, -4f, -4f)
                lineTo(6f, 15f)
                arcToRelative(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = false, -4f, 4f)
                lineTo(2f, 21f)
                moveTo(16f, 3.128f)
                arcToRelative(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 7.744f)
                moveTo(22f, 21f); lineTo(22f, 19f)
                arcToRelative(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = false, -3f, -3.87f)
                circleSubpath(9f, 7f, 4f)
            }
        }
    }

    /** ⑥ Album Artists: users (front person filled with 2.2 stroke, back person outline only) */
    val TabAlbumArtistsFilled: ImageVector by lazy {
        lucideTabIcon("bc_tab_album_artists_filled") {
            lucideStroke {
                moveTo(16f, 3.128f)
                arcToRelative(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 7.744f)
                moveTo(22f, 21f); lineTo(22f, 19f)
                arcToRelative(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = false, -3f, -3.87f)
            }
            lucideFillAndStroke {
                circleSubpath(9f, 7f, 4f)
                moveTo(16f, 21f); lineTo(16f, 19f)
                arcToRelative(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = false, -4f, -4f)
                lineTo(6f, 15f)
                arcToRelative(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = false, -4f, 4f)
                lineTo(2f, 21f)
                close()
            }
        }
    }

    /** ⑦ Folders: folder-closed (outline) */
    val TabFoldersOutline: ImageVector by lazy {
        lucideTabIcon("bc_tab_folders_outline") {
            lucideStroke {
                moveTo(20f, 20f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2f, -2f)
                lineTo(22f, 8f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, -2f, -2f)
                lineTo(12.1f, 6f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.69f, -0.9f)
                lineTo(9.6f, 3.9f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 7.93f, 3f)
                lineTo(4f, 3f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, -2f, 2f)
                lineTo(2f, 18f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2f, 2f)
                close()
                moveTo(2f, 10f); lineTo(22f, 10f)
            }
        }
    }

    /** ⑦ Folders: folder-closed (+1.1 outer contour split by true transparent y=10 gap) */
    val TabFoldersFilled: ImageVector by lazy {
        lucideTabIcon("bc_tab_folders_filled") {
            path(fill = stroke) {
                // Upper lid above transparent divider (y <= 9.1)
                moveTo(0.9f, 9.1f)
                lineTo(0.9f, 5f)
                arcTo(3.1f, 3.1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 4f, 1.9f)
                lineTo(7.93f, 1.9f)
                arcTo(3.1f, 3.1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 10.51f, 3.285f)
                lineTo(11.32f, 4.485f)
                arcTo(0.9f, 0.9f, 0f, isMoreThanHalf = false, isPositiveArc = false, 12.1f, 4.9f)
                lineTo(20f, 4.9f)
                arcTo(3.1f, 3.1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 23.1f, 8f)
                lineTo(23.1f, 9.1f)
                close()
                // Lower folder body below transparent divider (y >= 10.9)
                moveTo(0.9f, 10.9f)
                lineTo(23.1f, 10.9f)
                lineTo(23.1f, 18f)
                arcTo(3.1f, 3.1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 20f, 21.1f)
                lineTo(4f, 21.1f)
                arcTo(3.1f, 3.1f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.9f, 18f)
                close()
            }
        }
    }

    /** ⑧ Search: search (outline) */
    val TabSearchOutline: ImageVector by lazy {
        lucideTabIcon("bc_tab_search_outline") {
            lucideStroke {
                circleSubpath(11f, 11f, 8f)
                moveTo(21f, 21f); lineTo(16.66f, 16.66f)
            }
        }
    }

    /** ⑧ Search: search (subtle 20% lens fill + 2.2 outer ring & handle) */
    val TabSearchFilled: ImageVector by lazy {
        lucideTabIcon("bc_tab_search_filled") {
            path(fill = stroke, fillAlpha = 0.2f) {
                circleSubpath(11f, 11f, 8f)
            }
            lucideStroke {
                circleSubpath(11f, 11f, 8f)
                moveTo(21f, 21f); lineTo(16.66f, 16.66f)
            }
        }
    }

    /** ⑨ Playlists: list-music (outline) */
    val TabPlaylistsOutline: ImageVector by lazy {
        lucideTabIcon("bc_tab_playlists_outline") {
            lucideStroke {
                moveTo(16f, 5f); lineTo(3f, 5f)
                moveTo(11f, 12f); lineTo(3f, 12f)
                moveTo(11f, 19f); lineTo(3f, 19f)
                moveTo(21f, 16f); lineTo(21f, 5f)
                circleSubpath(18f, 16f, 3f)
            }
        }
    }

    /** ⑨ Playlists: list-music (filled note head retaining 2.2 stroke + list bars & stem) */
    val TabPlaylistsFilled: ImageVector by lazy {
        lucideTabIcon("bc_tab_playlists_filled") {
            lucideFillAndStroke {
                circleSubpath(18f, 16f, 3f)
            }
            lucideStroke {
                moveTo(16f, 5f); lineTo(3f, 5f)
                moveTo(11f, 12f); lineTo(3f, 12f)
                moveTo(11f, 19f); lineTo(3f, 19f)
                moveTo(21f, 16f); lineTo(21f, 5f)
            }
        }
    }
}
