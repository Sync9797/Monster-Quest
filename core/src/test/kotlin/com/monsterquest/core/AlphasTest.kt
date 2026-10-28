package com.monsterquest.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AlphasTest {
    @Test
    fun firstFormsFillTheWildAtEveryLevel() {
        for (level in listOf(10, 30, 60)) {
            val picks = (1..4000).map { Bestiary.randomWild(Random(it), level) }
            val evolved = picks.count { it.stage > 1 }.toDouble() / picks.size
            assertTrue(evolved <= 0.25, "level $level: ${"%.2f".format(evolved)} evolved")
        }
        // Dens still bring strong monsters.
        val den = (1..4000).map { Bestiary.randomWild(Random(it), 60, favorFirstForms = false) }
        assertTrue(den.count { it.stage > 1 }.toDouble() / den.size > 0.3)
    }

    @Test
    fun alphasAreToughFirstFormsWithPerfectGenes() {
        val sp = Bestiary.get("emberkit")
        val spawns = (1..3000).map { WildSpawn("s:1:$it:0", sp.id, 30, 0.0, 0.0, 0L) }
        val alphas = spawns.filter { it.alpha }
        assertTrue(alphas.size in 150..330, "${alphas.size} alphas")
        // Never below their level, never evolved forms.
        assertTrue(spawns.map { it.copy(level = 10) }.none { it.alpha })
        assertTrue(spawns.map { it.copy(speciesId = "blazecat") }.none { it.alpha })
        val m = World.spawnMonster(alphas.first())
        assertTrue(m.alpha)
        assertEquals("emberkit", m.speciesId)
        assertTrue(MONSTER_STATS.count { m.ivs[it] == MAX_IV } >= Alphas.PERFECT_STATS)
        // In battle: half again as tough, and a bit harder to tame.
        val s = GameEngine.chooseStarter(GameState(), "voltfawn", Random(1)).state.copy(inventory = mapOf(Item.TAMING_ORB to 5))
        val plain = World.spawnMonster(spawns.first { !it.alpha })
        val bA = GameEngine.newBattle(s, m.copy(ivs = plain.ivs), EncounterType.WILD, rng = Random(2))
        val bP = GameEngine.newBattle(s, plain.copy(level = m.level), EncounterType.WILD, rng = Random(2))
        assertTrue(bA.enemy.maxHp > bP.enemy.maxHp * 1.4, "${bA.enemy.maxHp} vs ${bP.enemy.maxHp}")
        bA.enemy.hp = bA.enemy.maxHp / 4
        bP.enemy.hp = bP.enemy.maxHp / 4
        assertTrue(bA.captureOdds(Item.TAMING_ORB) < bP.captureOdds(Item.TAMING_ORB))
    }

    @Test
    fun firstFormsGiveMoreCandy() {
        assertEquals(GameEngine.FIRST_FORM_CANDY, GameEngine.tameCandy(Bestiary.get("emberkit")))
        assertEquals(GameEngine.TAME_CANDY, GameEngine.tameCandy(Bestiary.get("blazecat")))
        assertEquals(GameEngine.TAME_CANDY, GameEngine.tameCandy(Bestiary.get("mimic")))
    }
}
