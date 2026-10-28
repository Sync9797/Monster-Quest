package com.monsterquest.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FocusRewardTest {
    private fun started() = GameEngine.chooseStarter(GameState(), "emberkit", Random(1)).state

    private fun finish(state: GameState, minutes: Int, seed: Int): Update {
        val s = GameEngine.startFocus(state, minutes, 0L, Random(seed)).state
        return GameEngine.resolveFocus(s, success = true, rng = Random(seed))
    }

    @Test
    fun twoHoursOffThePhoneIsTheJackpot() {
        // Always Epic or Legendary, at half the walk.
        repeat(40) { seed ->
            val box = finish(started(), 120, seed).state.lootBoxes.last()
            assertTrue(box.rarity == BoxRarity.EPIC || box.rarity == BoxRarity.LEGENDARY)
            assertTrue(box.halfWalk)
            assertEquals(box.rarity.meters / 2, box.meters, 0.01)
        }
        // A tome, party training and a full Energy refill.
        val tired = started().copy(heroEnergyUsed = 20)
        val before = tired.partyMonsters.first()
        val u = finish(tired, 120, 7)
        assertEquals(listOf(5), u.state.tomes)
        assertEquals(0, u.state.heroEnergyUsed)
        val after = u.state.monsters.first { it.uid == before.uid }
        assertTrue(after.level > before.level || after.xp > before.xp)
        assertTrue("Scholar's Tome" in u.message)
    }

    @Test
    fun shorterSessionsGetLess() {
        assertEquals(3, finish(started(), 90, 3).state.tomes.single())
        assertTrue(finish(started(), 60, 3).state.tomes.isEmpty())
        assertTrue(!finish(started(), 60, 3).state.lootBoxes.last().halfWalk)
        assertTrue(LootBoxes.odds(90).keys.none { it == BoxRarity.COMMON })
    }

    @Test
    fun aTomeGoesToTheSpellYouPick() {
        val s = started().copy(tomes = listOf(5))
        val spell = GameEngine.tomeChoices(s).first { s.hero.spellRank(it.id) == 0 }
        val u = GameEngine.readTome(s, 0, spell.id)
        assertTrue(u.state.tomes.isEmpty())
        // 5 pages: enough to learn it outright.
        assertTrue(spell.id in u.state.hero.knownSpells, u.message)
    }

    @Test
    fun moodCrystalsComeFromLongSessionsAndChangePersonality() {
        // Two hours: always one. Under an hour: never.
        assertEquals(1, finish(started(), 120, 5).state.count(Item.MOOD_CRYSTAL))
        assertTrue((0..30).none { finish(started(), 45, it).state.count(Item.MOOD_CRYSTAL) > 0 })
        assertTrue((0..40).any { finish(started(), 60, it).state.count(Item.MOOD_CRYSTAL) > 0 })
        assertEquals(0, Item.MOOD_CRYSTAL.price)
        val s = finish(started(), 120, 5).state
        val m = s.monsters.first()
        val u = GameEngine.rerollPersonality(s, m.uid, Random(9))
        val after = u.state.monsters.first { it.uid == m.uid }
        assertTrue(after.personality != m.personality)
        assertEquals(0, u.state.count(Item.MOOD_CRYSTAL))
        // Without one, nothing happens.
        assertEquals(after.personality, GameEngine.rerollPersonality(u.state, m.uid).state.monsters.first { it.uid == m.uid }.personality)
    }
}
