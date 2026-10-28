package com.monsterquest.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** A Silver or Gold shrine that rises near the player on its days. */
@kotlinx.serialization.Serializable
data class RoamingShrine(
    val shrine: Shrine,
    val day: Long,
    /** How many have risen today (each move out of range brings a new one). */
    val n: Int,
    /** When it rose, for its arrival animation. */
    val bornMs: Long,
)

/**
 * On Silver days (Tuesday, Thursday) and Gold days (Saturday, Sunday) a
 * shrine of that tier rises within [RANGE_M] (about 0.2 mile) of you. Walk out
 * of its range and it fades, and a new one rises near you, until you use one:
 * then it stays put for the rest of the day. The usual weekly quest goals
 * still decide whether it's open (see [Schedule]).
 */
object RoamingShrines {
    /** About 0.2 mile. */
    const val RANGE_M = 322.0
    private const val MIN_M = 90.0
    private const val MAX_M = 260.0

    fun tierOn(nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): ShrineTier? =
        when (Instant.ofEpochMilli(nowMs).atZone(zone).dayOfWeek) {
            DayOfWeek.TUESDAY, DayOfWeek.THURSDAY -> ShrineTier.SILVER
            DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> ShrineTier.GOLD
            else -> null
        }

    /** Today's roaming shrine, if it's still today's. */
    fun current(state: GameState, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): RoamingShrine? =
        state.roaming?.takeIf { it.day == GameEngine.epochDay(nowMs, zone) && it.shrine.tier == tierOn(nowMs, zone) }

    /** Whether today's shrine has been used (visited or fought): it stops moving. */
    private fun used(state: GameState, r: RoamingShrine) = state.shrines[r.shrine.id] != null

    /** Keeps a shrine near the player at [lat], [lon]: places one, or a new one when they've walked out of range. */
    fun update(state: GameState, lat: Double, lon: Double, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): GameState {
        val tier = tierOn(nowMs, zone) ?: return if (state.roaming != null) state.copy(roaming = null) else state
        val day = GameEngine.epochDay(nowMs, zone)
        val cur = current(state, nowMs, zone)
        if (cur != null && (used(state, cur) || World.distanceMeters(lat, lon, cur.shrine.lat, cur.shrine.lon) <= RANGE_M)) return state
        val n = (cur?.n ?: -1) + 1
        val r = Random(day * 7_919 + n * 31 + tier.ordinal)
        val angle = r.nextDouble() * 2 * Math.PI
        val dist = MIN_M + r.nextDouble() * (MAX_M - MIN_M)
        val sLat = lat + dist * cos(angle) / 111_320.0
        val sLon = lon + dist * sin(angle) / (111_320.0 * cos(Math.toRadians(lat)))
        val id = "roam:${tier.name.lowercase()}:$day:$n"
        val name = if (tier == ShrineTier.GOLD) "Risen Altar" else "Risen Obelisk"
        val shrine = Shrine(id, name, sLat, sLon, tier, World.guardianFor(id, tier))
        return state.copy(roaming = RoamingShrine(shrine, day, n, nowMs))
    }
}
