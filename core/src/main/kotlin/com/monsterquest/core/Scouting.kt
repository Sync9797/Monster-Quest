package com.monsterquest.core

import kotlin.random.Random

/**
 * Sizing up a monster at a glance: what its line becomes and how strong that
 * final form is, as 1-5 stars (by the final form's max stats against every
 * other final form).
 */
object Scouting {
    fun finalForm(species: Species): Species = Bestiary.lineOf(species).last()

    private val finals: List<String> by lazy {
        Bestiary.all.filter { it.evolvesTo == null }.sortedByDescending { MaxStats.total(it) }.map { it.id }
    }

    fun stars(species: Species): Int {
        val i = finals.indexOf(finalForm(species).id).coerceAtLeast(0)
        val p = i.toDouble() / finals.size
        return when {
            p < 0.10 -> 5
            p < 0.30 -> 4
            p < 0.55 -> 3
            p < 0.80 -> 2
            else -> 1
        }
    }

    fun starText(n: Int): String = "★".repeat(n) + "☆".repeat(5 - n)
}

/**
 * Species pinned in the Journal. Any monster of a wanted line (catch the
 * first stage of the line you want) is marked on the map.
 */
object Wanted {
    const val MAX = 6

    fun toggle(state: GameState, speciesId: String): Update {
        val sp = Bestiary.get(speciesId)
        if (speciesId in state.wanted) return Update(state.copy(wanted = state.wanted - speciesId), "${sp.name} is off your Wanted list.")
        if (state.wanted.size >= MAX) return Update(state, "Your Wanted list holds $MAX. Unpin one first.")
        return Update(state.copy(wanted = state.wanted + speciesId), "${sp.name} is on your Wanted list: its line is marked on the map.")
    }

    /** Whether [species] is (in the line of) something you want. */
    fun matches(state: GameState, species: Species): Boolean = state.wanted.any { Bestiary.get(it).family == species.family }

    /** Wanted lines whose types this weather brings out more. */
    fun boostedBy(state: GameState, weather: LiveWeather): List<Species> =
        state.wanted.map { Bestiary.get(it) }.filter { sp -> Bestiary.lineOf(sp).first().types.any { (weather.boosts[it] ?: 1.0) > 1.0 } }
}

/**
 * The mystery monster of the week: a silhouette on Home with hints about where
 * it lives. It turns up more often all week; catch one for a bonus.
 */
object Mystery {
    /** How much more often it spawns while it's the mystery. */
    const val SPAWN_BOOST = 4.0
    const val GOLD = 500
    const val CANDY = 10
    val ITEM = Item.GOLDEN_BERRY

    /** Starts a new week's mystery, fitting the hero's level, when the week turns over. */
    fun rollover(state: GameState, epochDay: Long): GameState {
        val week = Goals.weekOf(epochDay)
        if (state.mysteryWeek == week && state.mysteryId != null) return state
        val rng = Random(week * 104_729 + 17)
        val pool = Bestiary.wild.filter {
            it.rarity in Rarity.UNCOMMON..Rarity.EPIC && !it.genderless && it.minWildLevel <= state.hero.level + 2
        }.ifEmpty { Bestiary.wild.filter { it.minWildLevel <= state.hero.level + 2 } }
        val pick = pool.filter { it.id !in state.caught }.ifEmpty { pool }.random(rng)
        return state.copy(mysteryWeek = week, mysteryId = pick.id, mysteryFound = false, mysteryClaimed = false)
    }

    fun species(state: GameState): Species? = state.mysteryId?.let { Bestiary.get(it) }

    /** Spawn weight multipliers by species id. */
    fun boosts(state: GameState): Map<String, Double> =
        if (state.mysteryId != null && !state.mysteryFound) mapOf(state.mysteryId to SPAWN_BOOST) else emptyMap()

    /** A newly tamed (or hatched) monster of this week's mystery reveals it. */
    fun track(before: GameState, after: GameState): GameState {
        val id = after.mysteryId ?: return after
        if (after.mysteryFound) return after
        val had = before.monsters.map { it.uid }.toSet()
        return if (after.monsters.any { it.uid !in had && it.speciesId == id }) after.copy(mysteryFound = true) else after
    }

    /** Clues about where to look: type, weather, time of day, water. */
    fun hints(species: Species): List<String> {
        val type = species.element
        val out = mutableListOf("A ${type.label} type" + (species.secondary?.let { " (with a second type)" } ?: ""))
        LiveWeather.entries.maxByOrNull { it.boosts[type] ?: 1.0 }?.takeIf { (it.boosts[type] ?: 1.0) > 1.0 }?.let {
            out += "Seen more in ${it.label.lowercase()} weather"
        }
        TimeOfDay.entries.firstOrNull { type in it.favored }?.let { out += "Most active at ${it.label.lowercase()}" }
        if (type == Element.WATER) out += "Lives near lakes, rivers and the coast"
        out += "${species.rarity.label}, from level ${species.minWildLevel}"
        return out
    }

    fun claim(state: GameState): Update {
        val sp = species(state) ?: return Update(state, "No mystery this week.")
        if (!state.mysteryFound) return Update(state, "Find and tame this week's mystery first.")
        if (state.mysteryClaimed) return Update(state, "Already claimed this week.")
        var s = GameEngine.addCandy(state.copy(gold = state.gold + GOLD, mysteryClaimed = true), sp, CANDY)
        s = GameEngine.addItems(s, mapOf(ITEM to 1)).first
        return Update(s, "Mystery solved: ${sp.name}! +$GOLD gold, +$CANDY ${GameEngine.candyName(sp)} and a ${ITEM.label}.")
    }
}
