package com.monsterquest.app.data

import android.util.Log
import com.monsterquest.core.Shrine
import com.monsterquest.core.ShrineTier
import com.monsterquest.core.World
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
 * Falls back to generated shrines when offline or in areas with few mapped places.
 */
object PlacesRepository {
    private const val ENDPOINT = "https://overpass-api.de/api/interpreter"
    private const val RADIUS_M = 1500
    private const val MAX_ITEM_SHRINES = 90
    private const val MAX_SILVER_SHRINES = 15
    private const val MAX_GOLD_SHRINES = 10

    private val silverAmenity = setOf("cinema", "nightclub", "casino")
    private val silverLeisure = setOf("bowling_alley", "amusement_arcade", "sports_centre", "escape_game", "miniature_golf")
    private val goldAmenity = setOf("theatre", "arts_centre")
    private val goldLeisure = setOf("stadium", "water_park")
    private val goldTourism = setOf("museum", "theme_park", "zoo", "aquarium", "attraction")
    private val goldHistoric = setOf("castle", "monument", "fort", "ruins")

    fun shrinesNear(lat: Double, lon: Double, userAgent: String): List<Shrine> {
        val places = try {
            fetch(lat, lon, userAgent)
        } catch (e: Exception) {
            Log.w("MonsterQuest", "Overpass lookup failed, using generated shrines", e)
            emptyList()
        }
        val base = if (places.size >= 3) places else places + World.proceduralShrines(lat, lon)
        // Waystones fill the gaps so there's always a stop within a short walk.
        return base + World.waystones(lat, lon, base)
    }

    private fun fetch(lat: Double, lon: Double, userAgent: String): List<Shrine> {
        val around = String.format(Locale.US, "around:%d,%.6f,%.6f", RADIUS_M, lat, lon)
        val query = """
            [out:json][timeout:25];
            (
              nwr($around)["amenity"~"^(cafe|restaurant|fast_food|ice_cream|library|pub|bar|food_court|cinema|theatre|arts_centre|nightclub|casino|post_office|townhall|community_centre|fountain|place_of_worship|marketplace)$"];
              nwr($around)["leisure"~"^(park|garden|playground|fitness_station|stadium|bowling_alley|amusement_arcade|water_park|sports_centre|escape_game|miniature_golf)$"]["name"];
              nwr($around)["tourism"~"^(museum|theme_park|zoo|aquarium|attraction|artwork|viewpoint|picnic_site)$"];
              nwr($around)["historic"~"^(castle|monument|fort|ruins)$"];
              nwr($around)["shop"~"^(books|supermarket|convenience|bakery|mall|department_store|gift|music|video_games|games|toys|sports|outdoor|florist|garden_centre|art|comic|hobby)$"]["name"];
            );
            out center 500;
        """.trimIndent()

        val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.doOutput = true
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.outputStream.use { it.write(("data=" + URLEncoder.encode(query, "UTF-8")).toByteArray()) }
            if (conn.responseCode != 200) error("Overpass HTTP ${conn.responseCode}")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            return parse(body, lat, lon)
        } finally {
            conn.disconnect()
        }
    }

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    private fun parse(body: String, myLat: Double, myLon: Double): List<Shrine> {
        val root = Json.parseToJsonElement(body).jsonObject
        val elements = root["elements"]?.jsonArray ?: return emptyList()
        val all = mutableListOf<Shrine>()
        for (el in elements) {
            val o = el.jsonObject
            val center = o["center"]?.jsonObject ?: o
            val lat = center["lat"]?.jsonPrimitive?.doubleOrNull ?: continue
            val lon = center["lon"]?.jsonPrimitive?.doubleOrNull ?: continue
            val tags = o["tags"]?.jsonObject ?: JsonObject(emptyMap())
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
            val kind = kindLabel(amenity, leisure, tourism, historic, shop)
            // Skip anything almost on top of an existing shrine to avoid clutter.
            if (all.any { World.distanceMeters(it.lat, it.lon, lat, lon) < 40 }) continue
            all += World.shrineFromPlace(
                "${o.str("type")}${o.str("id")}", tags.str("name"), lat, lon, tier,
                kind = kind,
                street = tags.str("addr:street"),
                category = category(amenity, leisure, tourism, historic, shop),
            )
        }
        // Dense downtowns have hundreds of cafes; keep the closest ones of each tier.
        fun closest(tier: ShrineTier, n: Int) =
            all.filter { it.tier == tier }.sortedBy { World.distanceMeters(myLat, myLon, it.lat, it.lon) }.take(n)
        return closest(ShrineTier.ITEM, MAX_ITEM_SHRINES) +
            closest(ShrineTier.SILVER, MAX_SILVER_SHRINES) +
            closest(ShrineTier.GOLD, MAX_GOLD_SHRINES)
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
