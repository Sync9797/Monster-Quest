package com.monsterquest.core

import java.time.ZoneOffset
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WanderersTest {
    private val utc = ZoneOffset.UTC
    private val now = 1_800_000_000_000L
    private fun start(): GameState = GameEngine.chooseStarter(GameState(), "voltfawn", Random(1)).state

    @Test
    fun starsAndFinalForms() {
        assertEquals("solar_tiger", Scouting.finalForm(Bestiary.get("emberkit")).id)
        // A line's stars come from its final form, so every stage shares them.
        val line = Bestiary.lineOf(Bestiary.get("emberkit"))
        assertEquals(1, line.map { Scouting.stars(it) }.distinct().size)
        assertEquals(5, Scouting.stars(Bestiary.get("thunder_titan")))
        assertTrue(Bestiary.all.all { Scouting.stars(it) in 1..5 })
        assertEquals("★★★☆☆", Scouting.starText(3))
    }

    @Test
    fun wantedListMarksTheWholeLine() {
        var s = start()
        s = Wanted.toggle(s, "flarepanther").state
        assertTrue(Wanted.matches(s, Bestiary.get("emberkit")))
        assertTrue(!Wanted.matches(s, Bestiary.get("tidefin")))
        assertTrue(Wanted.boostedBy(s, LiveWeather.HOT).isNotEmpty())
        s = Wanted.toggle(s, "flarepanther").state
        assertTrue(s.wanted.isEmpty())
        val full = s.copy(wanted = Bestiary.wild.take(Wanted.MAX).map { it.id }.toSet())
        assertEquals(full, Wanted.toggle(full, "tidefin").state)
    }

    @Test
    fun mysteryOfTheWeek() {
        var s = Mystery.rollover(start(), 20_000)
        val sp = Mystery.species(s)!!
        assertTrue(!sp.genderless && sp.rarity >= Rarity.UNCOMMON)
        // Same week: the same mystery; a new week rolls a new one.
        assertEquals(s, Mystery.rollover(s, 20_001))
        assertEquals(Mystery.SPAWN_BOOST, Mystery.boosts(s)[sp.id])
        assertTrue(Mystery.hints(sp).isNotEmpty())
        assertEquals(s, Mystery.claim(s).state)
        val caught = Monster.create(sp, 10, Random(2))
        s = Mystery.track(s, s.copy(monsters = s.monsters + caught))
        assertTrue(s.mysteryFound)
        val u = Mystery.claim(s)
        assertEquals(s.gold + Mystery.GOLD, u.state.gold)
        assertEquals(u.state, Mystery.claim(u.state).state)
    }

    @Test
    fun villainsFieldEvolvedLines() {
        val sp = Bestiary.evolvedFor(Bestiary.get("emberkit"), 60)
        assertEquals("solar_tiger", sp.id)
        assertEquals("flarepanther", Bestiary.evolvedFor(Bestiary.get("emberkit"), Bestiary.get("flarepanther").evolveLevel!! - 1).id)
        assertEquals("emberkit", Bestiary.evolvedFor(Bestiary.get("flarepanther"), 2).id)
    }

    @Test
    fun tradersSellOnceEach() {
        var s = start().copy(gold = 100_000)
        val t = (0 until 50).asSequence().flatMap { Wanderers.tradersNear(s, 40.0 + it * 0.05, -74.0, now, utc) }.first()
        assertTrue(t.offers.any { it.gear != null } && t.offers.any { it.armor != null } && t.offers.any { it.item != null })
        val item = t.offers.first { it.item != null }
        assertTrue(item.discountPct in 20..35 && item.price < item.item!!.price * item.qty)
        val bought = Wanderers.buy(s, t, item.index, now, utc)
        assertEquals(s.gold - item.price, bought.state.gold)
        assertEquals(bought.state, Wanderers.buy(bought.state, t, item.index, now, utc).state)
        val gear = t.offers.first { it.gear != null }
        s = Wanderers.buy(bought.state, t, gear.index, now, utc).state
        assertTrue(s.gear.any { it.id == gear.gear!!.id })
    }

    @Test
    fun nomadsFightThreeOnThreeWithTwoSwapsEach() {
        var s = start()
        // A strong team of five so there are spare monsters to swap.
        val mine = (1..5).map { Monster.create(Bestiary.get("flarepanther"), 60, Random(it)) }
        s = s.copy(monsters = mine, party = mine.map { it.uid }, swaps = emptyList(), hero = s.hero.copy(level = 60))
        val nomad = (0 until 50).asSequence().flatMap { Wanderers.nomadsNear(s, 40.0 + it * 0.05, -74.0, now, utc) }.first()
        assertEquals(Wanderers.NOMAD_ACTIVE + Wanderers.NOMAD_SWAPS, nomad.team.size)
        // Weak foes so the fight resolves.
        val weak = nomad.copy(team = (1..4).map { Monster.create(Bestiary.get("zapmouse"), 2, Random(it)) }, level = 2)
        val b = GameEngine.newBattle(
            s, weak.active.first(), EncounterType.NOMAD, rng = Random(3), pack = weak.active.drop(1), thug = weak.fighter,
            enemyReserve = weak.reserve, swapLimit = Wanderers.NOMAD_SWAPS, trainer = weak.name,
        )
        // The nomad and two monsters against the hero and two of yours.
        assertEquals(3, b.enemies.size)
        assertEquals(weak.name, b.enemies.last().name)
        assertEquals(3, b.allies.size)
        assertEquals(2, b.swapsWaiting.size)
        assertEquals(2, b.enemyReserveLeft)
        assertTrue(b.captureLocked)
        var guard = 0
        while (b.outcome == BattleOutcome.ONGOING && guard++ < 400) b.act(BattleAction.Attack)
        assertEquals(BattleOutcome.WON, b.outcome)
        assertEquals(0, b.enemyReserveLeft)
        assertTrue(b.log.any { "sends out" in it })
        assertEquals(4, b.result().defeated.size)
        val r = Wanderers.nomadReward(s, weak, now, Random(4), utc)
        assertEquals(s.gold + weak.level * Wanderers.NOMAD_GOLD_PER_LEVEL, r.state.gold)
        assertEquals(1, r.state.eggs.size)
        assertTrue(MONSTER_STATS.count { r.state.eggs.single().ivs[it] == MAX_IV } >= Wanderers.NOMAD_EGG_PERFECT)
        assertTrue(Wanderers.nomadsNear(r.state, 0.0, 0.0, now, utc).none { it.id == weak.id })
        assertTrue(weak.id in r.state.nomadsBeaten)
    }
}
