package com.music.bitchord.ui.player

import com.music.bitchord.R
import com.music.bitchord.ui.components.ExplicitSongTitle

import android.database.ContentObserver
import android.graphics.Bitmap
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.LruCache
import android.view.View
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.animation.core.animateDpAsState
import kotlinx.coroutines.delay
import kotlin.math.abs
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.music.bitchord.feature.localmusic.data.LocalFavoritesStore
import com.music.bitchord.data.lyrics.LyricsFurigana
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.structuralEqualityPolicy
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.util.lerp
import androidx.compose.ui.unit.lerp
import com.music.bitchord.ui.player.LocalPlayerDock
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.music.bitchord.ui.theme.SystemBarIcons
import com.music.bitchord.ui.rememberIsForeground
import com.music.bitchord.ui.theme.rememberArtworkPalette
import com.music.bitchord.ui.components.ArtworkBackdrop
import com.music.bitchord.ui.components.thumbnailBorder
import com.music.bitchord.ui.components.FrostedGlassBackdrop
import com.music.bitchord.ui.components.optimizedHazeEffect
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.ui.icons.PlayerControlIcons
import androidx.compose.runtime.key
import androidx.compose.ui.semantics.selected
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.NerdStats
import com.music.bitchord.data.canvas.CanvasArtwork
import com.music.bitchord.data.canvas.CanvasRepository
import com.music.bitchord.data.lyrics.CharGrowth
import com.music.bitchord.data.lyrics.GrowingWord
import com.music.bitchord.data.lyrics.LrcParser
import com.music.bitchord.data.lyrics.LyricAlignment
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricsStripText
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.ui.components.LyricsLogConsole
import com.music.bitchord.ui.player.components.DoubleTapSeekArea
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.LyricsTextAlignment
import com.music.bitchord.data.settings.SeekButtonMode
import com.music.bitchord.data.settings.PlayerControl
import com.music.bitchord.data.settings.PlayerQuickAction
import com.music.bitchord.data.settings.AudioQuality
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.PLAYER_ART_PX
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.feature.localmusic.coil.LocalAudioArtworkFetcher
import com.music.bitchord.playback.SavedQueue
import com.music.bitchord.playback.SavedQueueStore
import com.music.bitchord.playback.cast.CastQuickActionButton
import com.music.bitchord.feature.localsongactions.ui.components.LocalSleepTimerPicker
import com.music.bitchord.playback.toMediaItem
import com.music.bitchord.data.model.durationMillis
import com.music.bitchord.playback.BACK_RESTARTS_AFTER_MS
import com.music.bitchord.playback.autoplaySectionStart
import com.music.bitchord.playback.queueInitialVisibleIndex
import kotlinx.coroutines.launch
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.unit.IntSize
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/** Collapsed-header geometry, shared by the layout and its animation. */
/**
 * Comfortably over the sleeve's drawn size on a phone, without wasting bytes.
 *
 * A rung on the app-wide ladder rather than a number of the player's own, so a
 * large home-screen widget asks for the same copy — see [PLAYER_ART_PX].
 */
private const val ART_PX = PLAYER_ART_PX

/**
 * How many further goes a cover that failed to load gets.
 *
 * Small on purpose. This is here for the connection that drops for a moment or
 * the request that loses a race with the app coming back to the foreground, not
 * for a track whose artwork has genuinely gone: past a few tries the answer is
 * not going to change, and the placeholder tile is the honest thing to draw.
 */
private const val ART_RETRIES = 3

/** How long to leave it before trying a failed cover again. */
private const val ART_RETRY_DELAY_MS = 1_500L

/**
 * How long a canvas lookup waits for the track's album name before giving up
 * on it. Long enough to cover the album lookup on a normal connection, short
 * enough not to be noticed on a track that has no album to find.
 */
private const val ALBUM_SETTLE_MS = 700L

/** Long enough for the post-upgrade rollback cue to be noticed without lingering. */
private const val REVERT_CUE_MS = 2_600L

/**
 * How close the player's reported position has to get to a released scrub
 * handle before the handle stops being drawn where it was dropped. Wide enough
 * to swallow a coarse progress tick, tight enough that the handle doesn't hand
 * over while it is still visibly wrong.
 */
private const val SEEK_SETTLE_TOLERANCE_MS = 1_500L

/**
 * How long that handle is held at the drop point regardless. A backstop, not a
 * schedule: a seek normally settles in a tick or two, and this only decides how
 * long a seek that never settles can freeze the bar for. Generous enough that a
 * slow buffer still hands over smoothly rather than snapping back.
 */
private const val SEEK_SETTLE_TIMEOUT_MS = 1_500L

private val THUMB_SIZE = 54.dp
private val HEADER_HEIGHT = 60.dp
private val ART_TITLE_GAP = 20.dp
/**
 * How long the sleeve takes to travel the whole way between the full player and
 * the queue's header.
 *
 * Spent in proportion rather than in full: a drag released four fifths of the
 * way up has a fifth of the journey left and gets a fifth of the time for it.
 * Only the toggle, which travels end to end, ever spends all of it.
 */
private const val QUEUE_TRAVEL_MS = 420
/**
 * How far up the sleeve has to have been dragged for a release to carry on
 * opening the queue rather than falling back, as a share of the sleeve's travel.
 *
 * Well under half, because the gesture is only ever *started* deliberately —
 * there is nothing else an upward drag on the artwork could have meant — so the
 * doubt a halfway line exists to settle isn't there.
 */
private const val QUEUE_CARRY_FRACTION = 0.3f
/**
 * How fast a release has to be moving, in pixels a second, to decide the queue
 * on its own and overrule [QUEUE_CARRY_FRACTION].
 *
 * A flick is a whole gesture in its own right: it says "open" without ever
 * asking the finger to travel, and the distance it covered is beside the point.
 */
private const val QUEUE_FLICK_VELOCITY = 450f
/**
 * The handle strip above the artwork, which always hands drags to the sheet.
 *
 * It isn't the only place that does — the artwork and the credits under it pass
 * theirs on as well, which is what makes the whole top of the player closable
 * rather than just its topmost 32dp. See the dismiss band in `NowPlayingScreen`.
 */
private val DISMISS_STRIP_HEIGHT = 32.dp
/** The breathing room above the sleeve, needed twice: once to apply, once to measure past. */
private val ART_BOX_TOP_PAD = 8.dp
/**
 * Share of the motion-artwork banner's height given over to its dissolve.
 *
 * Generous on purpose: the banner has no card edge to stop at, so anything
 * short enough to still be reading as artwork where it ends reads as a picture
 * that was cut off rather than one that ran out.
 */
private const val HERO_FADE_FRACTION = 0.42f

/**
 * How often the backdrop re-reads the colours of a playing Canvas clip.
 *
 * Every three seconds, with `MESH_FADE_MS` easing each read into the last so
 * the backdrop arrives at its new colour rather than cutting to it. The read is
 * the expensive half — a texture readback off the GPU — and this is the number
 * that decides how many of them there are; the fade is the cheap half and is
 * over well inside the gap, which leaves the backdrop still for most of it.
 */
private const val MESH_REFRESH_MS = 3_000L

/** The player's side margin. Scrollable panels reach back across it. */
private val PLAYER_GUTTER = 30.dp
/**
 * How wide the player's content is ever allowed to get. A sleeve and a volume
 * slider stretched right across a tablet aren't a bigger player, just a coarser
 * one; past this the column stops growing and centres itself instead. Phones
 * are narrower than this, so for them it does nothing.
 */
private val PLAYER_MAX_WIDTH = 560.dp
/**
 * The pane's share of a window wide enough to dock in, and the bounds it takes
 * that share within.
 *
 * A fraction alone hands a 13in screen half a metre of player. The ceiling is a
 * phone's width because that is the shape the player was drawn for and the shape
 * it looks right in — a square sleeve, one line of credits, a row of oversized
 * glyphs. Widened past that the sleeve stops being able to grow with it (it is
 * bounded by the pane's height long before that) and all the extra pane buys is
 * a scrubber and a volume slider stretched thin either side of it, which is a
 * coarser player rather than a bigger one, and a column of feed given up to pay
 * for it. The floor is there because the fraction of the narrowest window that
 * qualifies is thinner than the controls want to be.
 */
private const val DOCKED_PLAYER_FRACTION = 0.42f
private val DOCKED_PLAYER_MIN_WIDTH = 340.dp
private val DOCKED_PLAYER_MAX_WIDTH = 420.dp

/**
 * The narrowest the page is worth leaving while the player stands beside it.
 *
 * About a small phone: below this the shelves stop showing a second card, the
 * track rows lose their artist line to the ellipsis and a two-pane layout is
 * two things done badly instead of one done well.
 */
private val DOCKED_PAGE_MIN_WIDTH = 360.dp
/**
 * The room above a docked player's artwork, in place of the drag handle.
 *
 * The handle is a promise that the player can be pulled away, and a pane it is
 * pinned in cannot be — so what is left is the gap it used to sit in, minus the
 * strip the gesture needed.
 */
private val DOCKED_TOP_PAD = 12.dp
/**
 * How far a tall screen is allowed to push the transport from the blocks either
 * side of it.
 *
 * The spare height has to land somewhere, and above and below the play button is
 * where it reads as room rather than as a hole. Past this it stops reading as one
 * group of controls, so the rest goes back to the artwork block.
 */
// The beta layout grants none: its controls keep their natural gaps at the
// foot of the screen, and the spare height goes above them instead.
private val CONTROL_GAP_SPREAD_MAX = 48.dp

/**
 * Beta layout: the gap from the title row down to the scrubber, which the
 * mini lyrics line used to fill. The scrubber's touch target adds a little
 * more to what is seen.
 */
private val BETA_TITLE_SCRUBBER_GAP = 0.dp

/**
 * Beta layout: what the mini lyrics line took (36dp, plus the 18dp it sat
 * below the title row), handed to the transport instead — half above it,
 * half below. The title row stays where it was; the scrubber moves up.
 */
private val BETA_TRANSPORT_EXTRA = 27.dp
/**
 * The spread [NowPlayingScreen] settled on the last time it was laid out.
 *
 * It follows from the window, so it is very nearly the same answer on every open
 * — and the player is torn down with its sheet, so without this the first frame
 * of each open would show the unspread gaps and then step to the real ones. Only
 * a head start: the frame after re-derives it either way. A plain var because
 * that is all it is, a cache of a measurement, not state anything observes.
 */
private var lastControlSpread: Dp = 0.dp
private var lastHeroHeight: Dp = 0.dp
private var lastHeroSettled = false
private var lastPlayerBounds = IntSize.Zero

/**
 * How much of the sheet's travel the rest of the player has gone by before
 * the artwork arrives at the mini player: it is fully faded with this share of
 * the way still to go.
 */
private const val DOCK_FADE_LEAD = 0.15f

/** The least the faded player is drawn at while docking — see the player's fade. */
private const val DOCK_FADE_FLOOR = 1f / 255f

/**
 * What one frame of docking into the mini player worked out — see
 * [PlayerDock]. Written while the sleeve is laid out and read further down
 * the same frame, by the tile inside the sleeve and by the player's draw; plain
 * fields rather than state, because nothing should recompose or re-lay out for
 * them — the sheet's offset already drives every frame that reads them.
 */
private class DockFrame {
    /**
     * Where the player sits on screen with the sheet fully open. State, the
     * one field here that is: it arrives a frame after the sheet first lays
     * out, and the frame it arrives in has to hear about it.
     */
    var anchor: Offset? by mutableStateOf(null)
    /** The player's size [anchor] was measured at. */
    var anchorFor = IntSize.Zero
    /** The mini player's cover, in the sleeve's box. */
    var miniInBox: Rect? = null
    /** The sheet's offset this frame, in the whole pixels it is placed at. */
    var offset = 0f
    /** How much of that the artwork rides — see `dockRide`. */
    var ride = 0f
    /** The sleeve as laid out, in the same box. */
    var sleeve: Rect = Rect.Zero
    /**
     * The sleeve's corner as a shape, made again only when its radius moves:
     * both of the sleeve's clipping layers ask for it, on every frame of any
     * transition, and an unchanged radius needs no new shape.
     */
    private var cornerRadius = Dp.Unspecified
    private var corner: Shape = RectangleShape
    fun cornerShape(radius: Dp): Shape {
        if (radius != cornerRadius) {
            cornerRadius = radius
            corner = RoundedCornerShape(radius)
        }
        return corner
    }
    /** Where the sleeve's pixels land in the player, for drawing it on top. */
    var portalLeft = 0f
    var portalTop = 0f
    var portalScale = 1f
    /** The player itself, for placing the sleeve within it. */
    var host: LayoutCoordinates? = null
}

/** The placeholder tile's corners — one shape, not a new one every frame it moves. */
private val TileShape = RoundedCornerShape(8.dp)

/**
 * How long the shuffle glyph ignores further taps after one lands.
 *
 * Toggling shuffle rewrites the live queue one [Player.moveMediaItem] at a time,
 * and every move runs the timeline listeners — the queue panel, the snapshot
 * save, the notification. A held-down finger can post those faster than a frame
 * takes to draw, and the whole player stutters. One tap is all a toggle can
 * usefully mean anyway, so the rest are dropped rather than queued behind it.
 */
private const val SHUFFLE_TAP_WINDOW_MS = 400L
/**
 * The same gate for AutoPlay, held longer because its work is heavier: the
 * toggle crosses to the playback service, tears down the in-flight suggestion
 * load, and then either strips AutoPlay's tracks out of the queue or goes back
 * to the network for a fresh set of them.
 */
private const val AUTOPLAY_TAP_WINDOW_MS = 700L

/**
 * Whether the player is ever narrow enough in this window to run artwork edge to
 * edge — the gate on both the motion-artwork banner and
 * [AppSettings.fullBleedArtwork]. Public so the settings sheet can leave the
 * switch out entirely where it would do nothing.
 *
 * Two ways to qualify. A window narrow enough that the player fills it is one:
 * edge to edge there means the artwork *is* the screen, which is the whole idea.
 * A window wide enough to dock the player is the other, and for the same reason
 * rather than in spite of it — the pane is a phone's width by construction (see
 * [dockedPlayerWidth]), so edge to edge inside it reads exactly as it does on a
 * phone. Only the band between the two has nothing to offer: too wide for the
 * player to fill, too narrow to stand something beside it.
 */
fun fullBleedArtworkAvailable(windowWidth: Dp): Boolean =
    playerFillsWindow(windowWidth) || dockedPlayerAvailable(windowWidth)

/**
 * Whether a player given the whole of a window this wide is still narrow enough
 * to run its artwork edge to edge.
 *
 * The player's own width is the question, always — this is just the form it takes
 * when the player *is* the window, which is the only time the window's width is
 * an answer to it. A docked pane has its own, much smaller width and does not go
 * through here.
 */
private fun playerFillsWindow(windowWidth: Dp): Boolean =
    windowWidth <= PLAYER_MAX_WIDTH + PLAYER_GUTTER * 2

/**
 * Whether [windowWidth] is enough to keep the player open beside the page rather
 * than raising it over one: the least the page can live in and the least the
 * player can live in, side by side. Stated as the sum of the two minimums rather
 * than as a number of its own, so it cannot drift out of step with either.
 *
 * [windowWidth] is the width of the *window*, and it has to be measured rather
 * than read off `Configuration.screenWidthDp` — in a freeform or desktop window
 * that can report the display instead of the window, and it lands a beat late
 * when the window is dragged. Deciding a two-pane split from a width the app
 * does not have splits it at the wrong moment and in the wrong place.
 *
 * Public because it is not only the player's business: the page it stands next
 * to loses the mini player, gets its bottom inset back, and stops being able to
 * raise the sheet at all.
 */
fun dockedPlayerAvailable(windowWidth: Dp): Boolean =
    windowWidth >= DOCKED_PAGE_MIN_WIDTH + DOCKED_PLAYER_MIN_WIDTH

/** How wide that pane is. Only meaningful where [dockedPlayerAvailable] is true. */
fun dockedPlayerWidth(windowWidth: Dp): Dp =
    (windowWidth * DOCKED_PLAYER_FRACTION)
        .coerceIn(DOCKED_PLAYER_MIN_WIDTH, DOCKED_PLAYER_MAX_WIDTH)
        // Never at the page's expense. The floor above is what the player wants;
        // this is what it may actually have, and where the two disagree the page
        // wins — [dockedPlayerAvailable] is the promise that they only disagree
        // in windows narrow enough that there is no pane at all.
        .coerceAtMost(windowWidth - DOCKED_PAGE_MIN_WIDTH)

/**
 * How far back the part of the playing line that hasn't been sung yet is held.
 *
 * The strip above the scrubber gets less of a gap than the full panel: it is
 * one line of small type with nothing around it to compare against, and taking
 * it as far down as the panel does left the words ahead of the highlight hard
 * to read at a glance.
 */
private const val UNSUNG_ALPHA = 0.45f
private const val UNSUNG_ALPHA_STRIP = 0.55f

/**
 * The bloom behind the line being sung, at its very strongest.
 */
private const val GLOW_ALPHA = 0.62f

/**
 * How far the bloom spreads off a letter. Tight, because it is a letter's worth
 * of light now rather than a word's: a wide radius on something this small is
 * a smudge behind the text instead of a glow coming off it.
 */
private val GLOW_RADIUS = 6.dp
private val GLOW_TRAIL = 62.dp
private const val GLOW_TRAIL_FLOOR = 0.55f

/**
 * Room reserved inside each copy of a line for the halo to spread into.
 */
private val GLOW_ROOM = 10.dp

/**
 * How the answering vocal is drawn: smaller than the lead and a shade behind
 * it, the way Apple Music hangs a backing line under the one it answers.
 */
private val BACKING_FONT_SIZE = 23.sp
private val BACKING_LINE_HEIGHT = 29.sp
private const val BACKING_ALPHA = 0.72f
private const val BACKING_OPEN_MS = 400

/** Where a lyric line is in its own animation; see [SweptLyricLine]. */
private const val PHASE_BEFORE = 0
private const val PHASE_MOVING = 1
private const val PHASE_AFTER = 2

/** An instrumental break that is playing, so its fill is read off the clock. */
private const val GAP_RUNNING = -1f

private const val BACKING_LEAD_MS = 250L
private const val BACKING_CLOSE_MS = 450
private val BACKING_RISE = 6.dp
private const val BACKING_REST_SCALE = 0.94f
private const val BACKING_FADE_LEAD = 1.6f

private val WIPE_FEATHER = 30.dp
private val WORD_RISE = 2.dp
private const val GROW_HEADROOM = 3f
private val DUET_LANE = 44.dp
private val GAP_ROW_HEIGHT = 40.dp
private val GAP_ROW_SPACING = 16.dp

private val LINE_FALLOFF_ALPHA = floatArrayOf(1f, 0.8f, 0.7f, 0.58f, 0.46f)
private val LINE_FALLOFF_BLUR = arrayOf(0.dp, 1.dp, 1.dp, 1.7.dp, 2.4.dp)

private const val BROWSING_ALPHA = 0.8f
private const val INACTIVE_SCALE = 0.98f
private const val PRESSED_SCALE = 0.96f

private const val GAP_DOTS = 3
private val GAP_DOT_SIZE = 13.dp
private val GAP_DOT_GAP = 5.dp
private const val GAP_DOT_REST = 0.25f
private const val GAP_REST_SCALE = 0.76f

/** How long the panel takes to settle on a new line, and how far ahead it starts. */
private const val SCROLL_LEAD_MIN_MS = 350L
private const val SCROLL_LEAD_MAX_MS = 500L

/**
 * The curve every handover runs on: away quickly, in slowly and softly.
 *
 * One curve for the lot — dimming, blurring, scaling and the scroll — so a
 * line handing over reads as a single movement rather than four that happen to
 * start together.
 */
private val LYRIC_EASING = CubicBezierEasing(0.41f, 0f, 0.12f, 0.99f)
private const val LYRIC_SETTLE_MS = 400
private const val STAGGER_STEPS = 3
private const val STAGGER_FRACTION = 0.06f

/** One handover: how far the panel is going, and how long it is taking. */
private class ScrollRun(val id: Int, val delta: Float, val durationMs: Int) {
    /** The last row to arrive does so this long after the panel sets off. */
    val spanMs: Float get() = durationMs * (1f + STAGGER_FRACTION * STAGGER_STEPS)
}

private fun scrollLead(lines: List<LyricLine>, positionMs: Long): Long {
    val current = lines.indexOfLast { it.timeMs <= positionMs }
    if (current < 0) return SCROLL_LEAD_MIN_MS
    val next = lines.getOrNull(current + 1) ?: return SCROLL_LEAD_MIN_MS
    val gap = next.timeMs - lines[current].endMs
    return gap.coerceIn(SCROLL_LEAD_MIN_MS, SCROLL_LEAD_MAX_MS)
}

/** Keep every unfinished vocal visible, including overlaps spanning more than two rows. */
private fun activeLyricRows(lines: List<LyricLine>, positionMs: Long): List<Int> {
    val latest = lines.indexOfLast { it.timeMs <= positionMs }
    if (latest < 0) return emptyList()
    return (0..latest).filter { index ->
        val line = lines[index]
        index == latest || (!line.isGap &&
            (line.hasKnownEnd || line.background?.hasKnownEnd == true) &&
            line.timeMs <= positionMs && positionMs < line.endMs)
    }
}

private inline fun DrawScope.clipShiftedDown(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    dy: Float,
    block: DrawScope.() -> Unit,
) {
    clipRect(left = left, top = top, right = right, bottom = bottom) {
        translate(top = dy, block = block)
    }
}

private const val LYRICS_UNAVAILABLE_HOLD_MS = 5_000L
private const val LYRICS_UNAVAILABLE_FADE_MS = 900
private const val LIGHT_ARTWORK_LUMINANCE_THRESHOLD = 0.45f

private val artworkLuminanceCache = LruCache<String, Float>(20)

@Composable
private fun rememberArtworkLuminance(imageUrl: String?): Float? {
    val context = LocalContext.current
    var luminance by remember(imageUrl) { mutableStateOf<Float?>(null) }

    LaunchedEffect(imageUrl) {
        luminance = null
        if (imageUrl == null) return@LaunchedEffect

        artworkLuminanceCache.get(imageUrl)?.let { cached ->
            luminance = cached
            return@LaunchedEffect
        }

        val request = ImageRequest.Builder(context)
            .data(imageUrl.artworkAt(ART_PX))
            .size(128)
            .allowHardware(false)
            .build()
        val result = SingletonImageLoader.get(context).execute(request)
        val bitmap = (result as? SuccessResult)?.image?.toBitmap()
        if (bitmap != null) {
            val lum = withContext(Dispatchers.Default) {
                bitmap.topAreaLuminance()
            }
            artworkLuminanceCache.put(imageUrl, lum)
            luminance = lum
        } else {
            // Default to dark artwork (0f) so status bar icons stay light if image fails to load
            luminance = 0f
        }
    }
    return luminance
}

private fun Bitmap.topAreaLuminance(): Float {
    val sampleHeight = (height * 0.35f).toInt().coerceIn(1, height)
    val sampleWidth = width.coerceAtLeast(1)
    val pixels = IntArray(sampleWidth * sampleHeight)
    getPixels(pixels, 0, sampleWidth, 0, 0, sampleWidth, sampleHeight)

    var totalLuminance = 0.0
    val count = pixels.size.coerceAtLeast(1)
    for (pixel in pixels) {
        val r = ((pixel shr 16) and 0xFF) / 255.0f
        val g = ((pixel shr 8) and 0xFF) / 255.0f
        val b = (pixel and 0xFF) / 255.0f
        val lum = 0.2126f * r + 0.7152f * g + 0.0722f * b
        totalLuminance += lum
    }
    return (totalLuminance / count).toFloat()
}

