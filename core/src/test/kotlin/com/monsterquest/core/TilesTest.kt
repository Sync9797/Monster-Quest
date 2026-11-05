package com.monsterquest.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TilesTest {
    @Test
    fun tilesAreFixedAndSelectionIsStable() {
        assertEquals("4075:-7399", World.tileKey(40.7580, -73.9855))
        val b = World.tileBounds("4075:-7399")
        assertTrue(40.7580 in b[0]..b[2] && -73.9855 in b[1]..b[3])
        assertTrue(World.tileKey(40.7580, -73.9855) in World.tilesAround(40.7580, -73.9855, 1500.0))
        // The same places in any order give the same shrines.
        val places = (0 until 60).map { i ->
            World.shrineFromPlace("node$i", "Place $i", 40.75 + (i % 10) * 0.0008, -73.99 + (i / 10) * 0.0012, if (i % 15 == 0) ShrineTier.GOLD else ShrineTier.ITEM)
        }
        val a = World.selectForTile(places).map { it.id }
        assertEquals(a, World.selectForTile(places.shuffled(kotlin.random.Random(3))).map { it.id })
        assertTrue(a.size <= World.TILE_CAPS.values.sum())
    }

    @Test
    fun waterNearbyBringsOutWaterTypes() {
        assertEquals(World.NEAR_WATER_FACTOR, WeatherEvent.encounterBoosts(null, 100.0)[Element.WATER])
        assertEquals(World.FAR_FROM_WATER_FACTOR, WeatherEvent.encounterBoosts(null, 2000.0)[Element.WATER])
        assertTrue(WeatherEvent.encounterBoosts(null, 500.0).isEmpty())
        // Rain and a river stack.
        assertEquals(2.0 * World.NEAR_WATER_FACTOR, WeatherEvent.encounterBoosts(LiveWeather.RAIN, 50.0)[Element.WATER]!!, 1e-9)
    }

    @Test
    fun shrinesYouKeepVisitingBond() {
        var s = GameEngine.chooseStarter(GameState(), "emberkit", kotlin.random.Random(1)).state.copy(bagCapacity = 999)
        val sh = World.shrineFromPlace("node7", "Corner Cafe", 40.0, -73.0, ShrineTier.ITEM)
        var now = 0L
        repeat(15) {
            s = GameEngine.visitShrine(s, sh, now, kotlin.random.Random(it)).state
            now += 31 * 60_000L
        }
        val rec = s.shrines.getValue(sh.id)
        assertEquals(15, rec.visits)
        assertEquals(ShrineBond.TRUSTED, rec.bond)
        // Trusted: recharges in 20 minutes instead of 30.
        assertEquals(20 * 60_000L, GameEngine.shrineCooldownLeft(s, sh, now - 31 * 60_000L))
    }
}
