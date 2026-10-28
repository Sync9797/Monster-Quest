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
        assertEquals(World.NEAR_WATER_FACTOR, SpawnConditions(12, waterDistanceM = 100.0).boosts[Element.WATER])
        assertEquals(World.FAR_FROM_WATER_FACTOR, SpawnConditions(12, waterDistanceM = 2000.0).boosts[Element.WATER])
        assertTrue(SpawnConditions(12, waterDistanceM = 500.0).boosts.isEmpty())
        // Rain and a river stack.
        assertEquals(2.0 * World.NEAR_WATER_FACTOR, SpawnConditions(12, weather = LiveWeather.RAIN, waterDistanceM = 50.0).boosts[Element.WATER]!!, 1e-9)
    }
}
