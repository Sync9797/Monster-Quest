package com.monsterquest.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuddyTest {
    private fun start(): GameState = GameEngine.chooseStarter(GameState(), "voltfawn", Random(1)).state

    @Test
    fun theLeadMonsterEarnsCandyByDistance() {
        var s = start()
        val buddy = Buddy.of(s)!!
        val before = GameEngine.candyOf(s, buddy.species)
        val miles = Buddy.milesPerCandy(buddy.species)
        // Just short of one candy: none yet.
        s = GameEngine.walk(s, (STEPS_PER_MILE * miles).toInt() - 10).state
        assertEquals(before, GameEngine.candyOf(s, buddy.species))
        assertTrue(Buddy.progress(s) > 0)
        s = GameEngine.walk(s, 20).state
        assertEquals(before + 1, GameEngine.candyOf(s, buddy.species))
        assertTrue(Buddy.progress(s) < 100)
    }

    @Test
    fun strongerMonstersWalkFurther() {
        assertEquals(1.0, Buddy.milesPerCandy(Bestiary.get("emberkit")))
        assertTrue(Buddy.milesPerCandy(Bestiary.get("flarepanther")) > Buddy.milesPerCandy(Bestiary.get("emberkit")))
        assertEquals(10.0, Buddy.milesPerCandy(Bestiary.get("thunder_titan")))
    }

    @Test
    fun changingTheLeadResetsProgress() {
        var s = start()
        s = GameEngine.walk(s, STEPS_PER_MILE / 2).state
        assertTrue(Buddy.progress(s) > 0)
        val other = Monster.create(Bestiary.get("emberkit"), 5, Random(3))
        s = s.copy(monsters = s.monsters + other, party = listOf(other.uid) + s.party)
        assertEquals(0.0, Buddy.progress(s))
        s = Buddy.sync(s)
        assertEquals(other.uid, s.buddyUid)
        assertEquals(0.0, s.buddyMeters)
        // Moving the old buddy back in front doesn't bring its progress back.
        s = Buddy.sync(s.copy(party = s.party.reversed()))
        assertEquals(0.0, Buddy.progress(s))
    }
}
