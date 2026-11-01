package com.monsterquest.core

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TorchesTest {
    private val utc = ZoneOffset.UTC
    private val day = LocalDate.of(2026, 11, 1)
    private fun at(h: Int) = day.atTime(h, 0).toInstant(utc).toEpochMilli()
    private fun start() = GameEngine.chooseStarter(GameState(), "voltfawn", Random(1)).state

    @Test
    fun walkingAndMorningsBringTorches() {
        var s = GameEngine.walk(start(), 2_500, at(9), utc).state
        assertEquals(2, s.torches)
        assertEquals(500, s.torchSteps)
        s = Torches.morning(s, at(7), utc)!!.state
        assertEquals(2 + Torches.MORNING, s.torches)
        assertEquals(null, Torches.morning(s, at(12), utc))
        assertEquals(Torches.MAX, Torches.add(s, 99).torches)
    }

    @Test
    fun sleepSetsTheDaysRest() {
        fun night(hours: Double) = start().copy(sleep = mapOf(day.toEpochDay() to SleepNight(at(0) - (hours * 3_600_000).toLong(), at(0))))
        val well = Rest.update(night(8.5), at(8), utc).state
        assertEquals(Rest.WELL_RESTED, Rest.today(well, at(9), utc))
        assertEquals(Rest.WELL_RESTED_TORCHES, well.torches)
        assertEquals(Rest.TIRED, Rest.today(Rest.update(night(6.0), at(8), utc).state, at(9), utc))
        assertEquals(Rest.NORMAL, Rest.today(Rest.update(night(7.5), at(8), utc).state, at(9), utc))
        // Only for that day.
        assertEquals(Rest.NORMAL, Rest.today(well, at(9) + 86_400_000L, utc))
        // Tired fighters are weaker in battle.
        val foe = Monster.create(Bestiary.get("zapmouse"), 5, Random(2))
        val tired = Rest.update(night(5.0), at(8), utc).state
        val bT = Battle(tired.hero, tired.partyMonsters, foe, EncounterType.WILD, emptyMap(), false, restFactor = Rest.TIRED.statFactor)
        val bN = Battle(tired.hero, tired.partyMonsters, foe, EncounterType.WILD, emptyMap(), false)
        assertTrue(bT.allies.first().attack < bN.allies.first().attack)
    }
}
