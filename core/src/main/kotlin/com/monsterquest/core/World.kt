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
    DAWN("Dawn", 5..9, setOf(Element.FLIGHT, Element.EARTH)),
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
    /** Where it first appeared, out on the edge of your range. */
    val originLat: Double = lat,
    val originLon: Double = lon,
    /** Followed you because you walked (a walking reward); waits right beside you. */
    val follower: Boolean = false,
    /** A rare color variant. */
    val shiny: Boolean = false,
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
    /** Monsters closer than this show on the map; farther ones only in the tracker. */
    const val REVEAL_M = 200.0
    /** How far the tracker can sense monsters. */
    const val TRACK_M = 1_000.0
    /** New monsters appear in a ring this far from where you were when they spawned. */
    const val SPAWN_INNER_M = 300.0
    const val SPAWN_OUTER_M = 800.0
    /** Monsters this close to the one you fight may join in as a pack. */
    const val PACK_RADIUS_M = 120.0
    /** Aggressive monsters this close may ambush you. */
    const val AMBUSH_M = 60.0
    private const val SPAWN_CELL_RADIUS = 6

    private const val SPAWN_CELL_DEG = 0.0015 // ~160 m
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

    /** Gold guardians before the Earth legendary arrived; their shrines keep the same guardian. */
    private const val ORIGINAL_GOLD_GUARDIANS = 12

    private fun guardianFor(id: String, tier: ShrineTier): String? {
        val h = stableHash(id)
        return when (tier) {
            ShrineTier.ITEM -> null
            ShrineTier.SILVER -> Bestiary.silverGuardians.let { it[h % it.size].id }
            ShrineTier.GOLD -> {
                // Newer guardians take over a fair share of shrines; every other shrine
                // keeps the guardian it always had (so past boss wins still count).
                val pool = Bestiary.bosses
                val slot = h % pool.size
                val original = pool.filter { it.id != "worldroot_colossus" }
                if (slot >= ORIGINAL_GOLD_GUARDIANS) pool.first { it.id == "worldroot_colossus" }.id
                else original[h % ORIGINAL_GOLD_GUARDIANS].id
            }
        }
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
    fun cellChance(c: SpawnConditions): Double = 0.10 + 0.08 * c.activity + if (c.lure) 0.08 else 0.0

    /**
     * Wild monsters around you, Pokémon GO style but on the move:
     * - They appear out in a ring 300–800 m from where you were when they
     *   spawned ([anchorLat], [anchorLon]), so you head toward them.
     * - They hear you: each one drifts toward your current position over its
     *   15-minute window, and much faster while you're walking ([SpawnConditions.activity]).
     *   Curious ones reach you even if you stand still; lures pull them in fast.
     * - The time of day favors certain types; moving and lures make rarer ones likelier.
     * Deterministic for a place and time window, so the map doesn't reshuffle.
     */
    fun spawnsNear(
        lat: Double,
        lon: Double,
        nowMs: Long,
        heroLevel: Int,
        conditions: SpawnConditions = SpawnConditions(12),
        anchorLat: Double = lat,
        anchorLon: Double = lon,
    ): List<WildSpawn> {
        val window = spawnWindow(nowMs)
        val expires = (window + 1) * SPAWN_WINDOW_MS
        val ageMin = (nowMs - window * SPAWN_WINDOW_MS) / 60_000.0
        val favored = conditions.timeOfDay.favored
        val rareBoost = 1.0 + conditions.activity + if (conditions.lure) 0.5 else 0.0
        val chance = cellChance(conditions)
        val cx = floor(anchorLat / SPAWN_CELL_DEG).toLong()
        val cy = floor(anchorLon / SPAWN_CELL_DEG).toLong()
        val out = mutableListOf<WildSpawn>()
        for (dx in -SPAWN_CELL_RADIUS..SPAWN_CELL_RADIUS) for (dy in -SPAWN_CELL_RADIUS..SPAWN_CELL_RADIUS) {
            val x = cx + dx
            val y = cy + dy
            val r = Random(seed(x, y, window))
            if (r.nextDouble() > chance) continue
            val oLat = (x + r.nextDouble()) * SPAWN_CELL_DEG
            val oLon = (y + r.nextDouble()) * SPAWN_CELL_DEG
            if (distanceMeters(anchorLat, anchorLon, oLat, oLon) !in SPAWN_INNER_M..SPAWN_OUTER_M) continue
            val level = GameEngine.wildLevel(heroLevel, r)
            val species = Bestiary.randomWild(r, level, favored, rareBoost)
            // How quickly it closes in (share of the way per minute): most are wary, some curious.
            val curiosity = 0.008 + r.nextDouble().let { it * it } * 0.055
            val shiny = rollShiny(r, conditions.lure)
            val pull = (ageMin * curiosity * (if (conditions.lure) 2.5 else 1.0) +
                conditions.activity * 0.55 + (if (conditions.lure) 0.2 else 0.0)).coerceIn(0.0, 0.93)
            out += WildSpawn(
                id = "s:$window:$x:$y",
                speciesId = species.id,
                level = level,
                lat = oLat + (lat - oLat) * pull,
                lon = oLon + (lon - oLon) * pull,
                expiresAtMs = expires,
                lured = conditions.lure && curiosity > 0.03,
                originLat = oLat,
                originLon = oLon,
                shiny = shiny,
            )
        }
        return out
    }

    /** Compass bearing in degrees (0 = north, 90 = east) from the first point to the second. */
    fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lon2 - lon1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(kotlin.math.atan2(y, x)) + 360) % 360
    }

    /** "N", "NE", "E", ... for a bearing. */
    fun compass(bearing: Double): String =
        listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[(((bearing + 22.5) % 360) / 45).toInt()]

    private fun hashPercent(id: String, salt: Int) = abs((id.hashCode() * 31 + salt) % 100)

    /**
     * Monsters near the one you fight that join in. Not every monster teams up:
     * each nearby one joins about 45% of the time (fixed per monster), up to 3.
     */
    fun packFor(target: WildSpawn, others: List<WildSpawn>): List<WildSpawn> =
        others.asSequence()
            .filter { it.id != target.id && !it.follower }
            .filter { distanceMeters(target.lat, target.lon, it.lat, it.lon) <= PACK_RADIUS_M }
            .filter { hashPercent(it.id, 7) < 45 }
            .sortedBy { distanceMeters(target.lat, target.lon, it.lat, it.lon) }
            .take(3)
            .toList()

    /** Some monsters are aggressive and will attack first when they reach you; more of them at night. */
    fun isAggressive(spawn: WildSpawn, hour: Int): Boolean =
        !spawn.follower && hashPercent(spawn.id, 13) < (if (TimeOfDay.of(hour) == TimeOfDay.NIGHT) 40 else 25)

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
        Monster.create(spawn.species, spawn.level, Random(spawn.id.hashCode().toLong()), shiny = spawn.shiny)
}