/**
 * Apple Music's Now Playing, closely: artwork that shrinks when paused, a
 * hairline scrubber with elapsed / remaining either side, oversized transport
 * glyphs, a volume capsule flanked by speaker icons, and lyrics / AirPlay /
 * queue along the bottom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    song: Song,
    isPlaying: Boolean,
    isLoading: Boolean,
    positionMs: Long,
    durationMs: Long,
    /** True only while this current item is the manual catalogue-audio match. */
    isAudioVersion: Boolean,
    /** A catalogue lookup is in progress for this video's manual conversion. */
    audioVersionSwitching: Boolean,
    /** The player has just swapped this item to a higher-quality source. */
    qualityUpgraded: Boolean,
    queue: List<Song>,
    queueIndex: Int,
    hasPrevious: Boolean,
    hasNext: Boolean,
    repeatMode: Int,
    shuffleEnabled: Boolean,
    autoplayEnabled: Boolean,
    signedIn: Boolean,
    likeStatus: LikeStatus,
    onToggleLike: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    /**
     * Seek to a fraction of the track, for the scrubber.
     *
     * Separate from [onSeek] because the scrubber is the one caller that knows
     * *where along the bar* it wants to go rather than a time. Converting that
     * here would use this screen's cached duration, which lags a track change by
     * however long the session takes to report the new one — long enough to drop
     * the handle on a bar still scaled to the previous song and seek to the
     * wrong fraction of the current one. The conversion belongs wherever the
     * freshest duration is.
     */
    onSeekFraction: (Float) -> Unit,
    onToggleAudioVersion: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleAutoplay: () -> Unit,
    onJumpTo: (Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onMoveInQueue: (Int, Int) -> Unit,
    onClearQueue: () -> Unit,
    onOpenMenu: () -> Unit,
    onQuickAddToPlaylist: () -> Unit,
    onQuickPlaybackTuning: () -> Unit,
    onQuickMenuAction: (PlayerQuickAction) -> Unit,
    onLoadSavedQueue: (SavedQueue) -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    lyrics: List<LyricLine>?,
    lyricsSource: LyricsSource?,
    lyricsUnavailable: Boolean,
    /** The width of the window the player is in — see [fullBleedArtworkAvailable]. */
    windowWidth: Dp,
    /** Shared with player overlays so their glass samples the visible player artwork. */
    playerHaze: HazeState,
    /**
     * Whether the player is a pane the page sits beside rather than a sheet
     * raised over it — see [dockedPlayerAvailable].
     *
     * There is no sheet under a docked player to pull away, so the handle goes
     * and with it the strip of dead space that existed to pass drags down to one.
     * The artwork is unaffected: the pane is a phone's width, so it runs the
     * cover edge to edge exactly as a phone does — see [fullBleedArtworkAvailable].
     */
    docked: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // Its own window: give the sheets and menus opened in it their glass.
    com.music.bitchord.ui.components.GlassWindowScope {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptics = rememberHaptics()
    val artUrl = song.artworkAt(ART_PX)
    val artworkPalette = rememberArtworkPalette(artUrl, dark = true, artPx = ART_PX)
    // Ask MediaStore for its already-indexed thumbnail while the full-size
    // embedded cover is being read and decoded. The player keeps the sharp
    // request below and swaps it in as soon as it is ready.
    val localPreviewArtUrl = remember(song, artUrl) {
        artUrl?.takeIf { LocalAudioArtworkFetcher.isLocalAudioUri(Uri.parse(it)) }
            ?.let { song.artworkAt(480) }
    }
    val localPreviewRequest = remember(localPreviewArtUrl) {
        localPreviewArtUrl?.let { url ->
            ImageRequest.Builder(context)
                .data(url)
                .size(480)
                .build()
        }
    }
    // Keep the small local preview responsible for the backdrop as well as the
    // first frame of the cover. Asking the full-size artwork pipeline for its
    // own palette here used to leave an art-colour player black until a second,
    // larger decode had finished.
    val backdropArtworkUrl = localPreviewArtUrl ?: artUrl
    val remoteArtFallbackUrl = song.thumbnailUrl?.artworkAt(ART_PX)
        ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        ?.takeIf { it != artUrl }

    // A docked pane sits beside the page rather than covering the screen, so
    // the status bar it's under belongs to the page, not this artwork — only
    // the full-screen sheet gets to repaint it.
    val dock = LocalPlayerDock.current?.takeUnless { docked }
    if (dock != null) {
        // Attached on first placement below rather than here: until then the
        // sheet's window may not have drawn anything, and the mini player's
        // cover is the only artwork on screen.
        DisposableEffect(dock) {
            onDispose { dock.attached = false }
        }
    }
    val dockFrame = remember { DockFrame() }
    // 1 with the player fully open, falling to 0 as the sheet reaches the mini
    // player. Held at 1 — the player exactly as it always was — with no dock,
    // no cover to dock into, or before the sheet's first placement has told
    // this where on screen it is.
    //
    // The sheet's fraction is read first and always, whatever comes of it: the
    // layout and draw blocks calling this are re-run only for the state they
    // read, and one that bailed out before reading it would never hear the
    // sheet move.
    val dockT: () -> Float = {
        if (dock == null) {
            1f
        } else {
            val open = dock.openFraction()
            if (dockFrame.anchor != null && dock.hasMiniArt()) open else 1f
        }
    }
    val docking: () -> Boolean = { dockT() < 1f }
    val dockMoving by remember { derivedStateOf { docking() } }
    // The rest of the player gets out of the way a little ahead of the artwork,
    // so the cover lands on the bar rather than on a ghost of the player.
    // Quadratic easing keeps the start natural and melts the background away smoothly.
    val dockFade: () -> Float = {
        val raw = ((dockT() - DOCK_FADE_LEAD) / (1f - DOCK_FADE_LEAD)).coerceIn(0f, 1f)
        raw * raw
    }
    // The sleeve, drawn over the faded player while it docks — see the sleeve.
    val sleeveLayer = rememberGraphicsLayer()

    // The player's own end of the docking: where it sits on screen, the fade,
    // and the artwork drawn over that fade.
    val dockHost: Modifier = Modifier
        .then(
            if (dock != null) {
                Modifier.onGloballyPositioned { coordinates ->
                    dockFrame.host = coordinates
                    // Where the player would be fully open, on screen: its
                    // position now, less the sheet's offset that placed it
                    // there. Constant for the life of the sheet, and that
                    // plus the offset of any later frame is where the
                    // player is in that frame.
                    //
                    // Measured once, and again only if the player's size
                    // changes: this is called on every frame the sheet
                    // moves, with the same answer each time. Measuring it
                    // each time also walked the artwork back and forth by a
                    // pixel, the sheet rounding its offset its own way.
                    if (dockFrame.anchor != null && dockFrame.anchorFor == coordinates.size) {
                        return@onGloballyPositioned
                    }
                    val offset = dock.offset() ?: return@onGloballyPositioned
                    dockFrame.anchorFor = coordinates.size
                    dockFrame.anchor = coordinates.positionOnScreen() - Offset(0f, offset.toInt().toFloat())
                    if (!dock.attached) dock.attached = true
                }
            } else {
                Modifier
            },
        )
        // Outside the fade below, so the artwork is the one thing on the
        // player that doesn't fade on its way to the bar. Drawn on top of
        // everything while it travels, as the one thing still moving.
        .drawWithContent {
            drawContent()
            if (docking()) {
                translate(dockFrame.portalLeft, dockFrame.portalTop) {
                    val portalScale = dockFrame.portalScale
                    scale(portalScale, portalScale, pivot = Offset.Zero) { drawLayer(sleeveLayer) }
                }
            }
        }
        // Never quite zero while docking. A layer at alpha 0 is skipped
        // outright, children and all — and the sleeve records itself for
        // the draw above from inside it, so for the last stretch of the
        // way home the cover went on being drawn from a stale recording,
        // bigger and higher than the bar's. One step of 255 is nothing to
        // look at and keeps the subtree drawing.
        .graphicsLayer { alpha = if (docking()) dockFade().coerceAtLeast(DOCK_FADE_FLOOR) else 1f }

    val quietMode by AppSettings.quietMode.collectAsStateWithLifecycle()
    val showStatusBar by AppSettings.showStatusBar.collectAsStateWithLifecycle()
    val showNavigationBar by AppSettings.showNavigationBar.collectAsStateWithLifecycle()

    if (!docked) {
        val artLuminance = rememberArtworkLuminance(backdropArtworkUrl)
        val isLightArtwork = artLuminance?.let { it > LIGHT_ARTWORK_LUMINANCE_THRESHOLD } ?: false
        SystemBarIcons(dark = isLightArtwork)

        val playerView = LocalView.current
        LaunchedEffect(playerView, quietMode, showStatusBar, showNavigationBar) {
            val window = com.music.bitchord.ui.theme.findWindow(playerView)
            window?.let { win ->
                com.music.bitchord.applySystemBarPreferences(win)
            }
        }
    }

    // Kept local to the player: a modal player is not in the page's Haze
    // source tree, so it needs its own source for the same frosted material as
    // the bottom navigation pill.
    val syncedLyricsEnabled by AppSettings.syncedLyrics.collectAsStateWithLifecycle()
    val hideLyricsStatusText by AppSettings.hideLyricsStatusText.collectAsStateWithLifecycle()
    val hideLyricsSavedMessage by AppSettings.hideLyricsSavedMessage.collectAsStateWithLifecycle()
    val hideLyricsUnavailableLabel by AppSettings.hideLyricsUnavailableLabel.collectAsStateWithLifecycle()
    val showLyricsPreviousControl by AppSettings.showLyricsPreviousControl.collectAsStateWithLifecycle()
    val showLyricsPlayPauseControl by AppSettings.showLyricsPlayPauseControl.collectAsStateWithLifecycle()
    val showLyricsNextControl by AppSettings.showLyricsNextControl.collectAsStateWithLifecycle()
    val lyricsTransportControlSize by AppSettings.lyricsTransportControlSize.collectAsStateWithLifecycle()
    val lyricsTransportControlsCentered by AppSettings.lyricsTransportControlsCentered.collectAsStateWithLifecycle()
    val favoriteUsesStar by AppSettings.favoriteUsesStar.collectAsStateWithLifecycle()
    val playerQuickActions by AppSettings.playerQuickActions.collectAsStateWithLifecycle()
    val keepScreenOn by AppSettings.keepScreenOn.collectAsStateWithLifecycle()
    val keepScreenOnDuringLyrics by AppSettings.keepScreenOnDuringLyrics.collectAsStateWithLifecycle()
    val showTrackDurationInsteadOfRemaining by AppSettings.showTrackDurationInsteadOfRemaining.collectAsStateWithLifecycle()
    val artworkTapOpensLyrics by AppSettings.artworkTapOpensLyrics.collectAsStateWithLifecycle()
    val lyricsReplaceArtworkOnly by AppSettings.lyricsReplaceArtworkOnly.collectAsStateWithLifecycle()
    // Beta layout: controls gathered at the foot, no mini lyrics or volume bar,
    // bigger artwork, and lyrics opened inside the player rather than as a page.
    val betaLayout by AppSettings.betaPlayerLayout.collectAsStateWithLifecycle()
    val frostedQueueManager by AppSettings.frostedQueueManager.collectAsStateWithLifecycle()
    val frostedCompactLyrics by AppSettings.frostedCompactLyrics.collectAsStateWithLifecycle()
    val showPlayerLyricsStrip by AppSettings.showPlayerLyricsStrip.collectAsStateWithLifecycle()
    val offlineMode by AppSettings.offlineMode.collectAsStateWithLifecycle()
    val visiblePlayerControls by AppSettings.visiblePlayerControls.collectAsStateWithLifecycle()
    val playerControlOrder by AppSettings.playerControlOrder.collectAsStateWithLifecycle()
    val hidePlayerArtist by AppSettings.hidePlayerArtist.collectAsStateWithLifecycle()
    val hideUnknownPlayerArtist by AppSettings.hideUnknownPlayerArtist.collectAsStateWithLifecycle()
    val centerPlayerTrackInfo by AppSettings.centerPlayerTrackInfo.collectAsStateWithLifecycle()
    val playerDetailsVerticalMenu by AppSettings.playerDetailsVerticalMenu.collectAsStateWithLifecycle()
    val artworkBackdropBlurDp by AppSettings.artworkBackdropBlurDp.collectAsStateWithLifecycle()
    val useLibraryIconForPlaylistControl by AppSettings.useLibraryIconForPlaylistControl.collectAsStateWithLifecycle()
    val keepArtworkFullSizeWhenPaused by AppSettings.keepArtworkFullSizeWhenPaused.collectAsStateWithLifecycle()
    val hideVolumeBar by AppSettings.hideVolumeBar.collectAsStateWithLifecycle()
    val favoriteIds by LocalFavoritesStore.favoriteIds.collectAsStateWithLifecycle()
    val localUri = song.localUri
    val normalizedArtistName = song.artist.trim().lowercase(Locale.ROOT)
    val isUnknownArtist = normalizedArtistName.isBlank() || normalizedArtistName in setOf(
        "unknown artist",
        "unknown",
        "<unknown>",
        "unknown artists",
        "不明なアーティスト",
    )
    val showPlayerArtist = !hidePlayerArtist && !(hideUnknownPlayerArtist && isUnknownArtist)
    val liked = if (localUri != null) {
        localUri in favoriteIds || song.videoId in favoriteIds
    } else {
        likeStatus == LikeStatus.LIKE
    }
    val likeAvailable = localUri != null || signedIn
    val likeIcon = when {
        favoriteUsesStar && liked -> BitChordIcons.StarFilled
        favoriteUsesStar -> BitChordIcons.Star
        liked -> BitChordIcons.HeartFilled
        else -> BitChordIcons.Heart
    }
    val likeDescription = stringResource(if (liked) R.string.remove_from_liked else R.string.like)
    val toggleLike: () -> Unit = {
        if (localUri != null) LocalFavoritesStore.toggleFavorite(localUri) else onToggleLike()
        Unit
    }

    // Animated cover art: the looping video some labels publish alongside a
    // release, laid over the sleeve. A miss is the normal answer — see
    // CanvasRepository, which is also where the "is this actually the right
    // track" check lives.
    val canvasEnabled by AppSettings.animatedCanvas.collectAsStateWithLifecycle()
    val canvasOverCellular by AppSettings.canvasOverCellular.collectAsStateWithLifecycle()
    val meteredConnection by AppSettings.meteredConnection.collectAsStateWithLifecycle()
    // The switch turns the feature off outright; this is the narrower "not
    // over cellular" case — see [AppSettings.canvasOverCellular] for why a
    // clip's own loop makes that worth guarding separately from a still image.
    val canvasAllowedNow = !offlineMode && canvasEnabled && (meteredConnection != true || canvasOverCellular)
    var canvas by remember(song.videoId) { mutableStateOf<CanvasArtwork?>(null) }
    // Whether the clip actually has a frame on screen right now, and one of
    // them — used to blow the sleeve out to the full-bleed hero treatment and
    // to re-tint the backdrop off the clip's own colours rather than the
    // still sleeve's.
    var canvasRendered by remember(song.videoId) { mutableStateOf(false) }
    var canvasFrame by remember(song.videoId) { mutableStateOf<Bitmap?>(null) }
    // How much of the still artwork the clip is covering, reported by the clip
    // itself. Read from a draw scope rather than in composition: it moves every
    // frame of the fade, and the still art it governs is an AsyncImage whose
    // request is rebuilt on each pass and so would not be skipped.
    val canvasCover = remember(song.videoId) { mutableFloatStateOf(0f) }
    // The one thing about it worth recomposing for: whether the clip is opaque
    // enough that the still frame under it can go entirely. Derived, so this
    // flips twice across a fade instead of once per frame of it.
    val stillCovered by remember(song.videoId) {
        derivedStateOf { canvasCover.floatValue > 0.999f }
    }
    // v1.5's backdrop, kept behind a switch — see [AppSettings.legacyMeshGradient].
    val legacyMesh by AppSettings.legacyMeshGradient.collectAsStateWithLifecycle()
    // The backdrop's colours, taken off the artwork's own arrangement rather
    // than quantised out of it — see [ArtworkMesh].
    //
    // Only read for the backdrop that uses it. Each of these keeps a decode and
    // a pixel readback of its own on every track change, and the two answer the
    // same picture in two different ways, so whichever is not on screen is pure
    // cost — the legacy path pays [rememberArtworkColors] instead.
    val artMesh = if (legacyMesh) null else rememberArtworkMesh(backdropArtworkUrl, canvasFrame, ART_PX)
    // Asked of every clip, Spotify's Canvas and every other source alike — see
    // CanvasArtworkPlayer's refreshFrameEveryMs. A clip's own colours move as
    // it plays regardless of who published it, and the backdrop should follow.
    //
    // Often enough that the backdrop moves with the clip rather than catching up
    // with it every few seconds. What keeps that affordable is the size of each
    // read, not the number of them: the frame comes back at `frameCapturePx`
    // rather than full-bleed, and is averaged on a stride off the main thread.
    val meshRefreshMs = MESH_REFRESH_MS
    LaunchedEffect(song.videoId, song.albumName, canvasAllowedNow) {
        if (!canvasAllowedNow) {
            canvas = null
            return@LaunchedEffect
        }
        // Anything already settled for this track paints immediately: a
        // reopened player, or a track coming round again in the queue.
        canvas = CanvasRepository.cached(song) ?: canvas

        // The album name is looked up separately and lands a moment after the
        // player opens, and it is the field that makes the catalogue searches
        // match. Give it that moment: if it arrives, this effect restarts and
        // all that was spent waiting is the wait. If it never does — a track
        // with no album, or a lookup that failed — the search still goes out,
        // just a beat later, which is imperceptible for decoration.
        if (canvas == null && song.albumName == null) delay(ALBUM_SETTLE_MS)
        // Keep what an earlier pass found if this one comes back empty, rather
        // than pulling a playing clip out from under itself.
        canvas = CanvasRepository.canvasFor(song) ?: canvas
    }

    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }
    // The queue lives inside the player, Apple-style, rather than in a sheet.
    var queueOpen by remember { mutableStateOf(false) }
    val savedQueues by SavedQueueStore.queues.collectAsStateWithLifecycle()
    var savedQueueManagerOpen by remember { mutableStateOf(false) }
    var quickSleepTimerOpen by remember { mutableStateOf(false) }
    var lyricsOpen by remember { mutableStateOf(false) }
    // The classic lyrics page, and the beta layout's in-player lyrics. Only one
    // of them answers to [lyricsOpen] at a time, chosen by the layout.
    val classicLyrics = lyricsOpen && !betaLayout
    val inPlayerLyrics = lyricsOpen && betaLayout
    val inPlayerLyricsT by animateFloatAsState(
        targetValue = if (inPlayerLyrics) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "inPlayerLyrics",
    )
    var lyricsLogsOpen by remember { mutableStateOf(false) }
    val showLyricsLogsEnabled by AppSettings.showLyricsLogs.collectAsStateWithLifecycle()
    LaunchedEffect(song.videoId) {
        lyricsLogsOpen = false
    }
    LaunchedEffect(song.videoId, lyricsUnavailable) {
        if (lyricsUnavailable) {
            delay(150L)
            if (lyricsUnavailable && lyricsOpen && !betaLayout) {
                lyricsOpen = false
                lyricsLogsOpen = false
            }
        }
    }
    // A brief, non-modal confirmation that the three-dot menu now contains a
    // way back to the original YouTube rendition. The control keeps its usual
    // action — opening the menu — so the cue teaches rather than surprises.
    var showRevertCue by remember(song.videoId) { mutableStateOf(false) }
    LaunchedEffect(song.videoId, qualityUpgraded) {
        if (!qualityUpgraded) {
            showRevertCue = false
            return@LaunchedEffect
        }
        showRevertCue = true
        delay(REVERT_CUE_MS)
        showRevertCue = false
    }

    // Lyrics are meant to be read continuously, so prevent the device's
    // normal screen timeout only while this panel is visible. SideEffect keeps
    // the view in sync when a new track closes the lyrics panel as well.
    val playerView = LocalView.current
    SideEffect { playerView.keepScreenOn = (lyricsOpen && keepScreenOnDuringLyrics) || keepScreenOn }
    DisposableEffect(playerView, keepScreenOn) {
        onDispose { playerView.keepScreenOn = keepScreenOn }
    }

    if (savedQueueManagerOpen) {
        SavedQueuesDialog(
            queues = savedQueues,
            // Use the same thumbnail size, source URL and dark palette as the
            // player-details sheet so the two surfaces share one colour set.
            artworkPalette = rememberArtworkPalette(song.thumbnailUrl, dark = true, artPx = ROW_ART_PX),
            artworkUrl = song.thumbnailUrl,
            useFrostedGlass = frostedQueueManager,
            currentSongs = queue,
            currentIndex = queueIndex,
            positionMs = positionMs,
            onSave = SavedQueueStore::saveAs,
            onReplace = SavedQueueStore::replace,
            onLoad = onLoadSavedQueue,
            onDelete = { SavedQueueStore.delete(it.id) },
            onDismiss = { savedQueueManagerOpen = false },
        )
    }

    if (quickSleepTimerOpen) {
        ModalBottomSheet(
            onDismissRequest = { quickSleepTimerOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            LocalSleepTimerPicker(onBack = { quickSleepTimerOpen = false })
        }
    }

    // Back out of the lyrics panel to the player, and only from the player
    // itself out to the mini player.
    //
    // The BackHandler can't do that on its own. The player is a
    // ModalBottomSheet, and from API 33 the sheet puts its own dismiss
    // straight onto the window's OnBackInvokedDispatcher when its layout
    // attaches — at PRIORITY_DEFAULT, which is also where the dialog
    // dispatcher that every BackHandler in here feeds ends up. Equal
    // priority, and the platform picks whichever registered last: the
    // sheet's, every time. So back put the whole player away with the panel
    // still open on top of it.
    //
    // Outranking it while the panel is open is the fix, and only while it is
    // open: shut, the sheet keeps its own back handling and with it the
    // predictive-back shrink, which is the right animation for a gesture
    // that really is dismissing the player. Below 33 there is no window
    // dispatcher to outrank and the BackHandler is already the newest
    // callback on the dialog's, so it wins there unaided.
    BackHandler(enabled = lyricsOpen) {
        if (lyricsLogsOpen) {
            lyricsLogsOpen = false
        } else {
            lyricsOpen = false
        }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val view = LocalView.current
        DisposableEffect(view, lyricsOpen, lyricsLogsOpen) {
            val callback = if (lyricsOpen) {
                OverlayBack.register(view) {
                    if (lyricsLogsOpen) {
                        lyricsLogsOpen = false
                    } else {
                        lyricsOpen = false
                    }
                }
            } else {
                null
            }
            onDispose { OverlayBack.unregister(view, callback) }
        }
    }

    // Back out of the queue to the player. Same problem as lyrics: the sheet's
    // own dispatcher competes at PRIORITY_DEFAULT on API 33+, so we outrank it
    // with an overlay-priority callback while the queue is open. Below 33 the
    // BackHandler alone wins because it registers after the dialog's handler.
    BackHandler(enabled = queueOpen) { queueOpen = false }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val view = LocalView.current
        DisposableEffect(view, queueOpen) {
            val callback = if (queueOpen) {
                OverlayBack.register(view) { queueOpen = false }
            } else {
                null
            }
            onDispose { OverlayBack.unregister(view, callback) }
        }
    }

    // 0 = full sleeve, 1 = queue. Everything that moves reads off this.
    //
    // Plain state driven by an animation rather than [animateFloatAsState],
    // because it has two drivers and only one of them is an animation: the
    // toggle at the foot of the player, which travels end to end, and a finger
    // dragging the sleeve upward, which sets it outright. An animation keyed on
    // [queueOpen] cannot be pushed around mid-flight by a drag — and a drag that
    // could only move the *target* would have nothing to show for itself until
    // it was released, then jump from wherever the animation had got to.
    val queueSlide = remember { mutableFloatStateOf(0f) }
    val queueProgress = queueSlide.floatValue
    // Whether a finger is on the sleeve right now. Parks the settle below rather
    // than leaving the two to write the same value on alternate frames.
    var queueDragging by remember { mutableStateOf(false) }
    // Bumped when a drag hands the value back, so the settle runs again even
    // though [queueOpen] may not have moved: a swipe that gave up short of
    // [QUEUE_CARRY_FRACTION] has to fall back to 0 just as surely as one that
    // carried has to finish reaching 1.
    var queueReleased by remember { mutableIntStateOf(0) }
    LaunchedEffect(queueOpen, queueDragging, queueReleased) {
        if (queueDragging) return@LaunchedEffect
        val target = if (queueOpen) 1f else 0f
        val from = queueSlide.floatValue
        if (from == target) return@LaunchedEffect
        animate(
            initialValue = from,
            targetValue = target,
            animationSpec = tween(
                durationMillis = (QUEUE_TRAVEL_MS * abs(target - from)).roundToInt(),
                easing = FastOutSlowInEasing,
            ),
        ) { value, _ -> queueSlide.floatValue = value }
    }

    // Horizontal fling anywhere on the player skips tracks; the artwork
    // follows the finger so the gesture has something to hold on to.
    val swipeThreshold = with(density) { 72.dp.toPx() }
    var swipeOffset by remember { mutableFloatStateOf(0f) }
    val swipeSettle by animateFloatAsState(
        targetValue = swipeOffset,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "swipeOffset",
    )

    // After releasing the scrubber the player needs to buffer before it
    // reports the new position. Keep showing where the user dropped it so the
    // handle doesn't snap back and then jump forward once loading finishes.
    var pendingSeek by remember { mutableStateOf<Float?>(null) }

    val effectiveDurationMs = if (durationMs > 0) durationMs else song.durationMillis()
    val fraction = if (effectiveDurationMs > 0) positionMs.toFloat() / effectiveDurationMs else 0f
    val shown = when {
        scrubbing -> scrubValue
        pendingSeek != null -> pendingSeek!!
        else -> fraction.coerceIn(0f, 1f)
    }

    // Released as soon as the player's own position agrees with where the handle
    // was dropped — and unconditionally a few seconds later whether it agrees or
    // not.
    //
    // The agreement test alone is not enough, because it is the only thing that
    // ever cleared the override: if the position never passes close to the
    // target — a clamped or rejected seek, a rendition swapped underneath, a
    // progress sample that steps straight over the window — nothing releases it
    // and the handle sits frozen at the drop point for the rest of the track.
    // Audio and lyrics follow the real position perfectly throughout, so the
    // failure looks like a stuck seek bar on a track that is playing fine.
    //
    // Tolerance is absolute rather than a share of the duration: two percent is
    // a quarter-second on a jingle and twelve seconds on a long mix, and it is
    // the wall-clock gap that decides whether the handle appears to jump.
    LaunchedEffect(positionMs, effectiveDurationMs, pendingSeek) {
        val target = pendingSeek ?: return@LaunchedEffect
        if (effectiveDurationMs > 0 && abs(positionMs - (target * effectiveDurationMs).toLong()) < SEEK_SETTLE_TOLERANCE_MS) {
            pendingSeek = null
        }
    }
    LaunchedEffect(pendingSeek) {
        if (pendingSeek == null) return@LaunchedEffect
        delay(SEEK_SETTLE_TIMEOUT_MS)
        pendingSeek = null
    }
    LaunchedEffect(song.videoId) { pendingSeek = null }

    // Signature Apple Music touch: the sleeve shrinks back while paused.
    val artScale by animateFloatAsState(
        targetValue = if (keepArtworkFullSizeWhenPaused || isPlaying) 1f else 0.86f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "artScale",
    )

    val audioManager = remember(context) {
        context.getSystemService(AudioManager::class.java)
    }
    val maxVolume = remember(audioManager) {
        audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 15
    }
    val scope = rememberCoroutineScope()
    // Animatable rather than plain state: a hardware volume step is a jump of
    // 1/15th of the bar, which reads as a stutter unless it's tweened.
    val volume = remember {
        Animatable(
            (audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0).toFloat() / maxVolume,
        )
    }
    var volumeDragging by remember { mutableStateOf(false) }
    var systemVolume by remember { mutableFloatStateOf(volume.value) }

    // Glide to the level the system reports, but never fight the finger — a
    // drag writes the stream, which calls straight back through here.
    LaunchedEffect(systemVolume) {
        if (!volumeDragging) {
            volume.animateTo(systemVolume, tween(durationMillis = 220, easing = FastOutSlowInEasing))
        }
    }

    // Hardware volume keys and the system panel change the stream behind our
    // back — watch Settings for changes so the bar tracks them live.
    DisposableEffect(audioManager) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                val current = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: return
                systemVolume = current.toFloat() / maxVolume
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI,
            true,
            observer,
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    // 0 = the ordinary square sleeve, 1 = the artwork as a full-bleed banner.
    // Both states collapse the header, but the banner only ever shows over a
    // settled player: opening the queue or the lyrics hands the sleeve back its
    // card first.
    // How collapsed the sleeve is, whichever surface asked for it.
    //
    // This used to read `if (lyricsOpen) 1f else queueProgress`, which gave the
    // queue a 420ms ease and the lyrics nothing at all: opening them snapped
    // the sleeve to a thumbnail in a single frame while [heroT] — reading off
    // this same value — went on fading the banner out over the full 420. One
    // half of the artwork jumped, the other half glided after it, and the pair
    // read as a stutter rather than as either. One animation, both surfaces.
    val p by animateFloatAsState(
        targetValue = if (queueOpen || inPlayerLyrics || (classicLyrics && !lyricsReplaceArtworkOnly)) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "sleeveCollapse",
    )
    val collapseDone by remember { derivedStateOf { p >= 1f } }
    val panelFade = remember {
        Animatable(if (Snapshot.withoutReadObservation { collapseDone }) 1f else 0f)
    }
    LaunchedEffect(Unit) {
        snapshotFlow { collapseDone }.collectLatest { settled ->
            panelFade.animateTo(
                if (settled) 1f else 0f,
                tween(durationMillis = 200, easing = FastOutSlowInEasing),
            )
        }
    }
    val fullBleedArt by AppSettings.fullBleedArtwork.collectAsStateWithLifecycle()
    val keepOriginalArtworkAspectRatio by AppSettings.keepOriginalArtworkAspectRatio.collectAsStateWithLifecycle()
    var loadedArtworkAspectRatio by remember(song.videoId, artUrl) { mutableFloatStateOf(1f) }
    val animatedArtworkAspectRatio by animateFloatAsState(
        targetValue = loadedArtworkAspectRatio,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "coverAspectRatio",
    )
    // The option applies to the compact sleeve. Full-bleed artwork keeps its
    // existing edge-to-edge crop behavior.
    val preserveSleeveArtworkShape = keepOriginalArtworkAspectRatio && !fullBleedArt
    val sleeveContentScale = if (preserveSleeveArtworkShape) ContentScale.Fit else ContentScale.Crop
    val doubleTapToSeek by AppSettings.doubleTapToSeek.collectAsStateWithLifecycle()
    val seekIntervalSeconds by AppSettings.seekIntervalSeconds.collectAsStateWithLifecycle()
    val seekButtonMode by AppSettings.seekButtonMode.collectAsStateWithLifecycle()
    val hideSeekButtonLabels by AppSettings.hideSeekButtonLabels.collectAsStateWithLifecycle()
    // Full-bleed is a phone idiom, and a docked pane is a phone's width — so it
    // is asked of the player's own width rather than of the window's. Asking the
    // window is what left the pane with a square sleeve floating in a field of
    // backdrop: the window is wide, but the player in it never is.
    //
    // What the width has to rule out is a player running a foot wider than the
    // column of controls under it — edge to edge meaning "a picture, and
    // separately some controls" rather than "the artwork *is* the player". A pane
    // cannot do that; only the band between phone-width and dockable can, and
    // that is the band [fullBleedArtworkAvailable] excludes.
    //
    // One question for the still cover and the clip both, rather than two that
    // could disagree — and they did, twice over. Dissolving a TextureView's
    // bottom edge needs a RenderEffect, so below API 31 the clip was held in its
    // sleeve while the cover behind it went edge to edge, and the artwork
    // changed shape the moment a clip arrived. In the other direction the clip
    // ignored [fullBleedArt] entirely, so turning the setting off still left a
    // clip running the full screen. CanvasArtworkPlayer masks itself on every
    // API level now, and both layers answer to this.
    val heroMode = fullBleedArt && (docked || playerFillsWindow(windowWidth))
    // Whether there's a still image to blow out — a placeholder tile is a card
    // or it is nothing, and going full-bleed with one would just tint the top
    // third of the screen.
    //
    // Keyed on the artwork rather than on the track, because that is what it
    // actually describes and because only Coil can set it back to true. Two
    // tracks off one album share a cover, so skipping between them leaves the
    // request below byte-identical: the painter keeps the Success it already
    // had and never re-emits, so the `onState` that is the sole writer here
    // never fires again. Keyed on the track this reset to false and stayed
    // there, which pinned the sleeve fully opaque (see the alpha it feeds) on
    // top of an equally opaque banner — the same cover drawn twice, card and
    // full-bleed at once. Keyed on the cover there is nothing to reset: the
    // bitmap really is still loaded, so the state stays true and the two
    // layers go on trading places as they should.
    var useRemoteArtworkFallback by remember(artUrl) { mutableStateOf(false) }
    val displayedArtUrl = if (useRemoteArtworkFallback) remoteArtFallbackUrl ?: artUrl else artUrl
    // A large embedded-cover read/decode can compete with the first audio
    // buffer on slower devices. The MediaStore thumbnail is already on screen;
    // wait until ExoPlayer leaves BUFFERING before asking for the full cover.
    // Latch the decision for this track so a later brief rebuffer never swaps
    // the player back to its preview.
    var fullLocalArtworkAllowed by remember(song.videoId, artUrl) {
        mutableStateOf(!isLoading || localPreviewArtUrl == null)
    }
    LaunchedEffect(song.videoId, artUrl, isLoading) {
        if (!isLoading) {
            delay(150)
            fullLocalArtworkAllowed = true
        }
    }
    val requestedArtUrl = if (localPreviewArtUrl != null && !fullLocalArtworkAllowed) {
        localPreviewArtUrl
    } else {
        displayedArtUrl
    }
    var artLoaded by remember(requestedArtUrl) { mutableStateOf(false) }
    /**
     * Which go at this cover we are on, and the reason there is more than one.
     *
     * Coil does not retry: a request that fails is over, and the state it leaves
     * behind is the state this screen keeps until the model changes — which,
     * keyed on the cover, means until the next track. One dropped connection at
     * the wrong moment and the player showed its placeholder tile for a song it
     * would have drawn perfectly a second later, with the widget and the
     * notification both showing the cover from cache the whole time.
     *
     * Bounded and spaced, because the usual reason a cover fails is that there
     * is no network at all, and a retry per recomposition — which is what an
     * unremembered request effectively gave — is a spin, not a recovery.
     */
    var artAttempt by remember(requestedArtUrl) { mutableIntStateOf(0) }
    /**
     * The one request for this cover, built once.
     *
     * Both the sleeve and the full-bleed banner draw from it, which is what
     * their own comments claim ("one ask, one decode, one bitmap for both") and
     * what building it inline at each of them quietly failed to deliver: Coil
     * compares models to decide whether to start a new load, and two separately
     * built requests are never equal — `ImageRequest` has no `equals`, and
     * neither does the size resolver `.size()` hands it. So each was its own
     * load, and worse, *every recomposition* was another one. The player
     * recomposes at least twice a second off the position tick, and each pass
     * pushed the painter back through Loading before it settled on Success
     * again, which is exactly the [artLoaded] this screen hangs the banner, the
     * sleeve's alpha, its shadow and its placeholder icon on.
     *
     * Remembered on the cover and the attempt, so it changes when the picture
     * changes and when a retry is deliberately asked for, and at no other time.
     */
    val artRequest = remember(requestedArtUrl, artAttempt) {
        ImageRequest.Builder(context)
            .data(requestedArtUrl)
            .size(ART_PX)
            // What makes a retry a new request as far as Coil's model comparison
            // is concerned. Only from the second go onwards, so the ordinary
            // request stays byte-identical to the one the mesh and the palette
            // make of the same cover and goes on sharing their memory-cache
            // entry. The disk key is unaffected either way.
            .apply { if (artAttempt > 0) memoryCacheKeyExtra("attempt", artAttempt.toString()) }
            .build()
    }
    var artFailed by remember(requestedArtUrl) { mutableStateOf(false) }
    LaunchedEffect(requestedArtUrl, artFailed) {
        // A track with no artwork at all fails immediately and would fail
        // identically three more times: there is no request to make, so there is
        // nothing a second go could do differently.
        if (requestedArtUrl == null || !artFailed || artAttempt >= ART_RETRIES) return@LaunchedEffect
        delay(ART_RETRY_DELAY_MS)
        artFailed = false
        artAttempt++
    }
    // Sticky, unlike [artLoaded]: the banner is the shape of the player rather
    // than a property of the track in it. Waiting on each new cover would
    // collapse the banner into a card and blow it back out on every skip —
    // twice the length of the whole screen's worth of movement for a change the
    // artwork itself already announces. The frame stays; the cover arrives in
    // it, fading in as Coil fades in everywhere else.
    //
    // Latched off the clip as well as the still art, for a cover that never
    // arrives at all and leaves the banner standing on the clip alone: the clip
    // gives its frame up and takes it back every time the app leaves the screen,
    // and a banner that answered only to that would collapse behind the user's
    // back and blow itself out again in front of them on the way in.
    var heroSettled by remember { mutableStateOf(lastHeroSettled || requestedArtUrl != null) }
    LaunchedEffect(artLoaded, canvasRendered) {
        if (artLoaded || canvasRendered) {
            heroSettled = true
            lastHeroSettled = true
        }
    }
    // The clip that gets the banner, if any. Hoisted because the still frame
    // underneath keys its handover on exactly what is mounted here: both are
    // decided in the same composition pass, so opening the queue or the lyrics —
    // which takes the clip away — brings the still frame back in the very frame
    // the clip goes, instead of a frame later with the sleeve behind it still
    // transparent and no artwork anywhere.
    val heroClip = canvas?.takeIf { heroMode && p < 0.5f }
    // Whether the banner is the presentation at all: full-bleed is on, and there
    // is something to blow out. The collapse is deliberately *not* part of this
    // — see [heroVisible].
    val heroT by animateFloatAsState(
        targetValue = if (
            heroMode && (heroSettled || canvasRendered || artLoaded)
        ) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "heroCanvas",
    )

    val heroVisible = heroT * (1f - p)
    // How tall that banner is, worked out down in the layout where the sleeve's
    // own geometry is known. Seeded from [lastHeroHeight] so the banner is
    // mounted on the very first frame of a sheet open without a Frame-2 jump.
    var heroHeight by remember { mutableStateOf(lastHeroHeight) }
    var playerBounds by remember { mutableStateOf(lastPlayerBounds) }
    val statusBarTop = if (showStatusBar && !quietMode) WindowInsets.statusBars.asPaddingValues().calculateTopPadding() else 0.dp
    // What sits between the status bar and the artwork: the drag strip in a
    // sheet, plain padding in a pane. Read in three places — the strip itself,
    // the scrim drawn over it and the banner's own height — which all have to
    // agree or the artwork and the credits under it move.
    val topStrip = if (docked) DOCKED_TOP_PAD else DISMISS_STRIP_HEIGHT

    var dismissBandTop by remember { mutableFloatStateOf(0f) }
    var dismissBandBottom by remember { mutableFloatStateOf(0f) }
    // The suppressing Column's own coordinates, to put a pointer's local
    // position into the same space as the two edges above.
    var dismissBandSpace by remember { mutableStateOf<LayoutCoordinates?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged {
                playerBounds = it
                lastPlayerBounds = it
            }
            .then(dockHost),
    ) {
        val backdropModifier = Modifier.graphicsLayer {
            if (docking()) {
                val raw = ((dockT() - 0.20f) / 0.80f).coerceIn(0f, 1f)
                alpha = raw * raw
            }
        }
        if (legacyMesh) {
            Box(backdropModifier) {
                MeshGradientBackground(
                    palette = rememberArtworkColors(backdropArtworkUrl, canvasFrame),
                    trackKey = song.videoId,
                    blurRadius = artworkBackdropBlurDp.dp,
                )
            }
        } else {
            Box(backdropModifier) {
                ArtworkMeshBackdrop(
                    mesh = artMesh,
                    seam = if (heroMode) heroHeight else 0.dp,
                    blurRadius = artworkBackdropBlurDp.dp,
                )
            }
        }

        if (heroHeight > 0.dp) {
            // Motion artwork over the banner, in the same frame.
            //
            // Always composed while there's a clip to play, never gated on
            // [heroVisible]: the clip has to be mounted and decoding *before*
            // it can report the first frame that raises heroT in the first place.
            if (heroMode) {
                heroClip?.let { clip ->
                    CanvasArtworkPlayer(
                        canvas = clip,
                        isPlaying = isPlaying,
                        onRenderedChanged = { canvasRendered = it },
                        onFrameCaptured = { canvasFrame = it },
                        refreshFrameEveryMs = if (dockMoving) null else meshRefreshMs,
                        onCoverChanged = { canvasCover.floatValue = it },
                        bottomFade = HERO_FADE_FRACTION,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .height(heroHeight)
                            .hazeSource(playerHaze)
                            .graphicsLayer {
                                alpha = (1f - 2f * p).coerceIn(0f, 1f) * (if (docking()) dockFade() else 1f)
                            },
                    )
                }
            }

            // The clock, the signal bars and the drag handle are all white, and
            // the banner puts whatever the artwork happens to have up there
            // directly behind them — a bright frame or a pale sleeve leaves the
            // top of the screen unreadable. Faded in with the banner and gone
            // with it.
            if (heroVisible > 0.01f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .height(statusBarTop + topStrip)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.38f * heroVisible),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (showStatusBar && !quietMode) Modifier.statusBarsPadding() else Modifier)
                .navigationBarsPadding()
                .pointerInput(Unit) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragCancel = { swipeOffset = 0f },
                        onDragEnd = {
                            // The same two buzzes the transport glyphs give, so
                            // swiping the sleeve and tapping skip feel like one
                            // gesture with two spellings.
                            when {
                                total <= -swipeThreshold -> {
                                    haptics.play(Haptic.SkipNext)
                                    onNext()
                                }
                                total >= swipeThreshold -> {
                                    haptics.play(Haptic.SkipPrevious)
                                    onPrevious()
                                }
                            }
                            swipeOffset = 0f
                        },
                        onHorizontalDrag = { _, delta ->
                            total += delta
                            // Damped: it's a hint, not a drag-to-position.
                            swipeOffset = total * 0.35f
                        },
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The only strip that passes drags through to the sheet, so the
            // player closes from the handle and the space around it — not from
            // a stray downward swipe on the artwork or the controls. Docked
            // there is no sheet to pass anything to, so all that is left of it
            // is the room it kept above the artwork.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(topStrip),
                contentAlignment = Alignment.Center,
            ) {
                if (!docked) {
                    // Centred in the strip when it's the only thing there; nudged
                    // up when the radio caption needs the room below it.
                    Box(
                        (if (song.radioName != null) {
                            Modifier.align(Alignment.TopCenter).offset(y = 6.dp)
                        } else {
                            Modifier.align(Alignment.Center)
                        })
                            .width(38.dp)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = 0.32f)),
                    )
                    song.radioName?.let { radioName ->
                        Text(
                            text = stringResource(R.string.playing_radio, radioName),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(start = PLAYER_GUTTER, end = PLAYER_GUTTER, bottom = 1.dp),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // Swallow vertical drags before the sheet can read them as
                    // "dismiss me". Children that scroll consume first, so the
                    // lists are unaffected. This sits outside the side padding
                    // on purpose: inside it, the two gutters were left as bare
                    // sheet, and a swipe that strayed into one closed the whole
                    // player instead of scrolling the lyrics or the queue.
                    //
                    // With one hole in it, and where that hole is depends on
                    // which screen of the player is up:
                    //
                    //  * The main player — the artwork-and-credits block. Down is
                    //    left unconsumed for the sheet to dismiss with, so the
                    //    player closes from the picture as well as from the
                    //    handle; up is taken here and drags the queue in.
                    //  * The queue or the lyrics — the header those panels sit
                    //    below, and nothing else. Down closes the player, up does
                    //    nothing: there is no sleeve left to pull away from.
                    //
                    // The header is worked out from the state rather than read
                    // off the sleeve, which is the whole point of doing it here:
                    // the sleeve is still on its way for [QUEUE_TRAVEL_MS] after
                    // the queue opens, and a hole that waited for it spent that
                    // half second lying across a list the finger was already
                    // scrolling.
                    .onGloballyPositioned { dismissBandSpace = it }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            // Unconsumed on purpose, as the blanket version was:
                            // the collapsed sleeve's own clickable — the way back
                            // out of the queue — has taken the press by the time
                            // an ancestor sees it.
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val space = dismissBandSpace
                            val y = space?.localToRoot(down.position)?.y
                                ?: down.position.y
                            // A panel is up from the moment it is asked for to
                            // the moment the sleeve has finished growing back —
                            // never mind where the sleeve is in between.
                            val panelUp = queueOpen || lyricsOpen ||
                                queueSlide.floatValue > 0.01f
                            val bandTop: Float
                            val bandBottom: Float
                            if (panelUp) {
                                bandTop = space?.positionInRoot()?.y ?: 0f
                                bandBottom = bandTop +
                                    (ART_BOX_TOP_PAD + HEADER_HEIGHT).toPx()
                            } else {
                                bandTop = dismissBandTop
                                bandBottom = dismissBandBottom
                            }
                            if (y >= bandTop && y <= bandBottom) {
                                if (!panelUp) {
                                    dragQueueIn(
                                        down = down,
                                        travel = bandBottom - bandTop -
                                            HEADER_HEIGHT.toPx(),
                                        slide = queueSlide,
                                        onHold = { queueDragging = it },
                                        onSettle = { open ->
                                            if (open != queueOpen) {
                                                haptics.play(
                                                    if (open) Haptic.Expand else Haptic.Tap,
                                                )
                                                queueOpen = open
                                            }
                                            queueReleased++
                                        },
                                    )
                                }
                                return@awaitEachGesture
                            }
                            // What detectVerticalDragGestures does, minus the
                            // callbacks: cross the slop, then hold the gesture
                            // to the end so nothing downstream of the first
                            // event reaches the sheet either.
                            val drag = awaitVerticalTouchSlopOrCancellation(down.id) { change, _ ->
                                change.consume()
                            }
                            if (drag != null) verticalDrag(drag.id) { it.consume() }
                        }
                    }
                    .padding(horizontal = PLAYER_GUTTER),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            // ---- Top and centre: artwork, then the credits ----
            // Everything that changes between the artwork and the queue lives
            // in this one weighted box, so the controls below it never move.
            // Read up here rather than down by the scrubber: the stats-for-nerds
            // line now lives inside the sleeve itself, so the art Box below
            // needs these before the seek bar does.
            val showNerdStats by AppSettings.showNerdStats.collectAsStateWithLifecycle()
            val nerdStats by NerdStats.current.collectAsStateWithLifecycle()
            // Height the artwork block below turns out not to need, spent by the
            // controls at the foot of the screen. Filled in from inside the box,
            // where the sleeve's real size is known; see [lastControlSpread].
            var controlSpread by remember { mutableStateOf(lastControlSpread) }
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = PLAYER_MAX_WIDTH)
                    .fillMaxWidth()
                    .padding(top = ART_BOX_TOP_PAD, bottom = if (betaLayout) BETA_TITLE_SCRUBBER_GAP else 18.dp),
            ) {
                // The height this box would have if the controls at the foot of
                // the screen were at their natural size. They aren't: they are
                // holding [controlSpread] of extra gap, which came out of here,
                // so adding it back cancels the only thing down there that
                // depends on what is decided up here.
                //
                // The sleeve and the slack below are both worked out from this
                // rather than from the box as it actually stands, and that is
                // what keeps the hand-off from creeping. Measured off the real
                // height, granting the gaps 20dp came back as a box 20dp
                // shorter and read as a *further* 20dp going spare — so any
                // moment the controls were briefly shorter than usual (a track
                // change, where the lyric strip drops back to its loading line,
                // or coming back from the lyrics panel, where the strip is
                // rebuilt from scratch) was pocketed for good. The gaps
                // ratcheted open a little at a time and the sleeve paid for it.
                val compactLyrics = classicLyrics && lyricsReplaceArtworkOnly
                val roomy = maxHeight + if (classicLyrics && !compactLyrics) 0.dp else controlSpread
                // The sleeve is square, so it is bounded by whichever of the
                // two axes runs out first: the player's width on a phone, or —
                // on a tablet, where there is width to spare — the height left
                // over once the credits row and the gap above it have had
                // theirs. Sizing it off the width alone is what pushed the
                // credits down across the scrubber on anything but a phone.
                val wantArt = minOf(
                    maxWidth + if (betaLayout) ART_GUTTER_REACH * 2 else 0.dp,
                    roomy - ART_TITLE_GAP - HEADER_HEIGHT,
                )
                // Held to what the box has actually got, for the single frame it
                // takes the gaps below to catch up with a change in their own
                // height: a sleeve a few dp under for one frame is a better
                // failure than a credits row overhanging the lyric strip.
                val fullArt = minOf(wantArt, maxHeight - ART_TITLE_GAP - HEADER_HEIGHT)
                    .coerceAtLeast(THUMB_SIZE)
                // What's left over once the sleeve, the gap and the credits have
                // had theirs. A few dp on a phone; the better part of a
                // centimetre on anything taller, and since the group is centred,
                // half of it used to land between the credits and the lyric strip
                // as one wide hole in the middle of the controls.
                val slack = (roomy - wantArt - ART_TITLE_GAP - HEADER_HEIGHT)
                    .coerceAtLeast(0.dp)
                // Handed to the two gaps around the transport row instead, which
                // is where a tall screen should be doing its breathing.
                //
                // Assigned, not added to: [slack] is stated in terms the spread
                // cannot move, so this is the whole answer in one step, and it
                // gives the room back just as readily when the controls grow
                // into it again.
                //
                // Left alone while the lyrics panel is up: the spacers it feeds
                // aren't in the tree then, so there would be nothing to apply it.
                //
                // Granted in whole even pixels, and only when it actually moves.
                // This is a measurement feeding the layout it was measured from,
                // and [roomy] cancels that by adding the grant back — but only if
                // this pass's [maxHeight] already reflects the grant about to be
                // written, which needs the Column above to have re-measured the
                // controls at that grant already. It doesn't always have: on some
                // aspect ratios (a phone-shaped sheet as readily as a docked pane)
                // the cancellation lands a pass late, the grant overshoots, the
                // next pass corrects past it the other way, and the two chase
                // each other through the same handful of values forever instead
                // of settling — a full-amplitude standing oscillation, not the
                // single-pixel shiver this rounding alone was built to absorb.
                // See [granted] below for the fix.
                if ((!classicLyrics || compactLyrics) && p <= 0.001f) {
                    val target = with(density) {
                        val half = slack
                            .coerceAtMost(if (betaLayout) 0.dp else CONTROL_GAP_SPREAD_MAX * 2)
                            .toPx()
                            .div(2f)
                            .roundToInt()
                        (half * 2).toDp()
                    }
                    // Stepped towards [target] rather than jumped there in one
                    // grant, so a late cancellation (see above) decays instead of
                    // standing: still one pass to settle when the cancellation
                    // does land on time, and a fast-converging approach rather
                    // than a full-amplitude swing on the passes where it doesn't.
                    val isOpening = dockT() < 0.1f
                    val granted = if (isOpening) {
                        target
                    } else {
                        with(density) {
                            val steppedPx = (controlSpread.toPx() +
                                (target.toPx() - controlSpread.toPx()) * 0.4f)
                                .roundToInt()
                            steppedPx.toDp()
                        }
                    }
                    if (granted != controlSpread) {
                        SideEffect {
                            controlSpread = granted
                            lastControlSpread = granted
                        }
                    }
                }
                // The title row sits on top of the controls, and the artwork is
                // centred in what is left above it. With the full-bleed banner the
                // artwork stays at the top instead (the banner grows down from the
                // screen edge) and the spare height opens between it and the title.
                val sleeveAspectRatio = if (preserveSleeveArtworkShape) {
                    animatedArtworkAspectRatio.coerceIn(0.25f, 4f)
                } else {
                    1f
                }
                // Fit the original image proportion inside the same maximum
                // square footprint used by the old sleeve layout.
                val expandedArtWidth = fullArt * minOf(1f, sleeveAspectRatio)
                val expandedArtHeight = fullArt * minOf(1f, 1f / sleeveAspectRatio)
                val spare = (maxHeight - expandedArtHeight - ART_TITLE_GAP - HEADER_HEIGHT)
                    .coerceAtLeast(0.dp)
                val groupTop = if (betaLayout && heroMode) 0.dp else spare / 2
                // Extra room between the artwork and the title row (beta only:
                // the title row sits on the controls there).
                val titleDrop = if (betaLayout) spare - groupTop else 0.dp

                var settledArtWidth by remember { mutableStateOf(expandedArtWidth) }
                var settledArtHeight by remember { mutableStateOf(expandedArtHeight) }
                var settledGroupTop by remember { mutableStateOf(groupTop) }
                var settledTitleDrop by remember { mutableStateOf(titleDrop) }
                var settledArtStart by remember { mutableStateOf((maxWidth - expandedArtWidth) / 2) }

                if (p <= 0.001f && (!classicLyrics || compactLyrics)) {
                    settledArtWidth = expandedArtWidth
                    settledArtHeight = expandedArtHeight
                    settledGroupTop = groupTop
                    settledTitleDrop = titleDrop
                    settledArtStart = (maxWidth - expandedArtWidth) / 2
                }

                val inTransition = p > 0.001f || (classicLyrics && !compactLyrics)
                val targetArtWidth = settledArtWidth
                val targetArtHeight = settledArtHeight
                val targetGroupTop = settledGroupTop
                val targetArtStart = settledArtStart

                val artWidth = lerp(targetArtWidth, THUMB_SIZE, p)
                val artHeight = lerp(targetArtHeight, THUMB_SIZE, p)
                val artTop = lerp(targetGroupTop, 0.dp, p)
                // Expanded and height-bound, the sleeve is narrower than the
                // player and has to be centred in it; collapsed, it belongs
                // hard against the left edge with the credits beside it.
                val artStart = lerp(targetArtStart, 0.dp, p)
                val titleExpandedTop = targetGroupTop + targetArtHeight + ART_TITLE_GAP + settledTitleDrop
                // Classic: the title row glides up into the top strip with the
                // sleeve. Beta: it fades out where it is and fades in up there.
                val titleUp = betaLayout && p >= 0.5f
                val titleTop = if (betaLayout) {
                    if (titleUp) 0.dp else titleExpandedTop
                } else {
                    lerp(titleExpandedTop, 0.dp, p)
                }
                val titleStart = if (betaLayout) {
                    if (titleUp) THUMB_SIZE + 12.dp else 0.dp
                } else {
                    lerp(0.dp, THUMB_SIZE + 12.dp, p)
                }
                val titleAlpha = if (betaLayout) abs(1f - 2f * p) else 1f

                // How far down the *screen* the sleeve's bottom edge sits, which
                // is where the full-bleed banner has to stop for the credits
                // below it not to move when it appears. Everything between the
                // screen's top and this box's own top is fixed padding, so it
                // can simply be added back up rather than measured.
                val bannerBottom = statusBarTop + topStrip + ART_BOX_TOP_PAD +
                    targetGroupTop + targetArtHeight + ART_TITLE_GAP / 2
                // Held where it was while the lyrics are up.
                //
                // [groupTop] centres the block in this box's *real* height, and
                // the lyrics panel changes that height without changing anything
                // the block is made of: the spacers [controlSpread] feeds leave
                // the tree, so the box comes back that much taller and the block
                // is centred that much lower. [roomy] cancels it everywhere it
                // is read, but the centring is not read from [roomy] — nor could
                // it be, since [roomy] is deliberately the height the box *would*
                // have, and the block has to sit in the one it has.
                //
                // Nothing on screen normally notices. Once a panel is up the
                // sleeve is collapsed, so [artTop] and [titleTop] have both been
                // lerped to zero and [groupTop] is left feeding exactly one
                // thing: this. Which is the backdrop's anchor — so the whole mesh
                // slid down by half the spread as the panel opened, up to 24dp.
                // The queue never showed it because it leaves the controls, and
                // so this box's height, exactly where they were.
                //
                // Frozen rather than corrected because the value is not in
                // question — it is the same either side of the panel, and the
                // sleeve it describes is not on screen to be re-measured while
                // one is up. The first pass is exempt: a player composed with a
                // panel already open has no earlier answer to hold on to.
                //
                // Guarded, like the spread above: this runs on every pass, and a
                // state write from inside a layout is a recomposition asked for
                // from inside a layout. Writing the same answer back costs a
                // comparison here and a whole frame if it is left to the snapshot
                // to notice.
                val bannerSettled = !classicLyrics || heroHeight == 0.dp
                if (bannerSettled && bannerBottom != heroHeight) {
                    SideEffect {
                        heroHeight = bannerBottom
                        lastHeroHeight = bannerBottom
                    }
                }

                // Where the sleeve is drawn, as a function of the collapse — see
                // [p] — in this box's own pixels. Called at measure, placement
                // and draw; composition never reads it.
                //
                // Three rects rather than two. The card is the square sleeve
                // ([artStart], [artTop], [artWidth] x [artHeight]); the banner is the
                // full-bleed artwork — the player's whole width, up behind the
                // status bar, down to [heroHeight]; the thumbnail is the header.
                // [heroT] carries the expanded sleeve from the card to the
                // banner, and the collapse carries either one down to the
                // thumbnail. One layer the whole way: opening the queue or the
                // lyrics shrinks the full-bleed artwork itself into the header,
                // instead of dissolving it over a second, card-sized copy that
                // shrinks underneath.
                val bannerTopPx = with(density) {
                    -(statusBarTop.roundToPx() + topStrip.roundToPx() + ART_BOX_TOP_PAD.roundToPx())
                }
                val boxWidthPx = constraints.maxWidth
                val effectivePlayerWidth = if (playerBounds.width > 0) {
                    playerBounds.width
                } else {
                    boxWidthPx + with(density) { (PLAYER_GUTTER * 2).roundToPx() }
                }
                val effectiveHeroHeight = if (heroHeight > 0.dp) heroHeight else bannerBottom
                fun bannerShare(): Float =
                    if (effectiveHeroHeight > 0.dp && effectivePlayerWidth > 0) heroT else 0f
                fun bannerShown(): Float = bannerShare() * (1f - p)
                fun cardScale(): Float = artScale + (1f - artScale) * p
                fun cardRect(): Rect = with(density) {
                    Rect(
                        offset = Offset(artStart.roundToPx().toFloat(), artTop.roundToPx().toFloat()),
                        size = Size(artWidth.roundToPx().toFloat(), artHeight.roundToPx().toFloat()),
                    )
                }
                fun sleeveRect(): Rect {
                    val card = cardRect()
                    val share = bannerShare()
                    if (share == 0f) return card
                    val banner = with(density) {
                        Rect(
                            offset = Offset(-(effectivePlayerWidth - boxWidthPx) / 2f, bannerTopPx.toFloat()),
                            size = Size(effectivePlayerWidth.toFloat(), effectiveHeroHeight.roundToPx().toFloat()),
                        )
                    }
                    val thumbSide = with(density) { THUMB_SIZE.roundToPx().toFloat() }
                    val thumb = Rect(Offset.Zero, Size(thumbSide, thumbSide))
                    return lerp(card, lerp(banner, thumb, p), share)
                }
                val boxLeftPx = ((effectivePlayerWidth - boxWidthPx) / 2f).roundToInt()
                fun miniInBox(): Rect? {
                    if (dock == null || !docking()) return null
                    val mini = dock.miniArtOnScreen() ?: return null
                    val anchor = dockFrame.anchor ?: return null
                    val offset = dock.offset() ?: return null
                    dockFrame.offset = offset.toInt().toFloat()
                    return mini.translate(
                        -(anchor.x + boxLeftPx),
                        -(anchor.y + dockFrame.offset - bannerTopPx),
                    )
                }
                fun dockRide(sleeve: Rect, mini: Rect): Float {
                    val travel = dock?.sheetTravel ?: return 0f
                    if (travel <= 0f) return 0f
                    return ((mini.top + dockFrame.offset - sleeve.top) / travel).coerceIn(0f, 1f)
                }
                fun towardsDock(rect: Rect, mini: Rect?): Rect {
                    if (mini == null) return rect
                    val unridden = dockFrame.offset * (1f - dockFrame.ride)
                    return lerp(mini, rect.translate(0f, -unridden), dockT())
                }
                fun sleeveShown(): Float = bannerShown() * dockT()
                fun sleeveCorner(): Dp {
                    val corner = 8.dp * (1f - sleeveShown())
                    val t = dockT()
                    return if (dock == null || t >= 1f) corner else lerp(dock.miniArtCorner(), corner, t)
                }

                // The sleeve proper — card, banner and thumbnail in turn, see
                // [sleeveRect]. Separate from the slot below, which keeps the
                // card's place for the stats line and the gestures: a banner
                // is not where the stats sit, nor where a drag should start.
                Box(
                    modifier = Modifier
                        .layout { measurable, _ ->
                            val mini = miniInBox()
                            val open = sleeveRect()
                            if (mini != null) dockFrame.ride = dockRide(open, mini)
                            val rect = towardsDock(open, mini)
                            dockFrame.miniInBox = mini
                            dockFrame.sleeve = rect
                            val placeable = measurable.measure(
                                Constraints.fixed(
                                    rect.width.roundToInt().coerceAtLeast(0),
                                    rect.height.roundToInt().coerceAtLeast(0),
                                ),
                            )
                            layout(0, 0) {
                                val x = rect.left.roundToInt()
                                val y = rect.top.roundToInt()
                                dockFrame.portalLeft = (boxLeftPx + x).toFloat()
                                dockFrame.portalScale = 1f
                                dockFrame.portalTop = (y - bannerTopPx).toFloat()
                                placeable.place(x, y)
                            }
                        }
                        .drawWithContent {
                            if (docking()) {
                                sleeveLayer.record { this@drawWithContent.drawContent() }
                            } else {
                                drawContent()
                            }
                        }
                        .graphicsLayer {
                            val share = bannerShare()
                            val idle = cardScale()
                            val t = dockT()
                            val open = idle + (1f - idle) * share
                            val scale = if (t >= 1f) open else 1f + (open - 1f) * t
                            scaleX = scale
                            scaleY = scale
                            translationX = swipeSettle * (1f - p) * (1f - share) * t
                        }
                        .hazeSource(playerHaze)
                        .graphicsLayer {
                            val clipAlpha = if (heroClip != null) {
                                canvasCover.floatValue * (if (docking()) dockFade() else 1f)
                            } else {
                                0f
                            }
                            alpha = 1f - clipAlpha
                        }
                        .graphicsLayer {
                            val shown = sleeveShown()
                            shadowElevation = if (artLoaded) {
                                10.dp.toPx() * (1f - 2f * shown).coerceAtLeast(0f) * dockT()
                            } else {
                                0f
                            }
                            shape = dockFrame.cornerShape(sleeveCorner())
                            clip = artLoaded
                        }
                        .graphicsLayer {
                            shape = dockFrame.cornerShape(sleeveCorner())
                            clip = true
                            compositingStrategy = if (sleeveShown() > 0f) {
                                CompositingStrategy.Offscreen
                            } else {
                                CompositingStrategy.Auto
                            }
                        }
                        .drawWithContent {
                            drawContent()
                            val shown = sleeveShown()
                            if (shown > 0f) {
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color.Black, Color.Black.copy(alpha = 1f - shown)),
                                        startY = size.height * (1f - HERO_FADE_FRACTION),
                                        endY = size.height,
                                    ),
                                    blendMode = BlendMode.DstIn,
                                )
                            }
                        },
                ) {
                    Box(
                        modifier = Modifier
                            .layout { measurable, _ ->
                                val card = towardsDock(cardRect(), dockFrame.miniInBox)
                                val sleeve = dockFrame.sleeve
                                val placeable = measurable.measure(
                                    Constraints.fixed(
                                        card.width.roundToInt().coerceAtLeast(0),
                                        card.height.roundToInt().coerceAtLeast(0),
                                    ),
                                )
                                layout(0, 0) {
                                    placeable.place(
                                        (card.left - sleeve.left).roundToInt(),
                                        (card.top - sleeve.top).roundToInt(),
                                    )
                                }
                            }
                            .graphicsLayer {
                                val share = bannerShare()
                                val idle = cardScale()
                                val t = dockT()
                                val scale = if (t >= 1f) {
                                    idle / (idle + (1f - idle) * share)
                                } else {
                                    (1f + (idle - 1f) * t) / (1f + (idle + (1f - idle) * share - 1f) * t)
                                }
                                scaleX = scale
                                scaleY = scale
                                translationX = swipeSettle * (1f - p) * share * t
                                shape = TileShape
                                clip = share > 0f
                            }
                            .background(Color.Black.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!artLoaded && !canvasRendered) {
                            Icon(
                                imageVector = BitChordIcons.MusicNote,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.35f),
                                modifier = Modifier
                                    .size(40.dp)
                                    .graphicsLayer {
                                        val scale = 1f - 0.5f * p
                                        scaleX = scale
                                        scaleY = scale
                                    },
                            )
                        }
                    }
                    if (!useRemoteArtworkFallback) {
                        localPreviewRequest?.let { previewRequest ->
                            AsyncImage(
                                model = previewRequest,
                                contentDescription = null,
                                contentScale = sleeveContentScale,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    AsyncImage(
                        model = artRequest,
                        contentDescription = null,
                        contentScale = sleeveContentScale,
                        onState = {
                            artLoaded = it is AsyncImagePainter.State.Success
                            if (it is AsyncImagePainter.State.Success) {
                                val image = it.result.image
                                val aspectRatio = image.width.toFloat() / image.height.coerceAtLeast(1)
                                if (aspectRatio.isFinite() && aspectRatio in 0.25f..4f) {
                                    loadedArtworkAspectRatio = aspectRatio
                                }
                            }
                            if (it is AsyncImagePainter.State.Error) {
                                if (localPreviewArtUrl != null && !fullLocalArtworkAllowed) {
                                    fullLocalArtworkAllowed = true
                                    artFailed = false
                                } else if (!useRemoteArtworkFallback && remoteArtFallbackUrl != null) {
                                    useRemoteArtworkFallback = true
                                    artFailed = false
                                } else {
                                    artFailed = true
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .drawWithContent { if (artLoaded || !canvasRendered) drawContent() },
                    )

                    if (!heroMode) {
                        canvas?.takeIf { p < 0.5f }?.let { clip ->
                            CanvasArtworkPlayer(
                                canvas = clip,
                                isPlaying = isPlaying,
                                onRenderedChanged = { canvasRendered = it },
                                onFrameCaptured = { canvasFrame = it },
                                refreshFrameEveryMs = if (dockMoving) null else meshRefreshMs,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }

                // The card's slot: where the sleeve sits as a card, whether or
                // not it is being drawn as one. Holds the stats line and takes
                // the gestures, both of which belong to the card's place even
                // while the artwork runs full bleed around it.
                Box(
                    modifier = Modifier
                        .offset { IntOffset(artStart.roundToPx(), artTop.roundToPx()) }
                        .size(width = artWidth, height = artHeight)
                        .onGloballyPositioned { dismissBandTop = it.boundsInRoot().top }
                        .graphicsLayer {
                            val idle = cardScale()
                            scaleX = idle
                            scaleY = idle
                            translationX = swipeSettle * (1f - p)
                        }
                        .then(
                            if (queueOpen || lyricsOpen) {
                                Modifier.clickable {
                                    queueOpen = false
                                    lyricsOpen = false
                                }
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    var pendingSeekTargetMs by remember { mutableStateOf<Long?>(null) }
                    var lastSeekTriggerTime by remember { mutableLongStateOf(0L) }

                    LaunchedEffect(lastSeekTriggerTime) {
                        if (lastSeekTriggerTime > 0L) {
                            delay(1200L)
                            pendingSeekTargetMs = null
                        }
                    }

                    DoubleTapSeekArea(
                        enabled = doubleTapToSeek && !queueOpen && !lyricsOpen && p < 0.5f,
                        seekIntervalSeconds = seekIntervalSeconds,
                        onSingleTap = if (artworkTapOpensLyrics && !queueOpen && !lyricsOpen && p < 0.5f) {
                            {
                                queueOpen = false
                                lyricsOpen = true
                            }
                        } else {
                            null
                        },
                        onSeekRelative = { deltaSeconds ->
                            val now = SystemClock.uptimeMillis()
                            val base = if (now - lastSeekTriggerTime < 1200L && pendingSeekTargetMs != null) {
                                pendingSeekTargetMs!!
                            } else {
                                positionMs
                            }
                            val deltaMs = deltaSeconds * 1000L
                            val target = if (durationMs > 0L) {
                                (base + deltaMs).coerceIn(0L, durationMs)
                            } else {
                                (base + deltaMs).coerceAtLeast(0L)
                            }
                            pendingSeekTargetMs = target
                            lastSeekTriggerTime = now
                            onSeek(target)
                            haptics.play(if (deltaSeconds > 0) Haptic.SkipNext else Haptic.SkipPrevious)
                        },
                        shape = RoundedCornerShape(lerp(10.dp, 7.dp, p)),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (showNerdStats && p < 0.5f) {
                            val nerdStyle = MaterialTheme.typography.labelSmall.copy(
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.55f),
                                    offset = Offset(0f, 1f),
                                    blurRadius = 4f,
                                ),
                            )
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                                    .graphicsLayer { alpha = 1f - p * 2f },
                            ) {
                                nerdStats?.describe(context)?.let { stats ->
                                    Text(
                                        text = stats,
                                        style = nerdStyle,
                                        color = Color.White.copy(alpha = 0.65f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }

                // Video uploads begin as their own audio, immediately. This
                // frosted, pill-shaped control is the one explicit opt-in to a
                // catalogue match; after a successful swap it becomes Revert
                // so a bad match is one tap away from the original upload.
                if ((song.isVideo || isAudioVersion) && !lyricsOpen && p < 0.5f) {
                    VideoAudioVersionButton(
                        audioVersion = isAudioVersion,
                        loading = audioVersionSwitching,
                        onClick = onToggleAudioVersion,
                        hazeState = playerHaze,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = artTop - 20.dp),
                    )
                }

                // Sits in the gap under the sleeve, clear of its rounded
                // corners and shadow — no box, no clip, nothing for the art
                // itself to be cropped by. Just a glyph that fades in with
                // the drag to hint which way a release would skip.
                //
                // Shown under the banner as well as under the card, and it is
                // the only feedback the drag has there: a card can slide with
                // the finger, but a full-bleed image sliding would open a strip
                // of bare backdrop down one edge of the screen. It lands where
                // the banner has all but dissolved, so it reads against the
                // backdrop rather than against the artwork.
                val swipeHintProgress = (abs(swipeSettle) / swipeThreshold)
                    .coerceIn(0f, 1f) * (1f - p)
                if (swipeHintProgress > 0.01f) {
                    val showNext = swipeSettle > 0f
                    val enabled = if (showNext) hasNext else hasPrevious
                    Icon(
                        imageVector = if (showNext) BitChordIcons.TransportNext else BitChordIcons.TransportPrevious,
                        contentDescription = null,
                        tint = Color.White.copy(
                            alpha = swipeHintProgress * (if (enabled) 0.85f else 0.3f),
                        ),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = artTop + artHeight + (ART_TITLE_GAP - 16.dp) / 2)
                            .size(16.dp),
                    )
                }

                // ---- Title + menu ----
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = titleTop)
                        .padding(start = titleStart)
                        .height(HEADER_HEIGHT)
                        .graphicsLayer { alpha = titleAlpha }
                        // Where the dismiss band ends — see its top on the
                        // artwork above. Taken from the row rather than added up
                        // from the sleeve so the gap between the two is inside
                        // the band as well: it is a gap in one block, not a seam
                        // between two, and a finger should not be able to find it.
                        .onGloballyPositioned { dismissBandBottom = it.boundsInRoot().bottom },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val textScale = if (betaLayout) {
                        if (p >= 0.5f) 16f / 20f else 1f
                    } else {
                        lerp(1f, 16f / 20f, p)
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            // Beta: laid out 1/scale as wide, so once shrunk into the
                            // top strip the credits still reach the buttons instead of
                            // stopping a fifth of the way short of them.
                            .then(
                                if (betaLayout) {
                                    Modifier.layout { measurable, constraints ->
                                        val wide = (constraints.maxWidth / textScale).roundToInt()
                                        val placeable = measurable.measure(
                                            constraints.copy(minWidth = 0, maxWidth = wide),
                                        )
                                        layout(constraints.maxWidth, placeable.height) {
                                            val x = if (centerPlayerTrackInfo) (constraints.maxWidth - placeable.width) / 2 else 0
                                            placeable.place(x, 0)
                                        }
                                    }
                                } else {
                                    Modifier
                                },
                            )
                            .graphicsLayer {
                                scaleX = textScale
                                scaleY = textScale
                                transformOrigin = TransformOrigin(
                                    pivotFractionX = if (centerPlayerTrackInfo) 0.5f else 0f,
                                    pivotFractionY = 0.5f,
                                )
                            },
                        horizontalAlignment = if (centerPlayerTrackInfo) Alignment.CenterHorizontally else Alignment.Start,
                    ) {
                        // Only the title's own overflow gates the artist's stagger
                        // below — an artist line that's long on its own has no
                        // reason to wait on a title that already fits.
                        var titleOverflowing by remember { mutableStateOf(false) }
                        // Only while these credits are the screen. Collapsed into
                        // a header over the queue or the lyrics they are a label
                        // on a list, and a label that crawls pulls the eye off
                        // whatever is being read below it.
                        // The beta layout lets them run up there too, rather than
                        // leaving a long title cut off in the strip.
                        val scrolls = p < 0.01f || (betaLayout && p > 0.99f)
                        MarqueeText(
                            text = song.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            enabled = scrolls,
                            leading = if (song.isExplicit == true) {
                                { ExplicitBadge(color = Color.White) }
                            } else {
                                null
                            },
                            onOverflowChange = { titleOverflowing = it },
                            // Only the tracks YouTube hands us a browse id for
                            // lead anywhere; the rest stay plain text.
                            modifier = Modifier.opensPage(song.albumId, onOpenAlbum),
                        )
                        if (showPlayerArtist) {
                            MarqueeText(
                                text = song.artist,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.W500,
                                ),
                                color = Color.White.copy(alpha = 0.55f),
                                enabled = scrolls,
                                // A title that's also scrolling gets to go first —
                                // starting together reads as clutter, so the artist
                                // waits a beat before it joins in.
                                startDelayMillis = if (titleOverflowing) MARQUEE_ARTIST_STAGGER_MS else 0L,
                                modifier = Modifier.opensPage(song.artistId, onOpenArtist),
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))

                    Row(
                        modifier = Modifier
                            .widthIn(max = 264.dp)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        playerQuickActions.forEach { action ->
                            val titleRes = when (action) {
                                PlayerQuickAction.LYRICS -> R.string.quick_action_lyrics
                                PlayerQuickAction.ADD_TO_PLAYLIST -> R.string.quick_action_add_to_playlist
                                PlayerQuickAction.PLAYBACK_TUNING -> R.string.quick_action_playback_tuning
                                PlayerQuickAction.QUEUE -> R.string.quick_action_queue
                                PlayerQuickAction.PLAYLISTS -> R.string.quick_action_playlists
                                PlayerQuickAction.SEARCH -> R.string.quick_action_search
                                PlayerQuickAction.ALBUM -> R.string.go_to_album
                                PlayerQuickAction.ARTIST -> R.string.go_to_artist
                                PlayerQuickAction.EQUALIZER -> R.string.equalizer
                                PlayerQuickAction.CHROMECAST -> R.string.chromecast
                                PlayerQuickAction.SLEEP_TIMER -> R.string.sleep_timer
                                PlayerQuickAction.TAG_EDITOR -> R.string.tag_editor
                                PlayerQuickAction.EDIT_LYRICS -> R.string.edit_lyrics
                                PlayerQuickAction.DETAILS -> R.string.details
                                PlayerQuickAction.SHARE_FILE -> R.string.share_file
                                PlayerQuickAction.FAVORITE -> R.string.quick_action_favorite
                            }
                            val title = stringResource(titleRes)
                            if (action == PlayerQuickAction.CHROMECAST) {
                                CastQuickActionButton(
                                    onUnavailable = { onQuickMenuAction(action) },
                                )
                            } else {
                                val actionIcon = when (action) {
                                    PlayerQuickAction.LYRICS, PlayerQuickAction.EDIT_LYRICS -> Icons.AutoMirrored.Rounded.Notes
                                    PlayerQuickAction.ADD_TO_PLAYLIST -> Icons.Rounded.PlaylistAdd
                                    PlayerQuickAction.PLAYBACK_TUNING -> Icons.Rounded.Speed
                                    PlayerQuickAction.QUEUE -> Icons.AutoMirrored.Rounded.QueueMusic
                                    PlayerQuickAction.PLAYLISTS -> Icons.Rounded.LibraryMusic
                                    PlayerQuickAction.SEARCH -> BitChordIcons.Search
                                    PlayerQuickAction.ALBUM -> Icons.Rounded.Album
                                    PlayerQuickAction.ARTIST -> Icons.Rounded.Person
                                    PlayerQuickAction.EQUALIZER -> Icons.Rounded.GraphicEq
                                    PlayerQuickAction.CHROMECAST -> Icons.Rounded.Cast
                                    PlayerQuickAction.SLEEP_TIMER -> Icons.Rounded.Bedtime
                                    PlayerQuickAction.TAG_EDITOR -> Icons.Rounded.Edit
                                    PlayerQuickAction.DETAILS -> Icons.Rounded.Info
                                    PlayerQuickAction.SHARE_FILE -> Icons.Rounded.Share
                                    PlayerQuickAction.FAVORITE -> likeIcon
                                }
                                CircleGlyph(
                                    icon = actionIcon,
                                    contentDescription = title,
                                    active = when (action) {
                                        PlayerQuickAction.LYRICS -> lyricsOpen
                                        PlayerQuickAction.QUEUE -> queueOpen
                                        PlayerQuickAction.FAVORITE -> liked
                                        else -> false
                                    },
                                    enabled = action != PlayerQuickAction.FAVORITE || likeAvailable,
                                    haptic = if (action == PlayerQuickAction.FAVORITE) {
                                        if (liked) Haptic.ToggleOff else Haptic.ToggleOn
                                    } else Haptic.Tap,
                                    onClick = {
                                        when (action) {
                                            PlayerQuickAction.LYRICS -> { lyricsOpen = true; queueOpen = false }
                                            PlayerQuickAction.QUEUE -> { queueOpen = true; lyricsOpen = false }
                                            PlayerQuickAction.ADD_TO_PLAYLIST -> onQuickAddToPlaylist()
                                            PlayerQuickAction.PLAYBACK_TUNING -> onQuickPlaybackTuning()
                                            PlayerQuickAction.PLAYLISTS -> onOpenPlaylists()
                                            PlayerQuickAction.SEARCH -> onOpenSearch()
                                            PlayerQuickAction.ALBUM -> song.albumId?.takeIf(String::isNotBlank)?.let(onOpenAlbum)
                                                ?: onQuickMenuAction(action)
                                            PlayerQuickAction.ARTIST -> song.artistId?.takeIf(String::isNotBlank)?.let(onOpenArtist)
                                                ?: onQuickMenuAction(action)
                                            PlayerQuickAction.SLEEP_TIMER -> quickSleepTimerOpen = true
                                            PlayerQuickAction.FAVORITE -> toggleLike()
                                            PlayerQuickAction.EQUALIZER,
                                            PlayerQuickAction.TAG_EDITOR,
                                            PlayerQuickAction.EDIT_LYRICS,
                                            PlayerQuickAction.DETAILS,
                                            PlayerQuickAction.SHARE_FILE -> onQuickMenuAction(action)
                                            PlayerQuickAction.CHROMECAST -> Unit
                                        }
                                    },
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                    }
                    Spacer(Modifier.width(4.dp))

                    CircleGlyph(
                        icon = when {
                            showRevertCue -> Icons.AutoMirrored.Rounded.Undo
                            playerDetailsVerticalMenu -> Icons.Rounded.MoreVert
                            else -> Icons.Rounded.MoreHoriz
                        },
                        contentDescription = stringResource(R.string.more),
                        onClick = onOpenMenu,
                    )
                }

                if (classicLyrics) {
                    if (lyricsReplaceArtworkOnly) {
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(artStart.roundToPx(), artTop.roundToPx()) }
                                .size(width = artWidth, height = artHeight)
                                .zIndex(2f)
                                .clip(RoundedCornerShape(lerp(10.dp, 7.dp, p))),
                        ) {
                            if (frostedCompactLyrics) {
                                FrostedGlassBackdrop(
                                    hazeState = playerHaze,
                                    modifier = Modifier.matchParentSize(),
                                    // Keep the lyrics replacement readable while
                                    // letting the blurred player artwork show through.
                                    tint = Color.White.copy(alpha = 0.08f),
                                    tintAlpha = 0.06f,
                                )
                            } else {
                                Box(Modifier.matchParentSize().background(artworkPalette.background))
                            }
                            if (lyricsLogsOpen) {
                                LyricsLogConsole(Modifier.fillMaxSize().padding(top = 44.dp))
                            } else {
                                LyricsPanel(
                                    lines = lyrics.orEmpty(),
                                    trackKey = song.videoId,
                                    positionMs = positionMs,
                                    isPlaying = isPlaying,
                                    onSeekToLine = onSeek,
                                    lyricsUnavailable = lyricsUnavailable,
                                    modifier = Modifier.fillMaxSize().padding(top = 44.dp),
                                )
                            }
                            IconButton(
                                onClick = {
                                    lyricsLogsOpen = false
                                    lyricsOpen = false
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.45f)),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.close_lyrics),
                                    tint = Color.White,
                                    modifier = Modifier.size(21.dp),
                                )
                            }
                        }
                    } else {
                        val lyricsPanelVisible = collapseDone
                        if (lyricsPanelVisible) {
                            if (lyricsLogsOpen) {
                                // Full-screen log console — replaces the lyrics list while
                                // the debug panel is open. Same fade-in timing as the lyrics
                                // panel so the transition is identical from the user's side.
                                LyricsLogConsole(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(top = HEADER_HEIGHT)
                                        .graphicsLayer { alpha = panelFade.value },
                                )
                            } else {
                                LyricsPanel(
                                    lines = lyrics.orEmpty(),
                                    trackKey = song.videoId,
                                    positionMs = positionMs,
                                    isPlaying = isPlaying,
                                    onSeekToLine = onSeek,
                                    lyricsUnavailable = lyricsUnavailable,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(top = HEADER_HEIGHT)
                                        // Arrives once the sleeve has finished collapsing
                                        // into the header — fading lyrics in over a
                                        // sleeve still mid-collapse doubled movement in
                                        // two places on screen at once and caused stutter.
                                        .graphicsLayer { alpha = panelFade.value },
                                )
                            }
                        }
                    }
                }

                // Beta layout: the lyrics open in the same place as the queue.
                // The sleeve and title shrink into the top strip, the controls
                // stay where they are, and the lyrics run in the room between.
                if (inPlayerLyrics || inPlayerLyricsT > 0.01f) {
                    LyricsPanel(
                        lines = lyrics.orEmpty(),
                        trackKey = song.videoId,
                        positionMs = positionMs,
                        isPlaying = isPlaying,
                        onSeekToLine = onSeek,
                        lyricsUnavailable = lyricsUnavailable,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = HEADER_HEIGHT)
                            .graphicsLayer {
                                alpha = ((inPlayerLyricsT - 0.45f) / 0.55f).coerceIn(0f, 1f)
                                translationY = (1f - inPlayerLyricsT) * 26.dp.toPx()
                            },
                    )
                }

                // Toggles and the queue arrive after the sleeve has finished
                // travelling, and leave before it starts coming back.
                if (!lyricsOpen && queueProgress > 0.01f) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = HEADER_HEIGHT)
                            .graphicsLayer {
                                alpha = ((queueProgress - 0.45f) / 0.55f).coerceIn(0f, 1f)
                                translationY = (1f - queueProgress) * 26.dp.toPx()
                            },
                    ) {
                        InlineQueue(
                            queue = queue,
                            currentIndex = queueIndex,
                            autoplayEnabled = autoplayEnabled,
                            onJumpTo = onJumpTo,
                            onRemove = onRemoveFromQueue,
                            onMove = onMoveInQueue,
                            onClear = onClearQueue,
                            onManageQueues = { savedQueueManagerOpen = true },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // ---- Bottom: lyric strip, scrubber, transport, volume, toggles ----
            // One block, measured at its natural height and pinned to the foot
            // of the player. Whatever is left over above it is the artwork's,
            // which is what keeps this row of controls in the same place on
            // every screen instead of being shoved off the bottom of a tall one.
            Column(
                modifier = Modifier
                    .widthIn(max = PLAYER_MAX_WIDTH)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            // Current lyric, one line, directly above the scrubber. It stays in
            // the layout — and stays fully visible — whether or not the queue
            // is open: dropping it would shorten this block and the controls
            // under it would jump the moment the queue started sliding in, and
            // fading it away behind the queue left this the one place in the
            // player where the current line simply vanished.
            //
            val lyricsMode = classicLyrics && !lyricsReplaceArtworkOnly
            val miniLyricsVisible = showPlayerLyricsStrip && (syncedLyricsEnabled || !lyrics.isNullOrEmpty())
            if (miniLyricsVisible && !betaLayout && (!lyricsMode || p < 1f)) {
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clipToBounds()
                        .graphicsLayer {
                            alpha = ((0.30f - p) / 0.30f).coerceIn(0f, 1f)
                        }
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints.copy(minHeight = 0))
                            val height = (placeable.height * (1f - p)).roundToInt()
                            layout(placeable.width, height) {
                                placeable.placeRelative(0, 0)
                            }
                        }
                        .height(36.dp)
                        // The slider's touch target reaches ~13dp above the
                        // drawn bar, so the strip reads as further off it than
                        // it is. Nudged down into that dead space, the same way
                        // the timestamps below are pulled back up into it.
                        .offset(y = 6.dp),
                ) {
                    if (!lyrics.isNullOrEmpty()) {
                        CurrentLyricLine(
                            lines = lyrics,
                            trackKey = song.videoId,
                            positionMs = positionMs,
                            isPlaying = isPlaying,
                            durationMs = durationMs,
                            // Still visible over the queue, so still a valid way
                            // in: opens the same full lyrics panel it always has,
                            // closing the queue behind it the same way the "Up
                            // next" glyph closes lyrics behind the queue.
                            onClick = {
                                queueOpen = false
                                lyricsOpen = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else if (lyricsUnavailable) {
                        if (!hideLyricsStatusText && !hideLyricsUnavailableLabel) {
                            LyricsUnavailableLine(
                                trackKey = song.videoId,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    } else if (!hideLyricsStatusText) {
                        LyricsLoadingLine(
                            trackKey = song.videoId,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            ThinSlider(
                value = shown,
                onValueChange = {
                    scrubbing = true
                    scrubValue = it
                },
                onValueChangeFinished = {
                    // On release only. Ticking the whole way along the bar turns
                    // a scrub into a rattle, and the beat that matters is the one
                    // that says where the playhead landed.
                    haptics.play(Haptic.Select)
                    pendingSeek = scrubValue
                    onSeekFraction(scrubValue)
                    scrubbing = false
                },
            )
            val wifiQuality by AppSettings.audioQualityWifi.collectAsStateWithLifecycle()
            val cellularQuality by AppSettings.audioQualityCellular.collectAsStateWithLifecycle()
            val metered by AppSettings.meteredConnection.collectAsStateWithLifecycle()
            // Whether this playback session is even asking for a lossless
            // stream — the same computation SourceResolver.requestForNow()
            // makes, mirrored here so "Loading lossless" only appears when a
            // lossless fetch is actually in flight, not on every buffering
            // YouTube track.
            val losslessRequested =
                (if (metered == true) cellularQuality else wifiQuality) == AudioQuality.LOSSLESS
            val isLocalAudio = !song.localUri.isNullOrBlank() || !song.localPath.isNullOrBlank()
            // Whether a module is still racing YouTube for this exact track —
            // see [NerdStats.racingLossless]. YouTube can win that race and
            // already be playing while the module lookup is still running
            // detached in the background, and the badge should keep saying
            // "loading" through that stretch rather than going blank only to
            // possibly say "loading" again a moment later.
            // Whether this collapse folds the controls away (full-screen lyrics,
            // or on the way back from them) or keeps them (the queue: the
            // volume folds, the transport moves down, the toggle row stays).
            //
            // Which panel the collapse is for is remembered, not read off the
            // panels' own progress: the queue and the in-player lyrics finish
            // their slide out a frame or two before the sleeve finishes coming
            // back, and judging by their progress, those last frames looked like
            // the way back from the lyrics page and folded the controls for a
            // moment, a small jump under the scrubber on every close.
            val foldingForLyricsPage = remember { mutableStateOf(false) }
            if (lyricsMode && !foldingForLyricsPage.value) foldingForLyricsPage.value = true
            if ((queueOpen || inPlayerLyrics) && foldingForLyricsPage.value) foldingForLyricsPage.value = false
            val rowFolds = lyricsMode || (p > 0f && foldingForLyricsPage.value)
            // 0 → 1 as the queue opens. Only the queue tightens the controls:
            // the beta in-player lyrics leave every one of them where it was.
            val queueCompact = if (rowFolds || betaLayout) 0f else queueProgress.coerceIn(0f, 1f)
            val racingLossless by NerdStats.racingLossless.collectAsStateWithLifecycle()
            val stillRacing = song.videoId in racingLossless
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // The slider's touch target extends well past the drawn
                    // bar, so pull the labels back up under it. With the queue
                    // open the space they left behind is given back too, or the
                    // transport below would sit that much low of centre.
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val lift = TIMESTAMP_LIFT.roundToPx()
                        val height = placeable.height - (lift * queueCompact).roundToInt()
                        layout(placeable.width, height) {
                            placeable.placeRelative(0, -lift)
                        }
                    },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatTime((shown * effectiveDurationMs).toLong()),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.55f),
                    )
                    Text(
                        text = if (showTrackDurationInsteadOfRemaining) {
                            formatTime(effectiveDurationMs)
                        } else {
                            "-" + formatTime(effectiveDurationMs - (shown * effectiveDurationMs).toLong())
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.55f),
                    )
                }
                // Pinned to the box's own center rather than squeezed into the
                // gap between the two timestamps: that gap's width changes by
                // a digit's worth every time a minute rolls over, which was
                // dragging this along with it every tick. The screen's center
                // doesn't move.
                LosslessOrStats(
                    isLocalAudio = isLocalAudio,
                    isLoading = isLoading,
                    stillRacing = stillRacing,
                    losslessRequested = losslessRequested,
                    nerdStats = nerdStats,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 8.dp),
                )
            }

            // The full-screen lyrics header. Only lyrics open it: grown by the
            // queue's collapse too, it sat here invisible and pushed the
            // transport away from the scrubber.
            if (lyricsMode || (p > 0.001f && rowFolds)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clipToBounds()
                        .graphicsLayer {
                            val alphaVal = if (lyricsMode && collapseDone) panelFade.value else 0f
                            alpha = alphaVal
                            translationY = (1f - alphaVal) * 10.dp.toPx()
                        }
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints.copy(minHeight = 0))
                            val height = (placeable.height * (if (rowFolds) p else 0f)).roundToInt()
                            layout(placeable.width, height) {
                                placeable.placeRelative(0, 0)
                            }
                        },
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(Modifier.height(16.dp))
                // Full-width header row: optional logs icon on the far left,
                // source credit pill in the middle, close circle on the right.
                // The row stretches edge-to-edge so there is no dangling gap
                // on either side — the pill just sits between the two icons.
                val headerLabelVisible = lyricsSource != null ||
                    (lyrics.isNullOrEmpty() && !hideLyricsUnavailableLabel) ||
                    (!lyrics.isNullOrEmpty() && !hideLyricsSavedMessage)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Logs icon — only shown when the debug-log setting is on
                        // (Advanced Options in Settings). Icon-only, no label.
                        if (showLyricsLogsEnabled) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
                                    .clip(CircleShape)
                                    .background(if (lyricsLogsOpen) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.10f))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) {
                                        haptics.play(Haptic.Tap)
                                        lyricsLogsOpen = !lyricsLogsOpen
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.History,
                                    contentDescription = "Lyrics Logs",
                                    tint = if (lyricsLogsOpen) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        // Source credit pill — same style as original, but sits between
                        // the two icon buttons and fills leftover horizontal space.
                        if (headerLabelVisible) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(Color.White.copy(alpha = 0.10f))
                                    .padding(horizontal = 18.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = when {
                                        lyricsSource != null -> stringResource(R.string.lyrics_by, lyricsSource.label)
                                        lyrics.isNullOrEmpty() -> stringResource(R.string.no_lyrics_found)
                                        else -> stringResource(R.string.lyrics_saved_with_download)
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        } else {
                            // Keep the X at the trailing edge; the transport row is
                            // laid over the full-width parent below, so its centered
                            // position is independent of both the X and the log icon.
                            Spacer(Modifier.weight(1f))
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.10f))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    haptics.play(Haptic.Tap)
                                    lyricsLogsOpen = false
                                    lyricsOpen = false
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.close_lyrics),
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    if (!headerLabelVisible) {
                        Row(
                            modifier = if (lyricsTransportControlsCentered) {
                                Modifier.align(Alignment.Center)
                            } else {
                                Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 56.dp)
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (showLyricsPreviousControl) {
                                TransportGlyph(
                                    icon = BitChordIcons.TransportPrevious,
                                    contentDescription = stringResource(R.string.widget_previous),
                                    // The slider sets the row's overall size; within it the
                                    // full player's proportions hold (skip ≈ 0.7 × play).
                                    size = (lyricsTransportControlSize * LYRICS_SKIP_SCALE).dp,
                                    enabled = hasPrevious || positionMs > BACK_RESTARTS_AFTER_MS,
                                    onClick = onPrevious,
                                )
                            }
                            if (showLyricsPlayPauseControl) {
                                TransportGlyph(
                                    icon = if (isPlaying) BitChordIcons.TransportPause else BitChordIcons.TransportPlay,
                                    contentDescription = stringResource(if (isPlaying) R.string.pause else R.string.play),
                                    size = (lyricsTransportControlSize * LYRICS_PLAY_SCALE).dp,
                                    enabled = !isLoading,
                                    onClick = onPlayPause,
                                )
                            }
                            if (showLyricsNextControl) {
                                TransportGlyph(
                                    icon = BitChordIcons.TransportNext,
                                    contentDescription = stringResource(R.string.widget_next),
                                    size = (lyricsTransportControlSize * LYRICS_SKIP_SCALE).dp,
                                    enabled = hasNext,
                                    onClick = onNext,
                                )
                            }
                        }
                    }
                }
                        Spacer(Modifier.height(20.dp))
                    }
                }
            }

            if (!lyricsMode || p < 1f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clipToBounds()
                        .graphicsLayer {
                            alpha = if (rowFolds) ((0.30f - p) / 0.30f).coerceIn(0f, 1f) else 1f
                        }
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints.copy(minHeight = 0))
                            val fold = if (rowFolds) p else 0f
                            val height = (placeable.height * (1f - fold)).roundToInt()
                            layout(placeable.width, height) {
                                placeable.placeRelative(0, 0)
                            }
                        },
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // The transport rides midway between the two blocks it separates:
                        // the scrubber above it, and the volume bar and toggle row below,
                        // which sit close enough together to read as one. Both of its own
                        // gaps take half the spread, so on a tall screen it holds the
                        // centre rather than drifting up under the seek bar.
                        Spacer(
                            Modifier.height(
                                lerp(
                                    if (betaLayout) TRANSPORT_GAP_NO_VOLUME + BETA_TRANSPORT_EXTRA else 14.dp + controlSpread / 2,
                                    QUEUE_TRANSPORT_GAP,
                                    queueCompact,
                                ),
                            ),
                        )

            // ---- Transport ----
            // With the queue open it stays, moved down to sit midway between
            // the scrubber and the toggle row, so the queue can be played from.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val seekButtonsVisible = when (seekButtonMode) {
                    SeekButtonMode.OFF -> false
                    SeekButtonMode.ALWAYS -> true
                    SeekButtonMode.LONG_TRACKS_ONLY -> effectiveDurationMs >= 15L * 60L * 1000L
                }
                if (seekButtonsVisible) {
                    SeekTransportGlyph(
                        icon = Icons.Rounded.FastRewind,
                        contentDescription = stringResource(R.string.seek_back_seconds, seekIntervalSeconds),
                        label = stringResource(R.string.seek_seconds_short, seekIntervalSeconds),
                        showLabel = !hideSeekButtonLabels,
                        onClick = {
                            onSeek((positionMs - seekIntervalSeconds * 1000L).coerceAtLeast(0L))
                        },
                    )
                }
                TransportGlyph(
                    icon = BitChordIcons.TransportPrevious,
                    contentDescription = stringResource(R.string.widget_previous),
                    size = 46.dp,
                    onClick = onPrevious,
                    // Lit whenever back has something to do — either a track to
                    // step to, or enough elapsed for it to restart this one.
                    enabled = hasPrevious || positionMs > BACK_RESTARTS_AFTER_MS,
                    haptic = Haptic.SkipPrevious,
                )
                // While the stream URL resolves and buffers, the play glyph
                // would be a lie — show progress instead.
                if (isLoading) {
                    // Same footprint as TransportGlyph(62.dp) — a smaller box
                    // here would shunt everything below it on every load.
                    Box(Modifier.size(78.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(38.dp),
                        )
                    }
                } else {
                    TransportGlyph(
                        icon = if (isPlaying) BitChordIcons.TransportPause else BitChordIcons.TransportPlay,
                        contentDescription = stringResource(if (isPlaying) R.string.pause else R.string.play),
                        size = 66.dp,
                        onClick = onPlayPause,
                        haptic = if (isPlaying) Haptic.Pause else Haptic.Resume,
                    )
                }
                TransportGlyph(
                    icon = BitChordIcons.TransportNext,
                    contentDescription = stringResource(R.string.widget_next),
                    size = 46.dp,
                    onClick = onNext,
                    enabled = hasNext,
                    haptic = Haptic.SkipNext,
                )
                if (seekButtonsVisible) {
                    SeekTransportGlyph(
                        icon = Icons.Rounded.FastForward,
                        contentDescription = stringResource(R.string.seek_forward_seconds, seekIntervalSeconds),
                        label = stringResource(R.string.seek_seconds_short, seekIntervalSeconds),
                        showLabel = !hideSeekButtonLabels,
                        onClick = {
                            val end = effectiveDurationMs.takeIf { it > 0L } ?: Long.MAX_VALUE
                            onSeek((positionMs + seekIntervalSeconds * 1000L).coerceAtMost(end))
                        },
                    )
                }
            }

            // Hidden entirely rather than just faded out — with the setting
            // on, the slider takes up no space at all, so the transport and
            // the toggle row below it close the gap instead of leaving a
            // blank strip where the volume bar used to be.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .graphicsLayer { alpha = ((0.30f - queueCompact) / 0.30f).coerceIn(0f, 1f) }
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints.copy(minHeight = 0))
                        val height = (placeable.height * (1f - queueCompact)).roundToInt()
                        layout(placeable.width, height) {
                            placeable.placeRelative(0, 0)
                        }
                    },
            ) {
            if (betaLayout) {
                // No volume bar in the beta layout. The transport sits midway
                // between the scrubber and the toggle row: the timestamps' lift
                // leaves that much more above it.
                Spacer(Modifier.height(TRANSPORT_GAP_NO_VOLUME + TIMESTAMP_LIFT + BETA_TRANSPORT_EXTRA))
            } else if (hideVolumeBar) {
                Spacer(Modifier.height(24.dp + controlSpread / 2))
            } else {
                Spacer(Modifier.height(18.dp + controlSpread / 2))

                // ---- Volume ----
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.VolumeDown,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    ThinSlider(
                        value = volume.value,
                        onValueChange = {
                            volumeDragging = true
                            // Follow the finger exactly; only external changes tween.
                            scope.launch { volume.snapTo(it) }
                            audioManager?.setStreamVolume(
                                AudioManager.STREAM_MUSIC,
                                (it * maxVolume).roundToInt(),
                                0,
                            )
                        },
                        onValueChangeFinished = { volumeDragging = false },
                        idleHeight = 6.dp,
                        activeHeight = 10.dp,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    Icon(
                        Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(Modifier.height(24.dp))
            }
            }

                    }
                }
            }

            // ---- Shuffle · Repeat · Lyrics · Like · Queue · Playlists · Search ----
            // Kept outside the folding block above so it can stay while the queue
            // is open: the transport and volume fold away, this row slides up
            // under the queue, and a shuffle can be watched reordering it.
            // Full-screen lyrics still take it away along with everything else.
            if (visiblePlayerControls.isNotEmpty() && (!lyricsMode || p < 1f)) {
                val segments = buildList {
                    playerControlOrder.forEach { control ->
                        if (control !in visiblePlayerControls) return@forEach
                        when (control) {
                            PlayerControl.SHUFFLE -> add(
                                ToggleSegment(
                                    key = control,
                                    icon = PlayerControlIcons.Shuffle,
                                    contentDescription = stringResource(
                                        if (shuffleEnabled) R.string.shuffle_on else R.string.shuffle_off,
                                    ),
                                    selected = shuffleEnabled,
                                    onClick = onToggleShuffle,
                                    haptic = if (shuffleEnabled) Haptic.ToggleOff else Haptic.ToggleOn,
                                    tapWindowMs = SHUFFLE_TAP_WINDOW_MS,
                                    popOnSelect = true,
                                ),
                            )
                            PlayerControl.REPEAT -> add(
                                ToggleSegment(
                                    key = control,
                                    icon = if (repeatMode == Player.REPEAT_MODE_ONE) {
                                        PlayerControlIcons.RepeatOne
                                    } else {
                                        PlayerControlIcons.Repeat
                                    },
                                    contentDescription = when (repeatMode) {
                                        Player.REPEAT_MODE_ONE -> stringResource(R.string.repeat_one)
                                        Player.REPEAT_MODE_ALL -> stringResource(R.string.repeat_all)
                                        else -> stringResource(R.string.repeat_off)
                                    },
                                    selected = repeatMode != Player.REPEAT_MODE_OFF,
                                    onClick = onCycleRepeat,
                                    haptic = when (repeatMode) {
                                        Player.REPEAT_MODE_OFF -> Haptic.ToggleOn
                                        Player.REPEAT_MODE_ONE -> Haptic.ToggleOff
                                        else -> Haptic.Select
                                    },
                                    // Every step turns the arrows, so all → one,
                                    // which keeps the same shape, still reads as a move.
                                    spinKey = repeatMode,
                                ),
                            )
                            PlayerControl.LYRICS -> add(
                                ToggleSegment(
                                    key = control,
                                    icon = PlayerControlIcons.Lyrics,
                                    contentDescription = stringResource(R.string.quick_action_lyrics),
                                    selected = lyricsOpen,
                                    onClick = {
                                        lyricsOpen = !lyricsOpen
                                        if (lyricsOpen) queueOpen = false
                                    },
                                    haptic = if (lyricsOpen) Haptic.Tap else Haptic.Select,
                                    // No bounce: it would land on the frames that build
                                    // the panel, and the panel opening is feedback enough.
                                ),
                            )
                            PlayerControl.LIKE -> if (PlayerQuickAction.FAVORITE !in playerQuickActions) add(
                                ToggleSegment(
                                    key = control,
                                    icon = when {
                                        favoriteUsesStar && liked -> PlayerControlIcons.StarFilled
                                        favoriteUsesStar -> PlayerControlIcons.Star
                                        liked -> PlayerControlIcons.HeartFilled
                                        else -> PlayerControlIcons.Heart
                                    },
                                    contentDescription = likeDescription,
                                    selected = liked,
                                    onClick = toggleLike,
                                    enabled = likeAvailable,
                                    haptic = if (liked) Haptic.ToggleOff else Haptic.ToggleOn,
                                    popOnSelect = true,
                                ),
                            )
                            PlayerControl.QUEUE -> add(
                                ToggleSegment(
                                    key = control,
                                    icon = PlayerControlIcons.Queue,
                                    contentDescription = stringResource(R.string.up_next),
                                    selected = queueOpen,
                                    onClick = {
                                        lyricsOpen = false
                                        queueOpen = !queueOpen
                                    },
                                    haptic = if (queueOpen) Haptic.Tap else Haptic.Expand,
                                    // No bounce: it would land on the frames that build
                                    // the panel, and the panel opening is feedback enough.
                                ),
                            )
                            PlayerControl.PLAYLIST -> add(
                                ToggleSegment(
                                    key = control,
                                    icon = if (useLibraryIconForPlaylistControl) {
                                        Icons.Rounded.LibraryMusic
                                    } else {
                                        PlayerControlIcons.Playlists
                                    },
                                    contentDescription = stringResource(R.string.playlists),
                                    selected = false,
                                    onClick = onOpenPlaylists,
                                    haptic = Haptic.Select,
                                ),
                            )
                            PlayerControl.SEARCH -> add(
                                ToggleSegment(
                                    key = control,
                                    icon = PlayerControlIcons.Search,
                                    contentDescription = stringResource(R.string.search),
                                    selected = false,
                                    onClick = onOpenSearch,
                                    haptic = Haptic.Select,
                                ),
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clipToBounds()
                        .graphicsLayer {
                            alpha = if (rowFolds) ((0.30f - p) / 0.30f).coerceIn(0f, 1f) else 1f
                        }
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints.copy(minHeight = 0))
                            val fold = if (rowFolds) p else 0f
                            val height = (placeable.height * (1f - fold)).roundToInt()
                            layout(placeable.width, height) {
                                placeable.placeRelative(0, 0)
                            }
                        },
                ) {
                    // With the transport folded away, keep the row off the timestamps.
                    // Matches the gap above the transport, so it sits midway.
                    if (!rowFolds) Spacer(Modifier.height(QUEUE_TRANSPORT_GAP * queueCompact))
                    if (segments.isNotEmpty()) {
                        PlayerToggleGroup(
                            segments,
                            inverted = betaLayout,
                        )
                        Spacer(Modifier.height(18.dp))
                    }
                }
            }
            }
            }
        }
    }
    }
}

