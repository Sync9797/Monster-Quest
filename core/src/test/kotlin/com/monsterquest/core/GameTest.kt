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
    fun sixtyFiveMonstersWithEvolutionLines() {
        assertEquals(65, Bestiary.all.size)
        assertEquals(65, Bestiary.all.map { it.id }.toSet().size)
        val lines = Bestiary.all.groupBy { it.family }
        assertEquals(6, lines.values.count { it.size == 4 })
        assertEquals(11, lines.values.count { it.size == 3 })
        // The newer lines evolve like the hounds.
        assertEquals("sludgeon", Monster.create(Bestiary.get("goopling"), 16, rng).speciesId)
        assertEquals("magma_colossus", Monster.create(Bestiary.get("pebblit"), 32, rng).speciesId)
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
        val spear = Gear("g1", WeaponType.SWORD, GearRarity.RARE, 10, Element.ELECTRIC)
        var s = GameEngine.grantLoot(started(), GameEngine.Loot(weapons = listOf(spear))).state
        assertEquals(1, s.gear.size)
        val before = s.bagCount
        s = GameEngine.equip(s, "g1").state
        assertEquals(spear, s.equippedGear)
        assertEquals(before - 1, s.bagCount)
        val b = GameEngine.newBattle(s, Monster.create(Bestiary.get("bubblefin"), 1, rng), EncounterType.WILD, rng = rng)
        val hero = b.allies.first()
        assertEquals(Element.ELECTRIC, hero.attackElement)
        assertEquals(s.hero.attack + spear.attackBonus(s.hero.heroClass), hero.attack)
    }

    @Test
    fun weaponTypesClassesAndRarity() {
        val warrior = HeroClass.WARRIOR
        val sword = Gear("a", WeaponType.SWORD, GearRarity.NORMAL, 20)
        val broad = sword.copy(type = WeaponType.BROADSWORD)
        val dagger = sword.copy(type = WeaponType.DAGGER)
        val staff = sword.copy(type = WeaponType.STAFF)
        // Class match bonus.
        assertTrue(sword.attackBonus(warrior) > sword.attackBonus(HeroClass.MAGE))
        assertTrue(staff.magicBonus(HeroClass.MAGE) > staff.magicBonus(warrior))
        // Broadsword hits harder but slows; staff is magic-heavy.
        assertTrue(broad.attackBonus(warrior) > sword.attackBonus(warrior))
        assertTrue(staff.magicBonus(HeroClass.MAGE) > staff.attackBonus(HeroClass.MAGE))
        val hero = Hero(level = 20, heroClass = warrior)
        assertTrue(Combatant.fromHero(hero, broad).speed < Combatant.fromHero(hero, sword).speed)
        // Daggers hit twice; wands restore energy.
        assertEquals(2, Combatant.fromHero(hero, dagger).hits)
        assertTrue(Combatant.fromHero(hero, sword.copy(type = WeaponType.WAND)).energyRegen > 0)
        // Rarity raises power and adds effects.
        val legendary = sword.copy(rarity = GearRarity.LEGENDARY)
        assertTrue(legendary.power > sword.power)
        val rolledLegend = (1..400).map { Armory.roll(40, Random(it)) }.filter { it.rarity == GearRarity.LEGENDARY }
        assertTrue(rolledLegend.isNotEmpty() && rolledLegend.all { it.effects.size == 3 })
        // Higher levels: better weapons.
        val low = (1..400).count { Armory.roll(2, Random(it)).rarity >= GearRarity.EPIC }
        val high = (1..400).count { Armory.roll(40, Random(it)).rarity >= GearRarity.EPIC }
        assertTrue(high > low)
        assertTrue(Armory.roll(40, rng).power > Armory.roll(2, rng).power)
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
            assertEquals(0, l1.update.state.parts.values.sum())
            assertTrue(l1.update.state.spellPages.isEmpty())
            assertNull(l1.boss)
            val l2 = openBox(BoxRarity.RARE, seed).update.state
            assertTrue(l2.parts.values.sum() > 0)
            assertTrue(l2.spellPages.isEmpty())
            val l3 = openBox(BoxRarity.EPIC, seed).update.state
            assertTrue(l3.parts.values.sum() > 0 && l3.spellPages.isNotEmpty())
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
        val s = started().copy(parts = mapOf(WeaponType.DAGGER to GameEngine.PARTS_TO_FORGE + 1, WeaponType.STAFF to 2))
        val u = GameEngine.forge(s, WeaponType.DAGGER, rng)
        assertEquals(1, u.state.parts[WeaponType.DAGGER])
        assertEquals(WeaponType.DAGGER, u.state.gear.single().type)
        assertEquals(u.state, GameEngine.forge(u.state, WeaponType.DAGGER, rng).state)
        assertEquals(u.state, GameEngine.forge(u.state, WeaponType.STAFF, rng).state)
    }

    @Test
    fun oldWeaponsConvert() {
        val old = started().copy(version = 2, weapons = listOf(Weapon.STORM_SPEAR), equippedWeapon = Weapon.ARCANE_STAFF, weaponParts = 3)
        val s = GameState.fromJson(old.toJson())
        assertEquals(WeaponType.BROADSWORD, s.gear.single().type)
        assertEquals(WeaponType.STAFF, s.equippedGear?.type)
        assertEquals(3, s.parts[WeaponType.SWORD])
        assertTrue(s.weapons.isEmpty())
    }

    @Test
    fun walkingRewardsAndHeals() {
        val m = Monster.create(Bestiary.get("emberkit"), 5, rng).copy(damage = 30)
        var s = started().copy(monsters = listOf(m), party = listOf(m.uid), heroDamage = 40)
        s = GameEngine.onStepCount(s, 1000, at(27)).state // baseline
        assertEquals(0L, s.stats.totalSteps)
        s = GameEngine.onStepCount(s, 1250, at(27)).state // +250 steps = 2 rewards, 50 banked
        assertEquals(20, s.hero.xp)
        assertEquals(20, s.heroDamage)
        assertEquals(10, s.monsters.single().damage)
        assertEquals(20, s.monsters.single().xp)
        assertEquals(50, s.stepBank)
        assertEquals(250, s.stepsToday)
        s = GameEngine.onStepCount(s, 1300, at(27)).state // banked 50 + 50 = another reward
        assertEquals(10, s.heroDamage)
        assertEquals(0, s.monsters.single().damage)
        assertEquals(0, s.stepBank)
    }

    @Test
    fun damageCarriesBetweenBattles() {
        val s = started()
        val enemy = Monster.create(Bestiary.get("zapmouse"), 6, rng)
        val b = GameEngine.newBattle(s, enemy, EncounterType.WILD, rng = rng)
        var guard = 0
        while (b.outcome == BattleOutcome.ONGOING && guard++ < 200) b.act(BattleAction.Attack)
        val after = GameEngine.applyBattleResult(s, b.result(), 0).state
        val hurt = after.heroDamage > 0 || after.monsters.any { it.damage > 0 || it.fainted }
        assertTrue(hurt)
        // The next battle starts with the same missing HP.
        val hero = Combatant.fromHero(after.hero, after.equippedGear, after.heroDamage)
        assertEquals(after.hero.maxHp - after.heroDamage, hero.hp)
        // Potions heal outside battle.
        val healed = GameEngine.usePotion(after.copy(heroDamage = 60), Item.POTION, Combatant.HERO_KEY).state
        assertTrue(healed.heroDamage < 60)
    }

    @Test
    fun villainsAndShadowCurse() {
        val s = started().copy(hero = Hero(level = 10), inventory = mapOf(Item.RESTORE_POTION to 1))
        val camps = Villains.near(s, 40.7, -74.0, at(27), utc)
        assertTrue(camps.isNotEmpty())
        val camp = camps.first()
        assertTrue(camp.team.all { it.cursed })
        // Same day, same camps; cursed monsters can't be tamed.
        assertEquals(camps.map { it.id }, Villains.near(s, 40.7, -74.0, at(27, 20), utc).map { it.id })
        val b = GameEngine.newBattle(s, camp.team.first(), EncounterType.VILLAIN, rng = rng)
        b.enemy.hp = 1
        assertNotNull(b.captureBlockedReason())
        // The curse: half Defense, +25% Attack.
        val clean = camp.team.first().copy(cursed = false)
        assertTrue(camp.team.first().stats.defense < clean.stats.defense)
        assertTrue(camp.team.first().stats.attack > clean.stats.attack)

        val won = Villains.reward(s, camp, at(27), rng, utc).state
        assertTrue(won.gold > s.gold)
        assertEquals(camp.team.size, won.villainSpoils.size)
        assertTrue(Villains.near(won, 40.7, -74.0, at(27), utc).none { it.id == camp.id })
        // Redeeming costs a potion; about 75% join.
        val joins = (1..200).count { Villains.redeem(won, 0, Random(it)).state.monsters.size == won.monsters.size + 1 }
        assertTrue(joins in 120..180, "about 75%: $joins")
        val redeemed = (1..50).map { Villains.redeem(won, 0, Random(it)).state }.first { it.monsters.size > won.monsters.size }
        assertEquals(0, redeemed.count(Item.RESTORE_POTION))
        assertTrue(redeemed.villainSpoils.isEmpty())
        val newcomer = redeemed.monsters.last()
        assertTrue(newcomer.cursed)

        // Fighting lifts the curse; then one stat can be perfected.
        var t = redeemed.copy(party = listOf(newcomer.uid))
        repeat(newcomer.cureNeeded) {
            val r = BattleResult(BattleOutcome.WON, EncounterType.WILD, clean, 0, 0, mapOf(newcomer.uid to 1), emptyMap(), emptyMap(), null, null)
            t = GameEngine.applyBattleResult(t, r, 0).state
        }
        val cured = t.monsters.last()
        assertTrue(!cured.cursed && cured.cureReady)
        val perfect = GameEngine.perfectStat(t, cured.uid, Stat.SPEED).state.monsters.last()
        assertEquals(MAX_IV, perfect.ivs.speed)
        assertTrue(!perfect.cureReady)
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

    @Test
    fun adventures() {
        val places = (1..60).map { World.shrineFromPlace("$it", "Place $it", 0.0, 0.0, ShrineTier.ITEM) }
        val few = started()
        // Locked until you have 5 monsters.
        val anyOffer = Adventures.offersNear(few, places, at(27), utc).first()
        assertTrue(Adventures.send(few, anyOffer, few.monsters.map { it.uid }, at(27)).state.adventures.isEmpty())

        val team = (1..6).map { Monster.create(Bestiary.get("ember_pup"), 15, Random(it)) }
        val s = started().copy(hero = Hero(level = 12), monsters = team)
        val offers = Adventures.offersNear(s, places, at(27), utc)
        assertTrue(offers.isNotEmpty() && offers.size <= 6)
        // Same day, same offers; a different day rotates them.
        assertEquals(offers, Adventures.offersNear(s, places, at(27, 20), utc))
        assertNotEquals(offers, Adventures.offersNear(s, places, at(28), utc))

        val adv = offers.first()
        val uids = team.take(adv.teamSize).map { it.uid }
        assertEquals(s, Adventures.send(s, adv, team.take(adv.teamSize + 1).map { it.uid }.take(if (adv.teamSize == 5) 4 else adv.teamSize + 1), at(27)).state)
        val sent = Adventures.send(s, adv, uids, at(27)).state
        assertEquals(1, sent.adventures.size)
        // Away monsters sit out battles and aren't offered for other adventures.
        assertTrue(Adventures.available(sent).none { it.uid in uids })
        val sentParty = sent.copy(party = uids.take(1))
        assertEquals(1, GameEngine.newBattle(sentParty, Monster.create(Bestiary.get("zapmouse"), 3, rng), EncounterType.WILD, rng = rng).allies.size)
        // The taken adventure isn't offered again.
        assertTrue(Adventures.offersNear(sent, places, at(27), utc).none { it.id == adv.id })
        // Can't claim early.
        assertEquals(sent, Adventures.claim(sent, adv.id, at(27), rng, utc).state)

        val done = Adventures.claim(sent, adv.id, at(27) + adv.durationMs, Random(1), utc)
        assertTrue(done.state.adventures.isEmpty())
        assertTrue(done.state.gold > sent.gold)
        // Hero earns no XP from adventures; the monsters do.
        assertEquals(sent.hero, done.state.hero)
        assertTrue(done.state.monsters.filter { it.uid in uids }.all { it.xp > 0 || it.level > 15 })
        assertTrue(adv.id in done.state.adventureLog)
    }

    @Test
    fun strongerTeamsSucceedMoreOften() {
        val adv = Adventure("x", "Park", "Test", 3, 20, 60_000)
        val weak = (1..3).map { Monster.create(Bestiary.get("ember_pup"), 10, Random(it)) }
        val strong = (1..3).map { Monster.create(Bestiary.get("ember_pup"), 30, Random(it)) }
        assertTrue(Adventures.successChance(strong, adv) > Adventures.successChance(weak, adv))
        assertTrue(Adventures.successChance(weak, adv) < 0.5)
    }

    @Test
    fun heroClasses() {
        val base = Hero(level = 4)
        assertEquals(base, GameEngine.chooseClass(started().copy(hero = base), HeroClass.WARRIOR).state.hero)
        var s = started().copy(hero = Hero(level = 5), gold = 1000)
        s = GameEngine.chooseClass(s, HeroClass.WARRIOR).state
        assertEquals(HeroClass.WARRIOR, s.hero.heroClass)
        assertEquals(1000, s.gold) // first pick is free
        s = GameEngine.chooseClass(s, HeroClass.MAGE).state
        assertEquals(1000 - CLASS_CHANGE_COST, s.gold)

        // Small at level 5, much bigger later.
        fun gap(level: Int, cls: HeroClass, pick: (Hero) -> Int): Int {
            val attrs = HeroAttribute.entries.associateWith { 20 }
            return pick(Hero(level = level, attributes = attrs, heroClass = cls)) - pick(Hero(level = level, attributes = attrs))
        }
        assertTrue(gap(5, HeroClass.WARRIOR) { it.attack } in 0..2)
        assertTrue(gap(30, HeroClass.WARRIOR) { it.attack } >= 15)
        assertTrue(gap(30, HeroClass.WARRIOR) { it.magic } < 0)
        assertTrue(gap(30, HeroClass.MAGE) { it.magic } > 0 && gap(30, HeroClass.MAGE) { it.defense } > 0)
        assertTrue(gap(30, HeroClass.MAGE) { it.attack } < 0)
        assertTrue(gap(30, HeroClass.ROGUE) { it.speed } > 0)
        assertEquals(0.0, Hero(level = 30).evasion)
        assertEquals(0.2, Hero(level = 30, heroClass = HeroClass.ROGUE).evasion, 0.001)
        assertTrue(Hero(level = 5, heroClass = HeroClass.ROGUE).evasion < 0.02)
    }

    @Test
    fun roguesDodge() {
        val rogue = Hero(level = 60, heroClass = HeroClass.ROGUE, attributes = HeroAttribute.entries.associateWith { 60 })
        val s = started().copy(hero = rogue, party = emptyList())
        var dodges = 0
        repeat(20) { seed ->
            val b = GameEngine.newBattle(s, Monster.create(Bestiary.get("zapmouse"), 30, Random(seed)), EncounterType.WILD, rng = Random(seed))
            repeat(10) { if (b.outcome == BattleOutcome.ONGOING) b.act(BattleAction.Defend) }
            dodges += b.log.count { it.contains("nimbly dodges") }
        }
        assertTrue(dodges > 0)
    }

    @Test
    fun adventurerCanBeChosenAndLevelFiveGivesRareWeapon() {
        val s = started().copy(hero = Hero(level = 5))
        val u = GameEngine.chooseClass(s, HeroClass.NONE, Random(1))
        assertTrue(u.state.hero.classChosen)
        assertEquals(HeroClass.NONE, u.state.hero.heroClass)
        assertEquals(GearRarity.RARE, u.state.gear.single().rarity)
        // Switching later costs gold and gives no second weapon.
        val again = GameEngine.chooseClass(u.state.copy(gold = 1000), HeroClass.MAGE, Random(2)).state
        assertEquals(HeroClass.MAGE, again.hero.heroClass)
        assertEquals(1, again.gear.size)
        assertEquals(1000 - CLASS_CHANGE_COST, again.gold)
    }

    @Test
    fun armorAddsDefenseAndShowsInBattle() {
        val plate = Armor("a", ArmorType.PLATE, GearRarity.EPIC, 10)
        val s = started().copy(armor = listOf(plate))
        assertEquals(s.bagCount, s.inventory.values.sum() + 1)
        val worn = GameEngine.equipArmor(s, "a").state
        assertEquals(plate, worn.equippedArmor)
        assertTrue(worn.armor.isEmpty())
        val bare = Combatant.fromHero(s.hero)
        val armored = Combatant.fromHero(s.hero, armor = plate)
        assertTrue(armored.defense > bare.defense)
        assertTrue(armored.speed <= bare.speed)
        assertTrue(Armor("r", ArmorType.ROBE, GearRarity.RARE, 10).magicBonus(HeroClass.MAGE) > 0)
    }

    @Test
    fun journalTracksSeenAndCaught() {
        val s = started()
        assertTrue("emberkit" in s.caught && "emberkit" in s.seen)
        val seen = GameEngine.markSeen(s, "zapmouse")
        assertTrue("zapmouse" in seen.seen && "zapmouse" !in seen.caught)
        val loaded = GameState.fromJson(s.copy(caught = emptySet(), seen = emptySet()).toJson())
        assertTrue("emberkit" in loaded.caught)
    }

    @Test
    fun lureIsFreeDailyAndBoostsSpawns() {
        val now = 1_700_000_000_000L
        val s = started()
        val claimed = GameEngine.claimDailyLure(s, now)!!.state
        assertEquals(1, claimed.count(Item.LURE))
        assertNull(GameEngine.claimDailyLure(claimed, now + 1000))
        val lured = GameEngine.useLure(claimed, now).state
        assertTrue(lured.lureActive(now + 29 * 60_000L))
        assertTrue(!lured.lureActive(now + 31 * 60_000L))

        fun count(c: SpawnConditions) = (0 until 40).sumOf { i ->
            World.spawnsNear(40.0 + i * 0.05, -75.0, now + i * World.SPAWN_WINDOW_MS, 10, c).size
        }
        val still = count(SpawnConditions(12))
        val moving = count(SpawnConditions(12, activity = 1.0))
        val withLure = count(SpawnConditions(12, lure = true))
        assertTrue(moving > still, "moving $moving vs still $still")
        assertTrue(withLure > still, "lure $withLure vs still $still")
    }

    @Test
    fun alwaysSomethingNearbyWhenStandingStill() {
        val now = 1_700_000_000_000L
        // Over an hour at one spot, monsters keep wandering up within battle range.
        val inRange = (0 until 12).flatMap { i ->
            World.spawnsNear(40.0, -75.0, now + i * World.NEARBY_WINDOW_MS, 10).filter {
                World.distanceMeters(40.0, -75.0, it.lat, it.lon) <= World.SPAWN_RANGE_M
            }.map { it.id }
        }.toSet()
        assertTrue(inRange.size >= 5, "only ${inRange.size}")
    }

    @Test
    fun nightFavorsCorruptAndGas() {
        val rng = Random(9)
        val night = (1..2000).count { Bestiary.randomWild(rng, 50, TimeOfDay.NIGHT.favored).element in TimeOfDay.NIGHT.favored }
        val day = (1..2000).count { Bestiary.randomWild(rng, 50, TimeOfDay.DAY.favored).element in TimeOfDay.NIGHT.favored }
        assertTrue(night > day * 1.5)
        assertEquals(TimeOfDay.NIGHT, TimeOfDay.of(2))
        assertEquals(TimeOfDay.DAY, TimeOfDay.of(13))
    }

    @Test
    fun walkingTracksHistoryAndActivity() {
        val now = 1_700_000_000_000L
        val s = GameEngine.walk(started(), 1500, now).state
        assertEquals(1500, s.stepHistory.values.single())
        assertEquals(1.0, GameEngine.activityLevel(s, now), 0.01)
        assertEquals(0.5, GameEngine.activityLevel(s, now + 15 * 60_000L), 0.01)
        val withTask = GameEngine.addTask(s, "x", Difficulty.EASY, rng).state
        val done = GameEngine.completeTask(withTask, withTask.tasks.last().id, now).state
        assertEquals(mapOf(GameEngine.epochDay(now) to 1), done.questDays)
    }

    @Test
    fun releasingGivesCandyThatLevelsUpWithoutChangingStatPoints() {
        val s0 = started()
        val kit = s0.monsters.single()
        val extra = Monster.create(Bestiary.get("blazecat"), 20, Random(5))
        val s = s0.copy(monsters = s0.monsters + extra)
        val released = GameEngine.release(s, extra.uid).state
        val family = kit.species.family
        assertEquals(2 + 20 / 15, released.candy[family])
        assertEquals(0, released.bagCount - s0.bagCount)
        // Candy is shared by the whole line and cost climbs with level.
        assertTrue(GameEngine.candyCost(30) > GameEngine.candyCost(5))
        val leveled = GameEngine.candyLevelUp(released, kit.uid).state
        val after = leveled.monsters.single()
        assertEquals(kit.level + 1, after.level)
        assertEquals(kit.ivs, after.ivs)
        assertEquals(released.candy[family]!! - GameEngine.candyCost(kit.level), leveled.candy[family])
        // Not enough candy: nothing happens.
        val broke = leveled.copy(candy = emptyMap())
        assertEquals(broke, GameEngine.candyLevelUp(broke, kit.uid).state)
        // Evolving through candy keeps stat points too.
        var m = released.copy(candy = mapOf(family to 999))
        repeat(15) { m = GameEngine.candyLevelUp(m, kit.uid).state }
        assertEquals("blazecat", m.monsters.single().speciesId)
        assertEquals(kit.ivs, m.monsters.single().ivs)
    }
}
