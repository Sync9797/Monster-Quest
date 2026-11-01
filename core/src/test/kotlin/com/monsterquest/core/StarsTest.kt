package com.monsterquest.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StarsTest {
    @Test
    fun everyFiftyLevelsEarnsAStarAndTheShownLevelStartsOver() {
        assertEquals("Lv 1", Stars.label(1))
        assertEquals("Lv 50", Stars.label(50))
        assertEquals("★1 · Lv 1", Stars.label(51))
        assertEquals("★1 · Lv 50", Stars.label(100))
        assertEquals("★2 · Lv 1", Stars.label(101))
        assertEquals(0, Stars.of(50))
        assertEquals(1, Stars.of(51))
    }

    @Test
    fun reachingAStarOpensItsRewardBoxAndNothingDrops() {
        var s = GameEngine.chooseStarter(GameState(), "voltfawn", Random(1)).state
        s = s.copy(hero = s.hero.copy(level = 50, xp = 0))
        val gear = s.gear.size
        val armor = s.armor.size
        val orbs = s.count(Item.ULTRA_ORB)
        val gain = GameEngine.addHeroXp(s, heroXpForNext(50))
        // The real level keeps counting: 51, shown as star 1, level 1.
        assertEquals(51, gain.state.hero.level)
        assertEquals(1, Stars.of(gain.state.hero.level))
        assertEquals(1, gain.starBoxes.size)
        val box = gain.starBoxes.single()
        assertTrue(box.weapon.rarity >= GearRarity.EPIC && box.armor.rarity >= GearRarity.EPIC)
        assertEquals(gear + 1, gain.state.gear.size)
        assertEquals(armor + 1, gain.state.armor.size)
        assertTrue(gain.state.count(Item.ULTRA_ORB) >= orbs + 3)
        assertTrue("STAR 1" in GameEngine.levelUpText(gain))
        // The monster level limit still follows the real level.
        assertEquals(56, XpRules.monsterCap(gain.state))
        // Levelling within the star gives no new box.
        assertTrue(GameEngine.addHeroXp(gain.state, heroXpForNext(51)).starBoxes.isEmpty())
    }
}
