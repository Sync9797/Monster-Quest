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

data class WildSpawn(
    val id: String,
    val speciesId: String,
    val level: Int,
    val lat: Double,
    val lon: Double,
    val expiresAtMs: Long,
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

    /**
     * Wild monsters around a location. Deterministic for a given place and
     * 15-minute window, so the map doesn't reshuffle as you walk.
     */
    fun spawnsNear(lat: Double, lon: Double, nowMs: Long, heroLevel: Int): List<WildSpawn> {
        val window = spawnWindow(nowMs)
        val expires = (window + 1) * SPAWN_WINDOW_MS
        val cx = floor(lat / SPAWN_CELL_DEG).toLong()
        val cy = floor(lon / SPAWN_CELL_DEG).toLong()
        val out = mutableListOf<WildSpawn>()
        for (dx in -SPAWN_CELL_RADIUS..SPAWN_CELL_RADIUS) for (dy in -SPAWN_CELL_RADIUS..SPAWN_CELL_RADIUS) {
            val x = cx + dx
            val y = cy + dy
            val r = Random(seed(x, y, window))
            if (r.nextDouble() > 0.25) continue
            val level = GameEngine.wildLevel(heroLevel, r)
            val species = Bestiary.randomWild(r, level)
            out += WildSpawn(
                id = "s:$window:$x:$y",
                speciesId = species.id,
                level = level,
                lat = (x + r.nextDouble()) * SPAWN_CELL_DEG,
                lon = (y + r.nextDouble()) * SPAWN_CELL_DEG,
                expiresAtMs = expires,
            )
        }
        return out
    }

    fun spawnMonster(spawn: WildSpawn): Monster =
        Monster.create(spawn.species, spawn.level, Random(spawn.id.hashCode().toLong()))
}
