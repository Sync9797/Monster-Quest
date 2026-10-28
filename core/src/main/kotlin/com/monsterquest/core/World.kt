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

/** One map tile as kept on the phone: when it was looked up, its shrines, and points along its water. */
@kotlinx.serialization.Serializable
data class ShrineTile(val fetchedAt: Long, val shrines: List<Shrine>, val water: List<List<Double>> = emptyList())

@kotlinx.serialization.Serializable
data class Shrine(
    val id: String,
    /** The in-game name, e.g. "Supply Cache". */
    val name: String,
    val lat: Double,
    val lon: Double,
    val tier: ShrineTier,
    val bossSpeciesId: String? = null,
    /** The real place it stands at, e.g. "Barnes & Noble" (null for generated waystones). */
    val place: String? = null,
    /** What kind of place that is, e.g. "Bookstore". */
    val kind: String? = null,
    /** The street it's on, when the map data knows it. */
    val street: String? = null,
) {
    val isBoss: Boolean get() = tier != ShrineTier.ITEM

    /** The second line under the name: "Barnes & Noble on Wards Road", "Bookstore", ... */
    val subtitle: String?
        get() {
            val where = place ?: kind ?: return street?.let { "on $it" }
            return if (street != null) "$where on $street" else where
        }
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
    /** The real weather where you are, when known. */
    val weather: LiveWeather? = null,
    /** How far the nearest lake, river or coast is (null: unknown). */
    val waterDistanceM: Double? = null,
    /** Species that turn up more often (the mystery of the week). */
    val speciesBoosts: Map<String, Double> = emptyMap(),
) {
    val timeOfDay: TimeOfDay get() = TimeOfDay.of(hour)

    /** Spawn weight multipliers from the weather and from being near (or far from) water. */
    val boosts: Map<Element, Double>
        get() {
            val b = weather?.boosts.orEmpty().toMutableMap()
            // A weather event surges its type (fog also brings out Gas).
            WeatherEvent.of(weather)?.let { e ->
                b[e.element] = (b[e.element] ?: 1.0) * WeatherEvent.SURGE
                if (e == WeatherEvent.FOGBOUND) b[Element.GAS] = (b[Element.GAS] ?: 1.0) * WeatherEvent.SURGE
            }
            World.waterFactor(waterDistanceM)?.let { b[Element.WATER] = (b[Element.WATER] ?: 1.0) * it }
            return b
        }
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
    /** Lingers around this shrine (id) instead of roaming. */
    val shrineId: String? = null,
    /** A rare visitor (after a long phone-free session): easier to tame. */
    val visitor: Boolean = false,
) {
    val species: Species get() = Bestiary.get(speciesId)
}

/** Location-based content: shrines at real places and wild monsters that roam the map. */
object World {
    /** Water types by a lake, river or coast: this much more common within [NEAR_WATER_M]. */
    const val NEAR_WATER_FACTOR = 2.5
    /** ...and this much rarer with no water within [FAR_FROM_WATER_M]. */
    const val FAR_FROM_WATER_FACTOR = 0.4
    const val NEAR_WATER_M = 250.0
    const val FAR_FROM_WATER_M = 1_000.0

    /** How water nearby changes Water-type spawns; null when unknown or in between. */
    fun waterFactor(distanceM: Double?): Double? = when {
        distanceM == null -> null
        distanceM <= NEAR_WATER_M -> NEAR_WATER_FACTOR
        distanceM >= FAR_FROM_WATER_M -> FAR_FROM_WATER_FACTOR
        else -> null
    }

    // ---- fixed map tiles (shrines are looked up once per tile and kept) ----

    /** Tile size in degrees: about 1.1 km north-south. */
    const val TILE_DEG = 0.01

    /** A tile's key, e.g. "4075:-7399". */
    fun tileKey(lat: Double, lon: Double): String = "${floor(lat / TILE_DEG).toLong()}:${floor(lon / TILE_DEG).toLong()}"