/**
 * The upward half of the sleeve's vertical gesture: dragged up, the artwork
 * block pulls the queue in behind it, following the finger the whole way and
 * settling to whichever end it was nearer on release.
 *
 * Downward is deliberately not ours. The sheet the player sits in is what closes
 * when the sleeve is dragged that way, and it can only read a drag it was
 * allowed to see — so a downward crossing of the touch slop is left entirely
 * alone and this returns having consumed nothing at all.
 *
 * Which of the two it is can only be known at the crossing, which is why the
 * decision is made there rather than at the press. A pointer event reaches a
 * child before its parent, so consuming the very event that crossed the slop is
 * enough to keep the sheet out of an upward drag, and letting that one event
 * through is enough to hand it a downward one — the sheet's own slop detector
 * gives up the moment it sees a change already spoken for.
 *
 * @param travel how far the sleeve has to be dragged for the queue to arrive.
 * @param slide the 0..1 the player's whole layout reads off.
 * @param onHold true while the finger owns [slide] and false when it hands it
 *   back; the settling animation is parked in between so the two never write the
 *   same value on alternate frames.
 * @param onSettle the state the release decided on, which that animation then
 *   finishes reaching from wherever the finger left off.
 */
private suspend fun AwaitPointerEventScope.dragQueueIn(
    down: PointerInputChange,
    travel: Float,
    slide: MutableFloatState,
    onHold: (Boolean) -> Unit,
    onSettle: (Boolean) -> Unit,
) {
    // A block with nowhere to travel — a player not yet measured — would divide
    // by nothing and snap the queue open on the first pixel of movement.
    if (travel < 1f) return

    var pulled = 0f
    val drag = awaitVerticalTouchSlopOrCancellation(down.id) { change, overSlop ->
        if (overSlop < 0f) {
            pulled = -overSlop
            change.consume()
        }
    }
    if (drag == null || pulled <= 0f) return

    onHold(true)
    val velocity = VelocityTracker()
    velocity.addPointerInputChange(drag)
    slide.floatValue = (pulled / travel).coerceIn(0f, 1f)
    verticalDrag(drag.id) { change ->
        velocity.addPointerInputChange(change)
        pulled -= change.positionChange().y
        slide.floatValue = (pulled / travel).coerceIn(0f, 1f)
        change.consume()
    }

    // A flick decides on its own — it says "open" without asking the finger to
    // travel at all. Anything slower goes to whichever end it got nearer to.
    val flick = -velocity.calculateVelocity().y
    val open = when {
        flick >= QUEUE_FLICK_VELOCITY -> true
        flick <= -QUEUE_FLICK_VELOCITY -> false
        else -> slide.floatValue >= QUEUE_CARRY_FRACTION
    }
    onHold(false)
    onSettle(open)
}


