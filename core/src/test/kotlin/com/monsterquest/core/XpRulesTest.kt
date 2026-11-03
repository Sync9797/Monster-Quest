package com.monsterquest.core

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class XpRulesTest {
    private val utc = ZoneOffset.UTC
    private fun at(day: Int, hour: Int) = LocalDate.of(2027, 3, 1).plusDays(day.toLong()).atTime(hour, 0).toInstant(utc).toEpochMilli()
    private fun start() = GameEngine.chooseStarter(GameState(), "voltfawn", Random(1)).state

    @Test
    fun weakFoesGiveLittleXp() {
        assertEquals(1.0, XpRules.gapFactor(10, 10))
        assertEquals(1.0, XpRules.gapFactor(12, 10))
        assertTrue(XpRules.gapFactor(16, 10) in 0.4..0.6)
        assertEquals(XpRules.MIN_SHARE, XpRules.gapFactor(30, 10))
        // Fighting up is never penalised.
        assertEquals(1.0, XpRules.gapFactor(5, 20))
    }

    @Test
    fun monstersStopFiveLevelsAboveTheHeroAndCatchUpLater() {
        val s = start()
        val cap = XpRules.monsterCap(s)
        assertEquals(s.hero.level + XpRules.MONSTER_LEAD, cap)
        val m = s.partyMonsters.first()
        val (grown, notes) = GameEngine.grow(m, 1_000_000, cap)
        assertEquals(cap, grown.level)
        assertTrue(grown.heldXp > 0)
        assertTrue(notes.any { "saved" in it })
        // The hero levels: the held XP pays out up to the new limit.
        val held = s.copy(monsters = s.monsters.map { if (it.uid == m.uid) grown else it })
        val up = GameEngine.addHeroXp(held, heroXpForNext(held.hero.level)).state
        val after = up.monsters.first { it.uid == m.uid }
        assertEquals(cap + 1, after.level)
        assertTrue(after.heldXp in 1 until grown.heldXp)
    }

    @Test
    fun theGymTrainsThreeTimesADayForLessEachTime() {
        var s = start().copy(torches = 10)
        val m = s.partyMonsters.first().uid
        val gains = mutableListOf<Double>()
        repeat(3) {
            val share = Facilities.nextTrainingShare(s, at(0, 10), utc)!!
            gains += share
            s = Facilities.train(s, at(0, 10), utc).state
        }
        assertEquals(XpRules.TRAINING_SHARES, gains)
        assertNull(Facilities.nextTrainingShare(s, at(0, 11), utc))
        val torches = s.torches
        s = Facilities.train(s, at(0, 11), utc).state
        assertEquals(torches, s.torches, "a fourth session isn't charged")
        assertTrue(Facilities.usedToday(s, VenueRole.GYM, at(0, 12), utc))
        // A new day opens the gym again.
        assertEquals(0.30, Facilities.nextTrainingShare(s, at(1, 9), utc))
        assertTrue(s.monsters.first { it.uid == m }.level >= 1)
    }

    /** A heavy walker who spends every torch at the gym for 30 days never gets more than 5 levels ahead. */
    @Test
    fun aMonthOfGymTrainingStaysWithinTheLead() {
        var s = start()
        val uid = s.partyMonsters.first().uid
        var maxLead = 0
        for (d in 0 until 30) {
            s = s.copy(torches = Torches.cap(s))
            repeat(10) { s = Facilities.train(s, at(d, 10), utc).state }
            // A little hero XP from quests each day.
            s = GameEngine.addHeroXp(s, 40).state
            maxLead = maxOf(maxLead, s.monsters.first { it.uid == uid }.level - s.hero.level)
        }
        println("after 30 days: hero Lv ${s.hero.level}, monster Lv ${s.monsters.first { it.uid == uid }.level}, max lead $maxLead")
        assertTrue(maxLead <= XpRules.MONSTER_LEAD, "lead $maxLead")
    }
}
