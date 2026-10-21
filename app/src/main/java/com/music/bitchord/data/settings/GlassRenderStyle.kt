package com.music.bitchord.data.settings

/**
 * How a Liquid Glass surface treats what is behind it.
 *
 * [FROSTED] ("Frosted"): a saturated, blurred, milky glass that bends across a
 * wide band. [CLEAR] ("Clear"): a clear pane with a light blur, bent only along
 * a thin optical rim (Snell's law), with colour fringing in that rim and a tint
 * set by one "clarity" value.
 */
enum class GlassRenderStyle(val key: String) {
    FROSTED("frosted"),
    CLEAR("clear");

    companion object {
        fun fromKey(key: String?): GlassRenderStyle = when (key) {
            // Keys saved by earlier builds, when the styles had other names.
            "tinque" -> FROSTED
            "mornye" -> CLEAR
            else -> entries.firstOrNull { it.key == key } ?: FROSTED
        }
    }
}

const val DEFAULT_GLASS_CLARITY = 0.75f

/** Half of the clear glass's dark underlay: its look, without going quite as black. */
const val DEFAULT_GLASS_SHADE = 0.5f
