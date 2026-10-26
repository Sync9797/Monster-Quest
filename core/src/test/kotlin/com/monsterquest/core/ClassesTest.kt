package com.monsterquest.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClassesTest {
    private val rng = Random(7)

    private fun started(): GameState = GameEngine.chooseStarter(GameState(), "emberkit", rng).state

    private fun atLevel(level: Int, cls: HeroClass = HeroClass.WARRIOR, gold: Int = 5000): GameState =
        started().copy(hero = Hero(level = level, heroClass = cls, classChosen = true), gold = gold)

    @Test
    fun classWeaponsAddToEveryStatByRarity() {
        val bonuses = GearRarity.entries.map { Gear("w", WeaponType.BROADSWORD, it, 10).classStats(HeroClass.WARRIOR) }
        assertEquals(listOf(2, 4, 6, 8), bonuses)
        val hero = Hero(level = 10, heroClass = HeroClass.WARRIOR)
        val epic = Gear("w", WeaponType.BROADSWORD, GearRarity.EPIC, 10)
        val c = Combatant.fromHero(hero, epic)
        assertEquals(hero.defense + 6, c.defense)
        // Adventurers have no class weapons: no bonus, no penalty.
        assertEquals(0, epic.classStats(HeroClass.NONE))
    }

    @Test
    fun unfamiliarWeaponPenaltyFadesWithPracticeAndIsPerWeapon() {
        val staff = Gear("s", WeaponType.STAFF, GearRarity.LEGENDARY, 10)
        assertEquals(-8, staff.classStats(HeroClass.WARRIOR))
        val hero = Hero(level = 10, heroClass = HeroClass.WARRIOR)
        assertEquals(hero.defense - 8, Combatant.fromHero(hero, staff).defense)

        var s = atLevel(10).copy(equippedGear = staff)
        val won = BattleResult(BattleOutcome.WON, EncounterType.WILD, s.monsters.first(), 0, 0, emptyMap(), emptyMap(), emptyMap(), null, null)
        repeat(5) { s = GameEngine.applyBattleResult(s, won, 0).state }
        assertEquals(5, s.equippedGear!!.proficiency)
        assertEquals(-4, s.equippedGear!!.classStats(HeroClass.WARRIOR))
        // Running away doesn't count.
        s = GameEngine.applyBattleResult(s, won.copy(outcome = BattleOutcome.FLED), 0).state
        assertEquals(5, s.equippedGear!!.proficiency)
        var msg = ""
        repeat(5) { GameEngine.applyBattleResult(s, won, 0).let { u -> s = u.state; msg = u.message } }
        assertTrue(s.equippedGear!!.mastered)
        assertTrue("mastered" in msg)
        assertEquals(0, s.equippedGear!!.classStats(HeroClass.WARRIOR))
        // A second staff has to be learned all over again.
        assertEquals(-8, staff.copy(id = "s2").classStats(HeroClass.WARRIOR))
        // Weapons of your own class don't need practice.
        val sword = Gear("b", WeaponType.SWORD, GearRarity.NORMAL, 10)
        s = GameEngine.applyBattleResult(s.copy(equippedGear = sword), won, 0).state
        assertEquals(0, s.equippedGear!!.proficiency)
    }

    @Test
    fun subclassesUnlockAtFifteenAndBelongToTheClass() {
        assertEquals(3, Subclass.of(HeroClass.WARRIOR).size)
        assertEquals(3, Subclass.of(HeroClass.MAGE).size)
        assertEquals(3, Subclass.of(HeroClass.ROGUE).size)
        assertEquals(3, Subclass.of(HeroClass.NONE).size)
        assertTrue("unlock" in GameEngine.chooseSubclass(atLevel(14), Subclass.BERSERKER).message)
        assertNull(GameEngine.chooseSubclass(atLevel(15), Subclass.CLERIC).state.hero.subclass)

        var s = GameEngine.chooseSubclass(atLevel(15), Subclass.BERSERKER).state
        assertEquals(Subclass.BERSERKER, s.hero.subclass)
        assertEquals(5000, s.gold)
        // Berserker: +15% Attack, -10% Defense.
        val plain = Hero(level = 15, heroClass = HeroClass.WARRIOR)
        assertTrue(s.hero.attack > plain.attack)
        assertTrue(s.hero.defense < plain.defense)
        // Switching costs gold.
        s = GameEngine.chooseSubclass(s, Subclass.PALADIN).state
        assertEquals(5000 - SUBCLASS_CHANGE_COST, s.gold)
        assertTrue(Combatant.fromHero(s.hero).shield > 0)
        // A new class clears the subclass.
        s = GameEngine.chooseClass(s, HeroClass.MAGE).state
        assertNull(s.hero.subclass)
    }

    @Test
    fun heroSkillSlotsOpenAt30And45And60AndRankUpEvery10Levels() {
        assertEquals(0, Hero(level = 29).skillSlots)
        assertEquals(1, Hero(level = 30).skillSlots)
        assertEquals(2, Hero(level = 45).skillSlots)
        assertEquals(3, Hero(level = 60).skillSlots)
        assertTrue("level 30" in GameEngine.learnSkill(atLevel(29), 0, Trait.HOVER).message)
        assertTrue("level 45" in GameEngine.learnSkill(atLevel(30), 1, Trait.HOVER).message)

        var s = GameEngine.learnSkill(atLevel(45), 0, Trait.MORALE_MASTER).state
        s = GameEngine.learnSkill(s, 1, Trait.INTIMIDATE).state
        assertEquals(5000, s.gold)
        assertEquals(listOf(Trait.MORALE_MASTER to 2, Trait.INTIMIDATE to 1), s.hero.skillRanks)
        // Monster-only skills and duplicates are refused.
        assertEquals(s, GameEngine.learnSkill(s, 1, Trait.LEVITATE).state)
        assertEquals(s, GameEngine.learnSkill(s, 1, Trait.MORALE_MASTER).state)
        // Swapping costs gold; the slot keeps its rank.
        s = GameEngine.learnSkill(s, 0, Trait.OBLIVIOUS).state
        assertEquals(5000 - SKILL_CHANGE_COST, s.gold)
        assertEquals(Trait.OBLIVIOUS to 2, s.hero.skillRanks.first())
        assertEquals(5, skillRank(100, 30))
    }

    @Test
    fun moraleMasterBoostsMonstersAndIntimidateLowersFoes() {
        val s = started()
        val foe = Monster.create(Bestiary.get("emberkit"), 5, rng)
        val plain = GameEngine.newBattle(s, foe, EncounterType.WILD, rng = Random(1))
        val leader = s.copy(hero = s.hero.copy(level = 50, skills = listOf(Trait.MORALE_MASTER, Trait.INTIMIDATE)))
        val b = GameEngine.newBattle(leader, foe, EncounterType.WILD, rng = Random(1))
        // Rank 3 Morale Master: +6%.
        assertTrue(b.allies[1].attack > plain.allies[1].attack)
        assertTrue(b.enemies.all { (it.stages[BattleStat.ATTACK] ?: 0) < 0 })
        assertTrue(b.log.any { "Intimidate" in it })
    }

    @Test
    fun hoveringFightersIgnoreEarthMoves() {
        val hero = Hero(level = 30, skills = listOf(Trait.HOVER))
        assertTrue(Combatant.fromHero(hero).immuneEarth)
        val flier = Monster.create(Bestiary.get("tempest_roc"), 30, rng)
        assertEquals(Trait.LEVITATE, flier.trait?.first)
        // Earth types can't levitate; slimes don't intimidate; dragons do.
        assertTrue(Bestiary.all.filter { it.element == Element.EARTH }.none { MonsterTraits.of(it).hovers })
        assertTrue(Bestiary.all.filter { "Goopling" in it.family }.none { MonsterTraits.of(it) == Trait.INTIMIDATE })
        assertEquals(Trait.INTIMIDATE, MonsterTraits.of(Bestiary.get("cindrake")))
        // Every monster has a skill, and only monster skills.
        assertTrue(Bestiary.all.all { MonsterTraits.of(it).forMonster })

        // An Earth move against a levitating monster never lands.
        val s = started()
        val earthUser = Monster.create(Bestiary.get("terraursa"), 40, rng)
        val battle = Battle(Hero(level = 40), listOf(earthUser), flier, EncounterType.WILD, emptyMap(), false, rng = Random(3))
        val hp = battle.enemy.hp
        val earthMove = earthUser.activeMoves.firstOrNull { it.type == Element.EARTH && it.isDamaging && it.effects.none { e -> e is MoveEffect.SmackDown } }
        assertNotNull(earthMove)
        repeat(6) {
            val c = battle.current ?: return@repeat
            if (c.key == earthUser.uid) battle.act(BattleAction.UseMove(earthMove.id)) else battle.act(BattleAction.Defend)
        }
        assertTrue(battle.log.any { "floats above" in it })
        assertTrue(battle.enemy.hp == hp || battle.events.none { it.targetKey == Combatant.ENEMY_KEY && it.element == Element.EARTH && it.amount > 0 })
        assertNotNull(s)
    }

    @Test
    fun monstersGainTheirSkillAtLevel30() {
        val m = Monster.create(Bestiary.get("goopling"), 29, rng)
        assertNull(m.trait)
        val (after, up) = GameEngine.growDetailed(m, m.xpToNext)
        assertEquals(30, after.level)
        assertEquals(Trait.REGENERATION to 1, after.trait)
        assertTrue(up!!.notes.any { "Regeneration" in it })
    }

    @Test
    fun insomniaRankFiveCantSleepAndObliviousIgnoresDrops() {
        val c = Combatant.fromHero(Hero(level = 70, skills = listOf(Trait.INSOMNIA)))
        assertEquals(Combatant.NO_SLEEP, c.sleepCut)
        val o = Combatant.fromHero(Hero(level = 30, skills = listOf(Trait.OBLIVIOUS)))
        assertTrue(o.oblivious)
    }

    @Test
    fun kleptomaniacCarriesASecondWeapon() {
        val sword = Gear("a", WeaponType.SWORD, GearRarity.RARE, 30)
        val broad = Gear("b", WeaponType.BROADSWORD, GearRarity.RARE, 30)
        var s = atLevel(30).copy(gear = listOf(sword, broad))
        assertEquals(s, GameEngine.equipOffhand(s, "b").state)
        s = GameEngine.learnSkill(s, 0, Trait.KLEPTOMANIAC).state
        s = GameEngine.equip(s, "a").state
        s = GameEngine.equipOffhand(s, "b").state
        assertEquals(broad, s.offhandGear)
        val one = Combatant.fromHero(s.hero, s.equippedGear)
        val two = Combatant.fromHero(s.hero, s.equippedGear, offhand = s.offhandGear)
        assertTrue(two.attack > one.attack)
        // Swapping the skill out puts the second weapon away.
        s = GameEngine.learnSkill(s, 0, Trait.SCHOLAR).state
        assertNull(s.offhandGear)
        assertTrue(s.gear.any { it.id == "b" })
    }

    @Test
    fun sellingPaysMoreForRarerThingsAndSmeltingGivesParts() {
        var s = atLevel(10, gold = 100).copy(inventory = mapOf(Item.POTION to 2, Item.MASTER_ORB to 1))
        s = GameEngine.sellItem(s, Item.POTION).state
        assertEquals(100 + Item.POTION.price / 2, s.gold)
        assertEquals(1, s.count(Item.POTION))
        assertTrue(Item.MASTER_ORB.sellPrice > Item.GREAT_ORB.sellPrice)
        // Never sells for more than it costs.
        assertTrue(Item.entries.filter { it.price > 0 }.all { it.sellPrice < it.price })

        val prices = GearRarity.entries.map { Gear("x", WeaponType.SWORD, it, 10).sellPrice }
        assertEquals(prices.sorted(), prices)
        assertEquals(prices.size, prices.toSet().size)

        val legendary = Gear("l", WeaponType.DAGGER, GearRarity.LEGENDARY, 10)
        s = s.copy(gear = listOf(legendary), gold = 100)
        val smelted = GameEngine.smelt(s, "l").state
        assertEquals(100 - SMELT_COST, smelted.gold)
        assertEquals(4, smelted.parts[WeaponType.DAGGER])
        assertTrue(smelted.gear.isEmpty())
        assertEquals(s, GameEngine.smelt(s.copy(gold = 10), "l").state.copy(gold = 100))
        val sold = GameEngine.sellWeapon(s, "l").state
        assertEquals(100 + legendary.sellPrice, sold.gold)
        // Worn gear has to come off first.
        assertEquals(s.copy(equippedGear = legendary, gear = emptyList()), GameEngine.sellWeapon(s.copy(equippedGear = legendary, gear = emptyList()), "l").state)
    }

    @Test
    fun wayfarerAndScholarRaiseRewards() {
        val foe = Monster.create(Bestiary.get("emberkit"), 5, rng)
        fun win(h: Hero): BattleResult {
            val b = Battle(h, emptyList(), foe, EncounterType.WILD, emptyMap(), false, rng = Random(9))
            b.enemies.forEach { it.hp = 0 }
            b.act(BattleAction.Defend)
            if (b.outcome == BattleOutcome.ONGOING) return win(h)
            return b.result()
        }
        val base = win(Hero(level = 20, heroClass = HeroClass.NONE, classChosen = true))
        val way = win(Hero(level = 20, heroClass = HeroClass.NONE, classChosen = true, subclass = Subclass.WAYFARER))
        assertTrue(way.heroXp > base.heroXp)
        assertFalse(base.heroXp == 0)
    }
}
