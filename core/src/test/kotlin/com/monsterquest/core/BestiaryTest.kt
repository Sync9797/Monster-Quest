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

class DualTypeTest {
    @Test
    fun finalFormsAndStartersGainSecondTypes() {
        val duals = Bestiary.all.filter { it.secondary != null }
        assertTrue(duals.size >= 10)
        // Only final evolutions (or single forms), never legendaries, and the second type differs.
        assertTrue(duals.all { it.stage == it.stages && it.rarity != Rarity.LEGENDARY && it.secondary != it.element })
        // Every starter's final form is dual-typed; earlier forms aren't.
        GameEngine.starters.forEach { id ->
            val line = Bestiary.lineOf(Bestiary.get(id))
            assertTrue(line.last().secondary != null, line.last().name)
            assertTrue(line.dropLast(1).all { it.secondary == null })
        }
        val bird = Bestiary.get("thunderbird")
        assertEquals(listOf(Element.ELECTRIC, Element.FLIGHT), bird.types)
        assertEquals("Electric / Flight", bird.typeLabel)
    }

    @Test
    fun bothTypesCountInBattle() {
        // Two weaknesses stack; a weakness and a resistance cancel out.
        val horn = Bestiary.get("thunderhorn") // Electric / Earth: Fire beats both
        assertEquals(2.25, Element.FIRE.multiplierAgainst(horn.types), 1e-9)
        assertTrue(Element.FIRE in horn.weakTo)
        assertTrue(Element.WATER.multiplierAgainst(horn.types) < 1.5)
        // Same-type bonus and moves from the second type too.
        val bird = Bestiary.get("thunderbird")
        assertTrue(Moves.learned(bird, 60).any { it.type == Element.FLIGHT && it.isDamaging })
        val m = Monster.create(bird, 40, kotlin.random.Random(1))
        val c = Combatant.fromMonster(m, "x")
        assertEquals(listOf(Element.ELECTRIC, Element.FLIGHT), c.types)
        // A dual-type foe takes its double weakness in a real fight.
        val horned = Monster.create(horn, 40, kotlin.random.Random(2))
        assertEquals(listOf(Element.ELECTRIC, Element.EARTH), Combatant.fromMonster(horned, "y").types)
    }
}
