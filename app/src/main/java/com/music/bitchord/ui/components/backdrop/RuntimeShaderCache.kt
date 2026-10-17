/*
 * Vendored from Kyant0/backdrop v2.0.0 (io.github.kyant0:backdrop)
 * https://github.com/Kyant0/backdrop — Copyright 2025 Kyant0, Apache License 2.0
 *
 * Vendored so the library ships as source with this app (binary AARs compiled
 * against older Compose broke at runtime) and to add a backdrop resolution
 * scale for cheaper effect rendering. KMP expect/actual declarations were
 * merged into this single Android source set. Package renamed accordingly.
 */
package com.music.bitchord.ui.components.backdrop

import android.os.Build
import org.intellij.lang.annotations.Language

sealed interface RuntimeShaderCache {

    fun obtainRuntimeShader(key: String, @Language("AGSL") string: String): RuntimeShader
}

internal class RuntimeShaderCacheImpl : RuntimeShaderCache {

    private val runtimeShaders = mutableMapOf<String, RuntimeShader>()

    override fun obtainRuntimeShader(key: String, string: String): RuntimeShader {
        // Callers normally guard the effect using isRuntimeShaderSupported();
        // keep the API boundary safe here as well in case a future call site
        // forgets that RuntimeShader is API 33+.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            throw UnsupportedOperationException("RuntimeShader requires Android 13 or later")
        }
        return runtimeShaders.getOrPut(key) {
            acquirePooledShader(key, string)
        }
    }

    fun clear() {
        if (runtimeShaders.isEmpty()) return
        for ((key, shader) in runtimeShaders) {
            releasePooledShader(key, shader)
        }
        runtimeShaders.clear()
    }

    private companion object {
        private const val MAX_POOLED_PER_KEY = 12
        private val shaderPool = HashMap<String, ArrayDeque<RuntimeShader>>()

        fun acquirePooledShader(key: String, string: String): RuntimeShader {
            synchronized(shaderPool) {
                val queue = shaderPool[key]
                if (queue != null && queue.isNotEmpty()) {
                    return queue.removeLast()
                }
            }
            return RuntimeShader(string)
        }

        fun releasePooledShader(key: String, shader: RuntimeShader) {
            synchronized(shaderPool) {
                val queue = shaderPool.getOrPut(key) { ArrayDeque() }
                if (queue.size < MAX_POOLED_PER_KEY) {
                    queue.addLast(shader)
                }
            }
        }
    }
}