/**
 * The song position, ticking every frame.
 *
 * The player reports where it is about twice a second, which is fine for a
 * scrubber and far too coarse for a highlight that has to keep up with a
 * singer. This carries that report forward on the frame clock between
 * reports, and resets to the real value whenever a fresh one lands — so it
 * never drifts, it just fills in.
 *
 * Returned as state rather than a plain value on purpose: read inside a draw
 * lambda, only the draw phase re-runs each frame. Read in composition, the
 * whole line would recompose sixty times a second.
 */
@Composable
private fun rememberLyricClock(positionMs: Long, isPlaying: Boolean): MutableLongState {
    val clock = remember { mutableLongStateOf(positionMs) }
    // Gated on the app being on screen. The loop asks for a frame, writes a
    // value that invalidates a drawing, and is handed the next frame for it —
    // which is a request to render continuously for as long as it runs. That is
    // the right trade for a lyric being read and the wrong one for a phone in a
    // pocket, and the composition alone cannot tell the two apart.
    //
    // Resuming needs no catch-up: [positionMs] is a key, so coming back
    // restarts the effect and the clock is set from the player's own position
    // before the first frame is asked for.
    val foreground = rememberIsForeground()
    LaunchedEffect(positionMs, isPlaying, foreground) {
        clock.longValue = positionMs
        if (!isPlaying || !foreground) return@LaunchedEffect
        var previousFrame = withFrameMillis { it }
        while (true) {
            withFrameMillis { frame ->
                clock.longValue += frame - previousFrame
                previousFrame = frame
            }
        }
    }
    return clock
}

