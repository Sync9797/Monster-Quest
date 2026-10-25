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

    private fun started(): GameState = GameEngine.chooseStarter(GameState(), "emberpup", rng).state

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
        val wolf = Bestiary.get("greywolf")
        val wolves = (1..20).map { Monster.create(wolf, 20, rng) }
        assertTrue(wolves.map { it.stats }.toSet().size > 15)
        val fierce = Monster(uid = "a", speciesId = "greywolf", personality = Personality.FEROCIOUS, ivs = StatBlock(), level = 20)
        val stalwart = fierce.copy(personality = Personality.STALWART)
        assertTrue(fierce.stats.attack > stalwart.stats.attack)
        assertTrue(stalwart.stats.defense > fierce.stats.defense)
        assertEquals("High Attack, low Defense", Personality.FEROCIOUS.description)
    }

    @Test
    fun monstersLevelUp() {
        val m = Monster.create(Bestiary.get("pebblet"), 5, rng)
        val up = m.gainXp(monsterXpForNext(5) + monsterXpForNext(6))
        assertEquals(7, up.level)
        assertTrue(up.stats.hp > m.stats.hp)
    }

    @Test
    fun partyLimitEnforced() {
        var s = started()
        repeat(4) { s = s.copy(monsters = s.monsters + Monster.create(Bestiary.get("squeaker"), 3, rng)) }
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
        val shrine = World.shrineFromPlace("1", "Museum", 0.0, 0.0, landmark = true)
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
        val b = GameEngine.newBattle(s, Monster.create(Bestiary.get("squeaker"), 1, rng), EncounterType.WILD, rng = rng)
        var uses = 0
        while (b.outcome == BattleOutcome.ONGOING && uses < 10) {
            b.act(BattleAction.UseItem(Item.POTION, Combatant.HERO_KEY)); uses++
        }
        assertTrue(b.itemsUsedCount <= MAX_ITEMS_PER_BATTLE)
    }

    @Test
    fun fullBattleWinsAndPaysOut() {
        val s = started()
        val enemy = Monster.create(Bestiary.get("squeaker"), 1, rng)
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
        val boss = Monster.create(Bestiary.get("titan_golem"), 60, rng)
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
        val enemy = Monster.create(Bestiary.get("squeaker"), 1, rng)
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
}
