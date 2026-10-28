package com.monsterquest.app.data

import android.util.Log
import com.monsterquest.core.Shrine
import com.monsterquest.core.ShrineTile
import com.monsterquest.core.ShrineTier
import com.monsterquest.core.World
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * Finds real places near the player from OpenStreetMap's free Overpass API and
 * turns them into shrines:
 * - Item shrines: everyday spots you pass often (cafes, restaurants, parks, libraries).
 * - Silver shrines: entertainment venues (cinemas, arcades, bowling, sports
 *   centres, escape rooms, mini golf, nightclubs).
 * - Gold shrines: major destinations (museums, stadiums, theatres, zoos,
 *   aquariums, theme parks, castles and monuments).
 *
 * Shrines are permanent: the world is cut into fixed tiles (about 1 km), each
 * looked up once and kept on the phone, and each tile picks its shrines the
 * same way every time. The same lookup records lakes, rivers and coastline,
 * which bring out Water-type monsters. Tiles are refreshed after a month;
 * a tile that has never loaded (offline) shows generated shrines until it does.
 */
object PlacesRepository {
    private const val ENDPOINT = "https://overpass-api.de/api/interpreter"
    /** Shrines are shown this far around you. */
    private const val RADIUS_M = 1500.0
    /** Tiles are looked up again after this long (places open and close). */
    private const val REFRESH_MS = 30L * 24 * 60 * 60_000
    /** Water outlines are thinned to a point every this many meters. */
    private const val WATER_STEP_M = 80.0
    private const val MAX_WATER_POINTS = 600

    private val silverAmenity = setOf("cinema", "nightclub", "casino")
    private val silverLeisure = setOf("bowling_alley", "amusement_arcade", "sports_centre", "escape_game", "miniature_golf")
    private val goldAmenity = setOf("theatre", "arts_centre")
    private val goldLeisure = setOf("stadium", "water_park")
    private val goldTourism = setOf("museum", "theme_park", "zoo", "aquarium", "attraction")
    private val goldHistoric = setOf("castle", "monument", "fort", "ruins")


    /** What's around: shrines, and points along nearby water ([waterKnown]: every tile loaded for real). */
    class Area(val shrines: List<Shrine>, val water: List<Pair<Double, Double>>, val waterKnown: Boolean)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun tileFile(dir: File, key: String) = File(dir, "tile_${key.replace(':', '_')}.json")

    private fun read(dir: File, key: String): ShrineTile? = runCatching { json.decodeFromString(ShrineTile.serializer(), tileFile(dir, key).readText()) }.getOrNull()

    private fun write(dir: File, key: String, t: ShrineTile) {
        runCatching {
            dir.mkdirs()
            tileFile(dir, key).writeText(json.encodeToString(ShrineTile.serializer(), t))
        }
    }

    /** Shrines and water around a point: from the phone's tile cache, looking up tiles it doesn't have yet. */
    fun areaNear(dir: File, lat: Double, lon: Double, userAgent: String, nowMs: Long = System.currentTimeMillis()): Area {
        val keys = World.tilesAround(lat, lon, RADIUS_M)
        val tiles = keys.associateWith { read(dir, it) }.toMutableMap()
        val missing = keys.filter { k -> tiles[k].let { it == null || nowMs - it.fetchedAt > REFRESH_MS } }
        // Look up a row of tiles at a time, to keep each query small.
        for (row in missing.groupBy { it.substringBefore(':') }.values) {
            try {
                fetch(row, userAgent, nowMs).forEach { (k, t) ->
                    write(dir, k, t)
                    tiles[k] = t
                }
            } catch (e: Exception) {
                Log.w("MonsterQuest", "Overpass lookup failed for ${row.size} tiles; using what's cached", e)
            }
        }
        val shrines = keys.flatMap { k ->
            tiles[k]?.shrines ?: run {
                // Never loaded (offline): generated shrines until the real ones arrive.
                val b = World.tileBounds(k)
                World.proceduralShrines((b[0] + b[2]) / 2, (b[1] + b[3]) / 2).filter { it.lat in b[0]..b[2] && it.lon in b[1]..b[3] }
            }
        }.filter { World.distanceMeters(lat, lon, it.lat, it.lon) <= RADIUS_M }
        val water = keys.flatMap { tiles[it]?.water.orEmpty() }.map { it[0] to it[1] }
        // Waystones fill the gaps so there's always a stop within a short walk.
        return Area(shrines + World.waystones(lat, lon, shrines), water, keys.all { tiles[it] != null })
    }