/**
 * A lyric line with the sung part of it lit, the rest dimmed, and the boundary
 * travelling across the words in time with the vocal.
 *
 * Two copies of the same text stacked: a dim one and a bright one clipped to
 * whatever has been sung. Same string, same style, same constraints, so the
 * two lay out identically and the bright copy lands exactly on top of the dim
 * one. The alternative — colouring an AnnotatedString word by word — can only
 * change a whole word at a time, which turns the sweep into a flicker.
 *
 * The clip is recomputed in the draw phase, so a frame costs one clip and one
 * redraw of already-measured text.
 *
 * [glowAlpha] adds Apple's bloom: a third copy, blurred, behind the other two
 * and clipped to the same boundary. Blurring *after* the clip rather than
 * before is what makes the halo bleed a little way past the sweep's leading
 * edge, which is the part that reads as light coming off the word being sung
 * rather than a drop shadow sitting under the line.
 */
@Composable
private fun SweptLyricLine(
    line: LyricLine,
    clock: MutableLongState,
    style: TextStyle,
    dimAlpha: Float,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    glowAlpha: Float = 0f,
    glowRadius: Dp = GLOW_RADIUS,
    glowRoom: Dp = 0.dp,
    feather: Boolean = false,
    rise: Boolean = true,
    alignEnd: Boolean = false,
    fillWidth: Boolean = false,
) {
    var layout by remember(line) { mutableStateOf<TextLayoutResult?>(null) }
    val reduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    val autoFit by AppSettings.autoFitOneLineLyrics.collectAsStateWithLifecycle()
    val showFurigana by AppSettings.showLyricsFurigana.collectAsStateWithLifecycle()
    val textMeasurer = rememberTextMeasurer()
    val richText = remember(line.text, style, showFurigana) {
        makeLyricRichText(line.text, style, Color.White, showFurigana)
    }
    val hasRuby = richText.rubyRanges.isNotEmpty()

    // Filled in and read back a letter at a time inside the draw lambdas, and
    // shared by all three copies of the line — they draw one after another on
    // the same thread, so there is only ever one letter in hand.
    val growth = remember { CharGrowth() }

    val phase = remember(line, clock) {
        derivedStateOf(structuralEqualityPolicy()) {
            val now = clock.longValue
            when {
                now < line.animatesFromMs -> PHASE_BEFORE
                now > line.animatesUntilMs -> PHASE_AFTER
                else -> PHASE_MOVING
            }
        }
    }
    val drawnAt: () -> Long = {
        when (phase.value) {
            PHASE_MOVING -> clock.longValue
            PHASE_BEFORE -> line.animatesFromMs - 1
            else -> line.animatesUntilMs + 1
        }
    }

    val room = if (glowRoom > 0.dp) Modifier.padding(glowRoom) else Modifier

    val riseAgainst: (Modifier) -> Modifier = { inner ->
        if (!rise || reduceAnimation || hasRuby) {
            inner
        } else {
            Modifier
                .drawWithContent {
                    val measured = layout
                    if (measured == null || line.words.isEmpty()) {
                        drawContent()
                    } else {
                        riseWith(
                            layout = measured,
                            line = line,
                            positionMs = drawnAt(),
                            inset = glowRoom.toPx(),
                            peak = WORD_RISE.toPx(),
                            growth = growth,
                        )
                    }
                }
                .then(inner)
        }
    }

    val sweep = Modifier.drawWithContent {
        val position = drawnAt()
        when {
            position >= line.endMs -> drawContent()
            position <= line.timeMs -> Unit
            else -> layout?.let {
                val rawChars = line.revealedChars(position)
                val revealed = if (hasRuby) richText.renderedOffset(rawChars) else rawChars
                val spans = if (hasRuby) emptyList() else line.sweepSpans
                sweepTo(it, revealed, spans, feather)
            }
        }
    }

    BoxWithConstraints(modifier) {
        val fittedStyle = remember(line.text, style, constraints.maxWidth, autoFit) {
            fitLyricTextStyle(line.text, style, constraints.maxWidth, autoFit, textMeasurer)
        }
        val displayedMaxLines = if (autoFit) 1 else maxLines
        val displayedOverflow = if (autoFit) TextOverflow.Clip else overflow
        val widthMod = if (fillWidth) Modifier.fillMaxWidth() else Modifier

        Box(
            widthMod,
            contentAlignment = if (alignEnd) Alignment.TopEnd else Alignment.TopStart,
        ) {
            Text(
                text = richText.annotated,
                inlineContent = richText.inlineContent,
                style = fittedStyle,
                color = Color.White.copy(alpha = dimAlpha),
                maxLines = displayedMaxLines,
                overflow = displayedOverflow,
                onTextLayout = { layout = it },
                modifier = riseAgainst(room.then(widthMod)),
            )
            if (glowAlpha > 0.01f) {
                if (hasRuby) {
                    Text(
                        text = richText.annotated,
                        inlineContent = richText.inlineContent,
                        style = fittedStyle,
                        color = Color.White,
                        maxLines = displayedMaxLines,
                        overflow = displayedOverflow,
                        modifier = widthMod
                            .graphicsLayer { alpha = glowAlpha * line.glowIntensity(drawnAt()) }
                            .blur(glowRadius, BlurredEdgeTreatment.Unbounded)
                            .then(room)
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                val measured = layout ?: return@drawWithContent
                                val position = drawnAt()
                                glowAt(
                                    layout = measured,
                                    revealedChars = richText.renderedOffset(line.revealedChars(position)),
                                    intensity = line.glowIntensity(position),
                                )
                            },
                    )
                } else {
                    Text(
                        text = richText.annotated,
                        inlineContent = richText.inlineContent,
                        style = fittedStyle,
                        color = Color.White,
                        maxLines = displayedMaxLines,
                        overflow = displayedOverflow,
                        modifier = widthMod
                            .graphicsLayer { alpha = glowAlpha }
                            .blur(glowRadius, BlurredEdgeTreatment.Unbounded)
                            .then(room)
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                val measured = layout ?: return@drawWithContent
                                glowGrown(
                                    layout = measured,
                                    line = line,
                                    positionMs = drawnAt(),
                                    inset = glowRoom.toPx(),
                                    peak = WORD_RISE.toPx(),
                                    growth = growth,
                                )
                            },
                    )
                }
            }
            Text(
                text = richText.annotated,
                inlineContent = richText.inlineContent,
                style = fittedStyle,
                color = Color.White,
                maxLines = displayedMaxLines,
                overflow = displayedOverflow,
                modifier = riseAgainst(
                    widthMod
                        .graphicsLayer {
                            compositingStrategy = if (feather) {
                                CompositingStrategy.Offscreen
                            } else {
                                CompositingStrategy.Auto
                            }
                        }
                        .then(room)
                        .then(sweep),
                ),
            )
        }
    }
}

/**
 * Draws this text clipped to the letters of the words being held, each at its
 * own brightness — the light the singing is actually giving off, rather than a
 * band of it dragged along behind the highlight.
 */
