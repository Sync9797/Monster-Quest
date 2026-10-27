package com.monsterquest.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GearTiersTest {
    private fun hero(level: Int) = GameState(
        hero = Hero(level = level),
        monsters = listOf(Monster.create(Bestiary.get("emberkit"), level, Random(1))),
        starterChosen = true,
    ).let { it.copy(party = it.monsters.map { m -> m.uid }) }

    private fun shares(level: Int, n: Int = 20_000): Map<Material, Double> {
        val r = Random(level)
        return (1..n).map { Armory.rollMaterial(level, r) }.groupingBy { it }.eachCount().mapValues { it.value.toDouble() / n }
    }

    @Test
    fun materialsScaleBonusesAndGetLikelierWithLevelButStayCapped() {
        val wood = Gear("w", WeaponType.SWORD, GearRarity.NORMAL, 20)
        val bonuses = Material.entries.map { wood.copy(material = it).attackBonus(HeroClass.WARRIOR) }
        assertEquals(bonuses.sorted(), bonuses)
        assertEquals(bonuses.size, bonuses.toSet().size)
        assertEquals("Diamond Plate", Armor("a", ArmorType.PLATE, GearRarity.NORMAL, 5, material = Material.DIAMOND).name)
        assertEquals("Wooden Sword", wood.name)

        // Level 10: mostly wooden/leather, some bronze, diamond possible but very rare.
        val low = shares(10)
        assertTrue((low[Material.BASIC] ?: 0.0) > 0.6)
        assertTrue((low[Material.BRONZE] ?: 0.0) in 0.1..0.3)
        assertTrue((low[Material.DIAMOND] ?: 0.0) < 0.005)
        assertTrue(Material.DIAMOND in (1..200_000).map { Armory.rollMaterial(10, Random(it)) }.toSet(), "diamond can happen at level 10")
        // Level 100: better gear common, but diamond is capped.
        val high = shares(100)
        assertTrue((high[Material.BASIC] ?: 0.0) < 0.2)
        assertTrue((high[Material.DIAMOND] ?: 0.0) in 0.02..0.07)
    }

    @Test
    fun armorScrapForgesAndSmeltsAndDropsFromThugs() {
        var s = hero(10).copy(armorParts = mapOf(ArmorType.BOOTS to PARTS_TO_FORGE), gold = 100)
        s = GameEngine.forgeArmor(s, ArmorType.BOOTS, Random(2)).state
        assertEquals(ArmorType.BOOTS, s.armor.single().type)
        assertEquals(0, s.armorParts[ArmorType.BOOTS])
        s = GameEngine.smeltArmor(s, s.armor.single().id).state
        assertTrue((s.armorParts[ArmorType.BOOTS] ?: 0) >= 1)
        assertEquals(100 - SMELT_COST, s.gold)

        // Beating a thug crew drops weapon parts and armor scrap.
        val crew = Villains.ambusher(10, 1, Random(3))
        val b = GameEngine.newBattle(hero(10), crew.second.first(), EncounterType.THUG, rng = Random(4), thug = crew.first)
        b.enemies.forEach { it.hp = 0 }
        while (b.outcome == BattleOutcome.ONGOING && b.current != null) b.act(BattleAction.Defend)
        val after = GameEngine.applyBattleResult(hero(10), b.result(), 1_000).state
        assertTrue(after.parts.values.sum() > 0)
        assertTrue(after.armorParts.values.sum() > 0)
    }

    @Test
    fun cursedWeaponsHurtUntilLiftedThenKeepTheirPower() {
        val w = Armory.cursedWeapon(30, Random(5)).copy(curse = Curse.LIFE_DRAIN)
        assertEquals(GearRarity.LEGENDARY, w.rarity)
        assertTrue(w.cursed)
        assertTrue(w.name.startsWith("Cursed Life Drain"))
        // In battle: weaker hits, a drain on the wielder, and life drain on hit.
        val h = Hero(level = 30)
        val c = Combatant.fromHero(h, w)
        assertTrue(c.weaponCursed)
        assertTrue(c.curseDrainPct > 0)
        val foe = Monster.create(Bestiary.get("goopling"), 30, Random(6))
        val b = Battle(h, emptyList(), foe, EncounterType.WILD, emptyMap(), false, rng = Random(7), gear = w)
        var turns = 0
        while (b.outcome == BattleOutcome.ONGOING && turns++ < 20) {
            // Hurt (so life drain has something to heal) but never down.
            b.current?.let { it.hp = it.maxHp / 2 }
            b.enemy.hp = b.enemy.maxHp
            b.act(BattleAction.Attack)
        }
        assertTrue(b.log.any { "drained by a curse" in it })
        assertTrue(b.log.any { "drinks life" in it })
        assertTrue(b.allies.first().weaponUses > 0)

        // Every hit lifts 2%; at 0 the curse is gone and new effects arrive.
        var s = hero(30).copy(equippedGear = w)
        val msg = StringBuilder()
        s = GameEngine.wearCurses(s, 10, 0, msg)
        assertEquals(80, s.equippedGear!!.cursePct)
        s = GameEngine.wearCurses(s, 40, 0, msg)
        val lifted = s.equippedGear!!
        assertFalse(lifted.cursed)
        assertEquals(Curse.LIFE_DRAIN, lifted.curse)
        assertTrue(lifted.effects.size > w.effects.size)
        assertTrue("lifted" in msg.toString())
        assertFalse(Combatant.fromHero(h, lifted).weaponCursed)
        assertEquals(0, Combatant.fromHero(h, lifted).curseDrainPct)
    }

    @Test
    fun frostbiteHurtsUnlessADroughtIsOn() {
        val foe = Monster.create(Bestiary.get("goopling"), 10, Random(8))
        val frost = Armory.cursedWeapon(10, Random(9)).copy(curse = Curse.FROST)
        val b = Battle(Hero(level = 10), emptyList(), foe, EncounterType.WILD, emptyMap(), false, rng = Random(10), gear = frost)
        var turns = 0
        while (b.outcome == BattleOutcome.ONGOING && turns++ < 30 && b.log.none { "hurt by frostbite" in it }) {
            b.current?.let { it.hp = it.maxHp }
            b.enemy.hp = b.enemy.maxHp
            b.act(BattleAction.Attack)
        }
        assertTrue(b.log.any { "frostbite" in it })
    }

    @Test
    fun weekendGuardianPaysSpellBooksAndSometimesCursedGear() {
        val s = hero(25)
        val results = (1..400).map { GameEngine.weekendReward(s, Random(it)) }
        val books = results.count { u -> u.rewards.any { it is Reward.SpellBook } }.toDouble() / results.size
        val cursed = results.count { u -> u.state.gear.any { it.cursed } || u.state.armor.any { it.cursed } }.toDouble() / results.size
        assertTrue(books in 0.65..0.85, "books $books")
        assertTrue(cursed in 0.3..0.5, "cursed $cursed")
        assertTrue(results.all { it.state.gold > s.gold })
        // Cursed weapons are the usual cursed prize.
        assertTrue(results.count { u -> u.state.gear.any { it.cursed } } > results.count { u -> u.state.armor.any { it.cursed } })
    }
}
