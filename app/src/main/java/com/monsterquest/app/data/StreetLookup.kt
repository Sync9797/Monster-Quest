package com.monsterquest.app.data

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Finds the street a point is on with the phone's own geocoder, for places the
 * map data didn't give an address (waystones, villain camps, wild monsters).
 * Answers are cached by roughly 30 m cells so the map never asks twice.
 */
object StreetLookup {
    private val cache = ConcurrentHashMap<String, String>()
    private const val NONE = ""

    private fun key(lat: Double, lon: Double) = String.format(Locale.US, "%.4f,%.4f", lat, lon)

    fun cached(lat: Double, lon: Double): String? = cache[key(lat, lon)]?.takeIf { it != NONE }

    suspend fun streetAt(context: Context, lat: Double, lon: Double): String? {
        val k = key(lat, lon)
        cache[k]?.let { return it.takeIf { s -> s != NONE } }
        val street = withContext(Dispatchers.IO) {
            runCatching {
                if (!Geocoder.isPresent()) return@runCatching null
                @Suppress("DEPRECATION")
                Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)
                    ?.firstOrNull()?.thoroughfare
            }.getOrNull()
        }
        cache[k] = street ?: NONE
        return street
    }
}
