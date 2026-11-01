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

    @Test
    fun mapTilePlacesGetRoles() {
        assertEquals(VenueRole.DUNGEON, Venues.roleForPoi("cafe", "cafe", "a"))
        assertEquals(VenueRole.DUNGEON, Venues.roleForPoi("doctors", "hospital", "a"))
        assertEquals(VenueRole.MARKET, Venues.roleForPoi("supermarket", "grocery", "a"))
        assertEquals(VenueRole.PHARMACY, Venues.roleForPoi("pharmacy", "pharmacy", "a"))
        assertEquals(VenueRole.HOSPITAL, Venues.roleForPoi("hospital", "hospital", "a"))
        assertEquals(VenueRole.GYM, Venues.roleForPoi("fitness_centre", "fitness", "a"))
        assertEquals(VenueRole.NIGHTLIFE, Venues.roleForPoi("bar", "bar", "a"))
        assertEquals(VenueRole.NATURE, Venues.roleForPoi(null, "park", "a"))
        assertEquals(null, Venues.roleForPoi("bank", "bank", "a"))
        // Most restaurants hold a dungeon, and the same one always does.
        val picks = (0 until 1000).map { Venues.roleForPoi("restaurant", "restaurant", "r$it") }
        val share = picks.count { it == VenueRole.DUNGEON }
        assertTrue(share in 600..800, "$share")
        assertEquals(picks[5], Venues.roleForPoi("restaurant", "restaurant", "r5"))
        assertEquals("Doctor's office", Venues.kindLabel("doctors"))
    }

    @Test
    fun mergeFillsInWithoutDuplicates() {
        val a = Venue("n1", "Cafe", "Coffee shop", 40.0, -74.0, VenueRole.DUNGEON)
        val sameSpot = Venue("m1", "Cafe", "Coffee shop", 40.0001, -74.0, VenueRole.DUNGEON)
        val other = Venue("m2", "Market", "Supermarket", 40.0001, -74.0, VenueRole.MARKET)
        val far = Venue("m3", "Cafe 2", "Coffee shop", 40.01, -74.0, VenueRole.DUNGEON)
        val merged = Venues.merge(listOf(a), listOf(sameSpot, other, far, far))
        assertEquals(listOf("n1", "m2", "m3"), merged.map { it.id })
    }
}
