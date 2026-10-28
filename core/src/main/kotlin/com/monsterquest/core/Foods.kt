package com.monsterquest.core

/**
 * What each line eats. Set food out (from the shop) and for [DURATION_MS]
 * the monsters that eat it turn up [BOOST] times as often. Diets fit the
 * creature: a tree won't touch meat, and a dragon won't settle for a fish.
 * Legendaries and mythics can't be lured with food.
 */
object Foods {
    const val DURATION_MS = 30 * 60_000L
    /** How much more often a basic food's eaters turn up. */
    const val BOOST = 4.0

    /** The basic foods (tier 1); each has a Fine and a Gourmet tier. */
    val all: List<Item> = Item.entries.filter { it.kind == ItemKind.FOOD && tier(it) == 1 }

    /** 1 basic, 2 Fine, 3 Gourmet. */
    fun tier(food: Item): Int = food.power.toInt().coerceIn(1, 3)

    /** The basic food a tier belongs to (what decides who eats it). */
    fun base(food: Item): Item = Item.valueOf(food.name.removeSuffix("_FINE").removeSuffix("_GOURMET"))

    /** All tiers of [food], basic first. */
    fun tiers(food: Item): List<Item> = Item.entries.filter { it.kind == ItemKind.FOOD && base(it) == base(food) }.sortedBy { tier(it) }

    val TIER_LABELS = listOf("Basic", "Fine", "Gourmet")

    /** Pull on the whole group by tier. */
    private val tierBoost = listOf(BOOST, 6.0, 8.0)

    /** On top of that, better food favors rarer (more evolved) eaters: by tier, then rarity. */
    private val rarityPull = listOf(
        mapOf(Rarity.COMMON to 1.0, Rarity.UNCOMMON to 1.0, Rarity.RARE to 1.0, Rarity.EPIC to 1.0),
        mapOf(Rarity.COMMON to 1.0, Rarity.UNCOMMON to 1.5, Rarity.RARE to 2.0, Rarity.EPIC to 2.5),
        mapOf(Rarity.COMMON to 1.0, Rarity.UNCOMMON to 2.0, Rarity.RARE to 3.0, Rarity.EPIC to 4.0),
    )

    /** How much more often [species] turns up while [food] is out (1 if it doesn't eat it). */
    fun boostFor(food: Item, species: Species): Double {
        if (base(food) !in dietOf(species)) return 1.0
        val t = tier(food) - 1
        return tierBoost[t] * (rarityPull[t][species.rarity] ?: 1.0)
    }

    /** Diets by evolution line (each line eats one or two foods). */
    private val diets: Map<String, Set<Item>> = mapOf(
        "Voltfawn line" to setOf(Item.SEED_MIX),
        "Cindrake line" to setOf(Item.DRAGON_FEAST),
        "Drizzlet line" to setOf(Item.DRAGON_FEAST),
        "Mosscub line" to setOf(Item.ORCHARD_FRUIT, Item.FRESH_FISH),
        "Emberkit line" to setOf(Item.RAW_MEAT),
        "Zapmouse line" to setOf(Item.SEED_MIX),
        "Fluffchick line" to setOf(Item.SEED_MIX, Item.WRIGGLE_GRUBS),
        "Bubblefin line" to setOf(Item.FRESH_FISH),
        "Toxbug line" to setOf(Item.WRIGGLE_GRUBS),
        "Gloomwisp line" to setOf(Item.SPIRIT_INCENSE),
        "Ember Hound line" to setOf(Item.RAW_MEAT),
        "Spark Hound line" to setOf(Item.RAW_MEAT),
        "Gale Hound line" to setOf(Item.RAW_MEAT),
        "Tide Hound line" to setOf(Item.RAW_MEAT, Item.FRESH_FISH),
        "Venom Hound line" to setOf(Item.RAW_MEAT),
        "Shadow Hound line" to setOf(Item.RAW_MEAT),
        "Moss Hound line" to setOf(Item.RAW_MEAT),
        "Goopling line" to setOf(Item.RICH_COMPOST),
        "Pebblit line" to setOf(Item.MINERAL_SALTS),
        "Zipwing line" to setOf(Item.SEED_MIX, Item.WRIGGLE_GRUBS),
        "Shellsnap line" to setOf(Item.FRESH_FISH),
        "Twiglet line" to setOf(Item.RICH_COMPOST),
        "Impling line" to setOf(Item.SPIRIT_INCENSE),
        "Cinderskink line" to setOf(Item.RAW_MEAT, Item.WRIGGLE_GRUBS),
        "Cinderlamb line" to setOf(Item.SEED_MIX),
        "Joltlet line" to setOf(Item.ORCHARD_FRUIT),
        "Zaplume line" to setOf(Item.FRESH_FISH),
        "Puddlet line" to setOf(Item.WRIGGLE_GRUBS),
        "Pincerling line" to setOf(Item.FRESH_FISH),
        "Hootling line" to setOf(Item.RAW_MEAT),
        "Pterix line" to setOf(Item.FRESH_FISH),
        "Sporelet line" to setOf(Item.RICH_COMPOST),
        "Stinkit line" to setOf(Item.ORCHARD_FRUIT, Item.WRIGGLE_GRUBS),
        "Hexling line" to setOf(Item.WRIGGLE_GRUBS),
        "Nightling line" to setOf(Item.RAW_MEAT, Item.ORCHARD_FRUIT),
        "Rollpip line" to setOf(Item.WRIGGLE_GRUBS, Item.MINERAL_SALTS),
        "Stubhorn line" to setOf(Item.SEED_MIX, Item.MINERAL_SALTS),
    )

    /** What [species] eats (nothing for legendaries and mythics). */
    fun dietOf(species: Species): Set<Item> = if (species.genderless) emptySet() else diets[species.family].orEmpty()

    /** Wild monsters drawn by [food] (any tier), first stages first. */
    fun eaters(food: Item): List<Species> = Bestiary.wild.filter { base(food) in dietOf(it) }

    fun active(state: GameState, nowMs: Long): Item? = state.food?.takeIf { state.foodUntilMs > nowMs }

    /** Spawn weight multipliers by species id for the food set out. */
    fun boosts(state: GameState, nowMs: Long): Map<String, Double> =
        active(state, nowMs)?.let { f -> eaters(f).associate { it.id to boostFor(f, it) } }.orEmpty()

    fun use(state: GameState, food: Item, nowMs: Long): Update {
        if (food.kind != ItemKind.FOOD) return Update(state, "${food.label} isn't food.")
        if (state.count(food) <= 0) return Update(state, "No ${food.label}. Buy some in the Store.")
        val inv = GameEngine.removeItems(state.inventory, mapOf(food to 1))
        // The same food lasts longer; a different one replaces it.
        val until = (if (active(state, nowMs) == food) state.foodUntilMs else nowMs) + DURATION_MS
        val names = eaters(food).map { Bestiary.lineOf(it).first().name }.distinct()
        val rarer = if (tier(food) > 1) " Its rarer eaters are the most drawn." else ""
        return Update(
            state.copy(inventory = inv, food = food, foodUntilMs = until),
            "You set out ${food.label}. For 30 minutes, ${names.take(4).joinToString()}${if (names.size > 4) " and others" else ""} turn up far more often.$rarer",
        )
    }
}
