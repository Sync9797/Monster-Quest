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

/**
 * Notable real weather sets off an event: a surge of its types (on top of the
 * weather's own boosts) and, once a day per kind, a special visitor of a fitting
 * type that waits on the map.
 */
enum class WeatherEvent(val label: String, val weather: LiveWeather, val element: Element, val text: String) {
    STORM_SURGE("Storm surge", LiveWeather.STORM, Element.ELECTRIC, "Lightning charges the air: Electric monsters are everywhere."),
    HEATWAVE("Heatwave", LiveWeather.HOT, Element.FIRE, "The heat draws Fire monsters out of hiding."),
    FOGBOUND("Fogbound", LiveWeather.FOG, Element.CORRUPT, "Shapes move in the fog: Corrupt and Gas monsters prowl."),
    SNOWFALL("Snowfall", LiveWeather.SNOW, Element.WATER, "Snow blankets the ground: Water monsters come out to play.");

    companion object {
        fun of(kind: LiveWeather?): WeatherEvent? = entries.firstOrNull { it.weather == kind }

        /** Extra spawn weight for the event's type, on top of the weather's own. */
        const val SURGE = 1.5

        /**
         * Starts [event] for today if it hasn't happened yet: a special visitor
         * (an Epic or rarer monster of its type when one fits your level) waits
         * on the map for [GameEngine.VISITOR_TTL_MS].
         */
        fun trigger(state: GameState, event: WeatherEvent, nowMs: Long, epochDay: Long, rng: kotlin.random.Random): Update? {
            if (state.weatherEventDays[event.name] == epochDay) return null
            val level = GameEngine.wildLevel(state.hero.level, rng) + 2
            val fits = Bestiary.wild.filter { event.element in it.types && it.minWildLevel <= level }
            val pool = fits.filter { it.rarity >= Rarity.EPIC }.ifEmpty { fits.filter { it.rarity >= Rarity.RARE } }.ifEmpty { fits }
            if (pool.isEmpty()) return null
            val v = Follower(newId(rng), pool.random(rng).id, level, nowMs, rollShiny(rng), visitor = true)
            val s = state.copy(
                followers = state.followers + v,
                weatherEventDays = state.weatherEventDays + (event.name to epochDay),
            )
            return Update(s, "${event.label}! ${event.text} A rare ${v.species.name} (Lv ${v.level}) is waiting on your map for 15 minutes.")
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