private fun ContentDrawScope.glowGrown(
    layout: TextLayoutResult,
    line: LyricLine,
    positionMs: Long,
    inset: Float,
    peak: Float,
    growth: CharGrowth,
) {
    if (!line.isGrowing(positionMs)) return
    val em = layout.layoutInput.style.fontSize.toPx()
    val length = layout.layoutInput.text.length
    for (word in line.growingWords) {
        if (positionMs < word.startMs || positionMs > word.restsAtMs) continue
        val span = line.wordSpans[word.index]
        val fall = line.wordFall(word.index, positionMs)
        for (char in span.first..minOf(span.last, length - 1)) {
            word.sampleInto(char - span.first, positionMs, growth)
            if (growth.bloom <= 0.01f) continue
            val visualLine = layout.getLineForOffset(char)
            val from = layout.xOn(char, visualLine, inset)
            val to = layout.xOn(char + 1, visualLine, inset)
            if (to <= from) continue
            val dx = growth.shift * em
            val dy = -growth.rise * peak * fall
            val rowTop = layout.getLineTop(visualLine) + inset
            val bottom = layout.getLineBottom(visualLine) + inset
            val overhang = (to - from) * (growth.scale - 1f) / 2f
            clipRect(
                left = from - overhang + dx,
                top = rowTop - peak * GROW_HEADROOM,
                right = to + overhang + dx,
                bottom = bottom,
            ) {
                translate(left = dx, top = dy) {
                    scale(
                        growth.scale,
                        growth.scale,
                        Offset((from + to) / 2f, (rowTop + bottom) / 2f),
                    ) {
                        this@glowGrown.drawContent()
                    }
                }
                drawRect(
                    color = Color.White.copy(alpha = growth.bloom),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
    }
}

/**
 * Fallback glow band for lines using custom inline ruby annotations.
 */
private fun ContentDrawScope.glowAt(
    layout: TextLayoutResult,
    revealedChars: Float,
    intensity: Float,
) {
    val length = layout.layoutInput.text.length
    if (length == 0 || revealedChars <= 0f || intensity <= 0f) return

    val edge = revealedChars.coerceIn(0f, length.toFloat())
    val visualLine = layout.getLineForOffset(edge.toInt().coerceIn(0, length - 1))
    val lineStart = layout.getLineStart(visualLine)
    val lineEnd = layout.getLineEnd(visualLine, visibleEnd = true)

    val right = horizontalAt(layout, edge.coerceIn(lineStart.toFloat(), lineEnd.toFloat()), visualLine, emptyList())
    val trail = GLOW_TRAIL.toPx() * (GLOW_TRAIL_FLOOR + (1f - GLOW_TRAIL_FLOOR) * intensity)
    val left = (right - trail).coerceAtLeast(layout.getLineLeft(visualLine))
    if (right <= left) return

    clipRect(
        left = left,
        top = layout.getLineTop(visualLine),
        right = right,
        bottom = layout.getLineBottom(visualLine),
    ) {
        this@glowAt.drawContent()
    }

    drawRect(
        brush = Brush.horizontalGradient(
            0f to Color.Transparent,
            0.45f to Color.White.copy(alpha = 0.22f),
            1f to Color.White,
            startX = left,
            endX = right,
        ),
        blendMode = BlendMode.DstIn,
    )
}

/**
 * Redraws this row with the word being sung lifted off the line, and the ones
 * behind it settling back down.
 */
private fun ContentDrawScope.riseWith(
    layout: TextLayoutResult,
    line: LyricLine,
    positionMs: Long,
    inset: Float,
    peak: Float,
    growth: CharGrowth,
) {
    if (!line.isLifted(positionMs)) {
        drawContent()
        return
    }
    val em = layout.layoutInput.style.fontSize.toPx()
    for (visualLine in 0 until layout.lineCount) {
        val lineStart = layout.getLineStart(visualLine)
        val lineEnd = layout.getLineEnd(visualLine, visibleEnd = true)
        val top = layout.getLineTop(visualLine) + inset
        val bottom = layout.getLineBottom(visualLine) + inset
        var at = lineStart
        var edge = layout.getLineLeft(visualLine) + inset
        for (index in line.words.indices) {
            val span = line.wordSpans[index]
            val start = maxOf(span.first, lineStart)
            val end = minOf(span.last + 1, lineEnd)
            if (start >= end) continue
            val held = line.growingAt(index)?.takeIf { positionMs in it.startMs..it.restsAtMs }
            val lift = line.wordLift(index, positionMs)
            if (held == null && lift <= 0.01f) continue
            val from = layout.xOn(start, visualLine, inset)
            val to = layout.xOn(end, visualLine, inset)
            if (to <= from) continue
            if (start > at) sliceRisen(edge, top, from, bottom, 0f)
            if (held != null) {
                growEach(
                    layout, held, line, positionMs, visualLine,
                    start, end, top, bottom, inset, peak, em, growth,
                )
            } else {
                sliceRisen(from, top - peak, to, bottom, -lift * peak)
            }
            at = end
            edge = to
        }
        if (at < lineEnd) {
            sliceRisen(edge, top, layout.getLineRight(visualLine) + inset, bottom, 0f)
        }
    }
}

/**
 * Redraws one held word a letter at a time, each at its own swell and height.
 */
@Suppress("LongParameterList")
private fun ContentDrawScope.growEach(
    layout: TextLayoutResult,
    word: GrowingWord,
    line: LyricLine,
    positionMs: Long,
    visualLine: Int,
    start: Int,
    end: Int,
    top: Float,
    bottom: Float,
    inset: Float,
    peak: Float,
    em: Float,
    growth: CharGrowth,
) {
    val fall = line.wordFall(word.index, positionMs)
    val first = line.wordSpans[word.index].first
    val ceiling = top - peak * GROW_HEADROOM
    val middle = (top + bottom) / 2f
    for (char in start until end) {
        word.sampleInto(char - first, positionMs, growth)
        val from = layout.xOn(char, visualLine, inset)
        val to = layout.xOn(char + 1, visualLine, inset)
        if (to <= from) continue
        val dx = growth.shift * em
        val dy = -growth.rise * peak * fall
        val overhang = (to - from) * (growth.scale - 1f) / 2f
        clipShiftedDown(
            left = from - overhang + dx,
            top = ceiling,
            right = to + overhang + dx,
            bottom = bottom,
            dy = dy,
        ) {
            translate(left = dx) {
                scale(growth.scale, growth.scale, Offset((from + to) / 2f, middle)) {
                    this@growEach.drawContent()
                }
            }
        }
    }
}

/**
 * Where an offset sits horizontally *on the row it was cut out of*.
 */
private fun TextLayoutResult.xOn(offset: Int, visualLine: Int, inset: Float): Float {
    val left = getLineLeft(visualLine) + inset
    val right = getLineRight(visualLine) + inset
    return when {
        offset <= getLineStart(visualLine) -> left
        offset >= getLineEnd(visualLine, visibleEnd = true) -> right
        else -> (getHorizontalPosition(offset, usePrimaryDirection = true) + inset)
            .coerceIn(left, right)
    }
}

/** One piece of a line, clipped to its own width and drawn at its own height. */
private fun ContentDrawScope.sliceRisen(
    from: Float,
    top: Float,
    to: Float,
    bottom: Float,
    dy: Float,
) {
    if (to <= from) return
    clipShiftedDown(left = from, top = top, right = to, bottom = bottom, dy = dy) {
        this@sliceRisen.drawContent()
    }
}

/**
 * Where a fractional character index sits across a visual line, in pixels.
 */
private fun horizontalAt(
    layout: TextLayoutResult,
    chars: Float,
    visualLine: Int,
    spans: List<IntRange>,
): Float {
    val lineStart = layout.getLineStart(visualLine)
    val lineEnd = layout.getLineEnd(visualLine, visibleEnd = true)
    val word = spans.firstOrNull { chars >= it.first && chars < it.last + 1 }
    if (word != null && word.first >= lineStart && word.last + 1 <= lineEnd) {
        val from = layout.xOn(word.first, visualLine, 0f)
        val to = layout.xOn(word.last + 1, visualLine, 0f)
        return from + (to - from) * (chars - word.first) / (word.last + 1 - word.first)
    }
    val index = chars.toInt().coerceIn(lineStart, lineEnd)
    val here = layout.xOn(index, visualLine, 0f)
    val next = layout.xOn((index + 1).coerceAtMost(lineEnd), visualLine, 0f)
    return here + (next - here) * (chars - index)
}

/**
 * Draws this text clipped to its first [revealedChars] characters.
 */
private fun ContentDrawScope.sweepTo(
    layout: TextLayoutResult,
    revealedChars: Float,
    spans: List<IntRange>,
    feather: Boolean,
) {
    if (revealedChars <= 0f) return
    if (revealedChars >= layout.layoutInput.text.length) {
        drawContent()
        return
    }
    for (visualLine in 0 until layout.lineCount) {
        val start = layout.getLineStart(visualLine)
        if (revealedChars <= start) return
        val end = layout.getLineEnd(visualLine, visibleEnd = true)
        val cut = revealedChars < end
        val right = if (cut) {
            horizontalAt(layout, revealedChars, visualLine, spans)
        } else {
            layout.getLineRight(visualLine)
        }
        val top = layout.getLineTop(visualLine)
        val bottom = layout.getLineBottom(visualLine)
        clipRect(
            left = layout.getLineLeft(visualLine),
            top = top,
            right = right,
            bottom = bottom,
        ) {
            this@sweepTo.drawContent()
        }
        if (!feather || !cut) continue
        clipRect(top = top, bottom = bottom) {
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.White,
                    1f to Color.Transparent,
                    startX = (right - WIPE_FEATHER.toPx())
                        .coerceAtLeast(layout.getLineLeft(visualLine)),
                    endX = right,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
    }
}

/**
 * Apple Music's lyrics view: big tight type, the playing line crisp and
 * everything else falling out of focus the further it is from it. Blur needs
 * API 31+, so alpha carries the same hierarchy on older devices.
 *
 * Scrolling by hand clears the blur and suspends the auto-follow, so you can
 * read ahead; a couple of seconds after you stop it snaps back to the song.
 */
@Composable
private fun LyricsPanel(
    lines: List<LyricLine>,
    trackKey: Any,
    positionMs: Long,
    isPlaying: Boolean,
    onSeekToLine: (Long) -> Unit,
    lyricsUnavailable: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val clock = rememberLyricClock(positionMs, isPlaying)

    val isSynced = remember(lines) { lines.any { it.timeMs > 0L } }
    val duet = remember(lines) { lines.any { it.alignment == LyricAlignment.End } }

    val activeRows by remember(lines, isSynced) {
        derivedStateOf {
            if (!isSynced) emptyList() else activeLyricRows(lines, clock.longValue)
        }
    }
    val scrollLine = activeRows.firstOrNull() ?: -1
    val leadLine by remember(lines, isSynced) {
        derivedStateOf {
            if (!isSynced) {
                -1
            } else {
                val now = clock.longValue
                activeLyricRows(lines, now + scrollLead(lines, now)).firstOrNull() ?: -1
            }
        }
    }
    val focusLine = if (leadLine >= 0) leadLine else scrollLine
    val listState = rememberLazyListState()
    val viewportHeight by remember(listState) {
        derivedStateOf { listState.layoutInfo.viewportSize.height }
    }
    val keepScroll = remember(listState) { keepScrollInList(listState) }
    var browsing by remember { mutableStateOf(false) }
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val lyricsBlur by AppSettings.lyricsBlur.collectAsStateWithLifecycle()
    val hideLyricsGapNote by AppSettings.hideLyricsGapNote.collectAsStateWithLifecycle()
    val hideLyricsUnavailableLabel by AppSettings.hideLyricsUnavailableLabel.collectAsStateWithLifecycle()
    val reduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    val lyricsFontScale by AppSettings.lyricsFontScale.collectAsStateWithLifecycle()
    val lyricsTextAlignment by AppSettings.lyricsTextAlignment.collectAsStateWithLifecycle()

    val glowing = !reduceAnimation && !reduceDynamicBlur && lyricsBlur &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) browsing = true
        }
    }

    val currentLine by rememberUpdatedState(focusLine)
    val activeOnScreen by remember(listState) {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.any { it.index == currentLine }
        }
    }
    LaunchedEffect(browsing, activeOnScreen, listState.isScrollInProgress, isPlaying) {
        if (isPlaying && browsing && activeOnScreen && !listState.isScrollInProgress) {
            delay(600)
            browsing = false
        }
    }

    LaunchedEffect(browsing, listState.isScrollInProgress, isPlaying) {
        if (isPlaying && browsing && !listState.isScrollInProgress) {
            delay(5_000)
            browsing = false
        }
    }

    var run by remember(lines) { mutableStateOf(ScrollRun(0, 0f, LYRIC_SETTLE_MS)) }
    val since = remember(lines) { mutableFloatStateOf(0f) }
    LaunchedEffect(run.id) {
        if (run.id == 0) return@LaunchedEffect
        animate(
            initialValue = 0f,
            targetValue = run.spanMs,
            animationSpec = tween(run.spanMs.toInt(), easing = LinearEasing),
        ) { value, _ -> since.floatValue = value }
    }
    var placed by remember(trackKey) { mutableStateOf(false) }
    LaunchedEffect(focusLine, browsing, isSynced) {
        if (isSynced && !browsing &&
            focusLine >= 0 && focusLine in lines.indices
        ) {
            snapshotFlow { listState.layoutInfo.viewportSize.height }.first { it > 0 }
            val visible = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == focusLine }
            when {
                !placed || reduceAnimation -> {
                    listState.scrollToItem(focusLine, scrollOffset = 0)
                    placed = true
                }
                visible != null -> {
                    val span = scrollLead(lines, clock.longValue).toInt()
                    run = ScrollRun(run.id + 1, visible.offset.toFloat(), span)
                    listState.animateScrollBy(
                        value = visible.offset.toFloat(),
                        animationSpec = tween(durationMillis = span, easing = LYRIC_EASING),
                    )
                }
                else -> listState.animateScrollToItem(focusLine, scrollOffset = 0)
            }
        }
    }

    if (lines.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            if (lyricsUnavailable && !hideLyricsUnavailableLabel) {
                Text(
                    text = stringResource(R.string.no_lyrics_for_track),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.6f),
                )
            }
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .bleedHorizontally(PLAYER_GUTTER)
            .nestedScroll(keepScroll)
            .fadingEdges(),
        contentPadding = PaddingValues(
            top = 40.dp - GLOW_ROOM,
            bottom = with(LocalDensity.current) { viewportHeight.toDp() } * 0.8f,
            start = PLAYER_GUTTER - GLOW_ROOM,
            end = PLAYER_GUTTER - GLOW_ROOM,
        ),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        itemsIndexed(lines) { index, line ->
            if (!isSynced && LrcParser.isSectionHeader(line.text)) {
                val sectionTitle = line.text.removePrefix("[").removeSuffix("]").trim()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (index == 0) 6.dp else 24.dp, bottom = 8.dp)
                        .padding(horizontal = GLOW_ROOM),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.14f))
                            .padding(horizontal = 11.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = sectionTitle.uppercase(),
                            style = MaterialTheme.typography.labelMedium.copy(
                                letterSpacing = 1.3.sp,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                            ),
                            color = Color.White.copy(alpha = 0.9f),
                        )
                    }
                }
                return@itemsIndexed
            }

            if (!isSynced && line.isGap) {
                Spacer(Modifier.height(14.dp))
                return@itemsIndexed
            }

            val offset = if (scrollLine < 0) 0 else index - scrollLine
            val distance = abs(offset)
            val isActive = isSynced && index in activeRows
            val step = distance.coerceAtMost(LINE_FALLOFF_ALPHA.lastIndex)
            val blur by animateDpAsState(
                targetValue = when {
                    !isSynced || reduceDynamicBlur || !lyricsBlur || browsing || isActive -> 0.dp
                    else -> LINE_FALLOFF_BLUR[step]
                },
                animationSpec = tween(LYRIC_SETTLE_MS, easing = LYRIC_EASING),
                label = "lyricBlur",
            )
            val lineAlpha by animateFloatAsState(
                targetValue = when {
                    !isSynced -> 0.95f
                    isActive -> 1f
                    browsing -> BROWSING_ALPHA
                    else -> LINE_FALLOFF_ALPHA[step]
                },
                animationSpec = tween(LYRIC_SETTLE_MS, easing = LYRIC_EASING),
                label = "lyricAlpha",
            )
            if (line.isGap) {
                val until = lines.getOrNull(index + 1)?.timeMs ?: line.endMs
                val gapFill = remember(line, until, clock) {
                    derivedStateOf(structuralEqualityPolicy()) {
                        val now = clock.longValue
                        when {
                            now <= line.timeMs -> 0f
                            now >= until -> 1f
                            else -> GAP_RUNNING
                        }
                    }
                }
                val swell by animateFloatAsState(
                    targetValue = if (isActive) 1f else 0f,
                    animationSpec = tween(
                        durationMillis = if (isActive) 400 else 350,
                        easing = LYRIC_EASING,
                    ),
                    label = "gapSwell",
                )
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .height((GAP_ROW_HEIGHT + GAP_ROW_SPACING) * swell)
                        .clipToBounds(),
                ) {
                    Box(
                        modifier = Modifier
                            .blur(blur, BlurredEdgeTreatment.Unbounded)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = isSynced) { onSeekToLine(line.timeMs) }
                            .padding(GLOW_ROOM)
                            .size(
                                width = GAP_DOT_SIZE * 3 + GAP_DOT_GAP * 2,
                                height = GAP_DOT_SIZE,
                            )
                            .graphicsLayer {
                                val grow = GAP_REST_SCALE + (1f - GAP_REST_SCALE) * swell
                                scaleX = grow
                                scaleY = grow
                                transformOrigin = TransformOrigin(0f, 0.5f)
                                alpha = lineAlpha * swell
                            }
                            .drawBehind {
                                if (hideLyricsGapNote) return@drawBehind
                                val span = (until - line.timeMs).coerceAtLeast(1L)
                                val through = gapFill.value.takeUnless { it == GAP_RUNNING }
                                    ?: ((clock.longValue - line.timeMs).toFloat() / span).coerceIn(0f, 1f)
                                val radius = GAP_DOT_SIZE.toPx() / 2f
                                val stride = (GAP_DOT_SIZE + GAP_DOT_GAP).toPx()
                                repeat(GAP_DOTS) { dot ->
                                    val lit = (through * GAP_DOTS - dot).coerceIn(0f, 1f)
                                    drawCircle(
                                        color = Color.White.copy(
                                            alpha = GAP_DOT_REST + (1f - GAP_DOT_REST) * lit,
                                        ),
                                        radius = radius,
                                        center = Offset(radius + dot * stride, size.height / 2f),
                                    )
                                }
                            },
                    )
                }
            } else {
                val alignEnd = (duet && line.alignment == LyricAlignment.End) ||
                    (!duet && lyricsTextAlignment == LyricsTextAlignment.RIGHT)
                val alignCenter = !duet && lyricsTextAlignment == LyricsTextAlignment.CENTER
                val resolvedTextAlign = when {
                    alignEnd -> TextAlign.End
                    alignCenter -> TextAlign.Center
                    else -> TextAlign.Start
                }
                val style = if (isSynced) {
                    MaterialTheme.typography.headlineLarge.copy(
                        fontSize = (27f * lyricsFontScale).sp,
                        lineHeight = (33f * lyricsFontScale).sp,
                        textAlign = resolvedTextAlign,
                    )
                } else {
                    MaterialTheme.typography.headlineMedium.copy(
                        fontSize = (23f * lyricsFontScale).sp,
                        lineHeight = (31f * lyricsFontScale).sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = resolvedTextAlign,
                    )
                }
                val sung = offset < 0
                val behind = if (run.delta >= 0f) index - focusLine else focusLine - index
                val staggerDelay = behind.coerceIn(0, STAGGER_STEPS) *
                    STAGGER_FRACTION * run.durationMs
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    targetValue = when {
                        pressed -> PRESSED_SCALE
                        isActive -> 1f
                        else -> INACTIVE_SCALE
                    },
                    animationSpec = tween(
                        durationMillis = if (pressed) 120 else LYRIC_SETTLE_MS,
                        easing = LYRIC_EASING,
                    ),
                    label = "lyricScale",
                )
                val glow by animateFloatAsState(
                    targetValue = if (isActive && glowing) GLOW_ALPHA else 0f,
                    animationSpec = tween(durationMillis = 420),
                    label = "lyricGlow",
                )
                val originX = when {
                    alignEnd -> 1f
                    alignCenter -> 0.5f
                    else -> 0f
                }
                val shape = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = if (duet && line.alignment == LyricAlignment.End) DUET_LANE else 0.dp,
                        end = if (duet && line.alignment != LyricAlignment.End) DUET_LANE else 0.dp,
                    )
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(originX, 0.5f)
                        alpha = lineAlpha
                        translationY = if (staggerDelay <= 0f) {
                            0f
                        } else {
                            val elapsed = since.floatValue
                            run.delta * (
                                LYRIC_EASING.transform(
                                    (elapsed / run.durationMs).coerceIn(0f, 1f),
                                ) - LYRIC_EASING.transform(
                                    ((elapsed - staggerDelay) / run.durationMs)
                                        .coerceIn(0f, 1f),
                                )
                                )
                        }
                    }
                    .blur(blur, BlurredEdgeTreatment.Unbounded)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(
                        enabled = isSynced,
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                    ) { onSeekToLine(line.timeMs) }

                val backingOpen = line.background?.let { backing ->
                    val due by remember(backing, clock) {
                        derivedStateOf {
                            val now = clock.longValue
                            now >= backing.timeMs - BACKING_LEAD_MS && now < backing.endMs
                        }
                    }
                    val wanted = !isSynced || if (backing.isWordSynced) {
                        due
                    } else {
                        isActive || index == focusLine
                    }
                    animateFloatAsState(
                        targetValue = if (wanted) 1f else 0f,
                        animationSpec = when {
                            reduceAnimation -> tween(0)
                            wanted -> tween(BACKING_OPEN_MS, easing = LYRIC_EASING)
                            else -> tween(
                                BACKING_CLOSE_MS,
                                delayMillis = if (index < focusLine) run.durationMs else 0,
                                easing = FastOutSlowInEasing,
                            )
                        },
                        label = "backingOpen",
                    )
                }
                Column(modifier = shape) {
                    PanelVoice(
                        line = line,
                        clock = clock,
                        style = style,
                        isActive = isActive,
                        sung = sung,
                        synced = isSynced,
                        browsing = browsing,
                        glowAlpha = glow,
                        room = GLOW_ROOM,
                        alignEnd = alignEnd,
                        textAlign = resolvedTextAlign,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    line.background?.let { backing ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .then(backingOpen?.let { Modifier.revealBacking(it, alignEnd) } ?: Modifier),
                        ) {
                            PanelVoice(
                                line = backing.withoutBracketPunctuation(),
                                clock = clock,
                                style = style.copy(
                                    fontSize = (BACKING_FONT_SIZE.value * lyricsFontScale).sp,
                                    lineHeight = (BACKING_LINE_HEIGHT.value * lyricsFontScale).sp,
                                ),
                                isActive = isActive,
                                sung = sung,
                                synced = isSynced,
                                browsing = browsing,
                                glowAlpha = 0f,
                                room = 0.dp,
                                alignEnd = alignEnd,
                                textAlign = resolvedTextAlign,
                                rise = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = GLOW_ROOM, end = GLOW_ROOM, bottom = GLOW_ROOM)
                                    .graphicsLayer { alpha = BACKING_ALPHA },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One voice of a row in [LyricsPanel] — the lead, or the answering line drawn
 * under it.
 */
@Composable
private fun PanelVoice(
    line: LyricLine,
    clock: MutableLongState,
    style: TextStyle,
    isActive: Boolean,
    sung: Boolean,
    synced: Boolean,
    browsing: Boolean,
    glowAlpha: Float,
    room: Dp,
    alignEnd: Boolean,
    textAlign: TextAlign,
    rise: Boolean = true,
    modifier: Modifier = Modifier,
) {
    if (line.isWordSynced && !browsing) {
        val tail by animateFloatAsState(
            targetValue = if (sung) 1f else UNSUNG_ALPHA,
            label = "lyricTail",
        )
        SweptLyricLine(
            line = line,
            clock = clock,
            style = style,
            dimAlpha = tail,
            modifier = modifier,
            glowAlpha = glowAlpha,
            glowRoom = room,
            feather = isActive,
            rise = rise,
            alignEnd = alignEnd,
            fillWidth = textAlign == TextAlign.Center,
        )
    } else if (line.isWordSynced) {
        val tail by animateFloatAsState(
            targetValue = if (sung) 1f else UNSUNG_ALPHA,
            label = "lyricTail",
        )
        SweptLyricLine(
            line = line,
            clock = clock,
            style = style,
            dimAlpha = tail,
            modifier = modifier,
            glowAlpha = 0f,
            glowRoom = room,
            rise = rise,
            alignEnd = alignEnd,
            fillWidth = textAlign == TextAlign.Center,
        )
    } else {
        val lit by animateFloatAsState(
            targetValue = if (!synced || sung || isActive) 1f else UNSUNG_ALPHA,
            label = "lyricLit",
        )
        LyricTextDisplay(
            text = line.text,
            style = style,
            color = Color.White.copy(alpha = lit),
            textAlign = textAlign,
            modifier = modifier.padding(room).fillMaxWidth(),
        )
    }
}

/**
 * Opens the answering voice under its lead as [progress] runs from 0 to 1.
 */
private fun Modifier.revealBacking(progress: State<Float>, alignEnd: Boolean): Modifier = this
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val open = progress.value
        val rise = BACKING_RISE.toPx()
        layout(placeable.width, (placeable.height * open).roundToInt()) {
            placeable.placeWithLayer(0, 0) {
                alpha = (open * BACKING_FADE_LEAD).coerceAtMost(1f)
                translationY = rise * (1f - open)
                val grow = BACKING_REST_SCALE + (1f - BACKING_REST_SCALE) * open
                scaleX = grow
                scaleY = grow
                transformOrigin = TransformOrigin(if (alignEnd) 1f else 0f, 0f)
            }
        }
    }

/**
 * The answering vocal without the parentheses every text-only source wraps it
 * in — see [withBackgroundVocals].
 */
private fun LyricLine.withoutBracketPunctuation(): LyricLine = copy(
    text = text.stripParens(),
    words = words.mapNotNull { word -> word.withoutChars { it == '(' || it == ')' } },
)

private fun String.stripParens(): String = replace("(", "").replace(")", "").trim()

private data class LyricRichText(
    val annotated: AnnotatedString,
    val inlineContent: Map<String, InlineTextContent>,
    val rubyRanges: List<RubySourceRange> = emptyList(),
)

private data class RubySourceRange(
    val sourceStart: Int,
    val sourceEndExclusive: Int,
    val baseLength: Int,
    val renderedStart: Int,
)

private fun LyricRichText.renderedOffset(sourceOffset: Float): Float {
    if (rubyRanges.isEmpty()) return sourceOffset
    var removedCharacters = 0
    rubyRanges.forEach { range ->
        if (sourceOffset < range.sourceStart) return@forEach
        if (sourceOffset < range.sourceEndExclusive) {
            val baseEnd = range.sourceStart + range.baseLength
            return if (sourceOffset < baseEnd) range.renderedStart.toFloat()
            else (range.renderedStart + 1).toFloat()
        }
        removedCharacters += (range.sourceEndExclusive - range.sourceStart - 1)
    }
    return (sourceOffset - removedCharacters).coerceAtLeast(0f)
}

/** Expands the app's `漢字((かんじ))` convention into an inline ruby layout. */
private fun makeLyricRichText(
    source: String,
    style: TextStyle,
    color: Color,
    enabled: Boolean,
): LyricRichText {
    if (!enabled) return LyricRichText(AnnotatedString(source), emptyMap())
    val matches = LyricsFurigana.annotations(source)
    if (matches.isEmpty()) return LyricRichText(AnnotatedString(source), emptyMap())

    val renderedRanges = mutableListOf<RubySourceRange>()
    val builder = androidx.compose.ui.text.buildAnnotatedString {
        var cursor = 0
        var renderedLength = 0
        matches.forEachIndexed { index, match ->
            val ordinary = source.substring(cursor, match.sourceStart)
            append(ordinary)
            renderedLength += ordinary.length
            val id = "lyric-ruby-$index"
            renderedRanges += RubySourceRange(
                sourceStart = match.sourceStart,
                sourceEndExclusive = match.sourceEndExclusive,
                baseLength = match.base.length,
                renderedStart = renderedLength,
            )
            appendInlineContent(id, match.base)
            renderedLength++
            cursor = match.sourceEndExclusive
        }
        val trailing = source.substring(cursor)
        append(trailing)
    }
    val inline = matches.mapIndexed { index, match ->
        val id = "lyric-ruby-$index"
        val base = match.base
        val reading = match.reading
        val baseSize = style.fontSize.value
            .takeIf { it.isFinite() && it > 0f }
            ?.sp ?: 22.sp
        val emWidth = maxOf(base.length.toFloat(), reading.length * 0.5f).coerceAtLeast(1f)
        val width = (baseSize.value * emWidth).sp
        id to InlineTextContent(
            placeholder = Placeholder(
                width = width,
                height = baseSize * 1.6f,
                placeholderVerticalAlign = PlaceholderVerticalAlign.AboveBaseline,
            ),
            children = {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Text(
                        text = reading,
                        color = color,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.fillMaxWidth(),
                        style = style.copy(
                            fontSize = baseSize * 0.42f,
                            lineHeight = baseSize * 0.48f,
                            textAlign = TextAlign.Center,
                        ),
                    )
                    Text(
                        text = base,
                        color = color,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.fillMaxWidth(),
                        style = style.copy(fontSize = baseSize, lineHeight = baseSize),
                    )
                }
            },
        )
    }.toMap()
    return LyricRichText(builder, inline, renderedRanges)
}

@Composable
private fun LyricTextDisplay(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
) {
    val showFurigana by AppSettings.showLyricsFurigana.collectAsStateWithLifecycle()
    val autoFit by AppSettings.autoFitOneLineLyrics.collectAsStateWithLifecycle()
    val actualStyle = if (textAlign == null) style else style.copy(textAlign = textAlign)
    val rich = remember(text, actualStyle, color, showFurigana) {
        makeLyricRichText(text, actualStyle, color, showFurigana)
    }
    val textMeasurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val availableWidth = constraints.maxWidth
        val fittedStyle = remember(text, actualStyle, availableWidth, autoFit) {
            fitLyricTextStyle(text, actualStyle, availableWidth, autoFit, textMeasurer)
        }
        Text(
            text = rich.annotated,
            inlineContent = rich.inlineContent,
            style = fittedStyle,
            color = color,
            maxLines = if (autoFit) 1 else maxLines,
            overflow = if (autoFit) TextOverflow.Clip else overflow,
            modifier = if (textAlign != null) Modifier.fillMaxWidth() else Modifier,
        )
    }
}

