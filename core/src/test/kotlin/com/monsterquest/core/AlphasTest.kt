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
    fun firstFormsGiveMoreCandy() {
        assertEquals(GameEngine.FIRST_FORM_CANDY, GameEngine.tameCandy(Bestiary.get("emberkit")))
        assertEquals(GameEngine.TAME_CANDY, GameEngine.tameCandy(Bestiary.get("blazecat")))
        assertEquals(GameEngine.TAME_CANDY, GameEngine.tameCandy(Bestiary.get("mimic")))
    }

    @Test
    fun alphasAreToughAndHarderToTame() {
        val s = GameEngine.chooseStarter(GameState(), "voltfawn", Random(1)).state.copy(inventory = mapOf(Item.BINDING_CRYSTAL to 5))
        val plain = Monster.create(Bestiary.get("emberkit"), 30, Random(4))
        val alpha = plain.copy(alpha = true)
        val bA = GameEngine.newBattle(s, alpha, EncounterType.WILD, rng = Random(2))
        val bP = GameEngine.newBattle(s, plain, EncounterType.WILD, rng = Random(2))
        assertTrue(bA.enemy.maxHp > bP.enemy.maxHp * 1.4, "${bA.enemy.maxHp} vs ${bP.enemy.maxHp}")
        bA.enemy.hp = bA.enemy.maxHp / 4
        bP.enemy.hp = bP.enemy.maxHp / 4
        assertTrue(bA.captureOdds(Item.BINDING_CRYSTAL) < bP.captureOdds(Item.BINDING_CRYSTAL))
    }
}
