package com.monsterquest.core

/**
 * The real weather where the player is (from a weather service), and how it
 * shapes the monsters out on the map. Not to be confused with [Weather], the
 * in-battle weather; real rain and heat do start a battle in Rain or Drought.
 */
enum class LiveWeather(
    val label: String,
    /** Spawn weight multipliers by type: above 1 is more common, below 1 rarer. */
    val boosts: Map<Element, Double>,
    /** The in-battle weather a fight starts with. */
    val battle: Weather = Weather.CLEAR,
) {
    CLEAR("Clear", mapOf(Element.FIRE to 1.3, Element.FLIGHT to 1.3)),
    HOT("Heat", mapOf(Element.FIRE to 2.0, Element.EARTH to 1.3, Element.WATER to 0.6), Weather.DROUGHT),
    CLOUDY("Cloudy", mapOf(Element.GAS to 1.4, Element.CORRUPT to 1.3, Element.EARTH to 1.2)),
    FOG("Fog", mapOf(Element.GAS to 2.0, Element.CORRUPT to 1.6, Element.FLIGHT to 0.6)),
    RAIN("Rain", mapOf(Element.WATER to 2.0, Element.ELECTRIC to 1.4, Element.FIRE to 0.5), Weather.RAIN),
    STORM("Thunderstorm", mapOf(Element.ELECTRIC to 2.5, Element.WATER to 1.6, Element.FIRE to 0.4, Element.FLIGHT to 0.6), Weather.RAIN),
    SNOW("Snow", mapOf(Element.WATER to 1.5, Element.EARTH to 1.3, Element.FIRE to 0.5)),
    WIND("Windy", mapOf(Element.FLIGHT to 2.0, Element.GAS to 0.7));

    /** Types that turn up more often, strongest first. */
    val more: List<Element> get() = boosts.filter { it.value > 1.0 }.entries.sortedByDescending { it.value }.map { it.key }

    /** Types that turn up less often. */
    val fewer: List<Element> get() = boosts.filter { it.value < 1.0 }.keys.toList()

    companion object {
        /** Above this (°C) a clear sky counts as a heatwave. */
        const val HOT_C = 27.0
        /** Above this (km/h) a dry day counts as windy. */
        const val WINDY_KMH = 30.0

        /**
         * From a WMO weather code (as weather services report it), the
         * temperature and the wind speed.
         */
        fun from(code: Int, tempC: Double, windKmh: Double): LiveWeather = when (code) {
            in 95..99 -> STORM
            in 71..77, 85, 86 -> SNOW
            in 51..67, in 80..82 -> RAIN
            45, 48 -> FOG
            else -> when {
                windKmh >= WINDY_KMH -> WIND
                code <= 1 && tempC >= HOT_C -> HOT
                code <= 1 -> CLEAR
                else -> CLOUDY
            }
        }
    }
}

/** A reading of the real weather, and when it was taken. */
data class WeatherReading(
    val kind: LiveWeather,
    val tempC: Double,
    val windKmh: Double,
    val atMs: Long,
)
