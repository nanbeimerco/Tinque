package com.music.bitchord.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Derived views of the library — the sorted song list, the artist and album
 * groupings — kept across tab switches.
 *
 * The Songs / Albums / Artists tabs are one screen that is torn down whenever
 * another tab shows, and each visit used to redo these on the main thread
 * during composition: the artist grouping (splitting "feat." / "&" credits and
 * merging names over the whole library) was enough to stall the switch into
 * Artists. Now each is computed once per input, off the main thread, and
 * reused until the input changes.
 */
internal object LibraryIndexCache {
    private class Entry(val keys: List<Any?>, val value: Any)

    private val entries = HashMap<String, Entry>()

    /** Lists compare by identity (an equals walk over thousands of songs is the cost being avoided); the rest by value. */
    private fun sameKeys(a: List<Any?>, b: List<Any?>): Boolean =
        a.size == b.size && a.indices.all { i ->
            val x = a[i]
            val y = b[i]
            if (x is List<*> && y is List<*>) x === y else x == y
        }

    @Synchronized
    fun get(name: String, keys: List<Any?>): Any? =
        entries[name]?.takeIf { sameKeys(it.keys, keys) }?.value

    @Synchronized
    fun put(name: String, keys: List<Any?>, value: Any) {
        entries[name] = Entry(keys, value)
    }
}

/**
 * The cached value of [compute] for [keys], or null while it is first being
 * computed (off the main thread). Callers show an empty page for that one frame
 * rather than an "empty library" message.
 */
@Suppress("UNCHECKED_CAST")
@Composable
internal fun <T : Any> rememberLibraryIndex(
    name: String,
    vararg keys: Any?,
    compute: () -> T,
): T? {
    val keyList = keys.toList()
    val cached = remember(name, *keys) { LibraryIndexCache.get(name, keyList) as T? }
    val value by produceState(cached, name, *keys) {
        value = (LibraryIndexCache.get(name, keyList) as T?)
            ?: withContext(Dispatchers.Default) { compute() }
                .also { LibraryIndexCache.put(name, keyList, it) }
    }
    // A key change leaves the produced state on the old value for a frame;
    // prefer what the cache already holds for the new keys.
    return cached ?: value?.takeIf { LibraryIndexCache.get(name, keyList) === it }
}
