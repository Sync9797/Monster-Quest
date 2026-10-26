package com.monsterquest.core

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class ShrineTier(val label: String) {
    /** Loot: weapons, spell pages, orbs, potions. Always open. */
    ITEM("Item shrine"),
    /** Tough non-legendary guardians; open Tue/Thu when the quest goal is met. */
    SILVER("Silver shrine"),
    /** Legendary-class guardians; open Sat/Sun when the weekly quest goal is met. */
    GOLD("Gold shrine"),
}

data class Shrine(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val tier: ShrineTier,
    val bossSpeciesId: String? = null,
) {
    val isBoss: Boolean get() = tier != ShrineTier.ITEM
}

/** Times of day change which monster types are out, like a day/night cycle. */
enum class TimeOfDay(val label: String, val hours: IntRange, val favored: Set<Element>) {
    DAWN("Dawn", 5..9, setOf(Element.FLIGHT, Element.WATER)),
    DAY("Day", 10..16, setOf(Element.FIRE, Element.ELECTRIC)),
    DUSK("Dusk", 17..20, setOf(Element.GAS, Element.WATER)),
    NIGHT("Night", 21..28, setOf(Element.CORRUPT, Element.GAS));

    companion object {
        fun of(hour: Int): TimeOfDay = entries.firstOrNull { hour in it.hours || hour + 24 in it.hours } ?: NIGHT
    }
}

/** What shapes the spawns around you right now. */
data class SpawnConditions(
    val hour: Int,
    /** How much you've been moving lately, 0..1 (see GameEngine.activityLevel). */
    val activity: Double = 0.0,
    val lure: Boolean = false,
) {
    val timeOfDay: TimeOfDay get() = TimeOfDay.of(hour)
}

data class WildSpawn(
    val id: String,
    val speciesId: String,
    val level: Int,
    val lat: Double,
    val lon: Double,
    val expiresAtMs: Long,
    /** Drawn to you by a lure. */
    val lured: Boolean = false,
) {
    val species: Species get() = Bestiary.get(speciesId)
}

/** Location-based content: shrines at real places and wild monsters that roam the map. */
object World {
    /** How close (meters) you must be to use a shrine. */
    const val SHRINE_RANGE_M = 60.0
    /** How close (meters) you must be to battle a wild spawn. */
    const val SPAWN_RANGE_M = 100.0
    const val SHRINE_COOLDOWN_MS = 30 * 60_000L
    const val BOSS_COOLDOWN_MS = 4 * 60 * 60_000L
    /** Each silver shrine can be fought once per open day. */
    const val SILVER_COOLDOWN_MS = 20 * 60 * 60_000L
    /** Wild spawns reshuffle every 15 minutes. */
    const val SPAWN_WINDOW_MS = 15 * 60_000L
    /** Monsters wandering right up to you come and go every 5 minutes. */
    const val NEARBY_WINDOW_MS = 5 * 60_000L
    private const val NEARBY_CELL_DEG = 0.0005 // ~55 m, so GPS jitter doesn't reshuffle them

