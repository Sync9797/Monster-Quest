package com.monsterquest.core

/**
 * The first monster in your battle party is your buddy: it walks with you and
 * earns its line's candy for the distance you cover. Small, common monsters
 * find one every mile; stronger ones take longer. Changing who leads the party
 * starts the count over.
 */
object Buddy {
    /** Miles per candy, by the monster's own rarity (so it grows as it evolves). */
    fun milesPerCandy(species: Species): Double = when (species.rarity) {
        Rarity.COMMON -> 1.0
        Rarity.UNCOMMON -> 2.0
        Rarity.RARE -> 3.0
        Rarity.EPIC -> 5.0
        Rarity.LEGENDARY -> 10.0
    }

    fun metersPerCandy(species: Species): Double = milesPerCandy(species) * METERS_PER_MILE

    /** The monster in the first party slot, if any. */
    fun of(state: GameState): Monster? = state.partyMonsters.firstOrNull()

    /** A new buddy starts from zero. */
    fun sync(state: GameState): GameState {
        val lead = state.party.firstOrNull()
        return if (state.buddyUid == lead) state else state.copy(buddyUid = lead, buddyMeters = 0.0)
    }

    /** Distance walked toward the next candy with the current buddy. */
    fun progress(state: GameState): Double = if (state.buddyUid == state.party.firstOrNull()) state.buddyMeters else 0.0

    /** Walking with your buddy: candy for each full stretch. Returns the new state and a note (empty when nothing was earned). */
    fun addWalk(state: GameState, meters: Double): Pair<GameState, String> {
        var s = sync(state)
        val buddy = of(s) ?: return s to ""
        if (meters <= 0) return s to ""
        val per = metersPerCandy(buddy.species)
        val total = s.buddyMeters + meters
        val candy = (total / per).toInt()
        s = s.copy(buddyMeters = total - candy * per)
        if (candy == 0) return s to ""
        s = GameEngine.addCandy(s, buddy.species, candy)
        return s to "Your buddy ${buddy.displayName} found $candy ${GameEngine.candyName(buddy.species)} on your walk!"
    }
}
