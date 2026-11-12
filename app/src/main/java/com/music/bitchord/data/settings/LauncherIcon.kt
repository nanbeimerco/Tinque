package com.music.bitchord.data.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * The launcher artwork variants which ship with the app, in the order the
 * picker shows them.
 *
 * PINK, BLACK and GREEN are saved names from the BitChord artwork. They stay,
 * with the Tinque mark in their place ("normal", black-and-white, forest), so
 * an update never changes which alias is enabled — a launcher can drop a
 * home-screen icon whose alias goes away.
 */
enum class LauncherIcon(
    internal val alias: String,
    /** Background colours (two for a gradient), lines, and the third line. */
    val background: List<Long>,
    val lines: Long,
    val third: Long,
) {
    PINK(".LauncherPink", listOf(0xFF111111), 0xFFFFFFFF, 0xFFFF5A36),
    BLACK(".LauncherBlack", listOf(0xFF111111), 0xFFFFFFFF, 0xFFFFFFFF),
    BLACK_ORANGE(".LauncherBlackOrange", listOf(0xFF111111), 0xFFFF5A36, 0xFFFF5A36),
    ORANGE(".LauncherOrange", listOf(0xFFFF5A36), 0xFFFFFFFF, 0xFFFFFFFF),
    CREAM(".LauncherCream", listOf(0xFFF4F2EC), 0xFF111111, 0xFF111111),
    WHITE(".LauncherWhite", listOf(0xFFFFFFFF), 0xFF111111, 0xFFFF5A36),
    RED_ORANGE(".LauncherRedOrange", listOf(0xFFF76945), 0xFFFFFFFF, 0xFFFFFFFF),
    HEAT_PINK(".LauncherHeatPink", listOf(0xFFF7458F), 0xFFFFFFFF, 0xFFFFFFFF),
    NAVY(".LauncherNavy", listOf(0xFF14213D), 0xFFFFFFFF, 0xFFFF5A36),
    GREEN(".LauncherGreen", listOf(0xFF1F3A2E), 0xFFF4F2EC, 0xFFF4F2EC),
    SUNSET(".LauncherSunset", listOf(0xFFF76945, 0xFFF7458F), 0xFFFFFFFF, 0xFFFFFFFF),
}

/**
 * The old "mark one unit to the right" black-and-white alias. Every variant is
 * centred that way now, so it is never chosen again; it stays in the manifest
 * (and is switched off here) so an update never leaves a launcher entry dangling.
 */
internal const val BLACK_SHIFTED_ALIAS = ".LauncherBlackShifted"

/** Switches launcher aliases without restarting or disabling the app itself. */
internal object LauncherIconManager {
    fun apply(context: Context, selected: LauncherIcon) {
        val packageManager = context.packageManager
        // Manifest-relative alias names are resolved against the app's code
        // namespace (com.music.bitchord), while context.packageName is the
        // flavor applicationId. ComponentName needs both values separately.
        val componentNamespace = "com.music.bitchord"
        val wanted = selected.alias
        val aliases = LauncherIcon.entries.map { it.alias } + BLACK_SHIFTED_ALIAS
        val states = aliases.map { alias ->
            val component = ComponentName(context.packageName, componentNamespace + alias)
            val state = if (alias == wanted) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            component to state
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val applied = runCatching {
                packageManager.setComponentEnabledSettings(
                    states.map { (component, state) ->
                        PackageManager.ComponentEnabledSetting(
                            component,
                            state,
                            PackageManager.DONT_KILL_APP,
                        )
                    },
                )
            }.isSuccess
            if (applied) return
        }

        // On older Android releases there is no atomic multi-component update.
        // Enable the destination first so the launcher never briefly loses the app.
        states.firstOrNull { it.second == PackageManager.COMPONENT_ENABLED_STATE_ENABLED }
            ?.let { (component, state) ->
                runCatching {
                    packageManager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
                }
            }
        states.filter { it.second != PackageManager.COMPONENT_ENABLED_STATE_ENABLED }.forEach { (component, state) ->
            runCatching {
                packageManager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
            }
        }
    }
}