    private const val SPAWN_CELL_DEG = 0.0015 // ~160 m
    private const val SPAWN_CELL_RADIUS = 3
    private const val SHRINE_CELL_DEG = 0.004 // ~440 m
    private const val SHRINE_CELL_RADIUS = 3

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(sqrt(a))
    }

    private fun seed(vararg parts: Long): Long {
        var h = 1125899906842597L
        for (p in parts) {
            h = 31 * h + p
            h = h xor (h ushr 29)
            h *= -0x40a7b892e31b1a47L
        }
        return h
    }

    private fun stableHash(s: String): Int = abs(s.hashCode() % 1_000_000)

    private fun guardianFor(id: String, tier: ShrineTier): String? {
        val pool = when (tier) {
            ShrineTier.ITEM -> return null
            ShrineTier.SILVER -> Bestiary.silverGuardians
            ShrineTier.GOLD -> Bestiary.bosses
        }
        return pool[stableHash(id) % pool.size].id
    }

    /**
     * Turn a real-world place (from OpenStreetMap) into a shrine. The app
     * picks the tier from the kind of place: everyday spots are item shrines,
     * entertainment venues silver, and major destinations gold.
     */
    fun shrineFromPlace(placeId: String, name: String, lat: Double, lon: Double, tier: ShrineTier): Shrine {
        val id = "osm:$placeId"
        return Shrine(id, name, lat, lon, tier, guardianFor(id, tier))
    }

    /** Fallback shrines for areas with no mapped places (e.g. rural areas or offline). */
    fun proceduralShrines(lat: Double, lon: Double): List<Shrine> {
        val cx = floor(lat / SHRINE_CELL_DEG).toLong()
        val cy = floor(lon / SHRINE_CELL_DEG).toLong()
        val out = mutableListOf<Shrine>()
        for (dx in -SHRINE_CELL_RADIUS..SHRINE_CELL_RADIUS) for (dy in -SHRINE_CELL_RADIUS..SHRINE_CELL_RADIUS) {
            val x = cx + dx
            val y = cy + dy
            val r = Random(seed(x, y, 7))
            if (r.nextDouble() > 0.45) continue
            val id = "gen:$x:$y"
            val roll = r.nextDouble()
            val tier = when {
                roll < 0.08 -> ShrineTier.GOLD
                roll < 0.2 -> ShrineTier.SILVER
                else -> ShrineTier.ITEM
            }
            val sLat = (x + r.nextDouble()) * SHRINE_CELL_DEG
            val sLon = (y + r.nextDouble()) * SHRINE_CELL_DEG
            val name = when (tier) {
                ShrineTier.GOLD -> "Ancient Altar"
                ShrineTier.SILVER -> "Silver Obelisk"
                ShrineTier.ITEM -> listOf("Wayshrine", "Supply Cache", "Stone Circle", "Spirit Well").random(r)
            }
            out += Shrine(id, name, sLat, sLon, tier, guardianFor(id, tier))
        }
        return out
    }

    fun spawnWindow(nowMs: Long): Long = nowMs / SPAWN_WINDOW_MS

    fun windowOf(spawnId: String): Long? = spawnId.split(":").getOrNull(1)?.toLongOrNull()

    /** Chance a map cell holds a monster: more when you've been moving, more again with a lure. */
    fun cellChance(c: SpawnConditions): Double = 0.18 + 0.17 * c.activity + if (c.lure) 0.15 else 0.0

    /**
     * Wild monsters around a location, Pokémon GO style:
     * - Monsters scattered across the map, reshuffled every 15 minutes. Moving
     *   around raises how many there are and how rare they get, and walking
     *   somewhere new reveals fresh ones.
     * - The time of day favors certain types (fire by day, corrupt at night...).
     * - Even standing still, a monster wanders up to you most 5-minute windows,
     *   so the game stays playable at home or at a desk.
     * - A lure draws extra (and rarer) monsters right to you for 30 minutes.
     * Deterministic for a place and time window, so the map doesn't reshuffle as you walk.
     */
    fun spawnsNear(lat: Double, lon: Double, nowMs: Long, heroLevel: Int, conditions: SpawnConditions = SpawnConditions(12)): List<WildSpawn> {
        val window = spawnWindow(nowMs)
        val expires = (window + 1) * SPAWN_WINDOW_MS
        val favored = conditions.timeOfDay.favored
        val rareBoost = 1.0 + conditions.activity + if (conditions.lure) 0.5 else 0.0
        val chance = cellChance(conditions)
        val cx = floor(lat / SPAWN_CELL_DEG).toLong()
        val cy = floor(lon / SPAWN_CELL_DEG).toLong()
        val out = mutableListOf<WildSpawn>()
        for (dx in -SPAWN_CELL_RADIUS..SPAWN_CELL_RADIUS) for (dy in -SPAWN_CELL_RADIUS..SPAWN_CELL_RADIUS) {
            val x = cx + dx
            val y = cy + dy
            val r = Random(seed(x, y, window))
            if (r.nextDouble() > chance) continue
            val level = GameEngine.wildLevel(heroLevel, r)
            val species = Bestiary.randomWild(r, level, favored, rareBoost)
            out += WildSpawn(
                id = "s:$window:$x:$y",
                speciesId = species.id,
                level = level,
                lat = (x + r.nextDouble()) * SPAWN_CELL_DEG,
                lon = (y + r.nextDouble()) * SPAWN_CELL_DEG,
                expiresAtMs = expires,
            )
        }
        out += nearbySpawns(lat, lon, nowMs, heroLevel, conditions)
        return out
    }

    /** Monsters that wander right up to you (always within battle range). */
    private fun nearbySpawns(lat: Double, lon: Double, nowMs: Long, heroLevel: Int, c: SpawnConditions): List<WildSpawn> {
        val window = spawnWindow(nowMs)
        val small = nowMs / NEARBY_WINDOW_MS
        val expires = (small + 1) * NEARBY_WINDOW_MS
        val x = floor(lat / NEARBY_CELL_DEG).toLong()
        val y = floor(lon / NEARBY_CELL_DEG).toLong()
        val centerLat = (x + 0.5) * NEARBY_CELL_DEG
        val centerLon = (y + 0.5) * NEARBY_CELL_DEG
        val r = Random(seed(x, y, small, 11))
        val count = when {
            c.lure -> 2 + (if (r.nextDouble() < 0.5) 1 else 0)
            r.nextDouble() < 0.6 -> 1
            else -> 0
        }
        return (0 until count).map { k ->
            val level = GameEngine.wildLevel(heroLevel, r)
            val species = Bestiary.randomWild(r, level, c.timeOfDay.favored, if (c.lure) 2.0 else 1.0)
            WildSpawn(
                id = "s:$window:n$small:$x:$y:$k",
                speciesId = species.id,
                level = level,
                lat = centerLat + (r.nextDouble() - 0.5) * NEARBY_CELL_DEG,
                lon = centerLon + (r.nextDouble() - 0.5) * NEARBY_CELL_DEG,
                expiresAtMs = expires,
                lured = c.lure && k > 0,
            )
        }
    }

    /**
     * Extra item stops ("waystones") filling the gaps between real places, so
     * there's always something to collect within a short walk.
     */
    fun waystones(lat: Double, lon: Double, existing: List<Shrine>): List<Shrine> {
        val cell = 0.0025 // ~280 m
        val cx = floor(lat / cell).toLong()
        val cy = floor(lon / cell).toLong()
        val out = mutableListOf<Shrine>()
        for (dx in -5..5) for (dy in -5..5) {
            val x = cx + dx
            val y = cy + dy
            val r = Random(seed(x, y, 23))
            if (r.nextDouble() > 0.55) continue
            val sLat = (x + r.nextDouble()) * cell
            val sLon = (y + r.nextDouble()) * cell
            if ((existing + out).any { distanceMeters(it.lat, it.lon, sLat, sLon) < 120 }) continue
            val name = listOf("Waystone", "Supply Cache", "Stone Circle", "Spirit Well", "Old Signpost", "Traveler's Rest").random(r)
            out += Shrine("way:$x:$y", name, sLat, sLon, ShrineTier.ITEM)
        }
        return out
    }

    fun spawnMonster(spawn: WildSpawn): Monster =
        Monster.create(spawn.species, spawn.level, Random(spawn.id.hashCode().toLong()))
}
