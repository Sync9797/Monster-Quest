package com.monsterquest.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeroInBattleTest {
    @Test
    fun gearAddsToTheHerosBattleNumbers() {
        val s = GameEngine.chooseStarter(GameState(), "voltfawn", Random(1)).state
        // Bare-handed: the battle numbers are the hero's own.
        val bare = GameEngine.heroInBattle(s)
        assertEquals(s.hero.attack, bare.attack)
        assertEquals(s.hero.defense, bare.defense)
        // A weapon raises Attack, armor raises Defense, as they would in a fight.
        val sword = Armory.roll(10, Random(2), WeaponType.SWORD)
        val plate = Armory.rollArmor(10, Random(3))
        val geared = s.copy(equippedGear = sword, equippedArmor = plate)
        val fight = GameEngine.heroInBattle(geared)
        assertTrue(fight.attack > s.hero.attack, "${fight.attack} vs ${s.hero.attack}")
        assertTrue(fight.defense > s.hero.defense, "${fight.defense} vs ${s.hero.defense}")
        // The same numbers a battle starts with.
        val b = GameEngine.newBattle(geared, Monster.create(Bestiary.get("zapmouse"), 3, Random(4)), EncounterType.WILD, rng = Random(5))
        val hero = b.allies.first { it.isHero }
        val rest = Rest.today(geared, System.currentTimeMillis()).statFactor
        if (rest == 1.0) assertEquals(fight.attack, hero.attack)
    }
}
