package com.monsterquest.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LiveWeatherTest {
    @Test
    fun weatherCodesMapToKinds() {
        assertEquals(LiveWeather.STORM, LiveWeather.from(95, 20.0, 10.0))
        assertEquals(LiveWeather.RAIN, LiveWeather.from(61, 12.0, 40.0))
        assertEquals(LiveWeather.RAIN, LiveWeather.from(81, 12.0, 5.0))
        assertEquals(LiveWeather.SNOW, LiveWeather.from(73, -2.0, 5.0))
        assertEquals(LiveWeather.FOG, LiveWeather.from(45, 8.0, 3.0))
        assertEquals(LiveWeather.WIND, LiveWeather.from(2, 15.0, 35.0))
        assertEquals(LiveWeather.HOT, LiveWeather.from(0, 31.0, 5.0))
        assertEquals(LiveWeather.CLEAR, LiveWeather.from(1, 18.0, 5.0))
        assertEquals(LiveWeather.CLOUDY, LiveWeather.from(3, 18.0, 5.0))
    }

    @Test
    fun rainBringsOutWaterAndStartsBattlesInRain() {
        fun share(boosts: Map<Element, Double>, el: Element): Double {
            val r = Random(4)
            val n = 4000
            return (1..n).count { Element.WATER in Bestiary.randomWild(r, 60, boosts = boosts).types.let { t -> if (el in t) t else emptyList() } }.toDouble() / n
        }
        val dry = share(emptyMap(), Element.WATER)
        val wet = share(LiveWeather.RAIN.boosts, Element.WATER)
        assertTrue(wet > dry * 1.4, "water share dry $dry vs rain $wet")
        assertEquals(listOf(Element.WATER, Element.ELECTRIC), LiveWeather.RAIN.more)
        assertEquals(listOf(Element.FIRE), LiveWeather.RAIN.fewer)

        val s = GameEngine.chooseStarter(GameState(), "emberkit", Random(1)).state
        val foe = Monster.create(Bestiary.get("tide_pup"), 5, Random(2))
        val b = GameEngine.newBattle(s, foe, EncounterType.WILD, rng = Random(3), weather = LiveWeather.RAIN)
        assertEquals(Weather.RAIN, b.weather)
        assertTrue(b.log.any { "raining" in it })
        assertEquals(Weather.DROUGHT, GameEngine.newBattle(s, foe, EncounterType.WILD, rng = Random(3), weather = LiveWeather.HOT).weather)
        assertEquals(Weather.CLEAR, GameEngine.newBattle(s, foe, EncounterType.WILD, rng = Random(3)).weather)
    }
}