private fun fitLyricTextStyle(
    text: String,
    style: TextStyle,
    availableWidth: Int,
    enabled: Boolean,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
): TextStyle {
    if (!enabled || availableWidth <= 0 || style.fontSize.value <= MIN_AUTOFIT_LYRIC_SP) return style
    val measured = textMeasurer.measure(text = text, style = style, maxLines = 1, softWrap = false)
    if (measured.size.width <= availableWidth) return style
    var low = MIN_AUTOFIT_LYRIC_SP
    var high = style.fontSize.value
    repeat(12) {
        val candidate = (low + high) / 2f
        val candidateStyle = style.copy(fontSize = candidate.sp)
        val layout = textMeasurer.measure(text = text, style = candidateStyle, maxLines = 1, softWrap = false)
        if (layout.size.width <= availableWidth) low = candidate else high = candidate
    }
    return style.copy(fontSize = low.sp, lineHeight = minOf(style.lineHeight.value, low * 1.25f).sp)
}

private const val MIN_AUTOFIT_LYRIC_SP = 8f

@Composable
private fun CurrentLyricLine(
    lines: List<LyricLine>,
    trackKey: Any,
    positionMs: Long,
    isPlaying: Boolean,
    durationMs: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSynced = remember(lines) { lines.any { it.timeMs > 0L } }
    val hideLyricsStatusText by AppSettings.hideLyricsStatusText.collectAsStateWithLifecycle()
    if (!isSynced) {
        val previewLine = remember(lines) {
            LyricsStripText.firstLyricLine(lines)?.text
        }
        val label = if (!previewLine.isNullOrBlank()) {
            "$previewLine • Tap for lyrics"
        } else {
            "Lyrics available • Tap to view"
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 4.dp),
        ) {
            Icon(
                imageVector = BitChordIcons.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        return
    }

    val clock = rememberLyricClock(positionMs, isPlaying)

    val index by remember(lines) {
        derivedStateOf { lines.indexOfLast { it.timeMs <= clock.longValue } }
    }
    val current = lines.getOrNull(index)?.takeIf(LyricsStripText::isLyricLine)
    val firstSung = remember(lines) { LyricsStripText.firstLyricIndex(lines) }
    val showingFirstLineEarly = current == null &&
        LyricsStripText.shouldPreviewFirstLyric(index, firstSung)
    val displayIndex = if (showingFirstLineEarly) firstSung else index
    val displayLine = current ?: if (showingFirstLineEarly) lines.getOrNull(firstSung) else null
    val instrumental = displayLine == null
    val text = displayLine?.text ?: LyricsStripText.statusTextOrNull(
        stringResource(R.string.instrumental),
        hideLyricsStatusText,
    ).orEmpty()

    val reduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        if (instrumental) {
            Icon(
                imageVector = BitChordIcons.MusicNote,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        AnimatedContent(
            targetState = Triple(displayIndex, displayLine, text),
            transitionSpec = {
                val duration = if (reduceAnimation) 0 else 340
                if (reduceAnimation) {
                    (fadeIn(snap()) togetherWith fadeOut(snap())).using(
                        SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> snap() })
                    )
                } else {
                    (fadeIn(animationSpec = tween(duration, easing = FastOutSlowInEasing)) +
                        slideInVertically(animationSpec = tween(duration, easing = FastOutSlowInEasing)) { height -> (height * 0.35f).toInt() })
                        .togetherWith(
                            fadeOut(animationSpec = tween(duration, easing = FastOutSlowInEasing)) +
                                slideOutVertically(animationSpec = tween(duration, easing = FastOutSlowInEasing)) { height -> -(height * 0.35f).toInt() }
                        ).using(
                            SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> tween(duration, easing = FastOutSlowInEasing) })
                        )
                }
            },
            label = "currentLyricTransition",
            modifier = Modifier.weight(1f, fill = false),
        ) { (_, lineItem, lineText) ->
            val itemInstrumental = lineItem == null || lineItem.isGap
            val swept = lineItem?.takeIf { !showingFirstLineEarly && !itemInstrumental && it.isWordSynced }
            if (swept != null) {
                SweptLyricLine(
                    line = swept,
                    clock = clock,
                    style = MaterialTheme.typography.titleMedium,
                    dimAlpha = UNSUNG_ALPHA_STRIP,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    rise = false,
                )
            } else {
                LyricTextDisplay(
                    text = lineText,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (itemInstrumental) Color.White.copy(alpha = 0.5f) else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        // Disclosure hint: this strip opens the full lyrics screen.
        Icon(
            imageVector = BitChordIcons.ChevronRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(14.dp),
        )
    }
}

/**
 * Stands in for [CurrentLyricLine] once a lookup has come back empty — shown
 * for a few seconds so it registers, then left to fade rather than snapping
 * out or lingering for the rest of the track.
 */
@Composable
private fun LyricsUnavailableLine(trackKey: Any, modifier: Modifier = Modifier) {
    var visible by remember(trackKey) { mutableStateOf(true) }
    LaunchedEffect(trackKey) {
        delay(LYRICS_UNAVAILABLE_HOLD_MS)
        visible = false
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 0.55f else 0f,
        animationSpec = tween(durationMillis = LYRICS_UNAVAILABLE_FADE_MS),
        label = "lyricsUnavailableAlpha",
    )
    Text(
        text = stringResource(R.string.lyrics_not_available),
        style = MaterialTheme.typography.titleMedium,
        color = Color.White,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .padding(vertical = 4.dp)
            .graphicsLayer { this.alpha = alpha },
    )
}

/** Stands in for [CurrentLyricLine] while a lookup is still in flight. */
@Composable
private fun LyricsLoadingLine(trackKey: Any, modifier: Modifier = Modifier) {
    val loadingLines = stringArrayResource(R.array.lyrics_loading_lines)
    // Keep the loading copy stable while this track's lyric lookup is pending.
    // The resource array itself is not a stable Compose key.
    val text = remember(trackKey) { loadingLines.random() }
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = Color.White.copy(alpha = 0.55f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(vertical = 4.dp),
    )
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
private fun VideoAudioVersionButton(
    audioVersion: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .optimizedHazeEffect(
                state = hazeState,
                // The opaque surface used by the nav bar is too dark over a
                // player cover. A faint material tint keeps the same glass
                // blur while letting the artwork's colour show through.
                style = HazeMaterials.regular(MaterialTheme.colorScheme.surface.copy(alpha = 0.16f)),
            )
            .background(Color.White.copy(alpha = 0.04f)),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VideoAudioTab(
                icon = Icons.Rounded.Videocam,
                contentDescription = stringResource(R.string.revert_to_original),
                selected = !audioVersion,
                enabled = audioVersion && !loading,
                onClick = {
                    haptics.play(Haptic.Tap)
                    onClick()
                },
            )
            VideoAudioTab(
                icon = BitChordIcons.MusicNote,
                contentDescription = stringResource(R.string.convert_to_audio),
                selected = audioVersion,
                enabled = !audioVersion && !loading,
                onClick = {
                    haptics.play(Haptic.Tap)
                    onClick()
                },
                loading = loading,
            )
        }
    }
}

@Composable
private fun VideoAudioTab(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    loading: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (selected) 0.20f else 0f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(17.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White.copy(alpha = if (selected) 1f else 0.58f),
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

/**
 * Translucent circular button used for the track menu and the like control.
 *
 * [active] brightens the disc rather than only the glyph: this sits on album
 * artwork of any colour, and a white icon on a white-ish sleeve has no tint
 * change left to make. The filled heart carries the state as a shape too —
 * see [BitChordIcons.HeartFilled].
 */
@Composable
private fun CircleGlyph(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    active: Boolean = false,
    enabled: Boolean = true,
    haptic: Haptic = Haptic.Tap,
) {
    val haptics = rememberHaptics()
    val discAlpha by animateFloatAsState(
        targetValue = if (active) 0.34f else 0.18f,
        label = "glyphDisc",
    )
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = discAlpha))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                haptics.play(haptic)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(
            targetState = icon,
            animationSpec = tween(durationMillis = 180),
            label = "playerMenuGlyph",
        ) { glyph ->
            Icon(
                imageVector = glyph,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * Transport / bottom glyphs. The circular clip belongs on the touch target,
 * never on the [Icon] — clipping the icon itself shaves the corners off wide
 * glyphs like fast-forward and the queue list.
 */
/** Lyrics-mode transport: play/pause and skip scales of the size setting (0.84 / 1.2 ≈ 0.7). */
/**
 * Gap above and below the transport while the queue is open — the same on
 * both sides so it sits midway, and tight so the queue gets the room. The
 * glyphs' own touch padding adds a little more to what is seen.
 */
private val QUEUE_TRANSPORT_GAP = 8.dp

/** Gap above the transport, and (plus the timestamps' lift) below it, with no volume bar. */
private val TRANSPORT_GAP_NO_VOLUME = 18.dp

/**
 * How far the expanded sleeve may reach past the player's side gutter on each
 * side. The rest of the player keeps the full gutter; the artwork alone gets a
 * little wider, and still keeps a comfortable margin to the screen edge.
 */
private val ART_GUTTER_REACH = 10.dp

/** How far the timestamps are pulled up under the scrubber's touch target. */
private val TIMESTAMP_LIFT = 9.dp

private const val LYRICS_PLAY_SCALE = 1.2f
private const val LYRICS_SKIP_SCALE = 0.84f

@Composable
private fun TransportGlyph(
    icon: ImageVector,
    contentDescription: String,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    enabled: Boolean = true,
    haptic: Haptic = Haptic.Tap,
) {
    val haptics = rememberHaptics()
    // Faded rather than hidden: the row keeps its shape at the ends of a queue.
    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.3f,
        label = "transportAlpha",
    )
    Box(
        modifier = Modifier
            .size(size + 12.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
            ) {
                haptics.play(haptic)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = alpha),
            modifier = Modifier.size(size),
        )
    }
}

@Composable
private fun SeekTransportGlyph(
    icon: ImageVector,
    contentDescription: String,
    label: String,
    showLabel: Boolean,
    onClick: () -> Unit,
    visibility: Float = 1f,
) {
    val haptics = rememberHaptics()
    Column(
        modifier = Modifier
            .size(42.dp)
            .graphicsLayer { alpha = visibility }
            .clip(CircleShape)
            .clickable(
                enabled = visibility > 0.5f,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    haptics.play(Haptic.Tap)
                    onClick()
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(21.dp),
        )
        if (showLabel) {
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.86f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}

/** One button in the player's bottom row. */
private class ToggleSegment(
    val key: PlayerControl,
    val icon: ImageVector,
    val contentDescription: String,
    val selected: Boolean,
    val onClick: () -> Unit,
    val haptic: Haptic,
    val enabled: Boolean = true,
    /**
     * Shortest gap between taps that both reach [onClick]. A tap inside the
     * window of the last one is dropped whole, haptic included. Only the
     * buttons whose work is too heavy to repeat at finger speed ask for one.
     */
    val tapWindowMs: Long = 0L,
    /** Bounce the glyph once when this turns on. */
    val popOnSelect: Boolean = false,
    /** Turn the glyph whenever this changes (repeat's off → all → one). */
    val spinKey: Any? = null,
)

private val SEGMENT_HEIGHT = 48.dp
private val SEGMENT_INNER_CORNER = 8.dp
private val SEGMENT_GAP = 3.dp
/** Same wash as the title row's [CircleGlyph], so the two read as one material. */
private const val SEGMENT_FILL = 0.18f
private const val SEGMENT_FILL_ON = 0.40f
/** Beta: the translucent white capsule behind a segment that is on. */
private const val SEGMENT_FILL_INVERTED = 0.70f
/** Beta: the row's glyphs, a size up now that nothing sits behind them at rest. */
private val SEGMENT_GLYPH_BETA = 26.dp
private val SegmentEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * The bottom row as one connected group: square-ish segments split by a thin
 * gap, rounded only on the group's outer ends. A segment that turns on rounds
 * into a capsule and its wash deepens; pressing any segment squeezes it.
 */
@Composable
private fun PlayerToggleGroup(
    segments: List<ToggleSegment>,
    /**
     * Beta: no backing at rest, just the glyphs. A segment that turns on gets a
     * translucent white capsule with its glyph cut out of it, so what was behind
     * the glyph now shows through it. Neighbours that are on together read as
     * one connected group: rounded only at the run's two ends.
     */
    inverted: Boolean = false,
) {
    val ons = segments.map { it.selected && it.enabled }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SEGMENT_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        segments.forEachIndexed { index, segment ->
            key(segment.key) {
                PlayerToggleSegment(
                    segment = segment,
                    first = index == 0,
                    last = index == segments.lastIndex,
                    inverted = inverted,
                    prevOn = ons.getOrElse(index - 1) { false },
                    nextOn = ons.getOrElse(index + 1) { false },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun PlayerToggleSegment(
    segment: ToggleSegment,
    first: Boolean,
    last: Boolean,
    inverted: Boolean = false,
    prevOn: Boolean = false,
    nextOn: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Read only from the click handler, so writing it costs no recomposition.
    val lastTap = remember { mutableLongStateOf(-segment.tapWindowMs) }
    val on = segment.selected && segment.enabled

    val round = SEGMENT_HEIGHT / 2
    val shapeSpec = tween<Dp>(durationMillis = 600, easing = SegmentEasing)
    val startCorner by animateDpAsState(
        targetValue = when {
            inverted -> if (on && prevOn) SEGMENT_INNER_CORNER else round
            on || first -> round
            else -> SEGMENT_INNER_CORNER
        },
        animationSpec = shapeSpec,
        label = "segmentStart",
    )
    val endCorner by animateDpAsState(
        targetValue = when {
            inverted -> if (on && nextOn) SEGMENT_INNER_CORNER else round
            on || last -> round
            else -> SEGMENT_INNER_CORNER
        },
        animationSpec = shapeSpec,
        label = "segmentEnd",
    )
    val fill by animateFloatAsState(
        targetValue = when {
            inverted -> if (on) SEGMENT_FILL_INVERTED else 0f
            on -> SEGMENT_FILL_ON
            else -> SEGMENT_FILL
        },
        animationSpec = tween(durationMillis = 350, easing = SegmentEasing),
        label = "segmentFill",
    )
    // Beta: how far the glyph has turned from white ink into a hole in the
    // capsule behind it.
    val cutOut by animateFloatAsState(
        targetValue = if (inverted && on) 1f else 0f,
        animationSpec = tween(durationMillis = 350, easing = SegmentEasing),
        label = "segmentCutOut",
    )
    val squeeze by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "segmentPress",
    )

    val glyphScale = remember { Animatable(1f) }
    val glyphTurn = remember { Animatable(0f) }
    // Both start from what is already showing, so the first frame never animates.
    val wasOn = remember { mutableStateOf(on) }
    LaunchedEffect(on) {
        val turnedOn = on && !wasOn.value
        wasOn.value = on
        if (turnedOn && segment.popOnSelect) {
            glyphScale.snapTo(0.5f)
            glyphScale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 500f))
        }
    }
    val lastSpin = remember { mutableStateOf(segment.spinKey) }
    LaunchedEffect(segment.spinKey) {
        if (segment.spinKey == lastSpin.value) return@LaunchedEffect
        lastSpin.value = segment.spinKey
        launch {
            glyphTurn.snapTo(-180f)
            glyphTurn.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 380f))
        }
        glyphScale.snapTo(0.75f)
        glyphScale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 420f))
    }

    val shape = RoundedCornerShape(
        topStart = startCorner,
        bottomStart = startCorner,
        topEnd = endCorner,
        bottomEnd = endCorner,
    )
    Box(
        modifier = modifier
            .height(SEGMENT_HEIGHT)
            .graphicsLayer {
                scaleX = squeeze
                scaleY = squeeze
                // The capsule and the glyph cut out of it share one layer.
                if (inverted) compositingStrategy = CompositingStrategy.Offscreen
            }
            .clip(shape)
            .background(Color.White.copy(alpha = fill))
            .clickable(
                enabled = segment.enabled,
                interactionSource = interaction,
                indication = null,
            ) {
                val now = SystemClock.uptimeMillis()
                if (now - lastTap.longValue >= segment.tapWindowMs) {
                    lastTap.longValue = now
                    haptics.play(segment.haptic)
                    segment.onClick()
                }
            }
            .semantics {
                contentDescription = segment.contentDescription
                selected = on
            },
        contentAlignment = Alignment.Center,
    ) {
        val glyphSize = if (inverted) SEGMENT_GLYPH_BETA else 22.dp
        val glyphMotion: GraphicsLayerScope.() -> Unit = {
            rotationZ = glyphTurn.value
            scaleX = glyphScale.value
            scaleY = glyphScale.value
        }
        if (cutOut < 1f) {
            Icon(
                imageVector = segment.icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = if (segment.enabled) 1f - cutOut else 0.30f),
                modifier = Modifier.size(glyphSize).graphicsLayer(glyphMotion),
            )
        }
        if (cutOut > 0f) {
            // Drawn as an eraser: wherever the glyph is, the capsule is not.
            Icon(
                imageVector = segment.icon,
                contentDescription = null,
                tint = Color.Black.copy(alpha = cutOut),
                modifier = Modifier
                    .size(glyphSize)
                    .graphicsLayer {
                        glyphMotion()
                        blendMode = BlendMode.DstOut
                    },
            )
        }
    }
}

/**
 * Swallows whatever scroll the queue list itself didn't use. The player is a
 * ModalBottomSheet, and the sheet's own nested-scroll handler reads that
 * leftover as "drag me down" — so scrolling the queue would slide the player
 * away. Consuming it here keeps the gesture inside the list.
 *
 * A downward *fling* has to be caught in the pre-phase, before the sheet sees
 * it, but only at the top of the list — otherwise the queue could never fling.
 */
private fun keepScrollInList(listState: LazyListState) = object : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset = available

    override suspend fun onPreFling(available: Velocity): Velocity =
        if (available.y > 0f && !listState.canScrollBackward) available else Velocity.Zero

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
}

/** A credit that links somewhere, when [browseId] is known. */
private fun Modifier.opensPage(browseId: String?, onOpen: (String) -> Unit): Modifier =
    if (browseId == null) {
        this
    } else {
        clip(RoundedCornerShape(6.dp)).clickable { onOpen(browseId) }
    }

/** How fast the title/artist marquee crawls — unhurried, not a ticker. */
private const val MARQUEE_DP_PER_SEC = 26f

/** Clear air between the tail of the line and the copy chasing it round. */
private val MARQUEE_GAP = 48.dp

/** How long a line sits back at its start before the next pass — the "5 seconds" rest. */
private const val MARQUEE_REST_MS = 5_000L

/** Artist's head start is ceded to the title when both are scrolling, so they don't start as one block. */
private const val MARQUEE_ARTIST_STAGGER_MS = 3_000L

/**
 * A single line of text that scrolls in place, only when it is too long for
 * [modifier]'s width to show in full.
 *
 * Idle text never animates — the scroll only kicks in once measurement proves
 * an ellipsis would otherwise be needed. When it does, the line is drawn twice
 * with [MARQUEE_GAP] between the copies and the pair is crawled leftwards by
 * exactly one copy-plus-gap: the trailing copy chases the leading one in from
 * the right and lands precisely where it started, so the offset reset at the
 * end of the pass falls under a copy already in position and cannot be seen.
 * The line therefore only ever travels one way — right to left, round and back
 * to its resting place — rather than bouncing back the way it came.
 *
 * A pass is: wait [startDelayMillis] (used to stagger the artist line behind
 * the title), crawl one full loop, then rest [MARQUEE_REST_MS] at the start
 * before going again. [onOverflowChange] reports whether this line is scrolling
 * at all, so a sibling line can decide whether it needs to stagger behind it.
 *
 * With [enabled] false the line is a plain ellipsised one — no copies, no
 * animation, nothing left running off screen.
 */
@Composable
private fun MarqueeText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    startDelayMillis: Long = 0L,
    leading: (@Composable () -> Unit)? = null,
    onOverflowChange: (Boolean) -> Unit = {},
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(6.dp))
        }
        val offsetX = remember { Animatable(0f) }
        var scrolls by remember { mutableStateOf(false) }
        BoxWithConstraints(
            Modifier
                .weight(1f, fill = false)
                .clipToBounds()
                .marqueeFade(enabled = scrolls, offset = { offsetX.value }),
        ) {
            val maxWidthPx = constraints.maxWidth
            val layout = remember(text, style, maxWidthPx) {
                textMeasurer.measure(text = text, style = style, maxLines = 1, softWrap = false)
            }
            val overflowing = layout.size.width > maxWidthPx
            LaunchedEffect(overflowing) { onOverflowChange(overflowing) }
            // Soft edges whenever the line is too long, moving or not.
            SideEffect { scrolls = overflowing }

            // One whole copy plus the gap behind it: that is the distance at
            // which the second copy is sitting exactly where the first was.
            val travelPx = if (overflowing) {
                layout.size.width + with(density) { MARQUEE_GAP.roundToPx() }
            } else {
                0
            }

            LaunchedEffect(enabled, text, travelPx, startDelayMillis) {
                offsetX.snapTo(0f)
                if (!enabled || travelPx <= 0) return@LaunchedEffect
                val pxPerMs = with(density) { MARQUEE_DP_PER_SEC.dp.toPx() } / 1000f
                val scrollMs = (travelPx / pxPerMs).roundToInt().coerceAtLeast(400)
                delay(startDelayMillis)
                while (true) {
                    offsetX.animateTo(-travelPx.toFloat(), tween(scrollMs, easing = LinearEasing))
                    // Invisible: the trailing copy has arrived at the leading
                    // one's starting mark, so the line is already back where
                    // this puts it.
                    offsetX.snapTo(0f)
                    delay(MARQUEE_REST_MS)
                }
            }

            Row(
                // Measured unbounded so the copies actually lay out at their
                // full width, wider than the clipped box around them — bounded,
                // the text is truncated during its own measurement and sliding
                // it sideways just moves an already-cut string.
                modifier = Modifier
                    .wrapContentWidth(align = Alignment.Start, unbounded = true)
                    .offset { IntOffset(offsetX.value.roundToInt(), 0) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MarqueeLine(text = text, style = style, color = color)
                if (overflowing) {
                    Spacer(Modifier.width(MARQUEE_GAP))
                    MarqueeLine(text = text, style = style, color = color)
                }
            }
        }
    }
}

/**
 * Fades a scrolling line out at its edges instead of cutting it off. The right
 * edge is always soft while the line can scroll; the left one grows in as the
 * line starts to move, so text at rest starts crisp at its margin.
 */
private fun Modifier.marqueeFade(enabled: Boolean, offset: () -> Float): Modifier =
    if (!enabled) this else this
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            val fade = MARQUEE_EDGE_FADE.toPx().coerceAtMost(size.width / 4f)
            val left = (-offset()).coerceIn(0f, fade)
            if (left > 0.5f) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, Color.Black),
                        startX = 0f,
                        endX = left,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startX = size.width - fade,
                    endX = size.width,
                ),
                blendMode = BlendMode.DstIn,
            )
        }

private val MARQUEE_EDGE_FADE = 20.dp

/** One copy of a marquee's line, laid out at its full width rather than clipped. */
@Composable
private fun MarqueeLine(text: String, style: TextStyle, color: Color) {
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
    )
}

/** The small "E" pill for explicit tracks, kept outside the scrolling text. */
@Composable
private fun ExplicitBadge(color: Color) {
    Text(
        text = "E",
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .border(1.dp, color.copy(alpha = 0.72f), RoundedCornerShape(2.dp))
            .padding(horizontal = 3.dp),
    )
}

/**
 * Measure a child wider than its slot by [gutter] on each side and place it back
 * over that margin, still reporting the original width to the parent.
 *
 * The lists are the only things in the player you can scroll, and the side
 * padding left a strip of bare sheet down each edge. A finger that drifted into
 * one scrolled nothing and closed the player instead. Matching content padding
 * puts every row back exactly where it was drawn, so this is invisible.
 */
private fun Modifier.bleedHorizontally(gutter: Dp): Modifier = layout { measurable, constraints ->
    val extra = gutter.roundToPx() * 2
    val widened = if (constraints.hasBoundedWidth) {
        constraints.copy(
            minWidth = constraints.minWidth + extra,
            maxWidth = constraints.maxWidth + extra,
        )
    } else {
        constraints
    }
    val placeable = measurable.measure(widened)
    val width = (placeable.width - extra).coerceAtLeast(0)
    layout(width, placeable.height) {
        placeable.place(-(placeable.width - width) / 2, 0)
    }
}

/** Softens the list where it meets the header and the scrubber. */
private fun Modifier.fadingEdges(): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val fade = 28.dp.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black),
                startY = 0f,
                endY = fade,
            ),
            blendMode = BlendMode.DstIn,
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Black, Color.Transparent),
                startY = size.height - fade,
                endY = size.height,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

/** The live queue, in the player itself. */
@Composable
private fun InlineQueue(
    queue: List<Song>,
    currentIndex: Int,
    autoplayEnabled: Boolean,
    onJumpTo: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onClear: () -> Unit,
    onManageQueues: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val keepScroll = remember(listState) { keepScrollInList(listState) }
    // Where AutoPlay's tracks start. The queue is kept with them last, so this
    // is one boundary rather than a category to test row by row.
    val autoplayStart = remember(queue, currentIndex) {
        autoplaySectionStart(queue.map { it.fromAutoplay }, currentIndex)
    }

    // Each section reorders on its own — a drag never crosses the line
    // between what was queued by hand and what AutoPlay picked, same as
    // [addToQueue] and [playNext] already respect it.
    //
    // Both draw straight from the live [queue], never from a snapshot taken
    // when the drag began: the boundary between the sections moves on its own
    // as tracks play, so a frozen copy of either one goes stale the moment it
    // does — AutoPlay's section would keep listing tracks that have long
    // since played, and the row indices behind `onJumpTo`/`onRemove` would
    // start pointing at the wrong songs. Each swap is sent to the player as
    // it happens instead, and the rows animate into place off the live order.
    val manualRows = queue.subList(0, autoplayStart)
    val autoplayRows = queue.subList(autoplayStart, queue.size)
    // A song can be queued twice, so videoId alone isn't always a unique key
    // — LazyColumn throws on a repeat. Suffixing by how many times that id
    // has already been seen keeps every key unique while staying stable
    // across a reorder, which plain videoId+index (the previous key) wasn't:
    // that changed on every swap and silently broke animateItem's ability to
    // tell "this row moved" from "this row was replaced".
    val manualKeys = remember(manualRows) { manualRows.stableQueueKeys() }
    val autoplayKeys = remember(autoplayRows) { autoplayRows.stableQueueKeys("autoplay/") }

    // The heading is a row of the same LazyColumn, so it shifts every
    // AutoPlay index below it along by one — hence the offset back to queue
    // indices, which is what [onMove] and the rest of the callbacks take.
    val headingShown = autoplayEnabled || autoplayStart < queue.size
    val headingCount = if (headingShown) 1 else 0
    // Nothing moves at or above the track playing right now: what's already
    // been played is history, and the current row is the boundary the sections
    // are drawn from. Only what's still to come is the user's to reorder.
    // AutoPlay's section needs no such limit — [autoplaySectionStart] always
    // puts it after the current track.
    val firstMovable = (currentIndex + 1).coerceIn(0, autoplayStart)
    val manualDrag = rememberQueueDragState(
        listState = listState,
        lazyRange = firstMovable until autoplayStart,
        lazyOffset = 0,
        onMove = onMove,
    )
    val autoplayDrag = rememberQueueDragState(
        listState = listState,
        lazyRange = (autoplayStart + headingCount) until (autoplayStart + headingCount + autoplayRows.size),
        lazyOffset = headingCount,
        onMove = onMove,
    )

    // Open on what's playing, not at the top of a long queue. The heading sits
    // between the two sections, so it counts as a row once it's above this one.
    //
    // Never mid-drag, though. A track ending while a row is held would jump the
    // list out from under the finger, and the jump takes the list's scroll off
    // the edge auto-scroll below — which would leave the rest of that drag
    // unable to scroll at all. Reordering is also the one time the user is
    // certainly looking somewhere other than at the current track.
    LaunchedEffect(currentIndex) {
        val holding = manualDrag.draggedKey != null || autoplayDrag.draggedKey != null
        if (!holding && currentIndex in queue.indices) {
            // Keep the current track in view while exposing the two most
            // recent tracks above it when this queue has playback history.
            // The rest of history remains reachable by scrolling upward.
            listState.scrollToItem(queueInitialVisibleIndex(currentIndex, autoplayStart))
        }
    }

    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.queue),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.manage_queues),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .clickable(onClick = onManageQueues)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Text(
                text = stringResource(R.string.clear),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .clickable(onClick = onClear)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .bleedHorizontally(PLAYER_GUTTER)
                // Without this the sheet treats the list's leftover scroll as a
                // drag on itself and slides the whole player away.
                .nestedScroll(keepScroll)
                .fadingEdges(),
            contentPadding = PaddingValues(horizontal = PLAYER_GUTTER),
        ) {
            // What was asked for: the album, playlist or station the queue was
            // started from, plus anything queued by hand since.
            itemsIndexed(
                items = manualRows,
                key = { index, _ -> manualKeys[index] },
            ) { index, song ->
                val key = manualKeys[index]
                val dragging = manualDrag.draggedKey == key
                InlineQueueRow(
                    song = song,
                    isCurrent = index == currentIndex,
                    onClick = { onJumpTo(index) },
                    onRemove = { onRemove(index) },
                    // Only what's still queued ahead. The playing track and
                    // everything already played sit above the line a drag
                    // can't cross.
                    draggable = index >= firstMovable,
                    dragging = dragging,
                    onDragStart = { manualDrag.onDragStart(key) },
                    onDrag = manualDrag::onDrag,
                    onDragEnd = manualDrag::onDragEnd,
                    modifier = Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) manualDrag.renderOffset else 0f }
                        // The dragged row follows the finger, so it is the one
                        // row that must not also be animating to a slot. Its
                        // neighbours skip the animation too, for as long as
                        // *anything* in the section is being dragged — see the
                        // note on [manualDrag] below for why.
                        .then(if (manualDrag.draggedKey != null) Modifier else Modifier.animateItem()),
                )
            }
            // Heading first, then what AutoPlay has lined up under it. With
            // nothing lined up yet it closes the queue as a promise instead.
            if (autoplayEnabled || autoplayStart < queue.size) {
                item(key = "autoplay-heading") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            BitChordIcons.Infinity,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.autoplay),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                            )
                            Text(
                                text = if (autoplayStart < queue.size) {
                                    stringResource(R.string.autoplay_queue_description)
                                } else {
                                    stringResource(R.string.autoplay_empty_description)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.55f),
                            )
                        }
                    }
                }
            }
            itemsIndexed(
                items = autoplayRows,
                key = { index, _ -> autoplayKeys[index] },
            ) { index, song ->
                val at = autoplayStart + index
                val key = autoplayKeys[index]
                val dragging = autoplayDrag.draggedKey == key
                InlineQueueRow(
                    song = song,
                    isCurrent = at == currentIndex,
                    onClick = { onJumpTo(at) },
                    onRemove = { onRemove(at) },
                    draggable = true,
                    dragging = dragging,
                    onDragStart = { autoplayDrag.onDragStart(key) },
                    onDrag = autoplayDrag::onDrag,
                    onDragEnd = autoplayDrag::onDragEnd,
                    modifier = Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) autoplayDrag.renderOffset else 0f }
                        .then(if (autoplayDrag.draggedKey != null) Modifier else Modifier.animateItem()),
                )
            }
        }
    }
}

