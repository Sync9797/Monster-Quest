package com.monsterquest.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class FoodEncountersTest {
    @Test
    fun foodDrawsItsEatersToWalkingEncounters() {
        var s = GameEngine.chooseStarter(GameState(), "voltfawn", Random(1)).state
        s = s.copy(hero = s.hero.copy(level = 20), inventory = s.inventory + (Item.FRESH_FISH to 1))
        val now = 1_800_000_000_000L
        val eaters = Foods.eaters(Item.FRESH_FISH).map { Bestiary.lineOf(it).first().family }.toSet()
        fun share(state: GameState): Double {
            val (boosts, rare) = Foods.encounterBoosts(state, now)
            val r = Random(5)
            val n = 2_000
            return (0 until n).count { SecretEncounters.encounter(20, 12, r, boosts, rare).first().species.family in eaters } / n.toDouble()
        }
        val without = share(s)
        val fed = Foods.use(s, Item.FRESH_FISH, now).state
        val with = share(fed)
        println("fish eaters: $without without food, $with with Fresh Fish out")
        assertTrue(with > without * 2, "$without -> $with")
    }
}
