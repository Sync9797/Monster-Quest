package com.monsterquest.app.data

import android.util.Log
import com.monsterquest.core.Shrine
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
 * Finds real places near the player (parks, museums, landmarks, places of
 * worship, libraries, artwork...) from OpenStreetMap's free Overpass API and
 * turns them into shrines. Falls back to generated shrines when offline or in
 * areas with few mapped places.
 */
object PlacesRepository {
    private const val ENDPOINT = "https://overpass-api.de/api/interpreter"
    private const val RADIUS_M = 1500

    fun shrinesNear(lat: Double, lon: Double, userAgent: String): List<Shrine> {
        val places = try {
            fetch(lat, lon, userAgent)
        } catch (e: Exception) {
            Log.w("MonsterQuest", "Overpass lookup failed, using generated shrines", e)
            emptyList()
        }
        return if (places.size >= 3) places else places + World.proceduralShrines(lat, lon)
    }

    private fun fetch(lat: Double, lon: Double, userAgent: String): List<Shrine> {
        val around = String.format(Locale.US, "around:%d,%.6f,%.6f", RADIUS_M, lat, lon)
        val query = """
            [out:json][timeout:25];
            (
              nwr($around)["tourism"~"museum|attraction|artwork|viewpoint|gallery"];
              nwr($around)["historic"~"monument|memorial|castle|ruins|fort|archaeological_site"];
              nwr($around)["amenity"~"place_of_worship|library|fountain|theatre|townhall|arts_centre"];
              nwr($around)["leisure"~"park|garden|playground"]["name"];
            );
            out center 120;
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
            return parse(body)
        } finally {
            conn.disconnect()
        }
    }

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    private fun parse(body: String): List<Shrine> {
        val root = Json.parseToJsonElement(body).jsonObject
        val elements = root["elements"]?.jsonArray ?: return emptyList()
        val shrines = mutableListOf<Shrine>()
        for (el in elements) {
            val o = el.jsonObject
            val center = o["center"]?.jsonObject ?: o
            val lat = center["lat"]?.jsonPrimitive?.doubleOrNull ?: continue
            val lon = center["lon"]?.jsonPrimitive?.doubleOrNull ?: continue
            val tags = o["tags"]?.jsonObject ?: JsonObject(emptyMap())
            val tourism = tags.str("tourism")
            val historic = tags.str("historic")
            val amenity = tags.str("amenity")
            val leisure = tags.str("leisure")
            val name = tags.str("name") ?: fallbackName(tourism, historic, amenity, leisure)
            val landmark = tourism == "museum" || tourism == "attraction" ||
                (historic != null && historic in setOf("castle", "monument", "fort", "ruins", "archaeological_site"))
            // Skip anything almost on top of an existing shrine to avoid clutter.
            if (shrines.any { World.distanceMeters(it.lat, it.lon, lat, lon) < 40 }) continue
            shrines += World.shrineFromPlace("${o.str("type")}${o.str("id")}", name, lat, lon, landmark)
        }
        return shrines
    }

    private fun fallbackName(tourism: String?, historic: String?, amenity: String?, leisure: String?): String = when {
        tourism == "artwork" -> "Artwork Shrine"
        tourism == "viewpoint" -> "Lookout Shrine"
        historic != null -> "Historic Shrine"
        amenity == "fountain" -> "Fountain Shrine"
        amenity == "place_of_worship" -> "Sacred Shrine"
        leisure != null -> "Park Shrine"
        else -> "Shrine"
    }
}
