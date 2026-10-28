package com.monsterquest.core

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoamingShrinesTest {
    private val utc = ZoneOffset.UTC
    private fun at(day: LocalDate, hour: Int = 12) = day.atTime(hour, 0).toInstant(utc).toEpochMilli()
    private val tuesday = LocalDate.of(2026, 10, 27)
    private val saturday = LocalDate.of(2026, 10, 31)
    private val monday = LocalDate.of(2026, 10, 26)

    @Test
    fun risesNearYouOnItsDaysAndFollowsYou() {
        val s0 = GameEngine.chooseStarter(GameState(), "voltfawn", Random(1)).state
        assertEquals(s0, RoamingShrines.update(s0, 40.0, -74.0, at(monday), utc))
        var s = RoamingShrines.update(s0, 40.0, -74.0, at(tuesday), utc)
        val first = s.roaming!!
        assertEquals(ShrineTier.SILVER, first.shrine.tier)
        assertTrue(first.shrine.bossSpeciesId != null)
        val d = World.distanceMeters(40.0, -74.0, first.shrine.lat, first.shrine.lon)
        assertTrue(d in 50.0..RoamingShrines.RANGE_M, "$d")
        // Moving a little: it stays.
        assertEquals(s, RoamingShrines.update(s, 40.0005, -74.0, at(tuesday, 13), utc))
        // Walking out of range: a new one rises near you.
        s = RoamingShrines.update(s, 40.01, -74.0, at(tuesday, 14), utc)
        assertEquals(1, s.roaming!!.n)
        assertTrue(World.distanceMeters(40.01, -74.0, s.roaming!!.shrine.lat, s.roaming!!.shrine.lon) <= RoamingShrines.RANGE_M)
        // Once used, it stays put.
        s = s.copy(shrines = s.shrines + (s.roaming!!.shrine.id to ShrineRecord(lastVisitMs = at(tuesday, 14))))
        assertEquals(s, RoamingShrines.update(s, 40.05, -74.0, at(tuesday, 15), utc))
        // Weekends bring a Gold one; other days, none.
        assertEquals(ShrineTier.GOLD, RoamingShrines.update(s, 40.0, -74.0, at(saturday), utc).roaming!!.shrine.tier)
        assertEquals(null, RoamingShrines.update(s, 40.0, -74.0, at(monday.plusDays(7)), utc).roaming)
    }
}
