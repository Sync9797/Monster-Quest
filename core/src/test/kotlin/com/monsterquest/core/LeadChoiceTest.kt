package com.monsterquest.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LeadChoiceTest {
    private val state = GameEngine.chooseStarter(GameState(), "voltfawn", Random(1)).state
    private val starter = state.partyMonsters.first()
    private fun foe() = Monster.create(Bestiary.get("zapmouse"), 3, Random(2))

    @Test
    fun oneOnOneCanStartWithAMonster() {
        assertTrue(GameEngine.isOneOnOne(emptyList(), null, fullTeam = false))
        assertFalse(GameEngine.isOneOnOne(listOf(foe()), null, fullTeam = false))
        assertFalse(GameEngine.isOneOnOne(emptyList(), null, fullTeam = true))
        assertEquals(listOf(starter.uid), GameEngine.leadOptions(state).map { it.uid })

        val b = GameEngine.newBattle(state, foe(), EncounterType.WILD, rng = Random(3), lead = starter.uid)
        assertEquals(starter.uid, b.leader?.key)
        // The monster fights; the hero waits on the bench and can swap in.
        assertEquals(listOf(starter.uid), b.allies.map { it.key })
        assertTrue(b.swapsWaiting.any { it.isHero })
        assertTrue(b.log.any { "leads the fight" in it })
    }

    @Test
    fun theHeroStartsByDefaultAndInTeamFights() {
        val plain = GameEngine.newBattle(state, foe(), EncounterType.WILD, rng = Random(3))
        assertNull(plain.leader)
        assertTrue(plain.allies.first().isHero)
        // In a team fight everyone's out already: the lead is ignored.
        val team = GameEngine.newBattle(state, foe(), EncounterType.WILD, rng = Random(3), fullTeam = true, lead = starter.uid)
        assertNull(team.leader)
        assertTrue(team.allies.first().isHero)
        // A fainted monster can't lead.
        val hurt = state.copy(monsters = state.monsters.map { it.copy(fainted = true) })
        assertTrue(GameEngine.leadOptions(hurt).isEmpty())
    }
}
