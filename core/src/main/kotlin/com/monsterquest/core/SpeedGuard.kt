package com.monsterquest.core

/**
 * Keeps the game fair and safe: above [LIMIT_MPH] (a car, a bus) monsters
 * stop appearing, and the player is asked not to play while driving. Spawns
 * come back once you've been at walking pace for [RESUME_MS].
 */
object SpeedGuard {
    const val LIMIT_MPH = 15.0
    const val LIMIT_MPS = LIMIT_MPH * 0.44704
    const val RESUME_MS = 60_000L
    /** Fixes closer together than this are too noisy to work out a speed from. */
    private const val MIN_GAP_MS = 3_000L
    /** Fixes farther apart than this don't say how fast you're going now. */
    private const val MAX_GAP_MS = 120_000L

    data class State(
        val lat: Double? = null,
        val lon: Double? = null,
        val atMs: Long = 0,
        /** Moving too fast: no spawns. */
        val tooFast: Boolean = false,
        /** When you dropped back under the limit (spawns return [RESUME_MS] later). */
        val slowSinceMs: Long? = null,
        /** True on the reading that first crossed the limit (show the warning once). */
        val justSpedUp: Boolean = false,
        /** The last reading over the limit. */
        val fastAtMs: Long = 0,
    )

    /**
     * Whether spawns are paused at [nowMs]. Standing still sends no new fixes,
     * so after [STILL_MS] without a fast reading spawns come back anyway.
     */
    fun paused(s: State, nowMs: Long): Boolean = when {
        !s.tooFast -> false
        s.slowSinceMs != null -> nowMs - s.slowSinceMs < RESUME_MS
        else -> nowMs - s.fastAtMs < STILL_MS
    }

    const val STILL_MS = 3 * 60_000L

    /**
     * A new location fix. [gpsSpeedMps] is the phone's own speed reading if it
     * has one; otherwise speed comes from the distance since the last fix.
     */
    fun update(s: State, lat: Double, lon: Double, nowMs: Long, gpsSpeedMps: Float? = null): State {
        val gap = nowMs - s.atMs
        val measured: Double? = gpsSpeedMps?.toDouble()
            ?: if (s.lat != null && s.lon != null && gap in MIN_GAP_MS..MAX_GAP_MS) {
                World.distanceMeters(s.lat, s.lon, lat, lon) / (gap / 1000.0)
            } else {
                null
            }
        // Keep the older fix until enough time has passed to measure from it.
        val keep = gpsSpeedMps == null && s.lat != null && gap < MIN_GAP_MS
        val base = if (keep) s.copy(justSpedUp = false) else s.copy(lat = lat, lon = lon, atMs = nowMs, justSpedUp = false)
        if (measured == null) return base
        return when {
            measured > LIMIT_MPS -> base.copy(tooFast = true, slowSinceMs = null, justSpedUp = !paused(s, nowMs), fastAtMs = nowMs)
            !s.tooFast -> base
            s.slowSinceMs == null -> base.copy(slowSinceMs = nowMs)
            nowMs - s.slowSinceMs >= RESUME_MS -> base.copy(tooFast = false, slowSinceMs = null)
            else -> base
        }
    }
}
