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

    /** Types the weather favors in battle: they fight with +[FAVORED_PCT]% Attack and Speed (on either side). */
    val favored: List<Element> get() = when (this) {
        CLEAR -> listOf(Element.FIRE, Element.FLIGHT)
        HOT -> listOf(Element.FIRE, Element.EARTH)
        CLOUDY -> listOf(Element.GAS, Element.CORRUPT)
        FOG -> listOf(Element.GAS, Element.CORRUPT)
        RAIN -> listOf(Element.WATER, Element.ELECTRIC)
        STORM -> listOf(Element.ELECTRIC, Element.WATER)
        SNOW -> listOf(Element.WATER, Element.EARTH)
        WIND -> listOf(Element.FLIGHT)
    }

    /** Types the weather holds back in battle: −[HINDERED_PCT]% Attack. */
    val hindered: List<Element> get() = fewer

    /** The battle multiplier for a monster of [types] on Attack (and, when favored, Speed). */
    fun attackFactor(types: List<Element>): Double = when {
        types.any { it in favored } -> 1 + FAVORED_PCT / 100.0
        types.any { it in hindered } -> 1 - HINDERED_PCT / 100.0
        else -> 1.0
    }

    fun speedFactor(types: List<Element>): Double = if (types.any { it in favored }) 1 + FAVORED_PCT / 100.0 else 1.0

    /** Types that turn up more often, strongest first. */
    val more: List<Element> get() = boosts.filter { it.value > 1.0 }.entries.sortedByDescending { it.value }.map { it.key }

    /** Types that turn up less often. */
    val fewer: List<Element> get() = boosts.filter { it.value < 1.0 }.keys.toList()

    companion object {
        /** Battle boost for the weather's favored types, and the cut for the ones it holds back. */
        const val FAVORED_PCT = 10
        const val HINDERED_PCT = 10

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
         * Starts [event] for today if it hasn't happened yet: a rare visitor of
         * its type (Epic or rarer when one fits your level) finds you on your
         * next battery-saver walk within [GameEngine.VISITOR_TTL_MS].
         */
        fun trigger(state: GameState, event: WeatherEvent, nowMs: Long, epochDay: Long, rng: kotlin.random.Random): Update? {
            if (state.weatherEventDays[event.name] == epochDay) return null
            val v = GameEngine.rareVisitor(state, nowMs, rng, event.element) ?: return null
            val s = GameEngine.addVisitor(state, v, nowMs).copy(weatherEventDays = state.weatherEventDays + (event.name to epochDay))
            return Update(
                s,
                "${event.label}! ${event.text} A rare ${v.species.name} (Lv ${v.level}) is out in it: turn on the battery saver and walk within " +
                    "${GameEngine.VISITOR_TTL_MS / 3_600_000} hours to meet it.",
            )
        }

        /**
         * How the weather (and its event's surge) and nearby water shift which
         * types find you on a walk: spawn-weight multipliers by type.
         */
        fun encounterBoosts(weather: LiveWeather?, waterDistanceM: Double? = null): Map<Element, Double> {
            val b = weather?.boosts.orEmpty().toMutableMap()
            // A weather event surges its type (fog also brings out Gas).
            of(weather)?.let { e ->
                b[e.element] = (b[e.element] ?: 1.0) * SURGE
                if (e == FOGBOUND) b[Element.GAS] = (b[Element.GAS] ?: 1.0) * SURGE
            }
            World.waterFactor(waterDistanceM)?.let { b[Element.WATER] = (b[Element.WATER] ?: 1.0) * it }
            return b
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