    /** South, west, north, east edges of a tile. */
    fun tileBounds(key: String): DoubleArray {
        val (y, x) = key.split(":").map { it.toLong() }
        return doubleArrayOf(y * TILE_DEG, x * TILE_DEG, (y + 1) * TILE_DEG, (x + 1) * TILE_DEG)
    }

    /** Tiles covering [radiusM] around a point. */
    fun tilesAround(lat: Double, lon: Double, radiusM: Double): List<String> {
        val dy = kotlin.math.ceil(radiusM / 111_320.0 / TILE_DEG).toLong()
        val dx = kotlin.math.ceil(radiusM / (111_320.0 * kotlin.math.cos(Math.toRadians(lat))) / TILE_DEG).toLong()
        val cy = floor(lat / TILE_DEG).toLong()
        val cx = floor(lon / TILE_DEG).toLong()
        return (-dy..dy).flatMap { y -> (-dx..dx).map { x -> "${cy + y}:${cx + x}" } }
    }

    /** Most shrines of each tier kept per tile. */
    val TILE_CAPS = mapOf(ShrineTier.ITEM to 14, ShrineTier.SILVER to 3, ShrineTier.GOLD to 2)

    /**
     * Which of a tile's real places become shrines: the same ones every time
     * (picked by a stable hash of the place, not by where you are), spaced at
     * least 40 m apart, up to [TILE_CAPS] of each tier.
     */
    fun selectForTile(candidates: List<Shrine>): List<Shrine> {
        val kept = mutableListOf<Shrine>()
        for (sh in candidates.sortedBy { stableHash(it.id) * 31L + it.id.length }) {
            if (kept.count { it.tier == sh.tier } >= (TILE_CAPS[sh.tier] ?: 0)) continue
            if (kept.any { distanceMeters(it.lat, it.lon, sh.lat, sh.lon) < 40 }) continue
            kept += sh
        }
        return kept
    }
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
    fun shrineFromPlace(
        placeId: String,
        place: String?,
        lat: Double,
        lon: Double,
        tier: ShrineTier,
        kind: String? = null,
        street: String? = null,
        category: PlaceCategory = PlaceCategory.OTHER,
    ): Shrine {
        val id = "osm:$placeId"
        return Shrine(id, shrineTitle(tier, category), lat, lon, tier, guardianFor(id, tier), place = place, kind = kind, street = street)
    }

    /** What kind of real place a shrine stands at; picks its in-game name. */
    enum class PlaceCategory { FOOD, SHOP, KNOWLEDGE, NATURE, ART, WATER, CIVIC, OTHER }

