package com.monsterquest.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameTest {
    private val rng = Random(42)

    private fun started(): GameState = GameEngine.chooseStarter(GameState(), "emberkit", rng).state

    @Test
    fun completingTaskGivesXpGoldAndToken() {
        var s = GameEngine.addTask(started(), "Do laundry", Difficulty.HARD, rng).state
        val id = s.tasks.single().id
        s = GameEngine.completeTask(s, id, 1000).state
        assertEquals(50 + Difficulty.HARD.gold, s.gold)
        assertEquals(2, s.huntTokens)
        assertEquals(Difficulty.HARD.xp - heroXpForNext(1), s.hero.xp) // 60 xp levels past 50
        assertEquals(2, s.hero.level)
        assertEquals(STAT_POINTS_PER_LEVEL, s.hero.unspentPoints)
        // Can't double-claim.
        assertEquals(s, GameEngine.completeTask(s, id, 2000).state)
    }

    @Test
    fun upgradeEarnedEveryFiveLevels() {
        var s = started()
        while (s.hero.level < 10) s = GameEngine.addHeroXp(s, 25).state
        assertEquals(2, s.pendingUpgrades)
        s = GameEngine.applyUpgrade(s, bag = true).state
        s = GameEngine.applyUpgrade(s, bag = false).state
        assertEquals(STARTING_BAG_CAPACITY + BAG_UPGRADE_AMOUNT, s.bagCapacity)
        assertEquals(STARTING_MONSTER_CAPACITY + STORAGE_UPGRADE_AMOUNT, s.monsterCapacity)
        assertEquals(0, s.pendingUpgrades)
    }

    @Test
    fun personalitiesAndIvsMakeSameSpeciesDiffer() {
        val wolf = Bestiary.get("shadow_pup")
        val wolves = (1..20).map { Monster.create(wolf, 20, rng) }
        assertTrue(wolves.map { it.stats }.toSet().size > 15)
        val fierce = Monster(uid = "a", speciesId = "shadow_pup", personality = Personality.FEROCIOUS, ivs = StatBlock(), level = 20)
        val stalwart = fierce.copy(personality = Personality.STALWART)
        assertTrue(fierce.stats.attack > stalwart.stats.attack)
        assertTrue(stalwart.stats.defense > fierce.stats.defense)
        assertEquals("High Attack, low Defense", Personality.FEROCIOUS.description)
    }

    @Test
    fun monstersLevelUp() {
        val m = Monster.create(Bestiary.get("ember_pup"), 5, rng)
        val up = m.gainXp(monsterXpForNext(5) + monsterXpForNext(6))
        assertEquals(7, up.level)
        assertEquals("ember_pup", up.speciesId)
        assertTrue(up.stats.hp > m.stats.hp)
    }

    @Test
    fun partyLimitEnforced() {
        var s = started()
        repeat(4) { s = s.copy(monsters = s.monsters + Monster.create(Bestiary.get("zapmouse"), 3, rng)) }
        s.monsters.forEach { s = GameEngine.toggleParty(s, it.uid).state }
        assertEquals(MAX_PARTY_SIZE, s.party.size)
    }

    @Test
    fun bagCapacityLimitsLoot() {
        val s = started().copy(inventory = mapOf(Item.POTION to STARTING_BAG_CAPACITY - 1))
        val (after, lost) = GameEngine.addItems(s, mapOf(Item.ETHER to 3))
        assertEquals(STARTING_BAG_CAPACITY, after.bagCount)
        assertEquals(2, lost)
        assertEquals("Bag is full (20/20).", GameEngine.buy(after.copy(gold = 999), Item.POTION).message)
    }

    @Test
    fun captureOnlyWhenWeak() {
        assertEquals(0.0, captureChance(0.8, Rarity.COMMON, 0, Item.TAMING_ORB))
        val low = captureChance(0.1, Rarity.COMMON, 0, Item.TAMING_ORB)
        val mid = captureChance(0.45, Rarity.COMMON, 0, Item.TAMING_ORB)
        assertTrue(low > mid)
        assertTrue(captureChance(0.1, Rarity.COMMON, 0, Item.GREAT_ORB) > low)
        assertTrue(captureChance(0.1, Rarity.LEGENDARY, 0, Item.TAMING_ORB) < low)
    }

    @Test
    fun bossMustBeDefeatedBeforeTaming() {
        val s = started()
        val shrine = World.shrineFromPlace("1", "Museum", 0.0, 0.0, boss = true)
        val (boss, mode) = GameEngine.bossEncounter(s, shrine, 0)
        assertNotNull(boss)
        assertEquals("challenge", mode)
        val battle = GameEngine.newBattle(s, boss, EncounterType.BOSS_CHALLENGE, shrine.id, rng)
        assertTrue(battle.captureLocked)
        assertNotNull(battle.captureBlockedReason())
        battle.enemy.hp = 1
        // Even at 1 HP, capture is refused during the first challenge.
        battle.current?.let { battle.act(BattleAction.Capture(Item.TAMING_ORB)) }
        assertNotEquals(BattleOutcome.CAPTURED, battle.outcome)
    }

    @Test
    fun battleItemLimit() {
        val s = started().copy(inventory = mapOf(Item.POTION to 10))
        val b = GameEngine.newBattle(s, Monster.create(Bestiary.get("zapmouse"), 1, rng), EncounterType.WILD, rng = rng)
        var uses = 0
        while (b.outcome == BattleOutcome.ONGOING && uses < 10) {
            b.act(BattleAction.UseItem(Item.POTION, Combatant.HERO_KEY)); uses++
        }
        assertTrue(b.itemsUsedCount <= MAX_ITEMS_PER_BATTLE)
    }

    @Test
    fun fullBattleWinsAndPaysOut() {
        val s = started()
        val enemy = Monster.create(Bestiary.get("zapmouse"), 1, rng)
        val b = GameEngine.newBattle(s, enemy, EncounterType.WILD, rng = rng)
        var guard = 0
        while (b.outcome == BattleOutcome.ONGOING && guard++ < 200) b.act(BattleAction.Attack)
        assertEquals(BattleOutcome.WON, b.outcome)
        val u = GameEngine.applyBattleResult(s, b.result(), 0)
        assertTrue(u.state.gold > s.gold)
        assertTrue(u.state.monsters.first().xp > 0 || u.state.monsters.first().level > 3)
    }

    @Test
    fun losingCostsGold() {
        val s = started().copy(gold = 200)
        val boss = Monster.create(Bestiary.get("thunder_titan"), 60, rng)
        val b = GameEngine.newBattle(s, boss, EncounterType.WILD, rng = rng)
        var guard = 0
        while (b.outcome == BattleOutcome.ONGOING && guard++ < 200) b.act(BattleAction.Defend)
        assertEquals(BattleOutcome.LOST, b.outcome)
        val u = GameEngine.applyBattleResult(s, b.result(), 0)
        assertEquals(180, u.state.gold)
    }

    @Test
    fun captureAddsMonster() {
        val s = started().copy(inventory = mapOf(Item.MASTER_ORB to 3))
        val enemy = Monster.create(Bestiary.get("zapmouse"), 1, rng)
        val b = GameEngine.newBattle(s, enemy, EncounterType.WILD, rng = Random(1))
        b.enemy.hp = 1
        var guard = 0
        while (b.outcome == BattleOutcome.ONGOING && guard++ < 10) b.act(BattleAction.Capture(Item.MASTER_ORB))
        if (b.outcome == BattleOutcome.CAPTURED) {
            val u = GameEngine.applyBattleResult(s, b.result(), 0)
            assertEquals(2, u.state.monsters.size)
            assertEquals(2, u.state.party.size)
        }
    }

    @Test
    fun calendarSyncKeepsCompleted() {
        var s = started()
        val ev = CalendarEvent("e1:100", "Dentist", 0, 45 * 60_000, false, "Personal")
        s = GameEngine.syncCalendar(s, listOf(ev)).state
        s = GameEngine.completeTask(s, "e1:100", 5).state
        s = GameEngine.syncCalendar(s, listOf(ev, ev.copy(key = "e2:1", title = "Gym"))).state
        assertEquals(2, s.tasks.size)
        assertEquals(1, s.tasks.count { it.completed })
    }

    @Test
    fun spawnsAreDeterministic() {
        val a = World.spawnsNear(40.0, -74.0, 1_000_000, 5)
        val b = World.spawnsNear(40.0, -74.0, 1_000_500, 5)
        assertEquals(a, b)
        assertTrue(a.isNotEmpty())
        assertTrue(World.proceduralShrines(40.0, -74.0).isNotEmpty())
    }

    @Test
    fun saveRoundTrips() {
        val s = GameEngine.addTask(started(), "x", Difficulty.EASY, rng).state
            .copy(shrines = mapOf("osm:1" to ShrineRecord(5, true)), usedSpawns = setOf("s:1:2:3"))
        assertEquals(s, GameState.fromJson(s.toJson()))
    }

    @Test
    fun releaseKeepsLastMonster() {
        val s = started()
        assertNull(GameEngine.release(s, s.monsters.single().uid).state.monsters.firstOrNull { false })
        assertEquals(1, GameEngine.release(s, s.monsters.single().uid).state.monsters.size)
    }

    @Test
    fun fiftyMonstersWithEvolutionLines() {
        assertEquals(50, Bestiary.all.size)
        assertEquals(50, Bestiary.all.map { it.id }.toSet().size)
        val lines = Bestiary.all.groupBy { it.family }
        assertEquals(6, lines.values.count { it.size == 4 })
        assertEquals(6, lines.values.count { it.size == 3 })
        // Every type is represented, and every evolution target exists.
        assertEquals(Element.typed.toSet(), Bestiary.all.map { it.element }.toSet())
        Bestiary.all.mapNotNull { it.evolvesTo }.forEach { Bestiary.get(it) }
        // Final forms of four-stage lines out-stat the final hound breeds.
        val tiger = Bestiary.get("solar_tiger").base
        val fenrir = Bestiary.get("inferno_fenrir").base
        assertTrue(tiger.hp + tiger.attack + tiger.magic + tiger.defense + tiger.speed >
            fenrir.hp + fenrir.attack + fenrir.magic + fenrir.defense + fenrir.speed)
    }

    @Test
    fun monstersEvolveAtLevels() {
        val kit = Monster.create(Bestiary.get("emberkit"), 13, rng)
        assertEquals("emberkit", kit.speciesId)
        val evolved = kit.gainXp(monsterXpForNext(13))
        assertEquals("blazecat", evolved.speciesId)
        val all3 = Monster.create(Bestiary.get("emberkit"), 45, rng)
        assertEquals("solar_tiger", all3.speciesId)
        val hound = Monster.create(Bestiary.get("ember_pup"), 32, rng)
        assertEquals("inferno_fenrir", hound.speciesId)
    }

    @Test
    fun rockPaperScissorsTypes() {
        for (t in Element.typed) {
            val beats = t.beats!!
            assertEquals(1.5, t.multiplierAgainst(beats))
            assertTrue(beats.multiplierAgainst(t) < 1.0)
            assertEquals(t, beats.weakTo)
        }
        assertEquals(1.5, Element.ELECTRIC.multiplierAgainst(Element.WATER))
        assertEquals(1.5, Element.POISON.multiplierAgainst(Element.ELECTRIC))
        assertEquals(1.0, Element.FIRE.multiplierAgainst(Element.ELECTRIC))
    }

    @Test
    fun spellPagesTeachSpells() {
        var s = started()
        repeat(4) { s = GameEngine.grantLoot(s, GameEngine.Loot(pages = mapOf("gale" to 1))).state }
        assertTrue("gale" !in s.hero.knownSpells)
        assertEquals(4, s.spellPages["gale"])
        val u = GameEngine.grantLoot(s, GameEngine.Loot(pages = mapOf("gale" to 1)))
        assertTrue("gale" in u.state.hero.knownSpells)
        assertNull(u.state.spellPages["gale"])
        assertTrue(u.message.contains("learned Gale"))
    }

    @Test
    fun weaponsEquipAndTypeTheAttack() {
        var s = GameEngine.grantLoot(started(), GameEngine.Loot(weapons = listOf(Weapon.STORM_SPEAR))).state
        assertEquals(1, s.weapons.size)
        val before = s.bagCount
        s = GameEngine.equip(s, 0).state
        assertEquals(Weapon.STORM_SPEAR, s.equippedWeapon)
        assertEquals(before - 1, s.bagCount)
        val b = GameEngine.newBattle(s, Monster.create(Bestiary.get("bubblefin"), 1, rng), EncounterType.WILD, rng = rng)
        val hero = b.allies.first()
        assertEquals(Element.ELECTRIC, hero.attackElement)
        assertEquals(s.hero.attack + Weapon.STORM_SPEAR.attack, hero.attack)
    }

    @Test
    fun itemShrineGivesLoot() {
        val s = started().copy(inventory = emptyMap())
        val shrine = World.shrineFromPlace("9", "Cafe", 0.0, 0.0, boss = false)
        val u = GameEngine.visitShrine(s, shrine, 0, Random(5))
        val got = u.state.bagCount + u.state.spellPages.values.sum() + (u.state.hero.knownSpells.size - s.hero.knownSpells.size)
        assertTrue(got >= 2)
        assertTrue(GameEngine.shrineCooldownLeft(u.state, shrine, 1) > 0)
    }

    @Test
    fun focusRewardsAndPenalties() {
        val s = started().copy(gold = 500)
        val running = GameEngine.startFocus(s, 60, 0).state
        assertNotNull(running.focus)
        val win = GameEngine.resolveFocus(running, success = true, rng = rng).state
        assertNull(win.focus)
        assertTrue(win.gold >= 560)
        assertEquals(60, win.stats.focusMinutes)
        val fail = GameEngine.resolveFocus(running, success = false).state
        assertEquals(450, fail.gold)
        assertEquals(0, fail.huntTokens)
        assertEquals(1, fail.stats.focusFails)
    }

    @Test
    fun oldSavesMigrate() {
        val old = started().copy(version = 1, monsters = listOf(Monster("x", "greywolf", Personality.BRAVE, StatBlock(), level = 20)))
        val m = GameState.fromJson(old.toJson()).monsters.single()
        assertEquals("dusk_hound", m.speciesId) // shadow_pup, evolved at 16
    }
}
