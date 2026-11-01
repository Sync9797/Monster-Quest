package com.monsterquest.app

import com.monsterquest.app.data.PlacesRepository
import com.monsterquest.core.TILE_VERSION
import com.monsterquest.core.VenueRole
import com.monsterquest.core.World
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The tile parser: places become shrines, dungeons or traders in their own tile; water and streets become points. */
class PlacesTilesTest {
    private val body = """
        {"elements":[
          {"type":"node","id":1,"lat":40.7581,"lon":-73.9852,"tags":{"amenity":"cafe","name":"Cafe One"}},
          {"type":"node","id":2,"lat":40.7590,"lon":-73.9840,"tags":{"tourism":"museum","name":"Big Museum"}},
          {"type":"node","id":3,"lat":40.7700,"lon":-73.9700,"tags":{"amenity":"cafe","name":"Elsewhere"}},
          {"type":"node","id":4,"lat":40.7570,"lon":-73.9860,"tags":{"shop":"supermarket","name":"Green Grocer"}},
          {"type":"node","id":5,"lat":40.7576,"lon":-73.9830,"tags":{"amenity":"doctors","name":"Dr. Ames"}},
          {"type":"way","id":9,"tags":{"waterway":"river"},"geometry":[{"lat":40.7551,"lon":-73.9890},{"lat":40.7552,"lon":-73.9880},{"lat":40.7560,"lon":-73.9860}]},
          {"type":"way","id":10,"tags":{"highway":"residential"},"geometry":[{"lat":40.7555,"lon":-73.9895},{"lat":40.7575,"lon":-73.9895},{"lat":40.7595,"lon":-73.9895}]}
        ]}
    """.trimIndent()

    @Test fun placesAndWaterLandInTheirTile() {
        val key = World.tileKey(40.7581, -73.9852)
        val tiles = PlacesRepository.parse(body, setOf(key), 1_000L)
        val tile = tiles.getValue(key)
        // The cafe and the doctor's office hold dungeons; the grocery store keeps a shrine and hosts a trader.
        assertEquals(setOf("osm:node2", "osm:node4"), tile.shrines.map { it.id }.toSet())
        assertEquals(mapOf("node1" to VenueRole.DUNGEON, "node4" to VenueRole.MARKET, "node5" to VenueRole.DUNGEON), tile.venues.associate { it.id to it.role })
        assertEquals("Coffee shop", tile.venues.first { it.id == "node1" }.kind)
        assertTrue(tile.water.isNotEmpty())
        assertTrue(tile.streets.size >= 2)
        assertEquals(TILE_VERSION, tile.v)
        assertEquals(1_000L, tile.fetchedAt)
        // Parsing again gives the same shrines.
        assertEquals(tile.shrines, PlacesRepository.parse(body, setOf(key), 2_000L).getValue(key).shrines)
    }
}