    /** In-game shrine names, by tier and the kind of place. */
    fun shrineTitle(tier: ShrineTier, category: PlaceCategory): String = when (tier) {
        ShrineTier.GOLD -> "Ancient Altar"
        ShrineTier.SILVER -> "Silver Obelisk"
        ShrineTier.ITEM -> when (category) {
            PlaceCategory.FOOD -> "Provision Shrine"
            PlaceCategory.SHOP -> "Supply Cache"
            PlaceCategory.KNOWLEDGE -> "Lore Shrine"
            PlaceCategory.NATURE -> "Grove Shrine"
            PlaceCategory.ART -> "Muse Stone"
            PlaceCategory.WATER -> "Spring Shrine"
            PlaceCategory.CIVIC -> "Sanctum"
            PlaceCategory.OTHER -> "Wayshrine"
        }
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
    fun cellChance(c: SpawnConditions): Double = 0.26 + 0.08 * c.activity + if (c.lure) 0.1 else 0.0

    /** Monsters also wander close by, not only out on the ring: this much of the near ground has one. */
    const val NEARBY_SHARE = 0.9
    const val NEARBY_MIN_M = 70.0

    /** The type a shrine's surroundings favor, from the kind of place it stands at. */
    fun shrineElement(s: Shrine): Element? = when (s.name) {
        "Spring Shrine" -> Element.WATER
        "Grove Shrine" -> Element.EARTH
        "Provision Shrine" -> Element.FIRE
        "Supply Cache" -> Element.ELECTRIC
        "Lore Shrine" -> Element.CORRUPT
        "Muse Stone" -> Element.FLIGHT
        "Sanctum" -> Element.GAS
        else -> null
    }

    /**
     * Monsters that gather around shrines. Each shrine within the tracker's
     * reach has a good chance of one or two (boss shrines draw more), more when
     * you're on the move, standing 20-70 m from it. They favor the type that
     * suits the place (water by fountains, earth in parks...). They stay put
     * by their shrine and never ambush.
     */
    fun shrineSpawns(
        shrines: List<Shrine>,
        lat: Double,
        lon: Double,
        nowMs: Long,
        heroLevel: Int,
        conditions: SpawnConditions = SpawnConditions(12),
        /** Shrines bonded with you: monsters gather there more. */
        bonded: Set<String> = emptySet(),
    ): List<WildSpawn> {
        val window = spawnWindow(nowMs)
        val expires = (window + 1) * SPAWN_WINDOW_MS
        val rareBoost = 1.0 + conditions.activity + if (conditions.lure) 0.5 else 0.0
        val out = mutableListOf<WildSpawn>()
        for (sh in shrines) {
            val home = sh.id in bonded
            if (distanceMeters(lat, lon, sh.lat, sh.lon) > TRACK_M) continue
            val r = Random(seed(stableHash(sh.id).toLong(), window, 0x5a17))
            val chance = (if (sh.isBoss) 0.8 else 0.45) + 0.25 * conditions.activity + (if (conditions.lure) 0.15 else 0.0) + (if (home) 0.25 else 0.0)
            if (r.nextDouble() > chance) continue
            val count = (if (sh.isBoss) 2 + r.nextInt(2) else 1 + (if (r.nextDouble() < 0.25 + 0.25 * conditions.activity) 1 else 0)) + (if (home) 1 else 0)
            val favored = conditions.timeOfDay.favored + listOfNotNull(shrineElement(sh))
            repeat(count) { i ->
                val angle = r.nextDouble() * 2 * Math.PI
                val dist = 20 + r.nextDouble() * 50
                val dLat = dist * cos(angle) / 111_320.0
                val dLon = dist * sin(angle) / (111_320.0 * cos(Math.toRadians(sh.lat)))
                val level = GameEngine.wildLevel(heroLevel, r) + if (sh.isBoss) 1 else 0
                out += WildSpawn(
                    id = "h:$window:${stableHash(sh.id)}:$i",
                    speciesId = Bestiary.randomWild(r, level, favored, rareBoost, conditions.boosts, conditions.speciesBoosts).id,
                    level = level,
                    lat = sh.lat + dLat,
                    lon = sh.lon + dLon,
                    expiresAtMs = expires,
                    shiny = rollShiny(r, conditions.lure),
                    shrineId = sh.id,
                )
            }
        }
        return out
    }

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
            // Most appear out on the ring and head your way; some are already wandering nearby.
            val fromAnchor = distanceMeters(anchorLat, anchorLon, oLat, oLon)
            val nearby = fromAnchor in NEARBY_MIN_M..SPAWN_INNER_M && r.nextDouble() < NEARBY_SHARE
            if (fromAnchor !in SPAWN_INNER_M..SPAWN_OUTER_M && !nearby) continue
            val level = GameEngine.wildLevel(heroLevel, r)
            val species = Bestiary.randomWild(r, level, favored, rareBoost, conditions.boosts, conditions.speciesBoosts)
            // How quickly it closes in (share of the way per minute): most are wary, some curious.
            val curiosity = 0.018 + r.nextDouble().let { it * it } * 0.06
            val shiny = rollShiny(r, conditions.lure)
            val pull = (ageMin * curiosity * (if (conditions.lure) 2.5 else 1.0) +
                conditions.activity * 0.45 + (if (conditions.lure) 0.2 else 0.0)).coerceIn(0.0, 0.93)
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
        !spawn.follower && spawn.shrineId == null && hashPercent(spawn.id, 13) < (if (TimeOfDay.of(hour) == TimeOfDay.NIGHT) 40 else 25)

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
        Random(spawn.id.hashCode().toLong()).let { r -> Monster.create(spawn.species, spawn.level, r, shiny = spawn.shiny).withWildMoves(r) }
}
