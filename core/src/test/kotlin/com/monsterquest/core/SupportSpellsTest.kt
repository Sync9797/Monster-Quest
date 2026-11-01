package com.monsterquest.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SupportSpellsTest {
    private fun battle(): Battle {
        var s = GameEngine.chooseStarter(GameState(), "voltfawn", Random(1)).state
        s = s.copy(hero = s.hero.copy(knownSpells = s.hero.knownSpells + listOf("haste", "empower", "cleanse", "healing_circle")))
        val foe = Monster.create(Bestiary.get("zapmouse"), 3, Random(2))
        val b = GameEngine.newBattle(s, foe, EncounterType.WILD, rng = Random(3), fullTeam = true)
        var g = 0
        while (b.current?.isHero != true && g++ < 20) b.act(BattleAction.Defend)
        return b
    }

    @Test
    fun newSupportSpellsWork() {
        assertTrue(listOf("haste", "empower", "cleanse", "healing_circle").all { Spells.byId(it)!!.kind in SUPPORT_KINDS })
        // Haste and Empower raise stats.
        var b = battle()
        val ally = b.allies.first { !it.isHero }
        b.act(BattleAction.CastSpell("haste", ally.key))
        assertTrue((ally.stages[BattleStat.SPEED] ?: 0) >= 1)
        b = battle()
        val ally2 = b.allies.first { !it.isHero }
        b.act(BattleAction.CastSpell("empower", ally2.key))
        assertTrue((ally2.stages[BattleStat.ATTACK] ?: 0) >= 1)
        // Cleanse cures a status and lowered stats.
        b = battle()
        val sick = b.allies.first { !it.isHero }
        sick.status = Status.POISON; sick.statusTurns = 3; sick.stages[BattleStat.DEFENSE] = -2
        b.act(BattleAction.CastSpell("cleanse", sick.key))
        assertEquals(null, sick.status)
        assertEquals(0, sick.stages[BattleStat.DEFENSE])
        // Healing Circle heals everyone.
        b = battle()
        b.allies.forEach { it.hp = it.maxHp / 2 }
        val before = b.allies.map { it.hp }
        b.act(BattleAction.CastSpell("healing_circle", b.allies.first().key))
        assertTrue(b.allies.zip(before).all { (a, h) -> a.hp > h })
    }
}
