package com.monsterquest.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpellMasteryTest {
    private fun hero(level: Int, vararg spells: String, rank: Int = 1) =
        GameEngine.chooseStarter(GameState(), "voltfawn", Random(1)).state.let { s ->
            s.copy(
                gold = 1_000_000,
                hero = s.hero.copy(level = level, knownSpells = s.hero.knownSpells + spells, spellRanks = spells.associateWith { rank }),
                spellPages = spells.associateWith { 500 },
            )
        }

    @Test
    fun ranksNeedLevels() {
        val low = hero(9, "spark_bolt")
        assertEquals(1, GameEngine.upgradeSpell(low, "spark_bolt").state.hero.spellRank("spark_bolt"))
        assertTrue("level 10" in GameEngine.upgradeSpell(low, "spark_bolt").message)
        val ten = hero(10, "spark_bolt")
        assertEquals(2, GameEngine.upgradeSpell(ten, "spark_bolt").state.hero.spellRank("spark_bolt"))
        assertEquals(40, SpellMastery.levelForRank(5))
    }

    @Test
    fun everySpellHasTwoPaths() {
        Spells.all.forEach { sp -> assertEquals(2, SpellMastery.paths(sp).size, sp.name) }
    }

    @Test
    fun choosePathThenMasterIt() {
        var s = hero(44, "spark_bolt", rank = 5)
        assertTrue(SpellMastery.choose(hero(44, "spark_bolt", rank = 4), "spark_bolt", 1).message.contains("Rank"))
        s = SpellMastery.choose(s, "spark_bolt", 1).state
        assertEquals(1, s.hero.spellPaths["spark_bolt"])
        // Mastery 1 opens at level 45.
        assertEquals(s, SpellMastery.master(s, "spark_bolt").state)
        s = s.copy(hero = s.hero.copy(level = 65))
        repeat(7) { s = SpellMastery.master(s, "spark_bolt").state }
        assertEquals(SpellMastery.MAX, s.hero.spellMastery["spark_bolt"])
        // Switching keeps mastery, for gold.
        val gold = s.gold
        s = SpellMastery.choose(s, "spark_bolt", 2).state
        assertEquals(2, s.hero.spellPaths["spark_bolt"])
        assertEquals(SpellMastery.MAX, s.hero.spellMastery["spark_bolt"])
        assertEquals(gold - SpellMastery.SWITCH_COST, s.gold)
    }

    private fun heroTurn(b: Battle) {
        var g = 0
        while (b.current?.isHero != true && b.outcome == BattleOutcome.ONGOING && g++ < 30) b.step(BattleAction.Defend)
    }

    @Test
    fun stormHitsEveryFoeAndSharedAegisShieldsTheTeam() {
        var s = hero(50, "spark_bolt", Spells.AEGIS, rank = 5)
        s = SpellMastery.choose(s, "spark_bolt", 1).state
        s = SpellMastery.choose(s, Spells.AEGIS, 2).state
        val r = Random(7)
        val pack = List(2) { Monster.create(Bestiary.get("bubblefin"), 20, r) }
        val b = GameEngine.newBattle(s, Monster.create(Bestiary.get("bubblefin"), 20, r), EncounterType.WILD, rng = Random(8), pack = pack)
        heroTurn(b)
        val before = b.enemies.map { it.hp }
        b.act(BattleAction.CastSpell("spark_bolt", null))
        assertTrue(b.log.any { "strikes every foe" in it })
        assertTrue(b.enemies.zip(before).count { (e, hp) -> e.hp < hp } >= 2, b.log.takeLast(8).joinToString("\n"))
        heroTurn(b)
        if (b.outcome == BattleOutcome.ONGOING) {
            b.act(BattleAction.CastSpell(Spells.AEGIS, Combatant.HERO_KEY))
            assertTrue(b.log.any { "whole team is shielded" in it })
        }
    }

    @Test
    fun championStatPointsAreCappedPerStar() {
        assertEquals(10, Dungeons.championCap(40))
        assertEquals(20, Dungeons.championCap(51))
    }
}
