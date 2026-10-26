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
}
