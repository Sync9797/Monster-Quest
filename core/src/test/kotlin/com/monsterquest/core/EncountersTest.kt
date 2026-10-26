package com.monsterquest.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EncountersTest {
    private val rng = Random(11)

    private fun withMonsters(n: Int, level: Int = 10): GameState {
        val ms = List(n) { Monster.create(Bestiary.get("emberkit"), level, Random(it)) }
        return GameState(hero = Hero(level = level), monsters = ms, party = ms.take(MAX_PARTY_SIZE).map { it.uid }, starterChosen = true)
    }

    @Test
    fun everyTypeHasAGuardMove() {
        for (type in Element.typed) {
            val guards = Moves.learnsets.getValue(type).map { Moves.get(it.second) }.filter { MoveEffect.Protect in it.effects && it.type == type }
            assertEquals(1, guards.size, "$type should learn one guard move")
        }
        // Every monster has its guard by level 12.
        assertTrue(Bestiary.all.all { sp -> Moves.learned(sp, 12).any { MoveEffect.Protect in it.effects } })
    }

    @Test
    fun batterySaverMakesSurpriseAttacksLikelier() {
        fun hits(saver: Boolean): Int {
            val r = Random(5)
            return (1..2000).count { SecretEncounters.roll(0, SecretEncounters.STEPS, r, saver).second }
        }
        assertTrue(hits(true) > hits(false) * 1.3)
    }

    @Test
    fun thugsMatchYourTeamAndFightAlongside() {
        val s = withMonsters(4)
        val count = GameEngine.fightingCount(s)
        assertEquals(4, count)
        val (thug, crew) = Villains.ambusher(s.hero.level, count, rng)
        assertEquals(4, crew.size)
        val b = GameEngine.newBattle(s, crew.first(), EncounterType.THUG, rng = Random(2), pack = crew.drop(1), thug = thug)
        // Four monsters plus the thug against your hero plus four monsters.
        assertEquals(5, b.enemies.size)
        assertEquals(b.allies.size, b.enemies.size)
        assertTrue(b.enemies.last().thug)
        assertNotNull(b.captureBlockedReason())
        assertFalse(b.isBoss)
        // No extra scaling: the crew fights at its own strength.
        assertEquals(Combatant.fromMonster(crew[1], "x").maxHp, b.enemies[1].maxHp)
        // Targeting the thug doesn't break the taming odds shown in the menu.
        b.selectTarget(b.enemies.last().key)
        b.captureOdds(Item.TAMING_ORB)
        assertTrue(b.log.first().contains(thug.name))
    }

    @Test
    fun swapSlotsSendMonstersInMidBattle() {
        var s = withMonsters(7)
        val extra = s.monsters.drop(5)
        s = GameEngine.setSwapSlot(s, extra[0].uid, 0).state
        s = GameEngine.setSwapSlot(s, extra[1].uid, 1).state
        assertEquals(extra.map { it.uid }, s.swaps)
        // Swap-slot monsters stay home from adventures.
        assertTrue(Adventures.available(s).none { it.uid in s.swaps })

        val foe = Monster.create(Bestiary.get("emberkit"), 10, rng)
        val b = GameEngine.newBattle(s, foe, EncounterType.WILD, rng = Random(4))
        assertEquals(6, b.allies.size)
        assertEquals(2, b.swapsWaiting.size)
        // Swap in on the first ally turn, in place of a party monster.
        val out = b.allies[1].key
        b.act(BattleAction.Swap(extra[0].uid, out))
        assertTrue(b.allies.any { it.key == extra[0].uid })
        assertTrue(b.swapsWaiting.any { it.key == out })
        assertTrue(b.log.any { "Go, " in it })

        // Win: the one who stepped in gets full XP, the one who waited gets half.
        b.enemies.forEach { it.hp = 0 }
        while (b.outcome == BattleOutcome.ONGOING && b.current != null) b.act(BattleAction.Defend)
        val r = b.result()
        val full = r.monsterXp.getValue(extra[0].uid)
        assertEquals(full / 2, r.monsterXp.getValue(extra[1].uid))
    }

    @Test
    fun movingSwapAndPartyMonstersAround() {
        var s = withMonsters(7)
        val a = s.monsters[5].uid
        val p0 = s.party[0]
        s = GameEngine.setSwapSlot(s, a, 0).state
        // Drag the swap monster onto party slot 0: they trade places.
        s = GameEngine.setPartySlot(s, a, 0).state
        assertEquals(a, s.party[0])
        assertEquals(listOf(p0), s.swaps)
        // Toggling takes it out of the swap slot.
        s = GameEngine.toggleParty(s, p0).state
        assertTrue(s.swaps.isEmpty())
        // Releasing clears it everywhere.
        s = GameEngine.setSwapSlot(s, p0, 0).state
        s = GameEngine.release(s, p0).state
        assertTrue(p0 !in s.swaps)
    }

    @Test
    fun speedGuardPausesSpawnsAboveFifteenMph() {
        var g = SpeedGuard.State()
        g = SpeedGuard.update(g, 40.0, -74.0, 0)
        // ~30 mph by GPS.
        g = SpeedGuard.update(g, 40.001, -74.0, 5_000, gpsSpeedMps = 13.4f)
        assertTrue(g.tooFast)
        assertTrue(g.justSpedUp)
        assertTrue(SpeedGuard.paused(g, 5_000))
        g = SpeedGuard.update(g, 40.002, -74.0, 10_000, gpsSpeedMps = 13.4f)
        assertFalse(g.justSpedUp)
        // Walking pace again: paused for another minute, then spawns return.
        g = SpeedGuard.update(g, 40.002, -74.0, 20_000, gpsSpeedMps = 1.4f)
        assertTrue(SpeedGuard.paused(g, 20_000))
        g = SpeedGuard.update(g, 40.0021, -74.0, 20_000 + SpeedGuard.RESUME_MS, gpsSpeedMps = 1.4f)
        assertFalse(SpeedGuard.paused(g, 20_000 + SpeedGuard.RESUME_MS))

        // Without GPS speed it's worked out from the distance: 100 m in 5 s is ~45 mph.
        var h = SpeedGuard.update(SpeedGuard.State(), 40.0, -74.0, 0)
        h = SpeedGuard.update(h, 40.0009, -74.0, 5_000)
        assertTrue(h.tooFast)
        // Standing still sends no fixes: spawns come back after a few minutes anyway.
        assertFalse(SpeedGuard.paused(h, 5_000 + SpeedGuard.STILL_MS))
        // Walking (5 m in 4 s) never trips it.
        var w = SpeedGuard.update(SpeedGuard.State(), 40.0, -74.0, 0)
        w = SpeedGuard.update(w, 40.000045, -74.0, 4_000)
        assertFalse(w.tooFast)
    }

    @Test
    fun smackDownGroundsFliersAndMostMonstersLearnIt() {
        val sd = Moves.get("smack_down")
        assertEquals(Element.EARTH, sd.type)
        assertEquals(65, sd.power)
        // Most monsters learn it; fish, slimes, wisps and the fairy don't.
        assertTrue(Moves.learned(Bestiary.get("mosscub"), 20).contains(sd))
        assertTrue(Moves.learned(Bestiary.get("cindrake"), 24).contains(sd))
        for (id in listOf("bubblefin", "goopling", "gloomwisp", "prism_fairy")) assertFalse(Moves.learned(Bestiary.get(id), 100).contains(sd), id)
        val learners = Bestiary.all.count { Moves.canLearnSmackDown(it) }
        assertTrue(learners > Bestiary.all.size * 3 / 4)

        // Against a levitating bird: it connects, and afterwards Earth moves land.
        val bird = Monster.create(Bestiary.get("tempest_roc"), 40, rng)
        val bear = Monster.create(Bestiary.get("terraursa"), 40, rng).copy(moves = listOf("smack_down"))
        val b = Battle(Hero(level = 40), listOf(bear), bird, EncounterType.WILD, emptyMap(), false, rng = Random(12))
        assertTrue(b.enemy.immuneEarth)
        var tries = 0
        while (b.enemy.immuneEarth && b.outcome == BattleOutcome.ONGOING && tries++ < 20) {
            val c = b.current ?: break
            if (c.key == bear.uid) b.act(BattleAction.UseMove("smack_down")) else b.act(BattleAction.Defend)
        }
        assertFalse(b.enemy.immuneEarth)
        assertTrue(b.enemy.grounded)
        assertTrue(b.log.any { "knocked out of the air" in it })
    }

    @Test
    fun maxedHoverLiftsTheTeamUntilTheHeroIsHit() {
        val s = withMonsters(2, level = 70)
        val hero = s.hero.copy(level = 70, skills = listOf(Trait.HOVER))
        assertEquals(5, hero.rankOf(Trait.HOVER))
        val foe = Monster.create(Bestiary.get("goopling"), 70, rng) // no Smack Down: only a hit on the hero ends the hover
        val b = GameEngine.newBattle(s.copy(hero = hero), foe, EncounterType.WILD, rng = Random(3))
        // Everyone hovers (unless a faster foe already hit the hero before your first turn).
        assertTrue(b.allies.all { it.immuneEarth } || b.log.any { "hover breaks" in it })
        assertTrue(b.allies.first().immuneEarth)
        // Rank 4 only softens Earth damage for the team.
        val r4 = GameEngine.newBattle(s.copy(hero = hero.copy(level = 69)), foe, EncounterType.WILD, rng = Random(3))
        assertFalse(r4.allies[1].immuneEarth)
        assertEquals(50, r4.allies[1].earthResistPct)
        // Fight until the hero takes a hit: the team drops, the hero keeps floating.
        var tries = 0
        while (b.allies.drop(1).any { it.immuneEarth } && b.outcome == BattleOutcome.ONGOING && tries++ < 60) b.act(BattleAction.Defend)
        if (b.outcome == BattleOutcome.ONGOING) {
            assertTrue(b.allies.first().immuneEarth)
            assertTrue(b.allies.drop(1).none { it.immuneEarth })
            assertTrue(b.log.any { "hover breaks" in it })
        }
    }

    @Test
    fun energyCarriesOverAndRecoversWithTimeAndWalking() {
        var s = withMonsters(1).copy(inventory = mapOf(Item.ETHER to 1))
        val max = s.hero.maxMp
        // Cast a spell, then win: the Energy spent stays spent.
        val foe = Monster.create(Bestiary.get("emberkit"), 3, rng)
        val b = GameEngine.newBattle(s, foe, EncounterType.WILD, rng = Random(1))
        while (b.current?.isHero != true && b.outcome == BattleOutcome.ONGOING) b.act(BattleAction.Defend)
        b.act(BattleAction.CastSpell("ember_bolt"))
        b.enemies.forEach { it.hp = 0 }
        while (b.outcome == BattleOutcome.ONGOING && b.current != null) b.act(BattleAction.Defend)
        s = GameEngine.applyBattleResult(s, b.result(), 0).state
        assertTrue(s.heroEnergyUsed > 0)
        val used = s.heroEnergyUsed
        // The next fight starts with that much less.
        val next = GameEngine.newBattle(s, foe, EncounterType.WILD, rng = Random(1))
        assertEquals(max - used, next.allies.first().mp)

        // Over time: all of it back in 6 hours (a little at a time).
        var t = GameEngine.regenEnergy(s.copy(energyRegenMs = 1_000), 1_000)
        t = GameEngine.regenEnergy(t, 1_000 + GameEngine.ENERGY_FULL_MS / max * 2)
        assertEquals(used - 2, t.heroEnergyUsed)
        t = GameEngine.regenEnergy(t, 1_000 + GameEngine.ENERGY_FULL_MS)
        assertEquals(0, t.heroEnergyUsed)

        // Walking is faster: 2 Energy every 100 steps.
        val walked = GameEngine.walk(s, 100).state
        assertEquals(used - GameEngine.ENERGY_PER_REWARD, walked.heroEnergyUsed)

        // An Ether works outside battle too.
        val ether = GameEngine.usePotion(s, Item.ETHER, Combatant.HERO_KEY).state
        assertEquals(0, ether.heroEnergyUsed)
        assertEquals(0, ether.count(Item.ETHER))
    }

    @Test
    fun thugsFightTheirOwnWay() {
        val brute = Combatant.fromThug(Thug("Brick", "Rats", 20, HeroClass.WARRIOR), "e")
        val hexer = Combatant.fromThug(Thug("Vex", "Rats", 20, HeroClass.MAGE), "e")
        val cut = Combatant.fromThug(Thug("Sly", "Rats", 20, HeroClass.ROGUE), "e")
        assertTrue(brute.maxHp > cut.maxHp && cut.maxHp > hexer.maxHp)
        assertTrue(hexer.speed > brute.speed && cut.speed > hexer.speed)
        assertEquals(Element.CORRUPT, hexer.element)
        assertTrue(cut.evasion > 0)
        assertEquals("Brick the Brute", brute.name)
        assertEquals(HeroClass.MAGE, hexer.heroClass)
    }

    @Test
    fun mudShotStacksAccuracyDownAndRanksUp() {
        val mud = Spells.byId(Spells.MUD_SHOT)!!
        assertEquals(Element.EARTH, mud.element)
        // Weak: well under a bolt, and it ranks up like the others.
        assertTrue(mud.power < Spells.byId("stone_bolt")!!.power)
        assertTrue(Spells.ranked(mud, 5, 25).power > Spells.ranked(mud, 1, 25).power)
        val hero = Hero(level = 20, knownSpells = Spells.starting + Spells.MUD_SHOT, attributes = HeroAttribute.entries.associateWith { 30 })
        val foe = Monster.create(Bestiary.get("goopling"), 30, rng)
        val b = Battle(hero, emptyList(), foe, EncounterType.WILD, emptyMap(), false, rng = Random(4))
        var casts = 0
        while (b.outcome == BattleOutcome.ONGOING && casts < 8) {
            val c = b.current ?: break
            c.mp = c.maxMp
            c.hp = c.maxHp
            b.enemy.hp = b.enemy.maxHp
            b.act(BattleAction.CastSpell(Spells.MUD_SHOT))
            casts++
        }
        val muddied = b.enemy.muddiedPct
        assertTrue(muddied >= 20, "stacks: $muddied")
        assertTrue(muddied <= Spells.MUD_SHOT_ACCURACY * Spells.MUD_SHOT_STACKS)
        assertTrue(b.enemy.tags().any { "Acc" in it })
    }
}
