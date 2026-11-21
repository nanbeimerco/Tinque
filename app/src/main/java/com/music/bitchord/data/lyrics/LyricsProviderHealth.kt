package com.music.bitchord.data.lyrics

import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Explicit failure signal raised by a lyrics provider when an HTTP error
 * (401/403/429/5xx), timeout, DNS, or connection error prevented lookup.
 * Normal "not found" / 404 / empty results must return null without throwing.
 */
class ProviderFailureException(
    val statusCode: Int?,
    reason: String,
    cause: Throwable? = null,
) : IOException(reason, cause)

/**
 * Tracks lyrics provider health and enforces cooldowns on failing providers,
 * modeled directly after SpotiFLAC Mobile's provider health system.
 *
 * If a provider encounters a connection error, 401/403/429/5xx server error, or timeout,
 * it is placed on a 10-minute cooldown so subsequent songs do not lag or stall.
 * Normal "not found" (404 / empty) misses do NOT trigger cooldowns.
 */
object LyricsProviderHealth {

    private const val COOLDOWN_MS = 10 * 60 * 1000L // 10 minutes

    private data class HealthEntry(
        val unavailableUntil: Long,
        val reason: String,
    )

    private val healthMap = ConcurrentHashMap<String, HealthEntry>()

    /**
     * Checks if a provider is currently in cooldown and should be skipped.
     */
    fun shouldSkip(providerId: String): Boolean {
        val key = providerId.lowercase().trim()
        val entry = healthMap[key] ?: return false
        val now = System.currentTimeMillis()
        if (now < entry.unavailableUntil) {
            val remainingSec = (entry.unavailableUntil - now) / 1000
            LyricsLog.i("Health", "Skipping $providerId (in cooldown for ${remainingSec}s: ${entry.reason})")
            return true
        }
        healthMap.remove(key)
        return false
    }

    /**
     * Marks a provider as available, clearing any existing cooldown.
     */
    fun markAvailable(providerId: String) {
        val key = providerId.lowercase().trim()
        if (healthMap.remove(key) != null) {
            LyricsLog.i("Health", "Provider $providerId recovered and cooldown cleared")
        }
    }

    /**
     * Marks a provider as unavailable for [COOLDOWN_MS] due to a service/network error.
     */
    fun markUnavailable(providerId: String, error: Throwable) {
        // Do not cooldown on expected "not found" or user cancellations
        if (error is kotlinx.coroutines.CancellationException) return
        if (error is ProviderFailureException && error.statusCode == 404) return
        val message = error.message ?: error.javaClass.simpleName
        if (error !is ProviderFailureException &&
            (message.contains("not found", ignoreCase = true) || message.contains("404"))
        ) {
            return
        }

        val key = providerId.lowercase().trim()
        val unavailableUntil = System.currentTimeMillis() + COOLDOWN_MS
        val cleanReason = if (error is ProviderFailureException && error.statusCode != null) {
            "HTTP ${error.statusCode}: ${message.take(100)}"
        } else {
            message.take(120)
        }
        healthMap[key] = HealthEntry(unavailableUntil, cleanReason)
        LyricsLog.w("Health", "Provider $providerId marked unavailable for 10m: $cleanReason")
    }

    /**
     * Clears all health records (e.g. when user changes provider settings).
     */
    fun clearAll() {
        healthMap.clear()
    }
}
