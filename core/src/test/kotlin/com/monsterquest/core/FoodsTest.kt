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

    @Test
    fun betterTiersPullHarderAndFavorRarerEaters() {
        val meat = Foods.tiers(Item.RAW_MEAT)
        assertEquals(listOf(Item.RAW_MEAT, Item.RAW_MEAT_FINE, Item.RAW_MEAT_GOURMET), meat)
        assertEquals(Item.RAW_MEAT, Foods.base(Item.RAW_MEAT_GOURMET))
        assertEquals(9, Foods.all.size)
        // Every tier is locked behind a higher level, and costs more.
        for (f in Foods.all) {
            val t = Foods.tiers(f)
            assertEquals(3, t.size)
            assertTrue(t[0].minLevel < t[1].minLevel && t[1].minLevel < t[2].minLevel)
            assertTrue(t[0].price < t[1].price && t[1].price < t[2].price)
            // Same eaters at every tier.
            assertEquals(Foods.eaters(t[0]), Foods.eaters(t[2]))
        }
        val common = Bestiary.get("emberkit")
        val rare = Bestiary.lineOf(common).first { it.rarity >= Rarity.RARE }
        val boosts = meat.map { Foods.boostFor(it, rare) }
        assertTrue(boosts[0] < boosts[1] && boosts[1] < boosts[2], boosts.toString())
        // Rarer eaters gain the most from better food.
        assertTrue(Foods.boostFor(Item.RAW_MEAT_GOURMET, rare) / Foods.boostFor(Item.RAW_MEAT, rare) >
            Foods.boostFor(Item.RAW_MEAT_GOURMET, common) / Foods.boostFor(Item.RAW_MEAT, common))
        assertEquals(1.0, Foods.boostFor(Item.RAW_MEAT_GOURMET, Bestiary.get("twiglet")))
        // In the wild at a high level, Gourmet brings a rarer mix of meat-eaters than the basic tier.
        fun rareShare(food: Item): Double {
            val boost = Foods.eaters(food).associate { it.id to Foods.boostFor(food, it) }
            val picks = (1..6000).map { Bestiary.randomWild(Random(it), 45, speciesBoosts = boost) }.filter { Item.RAW_MEAT in Foods.dietOf(it) }
            return picks.count { it.rarity >= Rarity.RARE }.toDouble() / picks.size
        }
        assertTrue(rareShare(Item.RAW_MEAT_GOURMET) > rareShare(Item.RAW_MEAT) * 1.3)
        // The shop won't sell a tier before its level.
        val s = GameEngine.chooseStarter(GameState(), "voltfawn", Random(1)).state.copy(gold = 10_000)
        assertEquals(s, GameEngine.buy(s, Item.RAW_MEAT_GOURMET).state)
    }
}