    /** The old entry point: shrines only. */
    fun shrinesNear(dir: File, lat: Double, lon: Double, userAgent: String): List<Shrine> = areaNear(dir, lat, lon, userAgent).shrines

    /** Looks up [keys] (tiles in one row) and splits the places and water among them. */
    private fun fetch(keys: List<String>, userAgent: String, nowMs: Long): Map<String, ShrineTile> {
        val bounds = keys.map { World.tileBounds(it) }
        val s = bounds.minOf { it[0] }
        val w = bounds.minOf { it[1] }
        val n = bounds.maxOf { it[2] }
        val e = bounds.maxOf { it[3] }
        val bbox = String.format(Locale.US, "%.6f,%.6f,%.6f,%.6f", s, w, n, e)
        val query = """
            [out:json][timeout:60];
            (
              nwr($bbox)["amenity"~"^(cafe|restaurant|fast_food|ice_cream|library|pub|bar|food_court|cinema|theatre|arts_centre|nightclub|casino|post_office|townhall|community_centre|fountain|place_of_worship|marketplace)$"];
              nwr($bbox)["leisure"~"^(park|garden|playground|fitness_station|stadium|bowling_alley|amusement_arcade|water_park|sports_centre|escape_game|miniature_golf)$"]["name"];
              nwr($bbox)["tourism"~"^(museum|theme_park|zoo|aquarium|attraction|artwork|viewpoint|picnic_site)$"];
              nwr($bbox)["historic"~"^(castle|monument|fort|ruins)$"];
              nwr($bbox)["shop"~"^(books|supermarket|convenience|bakery|mall|department_store|gift|music|video_games|games|toys|sports|outdoor|florist|garden_centre|art|comic|hobby)$"]["name"];
            )->.places;
            .places out center;
            (
              way($bbox)["waterway"~"^(river|stream|canal)$"];
              way($bbox)["natural"~"^(water|coastline)$"];
              relation($bbox)["natural"="water"];
            )->.water;
            .water out geom($bbox);
        """.trimIndent()

        val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.doOutput = true
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.outputStream.use { it.write(("data=" + URLEncoder.encode(query, "UTF-8")).toByteArray()) }
            if (conn.responseCode != 200) error("Overpass HTTP ${conn.responseCode}")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            return parse(body, keys.toSet(), nowMs)
        } finally {
            conn.disconnect()
        }
    }

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    internal fun parse(body: String, keys: Set<String>, nowMs: Long): Map<String, ShrineTile> {
        val root = Json.parseToJsonElement(body).jsonObject
        val elements = root["elements"]?.jsonArray ?: return emptyMap()
        val places = mutableMapOf<String, MutableList<Shrine>>()
        val water = mutableMapOf<String, MutableList<List<Double>>>()
        fun addWater(lat: Double, lon: Double) {
            val k = World.tileKey(lat, lon)
            if (k !in keys) return
            val pts = water.getOrPut(k) { mutableListOf() }
            if (pts.size >= MAX_WATER_POINTS) return
            val last = pts.lastOrNull()
            if (last == null || World.distanceMeters(last[0], last[1], lat, lon) >= WATER_STEP_M) pts += listOf(lat, lon)
        }
        fun addGeometry(geom: kotlinx.serialization.json.JsonArray?) {
            geom?.forEach { p ->
                val o = p.jsonObject
                val la = o["lat"]?.jsonPrimitive?.doubleOrNull ?: return@forEach
                val lo = o["lon"]?.jsonPrimitive?.doubleOrNull ?: return@forEach
                addWater(la, lo)
            }
        }
        for (el in elements) {
            val o = el.jsonObject
            val tags = o["tags"]?.jsonObject ?: JsonObject(emptyMap())
            // Water: rivers, lakes, ponds and coastline, as points along their outline.
            if (tags.str("waterway") != null || tags.str("natural") == "water" || tags.str("natural") == "coastline") {
                addGeometry(o["geometry"]?.jsonArray)
                o["members"]?.jsonArray?.forEach { m -> addGeometry(m.jsonObject["geometry"]?.jsonArray) }
                continue
            }
            val center = o["center"]?.jsonObject ?: o
            val lat = center["lat"]?.jsonPrimitive?.doubleOrNull ?: continue
            val lon = center["lon"]?.jsonPrimitive?.doubleOrNull ?: continue
            val key = World.tileKey(lat, lon)
            if (key !in keys) continue
            val amenity = tags.str("amenity")
            val leisure = tags.str("leisure")
            val tourism = tags.str("tourism")
            val historic = tags.str("historic")
            val tier = when {
                (amenity != null && amenity in goldAmenity) || (leisure != null && leisure in goldLeisure) ||
                    (tourism != null && tourism in goldTourism) || (historic != null && historic in goldHistoric) -> ShrineTier.GOLD
                (amenity != null && amenity in silverAmenity) || (leisure != null && leisure in silverLeisure) -> ShrineTier.SILVER
                else -> ShrineTier.ITEM
            }
            val shop = tags.str("shop")
            places.getOrPut(key) { mutableListOf() } += World.shrineFromPlace(
                "${o.str("type")}${o.str("id")}", tags.str("name"), lat, lon, tier,
                kind = kindLabel(amenity, leisure, tourism, historic, shop),
                street = tags.str("addr:street"),
                category = category(amenity, leisure, tourism, historic, shop),
            )
        }
        // Every looked-up tile is kept, even an empty one (so it isn't looked up again).
        return keys.associateWith { k -> ShrineTile(nowMs, World.selectForTile(places[k].orEmpty()), water[k].orEmpty()) }
    }

    private fun category(amenity: String?, leisure: String?, tourism: String?, historic: String?, shop: String?): World.PlaceCategory = when {
        shop != null -> World.PlaceCategory.SHOP
        amenity in setOf<String?>("cafe", "restaurant", "fast_food", "ice_cream", "pub", "bar", "food_court", "marketplace") -> World.PlaceCategory.FOOD
        amenity == "library" || tourism == "museum" -> World.PlaceCategory.KNOWLEDGE
        amenity == "fountain" -> World.PlaceCategory.WATER
        tourism == "artwork" || amenity == "arts_centre" || amenity == "theatre" -> World.PlaceCategory.ART
        leisure != null && leisure in setOf("park", "garden", "playground", "fitness_station") ||
            tourism == "viewpoint" || tourism == "picnic_site" -> World.PlaceCategory.NATURE
        amenity in setOf<String?>("post_office", "townhall", "community_centre", "place_of_worship") || historic != null -> World.PlaceCategory.CIVIC
        else -> World.PlaceCategory.OTHER
    }

    /** A plain-English label for the kind of place, e.g. "Bookstore" or "Park". */
    private fun kindLabel(amenity: String?, leisure: String?, tourism: String?, historic: String?, shop: String?): String? {
        val special = mapOf(
            "books" to "Bookstore", "supermarket" to "Supermarket", "convenience" to "Convenience store",
            "bakery" to "Bakery", "mall" to "Shopping mall", "department_store" to "Department store",
            "gift" to "Gift shop", "music" to "Music store", "video_games" to "Game store", "games" to "Game store",
            "toys" to "Toy store", "sports" to "Sports store", "outdoor" to "Outdoor store", "florist" to "Florist",
            "garden_centre" to "Garden centre", "art" to "Art shop", "comic" to "Comic shop", "hobby" to "Hobby shop",
            "fast_food" to "Fast food", "ice_cream" to "Ice cream", "food_court" to "Food court",
            "post_office" to "Post office", "townhall" to "Town hall", "community_centre" to "Community centre",
            "place_of_worship" to "Place of worship", "arts_centre" to "Arts centre", "fitness_station" to "Fitness station",
            "bowling_alley" to "Bowling alley", "amusement_arcade" to "Arcade", "water_park" to "Water park",
            "sports_centre" to "Sports centre", "escape_game" to "Escape room", "miniature_golf" to "Mini golf",
            "theme_park" to "Theme park", "attraction" to "Attraction", "artwork" to "Public art",
            "picnic_site" to "Picnic site",
        )
        val raw = shop ?: amenity ?: leisure ?: tourism ?: historic ?: return null
        return special[raw] ?: raw.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
}
