/*
 * Vendored from Kyant0/backdrop v2.0.0 (io.github.kyant0:backdrop)
 * https://github.com/Kyant0/backdrop — Copyright 2025 Kyant0, Apache License 2.0
 *
 * Vendored so the library ships as source with this app (binary AARs compiled
 * against older Compose broke at runtime) and to add a backdrop resolution
 * scale for cheaper effect rendering. KMP expect/actual declarations were
 * merged into this single Android source set. Package renamed accordingly.
 */
package com.music.bitchord.ui.components.backdrop.highlight

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.util.fastCoerceAtMost
import com.music.bitchord.ui.components.backdrop.RuntimeShaderCacheImpl
import com.music.bitchord.ui.components.backdrop.internal.ShapeProvider
import com.music.bitchord.ui.components.backdrop.internal.blur
import com.music.bitchord.ui.components.backdrop.internal.clipOutline
import com.music.bitchord.ui.components.backdrop.internal.setRuntimeShader
import com.music.bitchord.ui.components.backdrop.isRuntimeShaderSupported
import kotlin.math.ceil

internal class HighlightElement(
    val shapeProvider: ShapeProvider,
    val highlight: () -> Highlight?
) : ModifierNodeElement<HighlightNode>() {

    override fun create(): HighlightNode {
        return HighlightNode(shapeProvider, highlight)
    }

    override fun update(node: HighlightNode) {
        if (node.shapeProvider != shapeProvider) {
            node.shapeProvider = shapeProvider
        }
        node.highlight = highlight
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "highlight"
        properties["shapeProvider"] = shapeProvider
        properties["highlight"] = highlight
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HighlightElement) return false

        if (shapeProvider != other.shapeProvider) return false
        if (highlight != other.highlight) return false

        return true
    }

    override fun hashCode(): Int {
        var result = shapeProvider.hashCode()
        result = 31 * result + highlight.hashCode()
        return result
    }
}

internal class HighlightNode(
    var shapeProvider: ShapeProvider,
    var highlight: () -> Highlight?
) : DrawModifierNode, Modifier.Node() {

    override val shouldAutoInvalidate: Boolean = false

    private val paint =
        Paint().apply {
            style = PaintingStyle.Stroke
        }
    private var clipPath: Path? = null

    private val runtimeShaderCache = RuntimeShaderCacheImpl()

    override fun ContentDrawScope.draw() {
        val highlight = highlight()
        if (highlight == null || highlight.width.value <= 0f || highlight.alpha <= 0f) {
            return drawContent()
        }

        drawContent()

        val size = size
        val density: Density = this
        val layoutDirection = layoutDirection

        val outline = shapeProvider.shape.createOutline(size, layoutDirection, density)
        val clipPath =
            if (outline is Outline.Rounded) {
                clipPath ?: Path().also { clipPath = it }
            } else {
                null
            }

        configurePaint(highlight)

        val canvas = drawContext.canvas
        canvas.save()
        canvas.clipOutline(outline, clipPath)
        canvas.drawOutline(outline, paint)
        canvas.restore()
    }

    override fun onDetach() {
        clipPath = null
        runtimeShaderCache.clear()
    }

    private fun DrawScope.configurePaint(highlight: Highlight) {
        val baseColor = highlight.style.color
        paint.color = baseColor.copy(alpha = (baseColor.alpha * highlight.alpha).coerceIn(0f, 1f))
        paint.blendMode = highlight.style.blendMode
        paint.strokeWidth = ceil(highlight.width.toPx().fastCoerceAtMost(size.minDimension / 2f)) * 2f
        paint.blur(highlight.blurRadius.toPx())
        if (isRuntimeShaderSupported()) {
            val shader =
                with(highlight.style) {
                    createShader(
                        shape = shapeProvider.shape,
                        runtimeShaderCache = runtimeShaderCache
                    )
                }
            paint.setRuntimeShader(shader)
        } else {
            paint.setRuntimeShader(null)
        }
    }
}
