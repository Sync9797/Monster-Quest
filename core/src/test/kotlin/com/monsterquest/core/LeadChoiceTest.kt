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

    @Test
    fun aFallenHeroIsReplacedFromTheBenchInsteadOfLosing() {
        // The hero is hanging on at 1 HP against a strong foe; the starter waits on the bench.
        val hurt = state.copy(heroDamage = 100_000)
        val brute = Monster.create(Bestiary.get("zapmouse"), 40, Random(5))
        val b = GameEngine.newBattle(hurt, brute, EncounterType.WILD, rng = Random(6))
        var guard = 0
        while (b.allies.first().alive && b.outcome == BattleOutcome.ONGOING && guard++ < 30) b.act(BattleAction.Attack)
        val hero = b.allies.first()
        assertTrue(hero.isHero && !hero.alive, "the hero fell")
        // Not a game over: the fight waits for someone to step in.
        assertEquals(BattleOutcome.ONGOING, b.outcome)
        assertTrue(b.mustSwap)
        assertEquals(hero.key, b.current?.key)
        // It can't be skipped.
        b.act(BattleAction.Skip)
        assertTrue(b.mustSwap)
        assertTrue(b.replaceFainted(starter.uid, hero.key))
        assertFalse(b.mustSwap)
        assertEquals(starter.uid, b.allies.first().key)
        assertTrue(b.allies.first().alive)
        assertTrue(b.log.any { "takes ${hero.name}'s place" in it })
    }

    @Test
    fun theFightIsLostOnlyWhenNobodyIsLeft() {
        val alone = state.copy(heroDamage = 100_000, monsters = state.monsters.map { it.copy(fainted = true) })
        val brute = Monster.create(Bestiary.get("zapmouse"), 40, Random(5))
        val b = GameEngine.newBattle(alone, brute, EncounterType.WILD, rng = Random(6))
        var guard = 0
        while (b.outcome == BattleOutcome.ONGOING && guard++ < 30) b.act(BattleAction.Attack)
        assertEquals(BattleOutcome.LOST, b.outcome)
    }
}
