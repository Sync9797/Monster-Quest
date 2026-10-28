package com.monsterquest.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TraitPoolsTest {
    private fun flies(sp: Species) = Element.FLIGHT in sp.types

    @Test
    fun fliersUsuallyLevitateButNotAlways() {
        val fliers = Bestiary.all.filter { flies(it) }
        assertTrue(fliers.isNotEmpty())
        for (sp in fliers) {
            val pool = MonsterTraits.pool(sp)
            val share = pool.filter { it.first == Trait.LEVITATE }.sumOf { it.second }.toDouble() / pool.sumOf { it.second }
            assertTrue(share >= 0.5, "${sp.family} levitates ${share}")
            assertTrue(pool.size > 1, "${sp.family} can roll something else")
        }
        // Rolled across many monsters: mostly Levitate, sometimes not.
        val rolls = (1..400).map { MonsterTraits.roll(Bestiary.get("fluffchick"), Random(it)) }
        val lev = rolls.count { it == Trait.LEVITATE }
        assertTrue(lev in 180..300, "levitate $lev of 400")
    }

    @Test
    fun newMonstersKeepTheirRolledSkillOlderOnesKeepTheLineSkill() {
        val m = Monster.create(Bestiary.get("emberkit"), 40, Random(3))
        val skill = m.skill
        assertTrue(skill != null && MonsterTraits.pool(m.species).any { it.first == skill })
        assertEquals(skill, m.trait?.first)
        // Evolving keeps it.
        assertEquals(skill, m.copy(speciesId = "sun_manticore").trait?.first)
        // A save from before rolled skills: the line's old fixed skill.
        val old = m.copy(skill = null)
        assertNull(old.skill)
        assertEquals(MonsterTraits.of(m.species), old.trait?.first)
    }

    @Test
    fun newTraitsWorkInBattle() {
        val s = GameEngine.chooseStarter(GameState(), "emberkit", Random(1)).state
        fun duel(me: Monster, foe: Monster): Pair<Battle, Combatant> {
            val st = s.copy(monsters = listOf(me), party = listOf(me.uid))
            val b = GameEngine.newBattle(st, foe, EncounterType.WILD, rng = Random(5), fullTeam = true)
            var g = 0
            while (b.current?.key != me.uid && g++ < 30 && b.outcome == BattleOutcome.ONGOING) b.act(BattleAction.Defend)
            return b to b.allies.first { it.key == me.uid }
        }
        // Lightning Eater: an Electric attack heals it instead.
        val eater = Monster.create(Bestiary.get("tempest_roc"), 40, Random(2)).copy(skill = Trait.LIGHTNING_EATER)
        val zapper = Monster.create(Bestiary.get("thunderhog"), 40, Random(3)).copy(moves = listOf("thunder_shock"), skill = Trait.STATIC)
        val (b1, me1) = duel(zapper, eater)
        b1.enemy.hp = b1.enemy.maxHp / 2
        val hp = b1.enemy.hp
        b1.act(BattleAction.UseMove("thunder_shock"))
        assertTrue(b1.log.any { "Lightning Eater absorbs" in it } || b1.log.any { "miss" in it }, b1.log.takeLast(5).toString())
        assertTrue(b1.enemy.hp >= hp)
        assertTrue(me1.alive)
        // Iron Skull at rank 5 (level 70): no recoil.
        val ram = Monster.create(Bestiary.get("cinderlamb"), 70, Random(4)).copy(moves = listOf("flare_blitz"), skill = Trait.IRON_SKULL)
        val (b2, me2) = duel(ram, Monster.create(Bestiary.get("goopling"), 70, Random(5)))
        val mine = me2.hp
        b2.act(BattleAction.UseMove("flare_blitz"))
        assertEquals(mine, me2.hp, b2.log.takeLast(5).toString())
        // Stalwart at full HP: survives a knockout blow once.
        val wall = Monster.create(Bestiary.get("rollpip"), 30, Random(6)).copy(skill = Trait.STALWART)
        val brute = Monster.create(Bestiary.get("emberkit"), 90, Random(7)).copy(moves = listOf("flare_blitz"))
        val (b3, hitter) = duel(brute, wall)
        hitter.powerUp = 2_000
        b3.enemy.hp = b3.enemy.maxHp
        b3.act(BattleAction.UseMove("flare_blitz"))
        assertTrue(b3.log.any { "Stalwart" in it } || b3.log.any { "miss" in it }, b3.log.takeLast(6).toString())
        if (b3.log.any { "Stalwart" in it }) assertTrue(b3.enemies.first().hp >= 1, "held on")
    }
}
