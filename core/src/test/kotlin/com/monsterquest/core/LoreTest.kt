package com.monsterquest.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoreTest {
    @Test
    fun everySpeciesHasLoreAndMaxStats() {
        assertEquals(emptyList(), Lore.missing())
        assertTrue(Bestiary.all.all { Lore.of(it).length in 20..220 })
        // Max stats grow along an evolution line, and legendaries top the list.
        val line = Bestiary.lineOf(Bestiary.get("emberkit"))
        assertTrue(line.zipWithNext().all { (a, b) -> MaxStats.total(b) > MaxStats.total(a) })
        val top = Bestiary.all.minBy { MaxStats.rank(it) }
        assertTrue(top.rarity == Rarity.LEGENDARY || top.rarity == Rarity.EPIC, top.name)
        assertEquals(setOf(Stat.HP, Stat.ATTACK, Stat.DEFENSE, Stat.SPEED), MaxStats.of(top).keys)
    }
}
