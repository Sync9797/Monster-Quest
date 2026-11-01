package com.monsterquest.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RenamesTest {
    @Test
    fun oldSavesGetTheNewMoveSpellAndAchievementIds() {
        val s0 = GameEngine.chooseStarter(GameState(), "voltfawn", Random(1)).state
        val old = s0.copy(
            version = 10,
            monsters = s0.monsters.map { it.copy(moves = listOf("tackle", "thunder_shock", "spark")) },
            hero = s0.hero.copy(knownSpells = s0.hero.knownSpells + "mud_shot", spellRanks = mapOf("mud_shot" to 2)),
            spellPages = mapOf("mud_shot" to 1),
            achievementsClaimed = setOf("dex_25"),
        )
        val m = old.migrated()
        assertEquals(listOf("shoulder_rush", "jolt_strike", "crackle_jolt"), m.monsters.first().moves)
        assertTrue(Spells.MUD_SLING in m.hero.knownSpells)
        assertEquals(2, m.hero.spellRank(Spells.MUD_SLING))
        assertEquals(mapOf(Spells.MUD_SLING to 1), m.spellPages)
        assertEquals(setOf("journal_25"), m.achievementsClaimed)
        // Every renamed move exists under its new id, and none of the old ids does.
        Moves.renamedIds.forEach { (old, new) ->
            assertTrue(Moves.all.any { it.id == new }, new)
            assertTrue(Moves.all.none { it.id == old }, old)
        }
    }

    @Test
    fun oldItemAndSkillNamesStillLoad() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val items = json.decodeFromString<Map<Item, Int>>("""{"HI_POTION":2,"ULTRA_ORB":1,"PHOENIX_DOWN":3}""")
        assertEquals(mapOf(Item.GREATER_POTION to 2, Item.PRISMATIC_CRYSTAL to 1, Item.PHOENIX_FEATHER to 3), items)
        assertEquals(Trait.MENACE, json.decodeFromString<Trait>("\"INTIMIDATE\""))
        assertEquals(Trait.EMBER_HIDE, json.decodeFromString<Trait>("\"FLAME_BODY\""))
    }
}
