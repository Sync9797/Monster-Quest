package com.monsterquest.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SnapBindTest {
    private fun battle(seed: Int): Battle {
        var s = GameEngine.chooseStarter(GameState(), "voltfawn", Random(1)).state
        s = s.copy(inventory = mapOf(Item.TAMING_ORB to 5, Item.GREAT_ORB to 2))
        val foes = (1..3).map { Monster.create(Bestiary.get("zapmouse"), 3, Random(it + seed * 10)) }
        return GameEngine.newBattle(s, foes[0], EncounterType.WILD, rng = Random(seed), pack = foes.drop(1))
    }

    @Test
    fun onlyOnTheFirstTurn() {
        val b = battle(1)
        assertTrue(b.snapAvailable)
        // Above half HP a normal cast isn't allowed, but Snap Bind is.
        assertTrue(b.enemy.hpFraction > 0.5)
        val odds = b.snapOdds(Item.TAMING_ORB)
        assertTrue(odds in 0.3..SNAP_BIND_MAX, "$odds")
        assertTrue(b.snapOdds(Item.GREAT_ORB) > odds)
        b.act(BattleAction.Defend)
        assertTrue(!b.snapAvailable)
        assertTrue(b.outcome != BattleOutcome.ONGOING || b.current == null || !b.snapAvailable)
    }

    @Test
    fun successScattersTheRestFailureDoublesDown() {
        var won = false
        var lost = false
        for (seed in 1..60) {
            val b = battle(seed)
            if (!b.snapAvailable) continue
            val before = b.captureOdds(Item.TAMING_ORB)
            b.act(BattleAction.SnapBind(Item.TAMING_ORB))
            if (b.scattered) {
                won = true
                assertEquals(BattleOutcome.CAPTURED, b.outcome)
                assertEquals(1, b.enemies.count { it.gone == "Tamed" })
                assertEquals(2, b.enemies.count { it.gone == "Scattered" })
                val r = b.result()
                assertEquals(1, r.tamed.size)
                assertTrue(r.scattered)
            } else {
                lost = true
                assertTrue(b.snapFailed)
                assertTrue(b.enemies.filter { it.alive }.all { (it.stages[BattleStat.ATTACK] ?: 0) >= 1 })
                assertTrue(b.captureOdds(Item.TAMING_ORB) <= before * SNAP_FAIL_CATCH + 1e-9)
                assertTrue(!b.snapAvailable)
            }
            if (won && lost) break
        }
        assertTrue(won && lost, "won=$won lost=$lost")
    }
}
