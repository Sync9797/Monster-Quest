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
    /** Pharmacies: an infirmary, a free full heal once a day. */
    PHARMACY,
    /** Gyms: a training ground, a torch for party XP. */
    GYM,
    /** Hospitals: wakes every fainted monster, once a day. */
    HOSPITAL,
    /** Parks and gardens: dens here hold Earth, Flight and Gas monsters (the park keeps its shrine). */
    NATURE,
    /** Fountains and waterfronts: dens here hold Water monsters. */
    WATERSIDE,
    /** Bars, pubs and nightclubs: thugs gather here at night, and keep away by day. */
    NIGHTLIFE,
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
    /** Chance a bar has a crew outside it at night, and how far from bars they keep by day. */
    const val NIGHT_CAMP_CHANCE = 0.5
    const val BAR_CLEAR_M = 120.0

    private fun epochDay(nowMs: Long, zone: ZoneId) = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toEpochDay()

    private fun rng(id: String, day: Long, salt: Int) = Random(id.hashCode().toLong() * 1_000_003 + day * 7_919 + salt)

    /** Places dens can take over, and how often: parks and waterside spots twice as often. */
    private fun takeover(role: VenueRole): Double = when (role) {
        VenueRole.DUNGEON, VenueRole.MARKET -> DEN_TAKEOVER
        VenueRole.NATURE, VenueRole.WATERSIDE -> DEN_TAKEOVER * 2
        else -> 0.0
    }

    /** Whether a den has taken over [v] today. */
    fun denToday(v: Venue, day: Long): Boolean = rng(v.id, day, 1).nextDouble() < takeover(v.role)

    /** The types a den at [v] favors: parks bring out Earth, Flight and Gas; waterside spots Water. */
    private fun favored(v: Venue): Set<Element> = when (v.role) {
        VenueRole.NATURE -> setOf(Element.EARTH, Element.FLIGHT, Element.GAS)
        VenueRole.WATERSIDE -> setOf(Element.WATER)
        else -> emptySet()
    }

    /** Night is from 8 PM to 4 AM: thugs gather at bars then. */
    fun isNight(nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        Instant.ofEpochMilli(nowMs).atZone(zone).hour.let { it >= 20 || it < 4 }

    /** Pharmacies, gyms and hospitals near you (they're the same every day). */
    fun facilities(venues: List<Venue>): List<Venue> = venues.filter { it.role in FACILITIES }

    /** Share of restaurants that hold a dungeon. */
    const val RESTAURANT_DUNGEON_PCT = 70
    /** Two places of the same role closer than this are the same place. */
    const val SAME_PLACE_M = 30.0

    /**
     * What a place from the map's own vector tiles hosts, from its OpenStreetMap
     * value (the "subclass": cafe, doctors, supermarket...), or null for none.
     */
    fun roleForPoi(subclass: String?, cls: String?, id: String): VenueRole? {
        val v = subclass ?: cls ?: return null
        return when (v) {
            "pharmacy", "chemist" -> VenueRole.PHARMACY
            "hospital" -> VenueRole.HOSPITAL
            "fitness_centre", "gym" -> VenueRole.GYM
            "cafe", "coffee", "doctors", "doctor", "clinic", "dentist" -> VenueRole.DUNGEON
            "restaurant", "fast_food" -> if (Math.floorMod(id.hashCode(), 100) < RESTAURANT_DUNGEON_PCT) VenueRole.DUNGEON else null
            "supermarket", "convenience", "grocery", "greengrocer" -> VenueRole.MARKET
            "park", "garden" -> VenueRole.NATURE
            "fountain" -> VenueRole.WATERSIDE
            "bar", "pub", "nightclub", "biergarten" -> VenueRole.NIGHTLIFE
            else -> null
        }
    }

    /** A friendly label for a place's OpenStreetMap value ("Coffee shop", "Doctor's office"...). */
    fun kindLabel(raw: String?): String? {
        raw ?: return null
        val special = mapOf(
            "cafe" to "Coffee shop", "doctors" to "Doctor's office", "doctor" to "Doctor's office", "clinic" to "Clinic",
            "dentist" to "Dentist", "pharmacy" to "Pharmacy", "chemist" to "Pharmacy", "hospital" to "Hospital",
            "fitness_centre" to "Gym", "supermarket" to "Supermarket", "convenience" to "Convenience store",
            "grocery" to "Grocery store", "greengrocer" to "Greengrocer", "fast_food" to "Fast food", "pub" to "Pub",
            "nightclub" to "Nightclub", "biergarten" to "Beer garden",
        )
        return special[raw] ?: raw.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    /**
     * [primary] plus every place in [extra] that isn't already there (no place
     * of the same role within [SAME_PLACE_M]). Used to fill in what one map
     * source missed with the other.
     */
    fun merge(primary: List<Venue>, extra: List<Venue>): List<Venue> {
        if (extra.isEmpty()) return primary
        val out = primary.toMutableList()
        val ids = primary.mapTo(HashSet()) { it.id }
        for (v in extra) {
            if (v.id in ids) continue
            if (out.any { it.role == v.role && World.distanceMeters(it.lat, it.lon, v.lat, v.lon) < SAME_PLACE_M }) continue
            out += v
            ids += v.id
        }
        return out
    }
    val FACILITIES = setOf(VenueRole.PHARMACY, VenueRole.GYM, VenueRole.HOSPITAL)

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
            // On a park or fountain, a step off its shrine so both can be tapped.
            val (lat, lon) = if (v.role == VenueRole.NATURE || v.role == VenueRole.WATERSIDE) v.lat - 0.00018 to v.lon + 0.00018 else v.lat to v.lon
            if (id in state.densCleared) null else Dens.make(state, id, lat, lon, rng(v.id, day, 4), favored(v))
        }
    }

    /**
     * Thug crews and villains standing on real streets today, spread apart. At
     * night they also gather outside bars ([nightlife]); by day they keep away
     * from them.
     */
    fun streetCamps(
        state: GameState,
        streets: List<Pair<Double, Double>>,
        nowMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        nightlife: List<Venue> = emptyList(),
    ): List<VillainCamp> {
        val day = epochDay(nowMs, zone)
        val night = isNight(nowMs, zone)
        val out = mutableListOf<VillainCamp>()
        if (night) {
            for (v in nightlife) {
                val r = rng(v.id, day, 6)
                if (r.nextDouble() >= NIGHT_CAMP_CHANCE) continue
                val id = "vn:${v.id}:$day"
                if (id in state.villainsDefeated) continue
                out += Villains.makeCamp(state, id, v.lat + 0.00022, v.lon - 0.00012, r)
            }
        }
        for ((lat, lon) in streets) {
            if (!night && nightlife.any { World.distanceMeters(it.lat, it.lon, lat, lon) < BAR_CLEAR_M }) continue
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
