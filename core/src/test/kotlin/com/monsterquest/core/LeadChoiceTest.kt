package com.monsterquest.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LeadChoiceTest {
    private val base = GameEngine.chooseStarter(GameState(), "voltfawn", Random(1)).state
    /** The starter plus a second monster in the party. */
    private val state = base.let { s ->
        val pal = Monster.create(Bestiary.get("bubblefin"), 5, Random(7))
        s.copy(monsters = s.monsters + pal, party = s.party + pal.uid)
    }
    private val starter = state.partyMonsters.first()
    private val pal = state.partyMonsters[1]
    private fun foe() = Monster.create(Bestiary.get("zapmouse"), 3, Random(2))

    @Test
    fun aLoneFoeFacesTheHeroAndOneMonster() {
        assertTrue(GameEngine.isOneOnOne(emptyList(), null, fullTeam = false))
        assertFalse(GameEngine.isOneOnOne(listOf(foe()), null, fullTeam = false))
        assertEquals(listOf(starter.uid, pal.uid), GameEngine.leadOptions(state).map { it.uid })
        // By default the first monster in the party fights beside the hero.
        val b = GameEngine.newBattle(state, foe(), EncounterType.WILD, rng = Random(3))
        assertEquals(MIN_SIDE, b.slots)
        assertTrue(b.allies.first().isHero)
        assertEquals(listOf(Combatant.HERO_KEY, starter.uid), b.allies.map { it.key })
        assertTrue(b.swapsWaiting.any { it.key == pal.uid })
        // Or the one you pick.
        val picked = GameEngine.newBattle(state, foe(), EncounterType.WILD, rng = Random(3), lead = pal.uid)
        assertEquals(listOf(Combatant.HERO_KEY, pal.uid), picked.allies.map { it.key })
        assertTrue(picked.swapsWaiting.any { it.key == starter.uid })
    }

    @Test
    fun aFallenHeroIsReplacedFromTheBenchInsteadOfLosing() {
        // Hero and starter both hanging on; the second monster waits on the bench.
        val hurt = state.copy(heroDamage = 100_000, monsters = state.monsters.map { if (it.uid == starter.uid) it.copy(damage = 100_000) else it })
        val brute = Monster.create(Bestiary.get("zapmouse"), 40, Random(5))
        val b = GameEngine.newBattle(hurt, brute, EncounterType.WILD, rng = Random(6))
        var guard = 0
        while (!b.mustSwap && b.outcome == BattleOutcome.ONGOING && guard++ < 60) b.act(if (b.current?.alive == false) BattleAction.Skip else BattleAction.Attack)
        // Not a game over: the fight waits for someone to step in.
        assertEquals(BattleOutcome.ONGOING, b.outcome)
        assertTrue(b.mustSwap)
        val fallen = b.current!!
        assertFalse(fallen.alive)
        // It can't be skipped.
        b.act(BattleAction.Skip)
        assertTrue(b.mustSwap)
        assertTrue(b.replaceFainted(pal.uid, fallen.key))
        assertFalse(b.mustSwap && b.allies.any { it.key == pal.uid && it.alive })
        assertTrue(b.allies.any { it.key == pal.uid })
        assertTrue(b.log.any { "takes ${fallen.name}'s place" in it })
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

    @Test
    fun aegisShieldsHalfTheHerosHpAndGrowsWithRank() {
        val aegis = Spells.byId(Spells.AEGIS)!!
        assertEquals(50, Spells.ranked(aegis, 1, 1).power)
        assertEquals(90, Spells.ranked(aegis, Spells.MAX_RANK, 1).power)
        val s = state.copy(hero = state.hero.copy(knownSpells = state.hero.knownSpells + Spells.AEGIS))
        val b = GameEngine.newBattle(s, foe(), EncounterType.WILD, rng = Random(3))
        var g = 0
        while (b.current?.isHero != true && g++ < 20) b.act(BattleAction.Defend)
        val hero = b.current!!
        b.act(BattleAction.CastSpell(Spells.AEGIS, hero.key))
        val shielded = b.combatant(hero.key)!!
        assertTrue(shielded.shield >= hero.maxHp / 2 - 1, "shield ${shielded.shield} of ${hero.maxHp}")
    }
}
