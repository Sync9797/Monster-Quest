package com.monsterquest.core

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FacilitiesTest {
    private val utc = ZoneOffset.UTC
    private val day = LocalDate.of(2026, 11, 2)
    private fun at(h: Int) = day.atTime(h, 0).toInstant(utc).toEpochMilli()
    private fun start(): GameState {
        val s = GameEngine.chooseStarter(GameState(), "voltfawn", Random(1)).state
        val extra = Monster.create(Bestiary.get("emberkit"), 10, Random(2)).copy(fainted = true, damage = 999)
        return s.copy(monsters = s.monsters.map { it.copy(damage = 10) } + extra, heroDamage = 15, torches = 3)
    }

    @Test
    fun pharmacyHealsHospitalWakesGymTrains() {
        var s = Facilities.infirmary(start(), at(9), utc).state
        assertEquals(0, s.heroDamage)
        assertTrue(s.monsters.filter { !it.fainted }.all { it.damage == 0 })
        assertTrue(s.monsters.any { it.fainted })
        // Once a day.
        assertEquals(s, Facilities.infirmary(s.copy(heroDamage = 5), at(12), utc).state.copy(heroDamage = 0).let { s })
        assertTrue(Facilities.usedToday(s, VenueRole.PHARMACY, at(12), utc))
        assertTrue(!Facilities.usedToday(s, VenueRole.PHARMACY, at(12) + 86_400_000L, utc))
        s = Facilities.hospital(s, at(10), utc).state
        assertTrue(s.monsters.none { it.fainted })
        val before = s.monsters.first { it.uid in s.party }
        s = Facilities.train(s, at(10), utc).state
        assertEquals(2, s.torches)
        val after = s.monsters.first { it.uid == before.uid }
        assertTrue(after.xp > before.xp || after.level > before.level)
    }

    @Test
    fun parksBringNatureDensAndBarsDrawThugsAtNight() {
        val s = start()
        val parks = (0 until 60).map { Venue("park$it", "Park $it", "Park", 40.0 + it * 0.002, -74.0, VenueRole.NATURE) }
        val dens = Venues.dens(s, parks, at(9), utc)
        assertTrue(dens.isNotEmpty())
        val favored = setOf(Element.EARTH, Element.FLIGHT, Element.GAS)
        assertTrue(dens.count { d -> d.species.types.any { it in favored } } * 2 > dens.size, dens.map { it.species.typeLabel }.toString())
        // Parks never host dungeons or traders.
        assertTrue(Venues.dungeons(s, parks, at(9), utc).isEmpty() && Venues.traders(s, parks, at(9), utc).isEmpty())
        val bars = (0 until 20).map { Venue("bar$it", "Bar $it", "Bar", 41.0 + it * 0.003, -74.0, VenueRole.NIGHTLIFE) }
        val night = Venues.streetCamps(s, emptyList(), at(22), utc, bars)
        assertTrue(night.size in 4..16, "${night.size} at night")
        assertTrue(Venues.streetCamps(s, emptyList(), at(14), utc, bars).isEmpty())
        // By day the streets right by a bar stay clear.
        val street = (0 until 400).map { 41.0 + it * 0.0002 to -74.0 }
        val day = Venues.streetCamps(s, street, at(14), utc, bars)
        assertTrue(day.none { c -> bars.any { World.distanceMeters(it.lat, it.lon, c.lat, c.lon) < Venues.BAR_CLEAR_M } })
    }

    @Test
    fun schoolsTeachMagicOnceADay() {
        assertEquals(VenueRole.SCHOOL, Venues.roleForPoi("school", "school", "s"))
        assertEquals(VenueRole.SCHOOL, Venues.roleForPoi("university", "college", "u"))
        assertTrue(VenueRole.SCHOOL in Venues.FACILITIES)
        var s = start()
        val tomes = s.tomes.size
        s = Facilities.study(s, at(10), utc).state
        assertEquals(tomes + 1, s.tomes.size)
        assertEquals(Facilities.STUDY_PAGES, s.tomes.last())
        assertTrue(Facilities.usedToday(s, VenueRole.SCHOOL, at(15), utc))
        // Only once a day.
        assertEquals(s, Facilities.study(s, at(15), utc).state)
        assertEquals(tomes + 2, Facilities.study(s, at(10) + 86_400_000L, utc).state.tomes.size)
    }

    @Test
    fun academiesSpreadOverCampusesAndStayOffSchoolGrounds() {
        val uni = Venue("u1", "State University", "University", 40.0, -74.0, VenueRole.SCHOOL)
        val college = Venue("c1", "City College", "College", 40.01, -74.0, VenueRole.SCHOOL)
        val school = Venue("s1", "Lincoln High", "School", 40.02, -74.0, VenueRole.SCHOOL)
        // A street running past the school: points every 20 m.
        val street = (-30..30).map { 40.02 + it * 20 / 111_320.0 to -74.0 + 0.0015 }
        val spots = Venues.facilities(listOf(uni, college, school), street)
        assertEquals(3, spots.count { it.id.startsWith("u1#") })
        assertEquals(2, spots.count { it.id.startsWith("c1#") })
        val study = spots.single { it.id.startsWith("s1#") }
        val d = World.distanceMeters(school.lat, school.lon, study.lat, study.lon)
        assertTrue(d >= Venues.OFF_CAMPUS_MIN_M && d <= Venues.OFF_CAMPUS_MAX_M, "$d m")
        assertTrue(street.any { it.first == study.lat && it.second == study.lon })
        assertEquals("Across from Lincoln High", study.name)
        // Campus spots are apart from each other, and the same every time.
        val campus = spots.filter { it.id.startsWith("u1#") }
        for (a in campus) for (b in campus) if (a !== b) assertTrue(World.distanceMeters(a.lat, a.lon, b.lat, b.lon) > 50)
        assertEquals(spots, Venues.facilities(listOf(uni, college, school), street))
        // No street nearby: a school gets no academy rather than one on its grounds.
        assertTrue(Venues.facilities(listOf(school), emptyList()).isEmpty())
    }
}
