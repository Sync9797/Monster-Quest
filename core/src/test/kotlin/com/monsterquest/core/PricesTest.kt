package com.monsterquest.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PricesTest {
    @Test
    fun repeatBuysMarkUpThenCoolOff() {
        val now = 1_800_000_000_000L
        var s = GameEngine.chooseStarter(GameState(), "voltfawn", Random(1)).state.copy(gold = 100_000, bagCapacity = 500)
        val p = Item.POTION.price
        // The first few at the usual price.
        repeat(Prices.FREE_UNITS) {
            assertEquals(p, Prices.unitPrice(s, Item.POTION, now))
            s = GameEngine.buy(s, Item.POTION, nowMs = now).state
        }
        // Then each one costs more.
        val next = Prices.unitPrice(s, Item.POTION, now)
        assertTrue(next > p, "$next")
        val gold = s.gold
        s = GameEngine.buy(s, Item.POTION, nowMs = now).state
        assertEquals(gold - next, s.gold)
        assertTrue(Prices.unitPrice(s, Item.POTION, now) > next)
        // Other items are unaffected.
        assertEquals(Item.HI_POTION.price, Prices.unitPrice(s, Item.HI_POTION, now))
        // Capped.
        repeat(40) { s = GameEngine.buy(s, Item.POTION, nowMs = now).state }
        assertEquals(Math.round(p * Prices.MAX_MULTIPLIER).toInt(), Prices.unitPrice(s, Item.POTION, now))
        // A few hours later it has eased, and the next day it's back to normal.
        assertTrue(Prices.unitPrice(s, Item.POTION, now + 30 * Prices.COOL_MS) < Prices.unitPrice(s, Item.POTION, now))
        assertEquals(p, Prices.unitPrice(s, Item.POTION, now + 24 * 60 * 60_000L))
    }
}
