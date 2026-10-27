package com.monsterquest.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BestiaryTest {
    private val newLines = listOf(
        "Cinderskink line", "Cinderlamb line", "Joltlet line", "Zaplume line", "Puddlet line", "Pincerling line",
        "Hootling line", "Pterix line", "Sporelet line", "Stinkit line", "Hexling line", "Nightling line",
        "Rollpip line", "Stubhorn line",
    )

    @Test
    fun fiveNewMonstersPerTypeAndNoneLegendary() {
        val added = Bestiary.all.filter { it.family in newLines }
        assertEquals(35, added.size)
        added.groupBy { it.element }.forEach { (e, sp) -> assertEquals(5, sp.size, "$e") }
        assertEquals(7, added.map { it.element }.toSet().size)
        assertTrue(added.none { it.rarity == Rarity.LEGENDARY || it.bossOnly })
        // Each type: a three-stage line and a two-stage line, and they all evolve.
        added.groupBy { it.family }.values.forEach { line ->
            assertTrue(line.size in 2..3)
            val sorted = line.sortedBy { it.stage }
            sorted.dropLast(1).forEach { assertTrue(it.evolvesTo != null && it.evolveLevel != null) }
            assertTrue(sorted.zipWithNext().all { (a, b) -> b.base.hp > a.base.hp })
        }
        // Every new line has its own level-30 skill.
        added.forEach { assertTrue(MonsterTraits.of(it) != Trait.SECOND_WIND, it.family) }
        // They show up in the wild.
        assertTrue(Bestiary.wild.containsAll(added))
        assertEquals(Bestiary.all.size, Bestiary.all.map { it.id }.toSet().size)
    }

    @Test
    fun mythicsThenLegendariesAreNumberedLast() {
        val n = Bestiary.all.size
        val special = Bestiary.all.filter { it.rarity == Rarity.LEGENDARY || it.id == "mimic" || it.id == "prism_fairy" }
        assertEquals(9, special.size)
        assertEquals((n - 8..n).toList(), special.map { Bestiary.number(it) }.sorted())
        // Mythics before legendaries.
        assertTrue(Bestiary.number(Bestiary.get("prism_fairy")) < special.filter { it.rarity == Rarity.LEGENDARY }.minOf { Bestiary.number(it) })
        assertEquals(1, Bestiary.number(Bestiary.get("voltfawn")))
        assertTrue(Bestiary.number(Bestiary.get("stubhorn")) < Bestiary.number(Bestiary.get("mimic")))
    }
}
