package com.monsterquest.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LevelResetTest {
    private fun start() = GameEngine.chooseStarter(GameState(), "voltfawn", Random(1)).state

    @Test
    fun aHeroAboveFiftyGoesBackToTwentyFiveOnce() {
        val s0 = start()
        val m0 = s0.monsters.first()
        val level = 62
        // Every level's points spent, plus 2 from dungeon champions.
        val attrs = s0.hero.attributes.toMutableMap()
        attrs[HeroAttribute.entries.first()] = STARTING_ATTRIBUTE + (level - 1) * STAT_POINTS_PER_LEVEL + 2
        val old = s0.copy(
            version = 9,
            hero = s0.hero.copy(level = level, xp = 500, attributes = attrs, unspentPoints = 0),
            monsters = s0.monsters.map { it.copy(level = 60, xp = 10) },
        )
        val m = old.migrated()
        assertEquals(LEVEL_RESET_TO, m.hero.level)
        assertEquals(0, m.hero.xp)
        assertTrue(m.hero.attributes.values.all { it == STARTING_ATTRIBUTE })
        assertEquals((LEVEL_RESET_TO - 1) * STAT_POINTS_PER_LEVEL + 2, m.hero.unspentPoints)
        // Monsters come down to the new limit, the lost XP saved to pay back.
        val mon = m.monsters.first { it.uid == m0.uid }
        assertEquals(LEVEL_RESET_TO + XpRules.MONSTER_LEAD, mon.level)
        assertTrue(mon.heldXp > 0)
        // Gear is kept.
        assertEquals(old.gear, m.gear)
        // It only happens once.
        val again = m.copy(hero = m.hero.copy(level = 70)).migrated()
        assertEquals(70, again.hero.level)
    }

    @Test
    fun heroesAtFiftyOrBelowAreUntouched() {
        val s = start().let { it.copy(version = 9, hero = it.hero.copy(level = 50)) }
        val m = s.migrated()
        assertEquals(50, m.hero.level)
        assertEquals(s.hero.attributes, m.hero.attributes)
    }
}
