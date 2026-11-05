package com.monsterquest.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VisitorsTest {
    private val base = GameEngine.chooseStarter(GameState(), "emberkit", Random(1)).state.copy(hero = Hero(level = 40))

    @Test
    fun aTwoHourSessionSendsARareVisitor() {
        val started = GameEngine.startFocus(base, 120, 0, Random(1)).state
        val done = GameEngine.resolveFocus(started, success = true, rng = Random(2))
        val v = done.state.visitors.single()
        assertTrue(v.species.rarity >= Rarity.RARE)
        assertTrue("battery saver" in done.message, done.message)
        // A shorter session doesn't.
        val short = GameEngine.resolveFocus(GameEngine.startFocus(base, 90, 0, Random(1)).state, success = true, rng = Random(2))
        assertTrue(short.state.visitors.isEmpty())
    }

    @Test
    fun theVisitorIsTheNextWalkingEncounterAndWaitsTwoHours() {
        val v = GameEngine.rareVisitor(base, 1_000L, Random(3), Element.WATER)!!
        assertTrue(Element.WATER in v.species.types)
        val s = GameEngine.addVisitor(base, v, 1_000L)
        val (after, m) = assertNotNull(GameEngine.takeVisitor(s, 1_000L + 60_000L, Random(4)))
        assertEquals(v.speciesId, m.speciesId)
        assertEquals(v.level, m.level)
        assertTrue(after.visitors.isEmpty())
        // Too late: it wandered off.
        assertNull(GameEngine.takeVisitor(s, 1_000L + GameEngine.VISITOR_TTL_MS, Random(4)))
    }

    @Test
    fun theWeatherShiftsWhoFindsYouOnAWalk() {
        fun share(types: Map<Element, Double>, e: Element) = (1..3000).count { i ->
            SecretEncounters.encounter(30, 12, Random(i), typeBoosts = types).first().species.types.contains(e)
        }
        val storm = WeatherEvent.encounterBoosts(LiveWeather.STORM)
        assertTrue(share(storm, Element.ELECTRIC) > share(emptyMap(), Element.ELECTRIC) * 1.5)
        assertTrue(share(storm, Element.FIRE) < share(emptyMap(), Element.FIRE))
    }

    @Test
    fun heldLuresTurnIntoGold() {
        val old = base.copy(version = 11, gold = 100, inventory = mapOf(Item.LURE to 3, Item.POTION to 2))
        val s = old.migrated()
        assertEquals(12, s.version)
        assertEquals(100 + 3 * RETIRED_LURE_GOLD, s.gold)
        assertEquals(0, s.count(Item.LURE))
        assertEquals(2, s.count(Item.POTION))
        // Lures are no longer sold or handed out.
        assertEquals(0, Item.LURE.price)
    }
}
