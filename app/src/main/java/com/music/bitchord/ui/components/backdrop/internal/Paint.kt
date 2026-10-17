/*
 * Vendored from Kyant0/backdrop v2.0.0 (io.github.kyant0:backdrop)
 * https://github.com/Kyant0/backdrop — Copyright 2025 Kyant0, Apache License 2.0
 *
 * Vendored so the library ships as source with this app (binary AARs compiled
 * against older Compose broke at runtime) and to add a backdrop resolution
 * scale for cheaper effect rendering. KMP expect/actual declarations were
 * merged into this single Android source set. Package renamed accordingly.
 */
package com.music.bitchord.ui.components.backdrop.internal

import android.graphics.BlurMaskFilter
import android.util.LruCache
import androidx.compose.ui.graphics.Paint
import com.music.bitchord.ui.components.backdrop.RuntimeShader
import com.music.bitchord.ui.components.backdrop.asAndroidRuntimeShader
import kotlin.math.roundToInt

private val blurMaskFilterCache = object : LruCache<Int, BlurMaskFilter>(32) {
    override fun create(key: Int): BlurMaskFilter {
        return BlurMaskFilter(key / 100f, BlurMaskFilter.Blur.NORMAL)
    }
}

internal fun Paint.blur(radius: Float) {
    val quantizedKey = (radius * 100f).roundToInt()
    this.asFrameworkPaint().maskFilter =
        if (quantizedKey > 0) blurMaskFilterCache.get(quantizedKey)
        else null
}

internal fun Paint.setRuntimeShader(runtimeShader: RuntimeShader?) {
    this.asFrameworkPaint().shader = runtimeShader?.asAndroidRuntimeShader()
}
