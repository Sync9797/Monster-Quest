package com.monsterquest.core

import kotlin.random.Random

/**
 * A hidden walking mechanic: every [STEPS] steps there's a [CHANCE] that a
 * monster leaps out in front of you and attacks. Usually one, sometimes two
 * or three of the same kind; groups fight as a pack, so each one is
 * proportionally weaker (see [TeamScaling.pack]).
 */
object SecretEncounters {
    const val STEPS = 50
    const val CHANCE = 0.25
    /** With the battery saver on (phone face-down in a pocket), monsters and thugs notice you more. */
    const val SAVER_CHANCE = 0.4
    /** Share of surprise attacks that are a thug and their crew (once you have a monster to fight with). */
    const val THUG_SHARE = 0.2
    /** Thugs start jumping you from this hero level. */
    const val THUG_LEVEL = 3
    /** No surprise attack within this long of the last fight ending. */
    const val COOLDOWN_MS = 60_000L

    /**
     * Adds [steps] to the [bank] and rolls once for every [STEPS] walked.
     * Returns the leftover bank and whether something attacks (at most one per call).
     */
    fun roll(bank: Int, steps: Int, rng: Random, saver: Boolean = false): Pair<Int, Boolean> {
        var b = bank + steps.coerceAtLeast(0)
        var hit = false
        val chance = if (saver) SAVER_CHANCE else CHANCE
        while (b >= STEPS) {
            b -= STEPS
            if (rng.nextDouble() < chance) hit = true
        }
        return b to hit
    }

    /** Whether this surprise attack is a thug rather than wild monsters. */
    fun isThug(heroLevel: Int, rng: Random): Boolean = heroLevel >= THUG_LEVEL && rng.nextDouble() < THUG_SHARE

    /** 1 monster (60%), 2 of the same (28%) or 3 of the same (12%). */
    fun groupSize(rng: Random): Int = rng.nextDouble().let { if (it < 0.6) 1 else if (it < 0.88) 2 else 3 }

    /** Who jumps out: one wild species (favoring the time of day), at your usual wild level. */
    fun encounter(heroLevel: Int, hour: Int, rng: Random): List<Monster> {
        val level = GameEngine.wildLevel(heroLevel, rng)
        val species = Bestiary.randomWild(rng, level, TimeOfDay.of(hour).favored)
        return List(groupSize(rng)) { Monster.create(species, level, rng, shiny = rollShiny(rng)).withWildMoves(rng) }
    }
}
