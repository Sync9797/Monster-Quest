package com.monsterquest.core

import java.time.ZoneOffset
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VenuesTest {
    private val utc = ZoneOffset.UTC
    private val now = 1_800_000_000_000L
    private val day = 1_800_000_000_000L / 86_400_000L
    private fun start() = GameEngine.chooseStarter(GameState(), "voltfawn", Random(1)).state

    private val cafes = (0 until 40).map { Venue("node$it", "Cafe $it", "Coffee shop", 40.0 + it * 0.001, -74.0, VenueRole.DUNGEON) }
    private val groceries = (0 until 40).map { Venue("way$it", "Market $it", "Supermarket", 40.0 + it * 0.001, -74.01, VenueRole.MARKET) }

    @Test
    fun everyVenueHostsSomethingAndDensTakeSomeOver() {
        val s = start()
        val all = cafes + groceries
        val dungeons = Venues.dungeons(s, all, now, utc)
        val traders = Venues.traders(s, all, now, utc)
        val dens = Venues.dens(s, all, now, utc)
        // Every venue is exactly one thing today.
        assertEquals(all.size, dungeons.size + traders.size + dens.size)
        assertTrue(dens.size in 1..20, "${dens.size} dens")
        // Dungeons sit in the coffee shops (forges), at the very spot; dens take the venue's exact spot.
        val d = dungeons.first()
        val cafe = cafes.first { "dgv:${it.id}:" in d.id }
        assertEquals(cafe.lat to cafe.lon, d.lat to d.lon)
        assertEquals(DungeonTheme.FORGE, d.theme)
        assertEquals(cafe.name, d.place)
        val den = dens.first()
        assertTrue(all.any { it.lat == den.lat && it.lon == den.lon })
        // The same tomorrow? No: a different set of dens.
        val tomorrow = Venues.dens(s, all, now + 86_400_000L, utc).map { it.lat to it.lon }.toSet()
        assertTrue(tomorrow != dens.map { it.lat to it.lon }.toSet())
        // A cleared dungeon stays cleared for the day.
        val cleared = s.copy(dungeonsCleared = mapOf(d.id to day))
        assertTrue(Venues.dungeons(cleared, all, now, utc).none { it.id == d.id })
    }

    @Test
    fun thugsStandOnStreetsSpreadApart() {
        val street = (0 until 2000).map { 40.0 + it * 0.0005 to -74.0 }
        val camps = Venues.streetCamps(start(), street, now, utc)
        assertTrue(camps.isNotEmpty() && camps.size <= Venues.MAX_STREET_CAMPS)
        assertTrue(camps.all { c -> street.any { it.first == c.lat && it.second == c.lon } })
        for (a in camps) for (b in camps) if (a !== b) assertTrue(World.distanceMeters(a.lat, a.lon, b.lat, b.lon) >= Venues.CAMP_SPACING_M)
    }
}
