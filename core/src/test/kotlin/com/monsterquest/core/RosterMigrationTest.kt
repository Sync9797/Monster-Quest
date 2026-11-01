package com.monsterquest.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RosterMigrationTest {
    @Test
    fun removedLinesBecomeTheKeptOneStageForStage() {
        val base = GameEngine.chooseStarter(GameState(), "voltfawn", Random(1)).state
        val old = base.copy(
            version = 7,
            monsters = base.monsters + Monster("a", "cinder_hound", Personality.BALANCED, StatBlock(1, 2, 3, 4, 5), level = 30) +
                Monster("b", "thunderbird", Personality.BALANCED, StatBlock(1, 2, 3, 4, 5), level = 40),
            candy = base.candy + ("Ember Hound line" to 7) + ("Shadow Hound line" to 3) + ("Zipwing line" to 4),
            seen = base.seen + "tide_pup",
        )
        val s = GameState.fromJson(old.toJson())
        assertEquals("dusk_hound", s.monsters.first { it.uid == "a" }.speciesId)
        assertEquals(30, s.monsters.first { it.uid == "a" }.level)
        assertEquals("galehawk", s.monsters.first { it.uid == "b" }.speciesId)
        assertEquals(10, s.candy["Shadow Hound line"])
        assertEquals(4, s.candy["Fluffchick line"])
        assertTrue("shadow_pup" in s.seen && "tide_pup" !in s.seen)
        // Only one wolf and one hawk line left.
        assertEquals(1, Bestiary.all.count { it.family.endsWith("Hound line") } / 3)
        assertTrue(Bestiary.all.none { it.family == "Zipwing line" })
    }
}
