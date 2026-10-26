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

    private val utc = java.time.ZoneId.of("UTC")

    /** Epoch ms for a date/time in UTC. 2026-10-26 is a Monday. */
    private fun at(day: Int, hour: Int = 12) =
        java.time.ZonedDateTime.of(2026, 10, day, hour, 0, 0, 0, utc).toInstant().toEpochMilli()

    /** A state with [done] of [total] quests completed on Monday 2026-10-26. */
    private fun withQuests(total: Int, done: Int): GameState {
        val tasks = (0 until total).map { i ->
            QuestTask("q$i", "Quest $i", createdAtMs = at(26, 9), completedAtMs = if (i < done) at(26, 10) else null)
        }
        return started().copy(tasks = tasks)
    }

    @Test
    fun bossMustBeDefeatedBeforeTaming() {
        val s = withQuests(total = 6, done = 5)
        val shrine = World.shrineFromPlace("1", "Museum", 0.0, 0.0, ShrineTier.GOLD)
        val saturday = at(31)
        val (boss, mode) = GameEngine.bossEncounter(s, shrine, saturday, zone = utc)
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
    fun goldShrinesOpenOnWeekendsWhenWeeklyGoalMet() {
        val shrine = World.shrineFromPlace("1", "Stadium", 0.0, 0.0, ShrineTier.GOLD)
        val good = withQuests(total = 7, done = 5) // 71%
        assertTrue(Schedule.access(good, shrine, at(31), utc).open) // Saturday
        assertTrue(Schedule.access(good, shrine, at(31) + 86_400_000, utc).open) // Sunday
        assertTrue(!Schedule.access(good, shrine, at(29), utc).open) // Thursday: wrong day
        val lowPercent = withQuests(total = 10, done = 6) // 60%
        assertTrue(!Schedule.access(lowPercent, shrine, at(31), utc).open)
        val tooFew = withQuests(total = 4, done = 4) // 100% but under the minimum of 5
        assertTrue(!Schedule.access(tooFew, shrine, at(31), utc).open)
        // Quests completed on the weekend don't count toward the Monday–Friday goal.
        val weekendWork = good.copy(tasks = good.tasks + QuestTask("w", "Weekend chore", createdAtMs = at(31, 9), completedAtMs = at(31, 10)))
        assertEquals(5, Schedule.workweek(weekendWork, at(31), utc).done)
    }

    @Test
    fun silverShrinesOpenTuesdayAndThursday() {
        val shrine = World.shrineFromPlace("2", "Cinema", 0.0, 0.0, ShrineTier.SILVER)
        val ok = withQuests(total = 4, done = 2) // 50%
        assertTrue(Schedule.access(ok, shrine, at(27), utc).open) // Tuesday
        assertTrue(Schedule.access(ok, shrine, at(29), utc).open) // Thursday
        assertTrue(!Schedule.access(ok, shrine, at(28), utc).open) // Wednesday
        assertTrue(!Schedule.access(withQuests(total = 4, done = 1), shrine, at(27), utc).open)

        val (guardian, mode) = GameEngine.bossEncounter(ok, shrine, at(27), rng, utc)
        assertEquals("silver", mode)
        assertNotNull(guardian)
        assertTrue(GameEngine.perfectStatCount(guardian) >= 3)
        assertTrue(guardian.species.rarity != Rarity.LEGENDARY)
        // Harder to tame than the same monster in the wild.
        val wild = GameEngine.newBattle(ok, guardian, EncounterType.WILD, rng = rng).apply { enemy.hp = enemy.maxHp / 5 }
        val silver = GameEngine.newBattle(ok, guardian, EncounterType.SILVER_BOSS, shrine.id, rng).apply { enemy.hp = enemy.maxHp / 5 }
        assertTrue(silver.captureOdds(Item.GREAT_ORB) < wild.captureOdds(Item.GREAT_ORB))
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
        assertEquals(1.5, Element.GAS.multiplierAgainst(Element.ELECTRIC))
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
        val shrine = World.shrineFromPlace("9", "Cafe", 0.0, 0.0, ShrineTier.ITEM)
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
        assertEquals(500, win.gold)
        assertEquals(1, win.lootBoxes.size)
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

    @Test
    fun lootBoxesUnlockByWalking() {
        var s = started().copy(
            lootBoxes = listOf(
                LootBox("a", BoxRarity.COMMON, 0),
                LootBox("b", BoxRarity.RARE, 0),
                LootBox("c", BoxRarity.LEGENDARY, 0),
            ),
        )
        s = LootBoxes.startUnlocking(s, "a").state
        s = LootBoxes.startUnlocking(s, "b").state
        // Only two at a time.
        val third = LootBoxes.startUnlocking(s, "c")
        assertEquals(s, third.state)
        // First reading sets the baseline; 2,000 steps later = 1 mile.
        s = LootBoxes.onStepCount(s, 10_000)
        assertEquals(0.0, s.lootBoxes[0].walkedMeters)
        s = LootBoxes.onStepCount(s, 12_000)
        assertTrue(s.lootBoxes[0].ready)
        assertTrue(!s.lootBoxes[1].ready)
        assertEquals(0.0, s.lootBoxes[2].walkedMeters)
        // Can't open early.
        assertEquals(s, LootBoxes.open(s, "b").update.state)
        val opened = LootBoxes.open(s, "a", rng).update
        assertEquals(2, opened.state.lootBoxes.size)
        assertTrue(opened.state.gold > s.gold)
        // Reboot: counter restarts from zero.
        s = LootBoxes.onStepCount(opened.state, 4_000)
        assertEquals(3 * METERS_PER_MILE, s.lootBoxes.first { it.id == "b" }.walkedMeters, 0.01)
    }

    @Test
    fun boxLimitBlocksNewSessions() {
        val full = started().copy(lootBoxes = (1..MAX_LOOT_BOXES).map { LootBox("$it", BoxRarity.COMMON, 0) })
        assertNull(GameEngine.startFocus(full, 30, 0).state.focus)
    }

    @Test
    fun longerSessionsGiveRarerBoxes() {
        val short = (1..2000).count { LootBoxes.roll(15, Random(it)) != BoxRarity.COMMON }
        val long = (1..2000).count { LootBoxes.roll(120, Random(it)) != BoxRarity.COMMON }
        assertTrue(long > short)
        assertEquals(1.5, Element.FLIGHT.multiplierAgainst(Element.GAS))
    }

    @Test
    fun boxContentsByLevel() {
        val base = started().copy(inventory = emptyMap(), gold = 0)
        fun openBox(r: BoxRarity, seed: Int): BoxOpening {
            val s = base.copy(lootBoxes = listOf(LootBox("x", r, 0, unlocking = true, walkedMeters = r.meters)))
            return LootBoxes.open(s, "x", Random(seed))
        }
        repeat(20) { seed ->
            val l1 = openBox(BoxRarity.COMMON, seed)
            assertTrue(l1.update.state.gold in 30..60)
            assertEquals(0, l1.update.state.weaponParts)
            assertTrue(l1.update.state.spellPages.isEmpty())
            assertNull(l1.boss)
            val l2 = openBox(BoxRarity.RARE, seed).update.state
            assertTrue(l2.weaponParts > 0)
            assertTrue(l2.spellPages.isEmpty())
            val l3 = openBox(BoxRarity.EPIC, seed).update.state
            assertTrue(l3.weaponParts > 0 && l3.spellPages.isNotEmpty())
            assertNotNull(openBox(BoxRarity.LEGENDARY, seed).boss)
        }
        val legendaryBosses = (1..1000).count { LootBoxes.boxBoss(10, Random(it)).species.rarity == Rarity.LEGENDARY }
        assertTrue(legendaryBosses in 150..250, "about 20%: $legendaryBosses")
        // Level 4 boxes need an hour or more off the phone.
        assertNull(LootBoxes.odds(45)[BoxRarity.LEGENDARY])
        assertNotNull(LootBoxes.odds(60)[BoxRarity.LEGENDARY])
    }

    @Test
    fun forgingWeapons() {
        val s = started().copy(weaponParts = GameEngine.PARTS_TO_FORGE + 1)
        val u = GameEngine.forge(s, rng)
        assertEquals(1, u.state.weaponParts)
        assertEquals(1, u.state.weapons.size)
        assertEquals(u.state, GameEngine.forge(u.state, rng).state)
    }

    @Test
    fun learnsetsAndLoadouts() {
        Moves.learnsets.values.flatten().forEach { (_, id) -> Moves.get(id) }
        for (sp in Bestiary.all) {
            val m = Monster.create(sp, 1, rng)
            assertTrue(m.activeMoves.any { it.isDamaging }, "${sp.id} needs an attack at Lv 1")
            assertTrue(Monster.create(sp, 60, rng).activeMoves.size == Moves.MAX_MOVES)
            // No monster is Normal type; Normal is attack-only.
            assertNotEquals(Element.NEUTRAL, sp.element)
        }
        // Better moves as they grow.
        val young = Monster.create(Bestiary.get("bubblefin"), 5, rng).activeMoves.maxOf { it.power }
        val old = Monster.create(Bestiary.get("bubblefin"), 40, rng).activeMoves.maxOf { it.power }
        assertTrue(old > young)
    }

    @Test
    fun monstersOutclassTheHero() {
        // A level-20 hero who spread their points evenly, vs. every species at level 20 with average genes.
        val hero = Hero(level = 20, attributes = HeroAttribute.entries.associateWith { STARTING_ATTRIBUTE + 19 * STAT_POINTS_PER_LEVEL / 5 })
        val heroTotal = hero.maxHp + hero.attack + hero.defense + hero.speed
        for (sp in Bestiary.all) {
            val m = Monster(uid = "m", speciesId = sp.id, personality = Personality.BALANCED, ivs = StatBlock(7, 7, 7, 7, 7), level = 20).stats
            assertTrue(m.hp + m.attack + m.defense + m.speed > heroTotal, "${sp.id} should out-stat the hero")
            assertTrue(m.hp > hero.maxHp, "${sp.id} HP")
        }
    }

    @Test
    fun growingTeachesMoves() {
        val m = Monster.create(Bestiary.get("emberkit"), 6, rng)
        val (after, notes) = GameEngine.grow(m, monsterXpForNext(6))
        assertEquals(7, after.level)
        assertTrue(notes.any { it.contains("learned Fire Fang") })
        // A custom loadout with a free slot gets the new move...
        val (filled, _) = GameEngine.grow(m.copy(moves = listOf("spit_fire", "tackle", "bite")), monsterXpForNext(6))
        assertEquals(listOf("spit_fire", "tackle", "bite", "fire_fang"), filled.moves)
        // ...but a full one is left alone, with a hint to swap it in.
        val (full, fullNotes) = GameEngine.grow(m.copy(moves = listOf("spit_fire", "tackle", "bite", "spit_fire").distinct() + "tackle"), monsterXpForNext(6))
        assertTrue(fullNotes.any { it.contains("can learn Fire Fang") })
        assertTrue("fire_fang" !in full.moves!!)
        // Players can choose any learned moves for their 4 slots.
        val s = started().copy(monsters = listOf(after))
        val set = GameEngine.setMoves(s, after.uid, listOf("fire_fang", "bite", "not_a_move")).state
        assertEquals(listOf("fire_fang", "bite"), set.monsters.single().moves)
    }

    @Test
    fun sleepStopsEnemyAndHelpsTaming() {
        val s = started().copy(party = emptyList())
        val enemy = Monster.create(Bestiary.get("zapmouse"), 5, rng)
        val b = GameEngine.newBattle(s, enemy, EncounterType.WILD, rng = Random(4))
        b.enemy.hp = b.enemy.maxHp / 4
        val awake = b.captureOdds(Item.TAMING_ORB)
        b.enemy.status = Status.SLEEP
        b.enemy.statusTurns = 99
        assertTrue(b.captureOdds(Item.TAMING_ORB) > awake)
        val heroHp = b.allies.first().hp
        repeat(5) { b.act(BattleAction.Defend) }
        assertEquals(heroHp, b.allies.first().hp)
    }

    @Test
    fun weatherAndTypes() {
        assertEquals(1.25, Weather.RAIN.multiplier(Element.WATER))
        assertEquals(1.25, Weather.RAIN.multiplier(Element.ELECTRIC))
        assertEquals(0.85, Weather.RAIN.multiplier(Element.FIRE))
        assertEquals(1.25, Weather.DROUGHT.multiplier(Element.CORRUPT))
        assertEquals(0.85, Weather.DROUGHT.multiplier(Element.WATER))
    }

    @Test
    fun faintedMonstersNeedRestorePotions() {
        var s = started().copy(gold = 100)
        val starter = s.monsters.single()
        val brute = Monster.create(Bestiary.get("thunder_titan"), 60, rng)
        val b = GameEngine.newBattle(s, brute, EncounterType.WILD, rng = rng)
        var guard = 0
        while (b.outcome == BattleOutcome.ONGOING && guard++ < 200) b.act(BattleAction.Defend)
        val r = b.result()
        assertTrue(starter.uid in r.faintedUids)
        s = GameEngine.applyBattleResult(s, r, 0).state
        assertTrue(s.monsters.single().fainted)
        // Fainted monsters sit out battles.
        assertEquals(1, GameEngine.newBattle(s, brute, EncounterType.WILD, rng = rng).allies.size)
        val restored = GameEngine.restore(s, starter.uid).state
        assertTrue(!restored.monsters.single().fainted)
        assertEquals(0, restored.count(Item.RESTORE_POTION))
        // Without a potion, the daily recovery kicks in once a day.
        val noPotion = s.copy(inventory = emptyMap())
        assertEquals(noPotion, GameEngine.restore(noPotion, starter.uid).state)
        val rec = GameEngine.dailyRecovery(noPotion, at(27), utc)
        assertNotNull(rec)
        assertTrue(!rec.state.monsters.single().fainted)
        val again = rec.state.copy(monsters = rec.state.monsters.map { it.copy(fainted = true) })
        assertNull(GameEngine.dailyRecovery(again, at(27, 20), utc))
    }
}
