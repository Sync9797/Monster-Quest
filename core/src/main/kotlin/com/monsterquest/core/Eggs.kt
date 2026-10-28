package com.monsterquest.core

import kotlinx.serialization.Serializable
import kotlin.random.Random

/** An egg from two monsters of the same line: it hatches the line's first stage after a walk. */
@Serializable
data class Egg(
    val id: String,
    /** What hatches: the line's first stage. */
    val speciesId: String,
    /** Genes inherited from the parents (at least [Eggs.INHERITED_STATS] are the better parent's). */
    val ivs: StatBlock,
    val personality: Personality,
    val shiny: Boolean,
    /** Distance to walk before it hatches. */
    val meters: Double,
    val walkedMeters: Double = 0.0,
) {
    val species: Species get() = Bestiary.get(speciesId)
    val ready: Boolean get() = walkedMeters >= meters
    val progress: Double get() = (walkedMeters / meters).coerceIn(0.0, 1.0)
}

/**
 * Breeding: two monsters of the same evolution line, both level
 * [BREED_LEVEL]+, make an egg (for line candy and gold). The parents stay, but
 * rest for a day. The egg hatches by walking and carries the best of its
 * parents' genes.
 */
object Eggs {
    const val MAX_EGGS = 3
    const val BREED_LEVEL = 20
    const val BREED_COOLDOWN_MS = 24 * 60 * 60_000L
    const val BREED_GOLD = 300
    const val BREED_CANDY = 20
    /** Stats guaranteed to come from the better parent. */
    const val INHERITED_STATS = 3
    /** Chance the egg takes one parent's personality. */
    const val PERSONALITY_CHANCE = 0.5

    /** How far an egg of [species]' line must be walked: rarer lines take longer. */
    fun walkMeters(species: Species): Double = when (Bestiary.lineOf(species).last().rarity) {
        Rarity.EPIC, Rarity.LEGENDARY -> 6_000.0
        Rarity.RARE -> 4_000.0
        else -> 2_000.0
    }

    fun resting(m: Monster, nowMs: Long): Boolean = m.bredAtMs > 0 && nowMs - m.bredAtMs < BREED_COOLDOWN_MS

    fun canBreed(m: Monster, nowMs: Long): Boolean = m.level >= BREED_LEVEL && !m.fainted && !resting(m, nowMs)

    /** Who [m] can make an egg with right now. */
    fun partners(state: GameState, m: Monster, nowMs: Long): List<Monster> =
        state.monsters.filter { it.uid != m.uid && it.species.family == m.species.family && canBreed(it, nowMs) }

    fun breed(state: GameState, uidA: String, uidB: String, nowMs: Long, rng: Random = Random.Default): Update {
        val a = state.monsters.find { it.uid == uidA } ?: return Update(state, "Monster not found.")
        val b = state.monsters.find { it.uid == uidB } ?: return Update(state, "Monster not found.")
        if (a.species.family != b.species.family) return Update(state, "Only monsters of the same line can make an egg.")
        if (!canBreed(a, nowMs) || !canBreed(b, nowMs)) return Update(state, "Both need to be level $BREED_LEVEL+, awake and rested.")
        if (state.eggs.size >= MAX_EGGS) return Update(state, "You can carry $MAX_EGGS eggs at a time. Hatch one first.")
        if (state.gold < BREED_GOLD) return Update(state, "Making an egg costs $BREED_GOLD gold.")
        if (GameEngine.candyOf(state, a.species) < BREED_CANDY) return Update(state, "Making an egg costs $BREED_CANDY ${GameEngine.candyName(a.species)}.")
        val baby = Bestiary.lineOf(a.species).first()
        // The best of both: some stats are guaranteed the better parent's, the rest are rolled.
        val best = MONSTER_STATS.shuffled(rng).take(INHERITED_STATS).toSet()
        fun iv(stat: Stat) = if (stat in best) maxOf(a.ivs[stat], b.ivs[stat]) else maxOf(rng.nextInt(MAX_IV + 1), minOf(a.ivs[stat], b.ivs[stat]))
        val ivs = StatBlock(iv(Stat.HP), iv(Stat.ATTACK), rng.nextInt(MAX_IV + 1), iv(Stat.DEFENSE), iv(Stat.SPEED))
        val personality = if (rng.nextDouble() < PERSONALITY_CHANCE) listOf(a, b).random(rng).personality
        else Personality.entries.random(rng)
        val shiny = rollShiny(rng) || ((a.shiny || b.shiny) && rollShiny(rng))
        val egg = Egg(newId(rng), baby.id, ivs, personality, shiny, walkMeters(baby))
        val s = GameEngine.addCandy(state, a.species, -BREED_CANDY).copy(
            gold = state.gold - BREED_GOLD,
            eggs = state.eggs + egg,
            monsters = state.monsters.map { if (it.uid == uidA || it.uid == uidB) it.copy(bredAtMs = nowMs) else it },
        )
        return Update(s, "${a.displayName} and ${b.displayName} made an egg! Walk ${"%.1f".format(egg.meters / 1000)} km to hatch it.")
    }

    /** Walking warms every egg. */
    fun addWalk(state: GameState, meters: Double): GameState {
        if (meters <= 0 || state.eggs.none { !it.ready }) return state
        return state.copy(eggs = state.eggs.map { if (it.ready) it else it.copy(walkedMeters = minOf(it.meters, it.walkedMeters + meters)) })
    }

    fun hatch(state: GameState, id: String, rng: Random = Random.Default): Update {
        val egg = state.eggs.find { it.id == id } ?: return Update(state, "Egg not found.")
        if (!egg.ready) return Update(state, "Keep walking: %.1f of %.1f km.".format(egg.walkedMeters / 1000, egg.meters / 1000))
        if (state.storageFull) return Update(state, "Monster storage is full. Release a monster or expand storage first.")
        val m = Monster.create(egg.species, 1, rng, shiny = egg.shiny).copy(ivs = egg.ivs, personality = egg.personality)
        val s = state.copy(
            eggs = state.eggs.filterNot { it.id == id },
            monsters = state.monsters + m,
            party = if (state.party.size < MAX_PARTY_SIZE) state.party + m.uid else state.party,
            stats = state.stats.copy(eggsHatched = state.stats.eggsHatched + 1),
        )
        return Update(s, "The egg hatched: a ${if (m.shiny) "shiny " else ""}${m.species.name}! ${m.personality.label}, with ${GameEngine.perfectStatCount(m)} perfect stat(s).")
    }
}
