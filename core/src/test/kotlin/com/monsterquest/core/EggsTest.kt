package com.monsterquest.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EggsTest {
    @Test
    fun twoOfALineMakeAnEggThatHatchesByWalking() {
        val r = Random(4)
        val a = Monster.create(Bestiary.get("flarepanther"), 30, r).copy(ivs = StatBlock(15, 2, 0, 15, 2))
        val b = Monster.create(Bestiary.get("emberkit"), 22, r).copy(ivs = StatBlock(2, 15, 0, 2, 15))
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
}
