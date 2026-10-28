package com.monsterquest.app

import com.monsterquest.app.data.PlacesRepository
import com.monsterquest.core.World
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The tile parser: places become shrines in their own tile, water becomes points. */
class PlacesTilesTest {
    private val body = """
        {"elements":[
          {"type":"node","id":1,"lat":40.7581,"lon":-73.9852,"tags":{"amenity":"cafe","name":"Cafe One"}},
          {"type":"node","id":2,"lat":40.7590,"lon":-73.9840,"tags":{"tourism":"museum","name":"Big Museum"}},
          {"type":"node","id":3,"lat":40.7700,"lon":-73.9700,"tags":{"amenity":"cafe","name":"Elsewhere"}},
          {"type":"way","id":9,"tags":{"waterway":"river"},"geometry":[{"lat":40.7551,"lon":-73.9890},{"lat":40.7552,"lon":-73.9880},{"lat":40.7560,"lon":-73.9860}]}
        ]}
    """.trimIndent()

    @Test fun placesAndWaterLandInTheirTile() {
        val key = World.tileKey(40.7581, -73.9852)
        val tiles = PlacesRepository.parse(body, setOf(key), 1_000L)
        val tile = tiles.getValue(key)
        assertEquals(setOf("osm:node1", "osm:node2"), tile.shrines.map { it.id }.toSet())
        assertTrue(tile.water.isNotEmpty())
        assertEquals(1_000L, tile.fetchedAt)
        // Parsing again gives the same shrines.
        assertEquals(tile.shrines, PlacesRepository.parse(body, setOf(key), 2_000L).getValue(key).shrines)
    }
}
