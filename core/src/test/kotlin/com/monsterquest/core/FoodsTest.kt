package com.monsterquest.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FoodsTest {
    @Test
    fun everyWildLineEatsSomethingAndDietsMakeSense() {
        for (sp in Bestiary.wild.filter { !it.genderless }) assertTrue(Foods.dietOf(sp).isNotEmpty(), "${sp.family} has no diet")
        // Trees don't eat meat; dragons don't settle for fish.
        assertEquals(setOf(Item.RICH_COMPOST), Foods.dietOf(Bestiary.get("twiglet")))
        assertTrue(Item.FRESH_FISH !in Foods.dietOf(Bestiary.get("cindrake")))
        assertTrue(Item.DRAGON_FEAST in Foods.dietOf(Bestiary.get("infernodrake")))
        assertTrue(Foods.dietOf(Bestiary.get("thunder_titan")).isEmpty())
        // Every food draws someone.
        for (f in Foods.all) assertTrue(Foods.eaters(f).isNotEmpty(), f.label)
    }

    @Test
    fun settingOutFoodBoostsItsEaters() {
        var s = GameEngine.chooseStarter(GameState(), "voltfawn", Random(1)).state
        s = s.copy(inventory = s.inventory + (Item.RAW_MEAT to 2))
        s = Foods.use(s, Item.RAW_MEAT, 1_000).state
        assertEquals(1, s.count(Item.RAW_MEAT))
        val boosts = Foods.boosts(s, 2_000)
        assertEquals(Foods.BOOST, boosts["emberkit"])
        assertTrue("twiglet" !in boosts)
        // Again extends it.
        s = Foods.use(s, Item.RAW_MEAT, 2_000).state
        assertEquals(1_000 + 2 * Foods.DURATION_MS, s.foodUntilMs)
        assertTrue(Foods.boosts(s, s.foodUntilMs + 1).isEmpty())
        // Meat-eaters really do turn up more.
        val level = 30
        fun count(boost: Map<String, Double>) = (1..4000).count {
            Bestiary.randomWild(Random(it), level, speciesBoosts = boost).id in Foods.eaters(Item.RAW_MEAT).map { e -> e.id }
        }
        val (with, without) = count(boosts) to count(emptyMap())
        assertTrue(with > without * 1.5, "$with vs $without")
    }
}