/**
 * A key per row, stable across a reorder and unique even when the same song
 * appears twice — the Nth time a given videoId is seen gets suffixed with
 * that count, so two copies of one song each keep their own identity instead
 * of colliding on the same LazyColumn key.
 */
private fun List<Song>.stableQueueKeys(prefix: String = ""): List<String> {
    val seen = HashMap<String, Int>()
    return map { song ->
        val n = seen.getOrDefault(song.videoId, 0)
        seen[song.videoId] = n + 1
        if (n == 0) "$prefix${song.videoId}" else "$prefix${song.videoId}#$n"
    }
}

/**
 * How far in from either end of the queue a held row starts scrolling the list,
 * and how fast it scrolls once it is all the way at the edge.
 *
 * The zone is a shade deeper than the 28.dp the list fades out over, so the
 * list is already moving by the time the row begins to disappear into the fade
 * rather than only once it has. The speed at the edge is about six rows a
 * second: quick enough to cross a long queue without waiting on it, slow
 * enough to still read the titles going past and stop on the right one.
 */
private val QUEUE_EDGE_SCROLL_ZONE = 40.dp
private val QUEUE_EDGE_SCROLL_SPEED = 340.dp

/**
 * The pace, in pixels a second, to scroll a list at while a row occupying
 * [top] to [bottom] is held in a viewport spanning [viewportStart] to
 * [viewportEnd] — negative towards the start of the list, positive towards its
 * end, and zero while the row is clear of both edges.
 *
 * Ramped by how far into the [zone] the row has reached, so how fast the queue
 * goes by stays the user's to choose — but from a fifth of [speed] rather than
 * from nothing, since a row just inside the zone should visibly move the list
 * instead of creeping a pixel a second until it is pushed further. A viewport
 * too short to hold the row clear of both edges at once scrolls neither way,
 * rather than picking one arbitrarily and running away with it.
 */
internal fun edgeScrollSpeed(
    top: Float,
    bottom: Float,
    viewportStart: Int,
    viewportEnd: Int,
    zone: Float,
    speed: Float,
): Float {
    if (zone <= 0f) return 0f
    val intoStart = (viewportStart + zone) - top
    val intoEnd = bottom - (viewportEnd - zone)
    val reach = when {
        intoStart > 0f && intoEnd <= 0f -> -intoStart
        intoEnd > 0f && intoStart <= 0f -> intoEnd
        else -> return 0f
    }
    val ramp = speed * (0.2f + 0.8f * (abs(reach) / zone).coerceAtMost(1f))
    return if (reach < 0f) -ramp else ramp
}

/**
 * Drag-to-reorder for one contiguous section of [InlineQueue]'s LazyColumn —
 * the user's own queue and AutoPlay's each get their own instance, since a
 * drag never crosses the boundary between them.
 *
 * Each swap goes to the player the moment the dragged row crosses a
 * neighbour, so the live queue is always what's on screen and the rows the
 * drag displaces animate to their new slots off it. The dragged row is
 * tracked by its LazyColumn key rather than by index, because the index under
 * it changes with every swap.
 *
 * [lazyRange] is the section's span of LazyColumn indices, and [lazyOffset]
 * the distance from those to queue indices — the AutoPlay heading is a row
 * of the list too, so below it the two no longer line up.
 */
@Composable
private fun rememberQueueDragState(
    listState: LazyListState,
    lazyRange: IntRange,
    lazyOffset: Int,
    onMove: (Int, Int) -> Unit,
): QueueDragState {
    val state = remember(listState) { QueueDragState(listState) }
    state.lazyRange = lazyRange
    state.lazyOffset = lazyOffset
    state.onMove = onMove
    with(LocalDensity.current) {
        state.edgeZone = QUEUE_EDGE_SCROLL_ZONE.toPx()
        state.edgeSpeed = QUEUE_EDGE_SCROLL_SPEED.toPx()
    }

    // Held near either end of the list, the row scrolls it. A track can be
    // moved across a queue many screens long without letting go, where before
    // the only way down was to drop the row at the edge, scroll by hand and
    // pick it up again, once per screenful.
    //
    // What the scroll moves is the list, not the finger, and
    // [QueueDragState.onScrolled] says exactly that: the held position stays
    // where it is and the new layout is read back against it. The row sits
    // still on screen while the rows above or below slide past it, and swaps
    // through them on the same terms it would if the finger had covered the
    // distance itself.
    val direction = state.autoScrollDir
    LaunchedEffect(state, direction) {
        if (direction == 0) return@LaunchedEffect
        listState.scroll {
            var previous = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                // A frame the system dropped, paid back in full, lands as a
                // lurch — so it isn't.
                val seconds = ((now - previous) / 1_000_000_000f).coerceAtMost(1f / 30f)
                previous = now
                val scrolled = scrollBy(state.autoScrollSpeed * seconds)
                // Nowhere left to scroll, or the row has left the edge and the
                // speed has gone to nothing. Let the list's scroll go rather
                // than spin on it holding the lock: the row can still be
                // dragged the rest of the way by hand, and coming back to an
                // edge starts this over.
                if (scrolled == 0f) break
                state.onScrolled()
            }
        }
    }
    return state
}

/**
 * Where a held row is being held, what it may do from there, and the moves it
 * has sent to the player on the way.
 *
 * The whole thing turns on one number: [heldCenter], where the row's centre is
 * being held, in the LazyColumn's own viewport pixels. The finger moves it and
 * nothing else does — not a scroll, not a swap, not a relayout. Everything
 * drawn or decided is then read back off the live layout against it: the row
 * is drawn at whatever its slot currently is plus the distance to
 * [heldCenter], and it trades places with whichever neighbour's slot
 * [heldCenter] has reached into.
 *
 * Tracking where the row is rather than how far it has come is what lets the
 * drag survive the list moving underneath it. The offset this replaces was
 * kept by hand — corrected on every scrolled pixel and again on every swap —
 * and held together only for as long as it was told about every last thing
 * that moved the list. It wasn't: LazyColumn re-anchors its own scroll
 * position when the row it measures from is reordered elsewhere (see
 * [swapTarget]), and one such jump left the offset a full row wrong, the row
 * drawn a row off the finger and its slot pushed clean out of the viewport.
 * Read fresh off the layout there is nothing left to be wrong — wherever the
 * list has ended up, the row is still under the finger.
 */
private class QueueDragState(private val listState: LazyListState) {
    var lazyRange: IntRange = IntRange.EMPTY
    var lazyOffset: Int = 0
    var onMove: (Int, Int) -> Unit = { _, _ -> }

    /** [QUEUE_EDGE_SCROLL_ZONE] and [QUEUE_EDGE_SCROLL_SPEED], in pixels. */
    var edgeZone: Float = 0f
    var edgeSpeed: Float = 0f

    /** LazyColumn key of the row being dragged; null at rest. */
    var draggedKey by mutableStateOf<Any?>(null)
        private set

    /**
     * How far from its own slot to draw the held row, in pixels.
     *
     * Not simply the distance to [heldCenter]: a queue longer than the screen
     * has nowhere to show a row above its first slot or below its last, so a
     * finger held past either end was drawing the row off the list into
     * nothing. Kept inside the viewport it sits at whichever edge it reached
     * and stays visible there while the auto-scroll carries the list under it.
     */
    var renderOffset by mutableFloatStateOf(0f)
        private set

    /**
     * Which way the list is scrolling itself under the held row: -1 towards the
     * start of the queue, 1 towards its end, 0 not at all. State, because this
     * is what starts and stops the loop that does the scrolling.
     */
    var autoScrollDir by mutableIntStateOf(0)
        private set

    /**
     * How fast it is doing so, signed, in pixels a second — and deliberately
     * *not* state. It changes with every pixel of drag travel, and only the
     * loop reads it, once a frame; as state it would recompose the whole queue
     * on every touch event to tell the composition something it has no use for.
     */
    var autoScrollSpeed: Float = 0f
        private set

    /**
     * Where the finger is holding the row's centre, in viewport pixels. NaN
     * until the first drag event, which takes it from the row's own slot — a
     * drag begins with the row exactly where it already was.
     */
    private var heldCenter: Float = Float.NaN

    /** Where the last swap put the row, until the list is laid out with it. */
    private var awaiting: Int? = null

    fun onDragStart(key: Any) {
        draggedKey = key
        heldCenter = Float.NaN
        renderOffset = 0f
        awaiting = null
        setAutoScroll(0f)
    }

    /** The finger moved [deltaY] pixels and the list stayed put. */
    fun onDrag(deltaY: Float) = settle(deltaY)

    /** The list moved under the finger and the finger stayed put. */
    fun onScrolled() = settle(0f)

    fun onDragEnd() {
        draggedKey = null
        heldCenter = Float.NaN
        renderOffset = 0f
        awaiting = null
        setAutoScroll(0f)
    }

    /**
     * Takes the drag in [deltaY] pixels further, then reads the list back to
     * see where that leaves the row: where to draw it, whether it has reached
     * an edge, and whether it has reached a neighbour worth trading with.
     */
    private fun settle(deltaY: Float) {
        val key = draggedKey ?: return
        val items = listState.layoutInfo.visibleItemsInfo
        // The row's own slot is off screen. There is nothing to measure an
        // edge or a swap against and nothing to draw against either, so the
        // way back is to stand still and let the swap already sent land and
        // bring the slot into view. If the row has been disposed outright
        // rather than merely scrolled past, it ends the drag itself on the
        // way out — see the disposal guard in [InlineQueueRow].
        val dragged = items.find { it.key == key } ?: run {
            setAutoScroll(0f)
            return
        }
        val half = dragged.size / 2f
        if (heldCenter.isNaN()) heldCenter = dragged.offset + half
        heldCenter += deltaY
        holdToSection(items, dragged)

        val top = heldCenter - half
        // Aimed before the guard below, not after: a swap in flight is a frame
        // or two of the list not having caught up yet, and the scroll should
        // carry on evenly through those rather than stutter once per row.
        aimAutoScroll(top, dragged)
        renderOffset = insideViewport(top, dragged.size) - dragged.offset

        // A swap already sent but not yet laid out: deciding the next one off
        // a position the list has moved on from would send a second move for
        // a swap that has already happened, and the two would fight.
        awaiting?.let {
            if (dragged.index != it) return
            awaiting = null
        }
        val target = swapTarget(items, dragged) ?: return
        onMove(dragged.index - lazyOffset, target.index - lazyOffset)
        awaiting = target.index
    }

    /**
     * The neighbour [heldCenter] has reached far enough into to trade places
     * with, or null while there is none to trade with yet.
     */
    private fun swapTarget(
        items: List<LazyListItemInfo>,
        dragged: LazyListItemInfo,
    ): LazyListItemInfo? {
        // Only rows of this section are fair targets — the heading and the
        // other section's rows share the LazyColumn but not this range.
        val target = items
            .filter { it.index in lazyRange && it.index != dragged.index }
            .minByOrNull { abs((it.offset + it.size / 2f) - heldCenter) }
            ?: return null
        // Held short of halfway the rows would swap back and forth over a
        // single pixel of travel; a full half-height of overlap is what makes
        // one swap per row crossed.
        if (abs(heldCenter - (target.offset + target.size / 2f)) > target.size / 2f) return null
        // Never with the row the list is keeping its own place by, while there
        // is still list above it to scroll.
        //
        // LazyColumn remembers where it is scrolled to as the *key* of its
        // first visible row plus an offset into it. Reorder that particular
        // row and it follows the key to wherever the row went, which slides
        // the entire list along by a row — and the held row, which has just
        // moved into the slot that row left, goes off the top of the viewport
        // with it. LazyColumn then disposes it, and disposal cancels the drag
        // gesture outright: neither onDragEnd nor onDragCancel runs, so the
        // row was left highlighted and offset with nothing dragging it,
        // stranded a row above where it was picked up. Dragging *down* never
        // met this, because the row traded with is the one below and the list
        // anchors on the one at the top; dragging up, the row traded with is
        // precisely the one the edge scroll is drawing in at the top, which is
        // why one direction worked and the other did not.
        //
        // Declining to swap this frame is the whole fix. The scroll that
        // brought the row here carries on, the next row up becomes the one the
        // list is anchored by, and the trade goes through a few frames later —
        // by which time it moves nothing the list is holding on to. With no
        // list left above to scroll there is no jump to decline in the first
        // place, so a row can still be dropped into the first slot of its
        // section.
        if (target.index == listState.firstVisibleItemIndex && listState.canScrollBackward) {
            return null
        }
        return target
    }

    /**
     * Points the auto-scroll at whichever edge the row now spanning [top] has
     * reached, if either — but only while there is both a row that way for it
     * to swap with and list left to scroll. Held past the last row of its own
     * section it would otherwise keep the list moving with no move left to
     * make, carrying the row's slot away under a finger that has nothing left
     * to answer with.
     */
    private fun aimAutoScroll(top: Float, dragged: LazyListItemInfo) {
        val info = listState.layoutInfo
        val speed = edgeScrollSpeed(
            top = top,
            bottom = top + dragged.size,
            viewportStart = info.viewportStartOffset,
            viewportEnd = info.viewportEndOffset,
            zone = edgeZone,
            speed = edgeSpeed,
        )
        val blocked = when {
            speed < 0f -> dragged.index <= lazyRange.first || !listState.canScrollBackward
            speed > 0f -> dragged.index >= lazyRange.last || !listState.canScrollForward
            else -> true
        }
        setAutoScroll(if (blocked) 0f else speed)
    }

    /**
     * Holds the drag inside the section it started in.
     *
     * A row can only be dropped between the first and last slots of its own
     * section — the playing track and the history above it are not the user's
     * to reorder, and neither is the far side of the AutoPlay heading. The
     * swap loop already respects that, by having no target to offer past
     * either end; what it does not do is stop [heldCenter] running on past the
     * boundary, and a finger a screen beyond it then has that whole distance
     * to travel back before the row answers again. Held at the boundary it
     * stops there under the finger, which is what "this is as far as it goes"
     * ought to look like.
     *
     * Only the ends actually on screen bound anything. A section that runs off
     * the viewport has more of itself that way for the auto-scroll to bring
     * in, and holding to whichever of its rows happens to be measured would
     * stop the drag at the edge of the screen instead of at the edge of the
     * section.
     */
    private fun holdToSection(items: List<LazyListItemInfo>, dragged: LazyListItemInfo) {
        val half = dragged.size / 2f
        items.firstOrNull { it.index == lazyRange.first }?.let {
            heldCenter = heldCenter.coerceAtLeast(it.offset + half)
        }
        items.firstOrNull { it.index == lazyRange.last }?.let {
            heldCenter = heldCenter.coerceAtMost(it.offset + it.size - half)
        }
    }

    /** [top], kept where a row of [size] can still be seen — see [renderOffset]. */
    private fun insideViewport(top: Float, size: Int): Float {
        val info = listState.layoutInfo
        val minTop = info.viewportStartOffset.toFloat()
        val maxTop = (info.viewportEndOffset - size).toFloat().coerceAtLeast(minTop)
        return top.coerceIn(minTop, maxTop)
    }

    private fun setAutoScroll(speed: Float) {
        autoScrollSpeed = speed
        val direction = when {
            speed > 0f -> 1
            speed < 0f -> -1
            else -> 0
        }
        if (autoScrollDir != direction) autoScrollDir = direction
    }
}

@Composable
private fun InlineQueueRow(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    draggable: Boolean = false,
    dragging: Boolean = false,
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
) {
    // LazyColumn disposes a row the instant its slot leaves the viewport, and
    // that takes the drag gesture below down with it: the coroutine running
    // [detectDragGestures] is cancelled where it stands, so neither onDragEnd
    // nor onDragCancel is ever reached and the drag is left held by nothing —
    // the row comes back into view highlighted and offset from its slot, and
    // stays that way until the queue is closed. The swap guard in
    // [QueueDragState.swapTarget] is what stops the slot being thrown out of
    // the viewport in the first place; this is here because "the gesture ended
    // and nothing was told" should not be a state the queue can be left in at
    // all, whatever put it there.
    val heldOnDispose by rememberUpdatedState(dragging)
    val endDrag by rememberUpdatedState(onDragEnd)
    DisposableEffect(Unit) {
        onDispose { if (heldOnDispose) endDrag() }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (dragging) Color.White.copy(alpha = 0.06f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (draggable) {
            Icon(
                Icons.Rounded.DragHandle,
                contentDescription = stringResource(R.string.drag_to_reorder),
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier
                    .size(20.dp)
                    // DragHandle's glyph sits well inset from the edges of
                    // its own bounding box — this pulls it back to the row's
                    // actual left edge instead of leaving a gap in front of it.
                    .offset(x = (-4).dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { onDragStart() },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.y)
                            },
                        )
                    },
            )
            Spacer(Modifier.width(4.dp))
        }
        AsyncImage(
            model = song.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(6.dp))
                .thumbnailBorder(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            ExplicitSongTitle(
                song = song,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.92f),
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isCurrent) {
            Icon(
                Icons.Rounded.GraphicEq,
                contentDescription = stringResource(R.string.now_playing),
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = stringResource(R.string.remove_from_queue),
                tint = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return "%d:%02d".format(Locale.ROOT, minutes, seconds)
}

/**
 * The gap between the two timestamps under the seek bar: just the "Lossless"
 * badge when one applies, and nothing otherwise. The stats line that used to
 * fall back to lives inside the sleeve now (see the bottom-centre overlay on
 * the artwork Box above), so there is no tap here to swap it in — the two say
 * the same thing at different resolutions, both read off the stream being
 * decoded rather than off what a source offered to send.
 */
@Composable
private fun LosslessOrStats(
    isLocalAudio: Boolean,
    isLoading: Boolean,
    stillRacing: Boolean,
    losslessRequested: Boolean,
    nerdStats: NerdStats.Snapshot?,
    modifier: Modifier = Modifier,
) {
    val hideLosslessLabel by AppSettings.hideLosslessLabel.collectAsStateWithLifecycle()
    when {
        // This preference hides the full quality/status badge family: lossless,
        // hi-res, high-quality and the in-progress quality-upgrade label.
        hideLosslessLabel -> {}
        // Still resolving — either the player itself is buffering, or a
        // module is still racing YouTube for this track in the background
        // (see [NerdStats.racingLossless]) even though YouTube already won
        // and is audible. Either way nothing measured yet to confirm with,
        // so this is a statement of intent, not a result — no shimmer, so
        // it never reads as "confirmed" before it is.
        // [stillRacing] on its own, not gated on the lossless preference: a
        // module outranks YouTube on the strength of the source order alone,
        // so the lookup runs — and can come back lossless — with that switch
        // off. Gating this on it left the badge blank through the wait and
        // then jumped straight to "Hi-Res Lossless".
        // The [isLoading] half is gated on `nerdStats == null` rather than
        // `nerdStats?.isLossless != true`: `isLoading` is just
        // `STATE_BUFFERING`, which a seek trips for a track whose quality
        // question was already settled — swallowing back into cache still
        // rebuffers. Gating on `!= true` read that rebuffer as "resolving"
        // again and flashed "Upgrading Quality" over a track already known
        // to be, say, Hi-Quality with no lossless copy anywhere. Once
        // [nerdStats] exists there is something measured to show instead, so
        // only a genuinely unmeasured track — or a real race via
        // [stillRacing] — earns this label.
        (!isLocalAudio && stillRacing && nerdStats?.isLossless != true) ||
            (!isLocalAudio && isLoading && losslessRequested && nerdStats == null) -> LosslessLabel(
            // What is already true, ahead of what is still being looked for.
            // A race running over JioSaavn's 320kbps AAC and one running over
            // YouTube's 160kbps Opus were both drawn as a bare "Upgrading
            // Quality", which reads as "this is not good yet" — wrong on the
            // first, where the track is already at the top of what lossy gets
            // and the search is only chasing a lossless copy that may not
            // exist. Naming the floor first makes the label describe a track
            // rather than a wait.
            //
            // Decided on [NerdStats.Snapshot.isHiQuality] rather than on which
            // source won, for the reason that property already gives: a
            // 320kbps stream is a 320kbps stream wherever it came from. It is
            // read off the stream rather than off what the module offered, so
            // a JioSaavn AAC qualifies once its container has stated its rate;
            // YouTube's Opus sits under the threshold and keeps the plain
            // label it had.
            text = if (nerdStats?.isHiQuality == true) {
                stringResource(R.string.high_quality_upgrading)
            } else {
                stringResource(R.string.upgrading_quality)
            },
            animated = false,
            modifier = modifier,
        )
        nerdStats?.isLossless == true && !hideLosslessLabel -> LosslessLabel(
            // Same line Tidal, Qobuz and Apple Music draw it at — see
            // [NerdStats.Snapshot.isHiRes].
            text = stringResource(if (nerdStats.isHiRes) R.string.hi_res_lossless else R.string.lossless),
            // Shimmer is reserved for the thing that was asked for and
            // confirmed. It is what makes the badge read as an achievement
            // rather than a label, which only one of these two is.
            animated = true,
            modifier = modifier,
        )
        nerdStats?.isDolbyAtmos == true -> LosslessLabel(
            text = "Dolby Atmos",
            animated = true,
            iconPainter = painterResource(R.drawable.ic_dolby_atmos),
            modifier = modifier,
        )
        // Lossy, but the good end of lossy — a module's 320kbps tier, which
        // for a great many tracks is the best copy that exists anywhere the
        // app can reach. See [NerdStats.Snapshot.isHiQuality].
        nerdStats?.isHiQuality == true -> LosslessLabel(
            text = stringResource(R.string.high_quality),
            animated = false,
            modifier = modifier,
        )
        else -> {}
    }
}

/** A quality glyph ahead of the status label. */
@Composable
private fun LosslessLabel(
    text: String,
    animated: Boolean,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.Headphones,
    iconPainter: Painter? = null,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = Color.White.copy(alpha = if (animated) 0.7f else 0.45f)
        if (iconPainter != null) {
            Icon(
                painter = iconPainter,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(width = 13.dp, height = 10.dp),
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(13.dp),
            )
        }
        Spacer(Modifier.width(4.dp))
        if (animated) {
            ShimmerText(text = text)
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = (MaterialTheme.typography.labelMedium.fontSize.value + 1).sp,
                ),
                color = Color.White.copy(alpha = 0.45f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * "Lossless", with a highlight band sweeping left to right across it every
 * three seconds — confirmed, not just claimed, so it's worth the shine.
 *
 * The band's width is measured off the text itself via [onSizeChanged]
 * rather than assumed, so the sweep always clears the word fully at both
 * ends instead of being sized for whatever length happened to be typical.
 */
@Composable
private fun ShimmerText(text: String) {
    var widthPx by remember { mutableIntStateOf(0) }
    val transition = rememberInfiniteTransition(label = "lossless-shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "lossless-shimmer-progress",
    )
    val baseColor = Color.White.copy(alpha = 0.55f)
    val brush = if (widthPx <= 0) {
        Brush.linearGradient(listOf(baseColor, baseColor))
    } else {
        val band = widthPx * 0.6f
        val center = -band + progress * (widthPx + 2 * band)
        Brush.linearGradient(
            colorStops = arrayOf(0f to baseColor, 0.5f to Color.White, 1f to baseColor),
            start = Offset(center - band, 0f),
            end = Offset(center + band, 0f),
        )
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(
            brush = brush,
            fontWeight = FontWeight.SemiBold,
            fontSize = (MaterialTheme.typography.labelMedium.fontSize.value + 1).sp,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.onSizeChanged { widthPx = it.width },
    )
}

/**
 * "FLAC · 24-bit · 96.0 kHz · 4608 kbps · Stereo" — whichever of those the
 * player has actually reported. A figure it hasn't is dropped rather than
 * filled in, so a short line means little was known, never that something was
 * invented.
 *
 * Bitrate is stated for a lossless stream too, and is the rate its samples
 * decode to — 1411 for 16-bit/44.1kHz, 4608 for 24-bit/96kHz. It is the same
 * figure Tidal, Qobuz and Apple Music print next to a lossless track, and the
 * one a listener can carry from track to track; a FLAC's compressed rate
 * cannot, because it says more about how compressible that recording was than
 * about the copy being played.
 *
 * A stream that arrived worse than its source promised gets that stated
 * outright rather than left to be spotted — see [NerdStats.Snapshot.downgraded].
 */
private fun NerdStats.Snapshot.describe(context: android.content.Context): String? {
    val parts = buildList {
        codecLabel(mimeType)?.let(::add)
        bitDepth?.let { add(context.getString(R.string.bit_depth, it)) }
        sampleRateHz?.let { add("%.1f kHz".format(Locale.ROOT, it / 1000f)) }
        bitrateKbps?.let { add("$it kbps") }
        channels?.let {
            add(
                when (it) {
                    1 -> context.getString(R.string.mono)
                    2 -> context.getString(R.string.stereo)
                    else -> context.getString(R.string.channel_count, it)
                },
            )
        }
        if (downgraded) add(context.getString(R.string.downgraded_from, claimed?.summary.orEmpty()))
    }
    return parts.joinToString(" · ").takeIf { it.isNotEmpty() }
}

/** The codec under its usual name rather than its MIME type. */
private fun codecLabel(mimeType: String?): String? = when {
    mimeType == null -> null
    mimeType.endsWith("opus") -> "Opus"
    mimeType.endsWith("mp4a-latm") -> "AAC"
    mimeType.endsWith("vorbis") -> "Vorbis"
    mimeType.endsWith("mpeg") -> "MP3"
    mimeType.endsWith("flac") -> "FLAC"
    mimeType.endsWith("alac") -> "ALAC"
    else -> mimeType.substringAfter('/').uppercase(Locale.ROOT)
}



/**
 * A back callback that outranks whatever else the window has registered —
 * here, the sheet the player is drawn in. See the call site in
 * [NowPlayingScreen] for why it takes that.
 *
 * Everything that names an `android.window` type lives in this object so those
 * classes, which don't exist below API 33, are only ever *loaded* on a device
 * that has them: the callback comes back as [Any] rather than as the platform
 * interface for the same reason. Gating the calls on [Build.VERSION.SDK_INT]
 * is very likely enough by itself; this way it can't come down to how eagerly
 * a particular runtime resolves a reference it is never going to use.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object OverlayBack {
    /** The registered callback, to hand back to [unregister]; null if it couldn't be. */
    fun register(view: View, onBack: () -> Unit): Any? {
        val dispatcher = view.findOnBackInvokedDispatcher() ?: return null
        val callback = OnBackInvokedCallback { onBack() }
        dispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_OVERLAY,
            callback,
        )
        return callback
    }

    fun unregister(view: View, callback: Any?) {
        if (callback !is OnBackInvokedCallback) return
        view.findOnBackInvokedDispatcher()?.unregisterOnBackInvokedCallback(callback)
    }
}
