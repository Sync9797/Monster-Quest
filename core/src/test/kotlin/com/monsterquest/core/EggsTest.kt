package com.monsterquest.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EggsTest {
    @Test
    fun twoOfALineMakeAnEggThatHatchesByWalking() {
        val r = Random(4)
        val a = Monster.create(Bestiary.get("flarepanther"), 30, r).copy(ivs = StatBlock(15, 2, 0, 15, 2)).withGender(Gender.MALE)
        val b = Monster.create(Bestiary.get("emberkit"), 22, r).copy(ivs = StatBlock(2, 15, 0, 2, 15)).withGender(Gender.FEMALE)
        var s = GameEngine.chooseStarter(GameState(), "voltfawn", Random(1)).state
        s = s.copy(monsters = s.monsters + a + b, gold = 1000, candy = s.candy + ("Emberkit line" to 40))
        assertEquals(setOf(b.uid), Eggs.partners(s, a, 10L).map { it.uid }.toSet(), "a=${a.species.family} L${a.level} b=${b.species.family} L${b.level} f=${b.fainted} bred=${b.bredAtMs} all=${s.monsters.map { it.speciesId + ":" + it.level }}")
        val u = Eggs.breed(s, a.uid, b.uid, 10L, Random(5))
        s = u.state
        val egg = s.eggs.single()
        assertEquals("emberkit", egg.speciesId)
        assertEquals(1000 - Eggs.BREED_GOLD, s.gold)
        assertEquals(40 - Eggs.BREED_CANDY, GameEngine.candyOf(s, a.species))
        // At least three stats are the better parent's (15 here).
        assertTrue(MONSTER_STATS.count { egg.ivs[it] == 15 } >= Eggs.INHERITED_STATS, egg.ivs.toString())
        // The parents rest a day.
        assertTrue(Eggs.partners(s, s.monsters.first { it.uid == a.uid }, 1_000L).isEmpty())
        assertEquals(s, Eggs.breed(s, a.uid, b.uid, 1_000L).state)
        // Walk it: not yet, then hatched.
        assertTrue(!Eggs.hatch(s, egg.id).state.eggs.isEmpty())
        s = GameEngine.walk(s, 8_000).state
        assertTrue(s.eggs.single().ready, "walked ${s.eggs.single().walkedMeters} of ${egg.meters}")
        val hatched = Eggs.hatch(s, egg.id, Random(6)).state
        assertTrue(hatched.eggs.isEmpty())
        val baby = hatched.monsters.last()
        assertEquals("emberkit", baby.speciesId)
        assertEquals(egg.ivs, baby.ivs)
        assertEquals(1, hatched.stats.eggsHatched)
    }

    @Test
    fun anEggTakesAMaleAndAFemale() {
        val r = Random(4)
        val a = Monster.create(Bestiary.get("flarepanther"), 30, r).withGender(Gender.MALE)
        val b = Monster.create(Bestiary.get("emberkit"), 22, r).withGender(Gender.MALE)
        var s = GameEngine.chooseStarter(GameState(), "voltfawn", Random(1)).state
        s = s.copy(monsters = s.monsters + a + b, gold = 1000, candy = s.candy + ("Emberkit line" to 40))
        assertTrue(Eggs.partners(s, a, 10L).isEmpty())
        assertEquals(s, Eggs.breed(s, a.uid, b.uid, 10L).state)
    }

    @Test
    fun legendariesAndMythicsHaveNoGenderAndNeverBreed() {
        val r = Random(2)
        for (sp in Bestiary.all) {
            val m = Monster.create(sp, 40, r)
            if (sp.rarity == Rarity.LEGENDARY || sp.id in Bestiary.MYTHIC_IDS) {
                assertEquals(null, m.gender, sp.id)
                assertTrue(!Eggs.canBreed(m, 0L), sp.id)
            } else {
                assertTrue(m.gender != null, sp.id)
            }
        }
        // Roughly even odds.
        val males = (1..2000).count { Monster.create(Bestiary.get("emberkit"), 5, r).gender == Gender.MALE }
        assertTrue(males in 850..1150, "$males of 2000 male")
    }

    @Test
    fun adventuresOfTwoOrMoreOftenBringAnEgg() {
        val r = Random(9)
        val s = GameEngine.chooseStarter(GameState(), "voltfawn", Random(1)).state
        val a = Monster.create(Bestiary.get("emberkit"), 10, r).withGender(Gender.MALE)
        val b = Monster.create(Bestiary.get("emberkit"), 10, r).withGender(Gender.FEMALE)
        val other = Monster.create(Bestiary.get("tidefin"), 10, r)
        val legend = Monster.create(Bestiary.get("thunder_titan"), 50, r)
        // About 75% of the time.
        val hits = (1..2000).count { Eggs.fromAdventure(s, listOf(a, other), r) != null }
        assertTrue(hits in 1400..1600, "$hits of 2000")
        // Never for one monster alone, or a team that can't pass on an egg.
        assertTrue((1..200).none { Eggs.fromAdventure(s, listOf(a), r) != null })
        assertTrue((1..200).none { Eggs.fromAdventure(s, listOf(legend, legend.copy(uid = "x")), r) != null })
        // A male and female of a line pass on their own egg.
        val (withEgg, note) = generateSequence(0) { it + 1 }.firstNotNullOf { Eggs.fromAdventure(s, listOf(a, b), r) }
        assertEquals("emberkit", withEgg.eggs.single().speciesId)
        assertTrue("came back with an egg" in note, note)
        // Full egg slots: the egg is left behind.
        val full = s.copy(eggs = List(Eggs.MAX_EGGS) { withEgg.eggs.single().copy(id = "e$it") })
        val (same, _) = generateSequence(0) { it + 1 }.firstNotNullOf { Eggs.fromAdventure(full, listOf(a, b), r) }
        assertEquals(full, same)
    }
}

/** The same monster with an id that gives it [g]. */
internal fun Monster.withGender(g: Gender): Monster {
    var i = 0
    while (Gender.of("$uid$i") != g) i++
    return copy(uid = "$uid$i")
}
