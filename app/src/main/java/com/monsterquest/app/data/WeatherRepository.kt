package com.monsterquest.app.data

import android.util.Log
import com.monsterquest.core.LiveWeather
import com.monsterquest.core.WeatherReading
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * The real weather where the player is, from Open-Meteo's free forecast API
 * (no account or key). Only a rough position is sent: rounded to 2 decimals,
 * about a kilometre.
 */
object WeatherRepository {
    private const val ENDPOINT = "https://api.open-meteo.com/v1/forecast"

    /** The weather now, or null when offline or the lookup fails. */
    fun current(lat: Double, lon: Double, userAgent: String, nowMs: Long): WeatherReading? = try {
        val url = String.format(
            Locale.US,
            "%s?latitude=%.2f&longitude=%.2f&current=temperature_2m,weather_code,wind_speed_10m",
            ENDPOINT, lat, lon,
        )
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", userAgent)
            if (conn.responseCode != 200) error("Open-Meteo HTTP ${conn.responseCode}")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val now = Json.parseToJsonElement(body).jsonObject["current"]?.jsonObject ?: error("no current weather")
            val code = now["weather_code"]?.jsonPrimitive?.intOrNull ?: error("no weather code")
            val temp = now["temperature_2m"]?.jsonPrimitive?.doubleOrNull ?: 15.0
            val wind = now["wind_speed_10m"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            WeatherReading(LiveWeather.from(code, temp, wind), temp, wind, nowMs)
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        Log.w("MonsterQuest", "Weather lookup failed", e)
        null
    }
}
