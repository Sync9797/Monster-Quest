package com.monsterquest.core

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

/** What a real place hosts on the map. */
@Serializable
enum class VenueRole {
    /** Coffee shops, doctor's offices and most restaurants: a dungeon. */
    DUNGEON,
    /** Grocery stores: a trader (beside the store's Supply Cache). */
    MARKET,
}

/** A real place (from OpenStreetMap) that hosts a dungeon or a trader. */
@Serializable
data class Venue(
    val id: String,
    val name: String?,
    /** "Coffee shop", "Doctor's office"... */
    val kind: String?,
    val lat: Double,
    val lon: Double,
    val role: VenueRole,
)

/**
 * Puts the day's dungeons, traders, dens and thugs at real places: a dungeon
 * in every coffee shop, doctor's office and most restaurants, a trader at
 * every grocery store, and each day some of those spots are taken over by a
 * den (at that exact spot). Thugs and villains turn up along real streets.
 * Where there's no map data (offline, or very rural), the old scattered
 * placement fills in.
 */
object Venues {
    /** Share of venues a den takes over each day. */
    const val DEN_TAKEOVER = 0.15
    /** Chance each street point has a thug camp today, and the closest two camps may be. */
    const val STREET_CAMP_CHANCE = 0.03
    const val CAMP_SPACING_M = 250.0
    const val MAX_STREET_CAMPS = 8

    private fun epochDay(nowMs: Long, zone: ZoneId) = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toEpochDay()

    private fun rng(id: String, day: Long, salt: Int) = Random(id.hashCode().toLong() * 1_000_003 + day * 7_919 + salt)

    /** Whether a den has taken over [v] today. */
    fun denToday(v: Venue, day: Long): Boolean = rng(v.id, day, 1).nextDouble() < DEN_TAKEOVER

    /** A dungeon's look, from the kind of place: coffee roasters are forges, doctor's offices crypts, the rest random. */
    private fun themeFor(v: Venue, r: Random): DungeonTheme = when {
        v.kind?.contains("Coffee", ignoreCase = true) == true -> DungeonTheme.FORGE
        v.kind?.contains("Doctor", ignoreCase = true) == true || v.kind?.contains("Clinic", ignoreCase = true) == true ||
            v.kind?.contains("Dentist", ignoreCase = true) == true -> DungeonTheme.CRYPT
        else -> DungeonTheme.entries.random(r)
    }

    fun dungeons(state: GameState, venues: List<Venue>, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): List<Dungeon> {
        val day = epochDay(nowMs, zone)
        return venues.filter { it.role == VenueRole.DUNGEON && !denToday(it, day) }.mapNotNull { v ->
            val id = "dgv:${v.id}:$day"
            if (id in state.dungeonsCleared) return@mapNotNull null
            val r = rng(v.id, day, 2)
            val theme = themeFor(v, r)
            val stars = 1 + r.nextInt(5)
            val level = (state.hero.level + (stars - 2) * 3 + r.nextInt(-1, 2)).coerceAtLeast(2)
            Dungeon(id, theme.names.random(r), theme, stars, level, v.lat, v.lon, r.nextLong(), place = v.name ?: v.kind)
        }
    }

    fun traders(state: GameState, venues: List<Venue>, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): List<Trader> {
        val day = epochDay(nowMs, zone)
        return venues.filter { it.role == VenueRole.MARKET && !denToday(it, day) }.map { v ->
            // A step off the store's Supply Cache, so both can be tapped.
            Wanderers.makeTrader(state, "tv:${v.id}:$day", v.lat + 0.00018, v.lon + 0.00018, rng(v.id, day, 3))
        }
    }

    fun dens(state: GameState, venues: List<Venue>, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): List<Den> {
        val day = epochDay(nowMs, zone)
        return venues.filter { denToday(it, day) }.mapNotNull { v ->
            val id = "denv:${v.id}:$day"
            if (id in state.densCleared) null else Dens.make(state, id, v.lat, v.lon, rng(v.id, day, 4))
        }
    }

    /** Thug crews and villains standing on real streets today, spread apart. */
    fun streetCamps(state: GameState, streets: List<Pair<Double, Double>>, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): List<VillainCamp> {
        val day = epochDay(nowMs, zone)
        val out = mutableListOf<VillainCamp>()
        for ((lat, lon) in streets) {
            if (out.size >= MAX_STREET_CAMPS) break
            val key = "%.5f,%.5f".format(java.util.Locale.US, lat, lon)
            val r = rng(key, day, 5)
            if (r.nextDouble() >= STREET_CAMP_CHANCE) continue
            if (out.any { World.distanceMeters(it.lat, it.lon, lat, lon) < CAMP_SPACING_M }) continue
            val id = "vs:$key:$day"
            if (id in state.villainsDefeated) continue
            out += Villains.makeCamp(state, id, lat, lon, r)
        }
        return out
    }
}
