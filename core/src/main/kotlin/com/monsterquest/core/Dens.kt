package com.monsterquest.core

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import kotlin.math.floor
import kotlin.random.Random

/** A den on the map: no loot, just one strong monster to beat and then try to tame. */
data class Den(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val monster: Monster,
) {
    val species: Species get() = monster.species
}

/** After beating a den's monster: a few throws to tame it before it slips away. */
@Serializable
data class DenCatch(
    val denId: String,
    val monster: Monster,
    val throwsLeft: Int,
    val throwsTotal: Int,
)

/**
 * Dens are lairs of strong ordinary monsters (never legendaries), a few levels
 * above you with great stats. They can't be tamed during the fight: beat the
 * monster first, then you get [MIN_THROWS]..[MAX_THROWS] throws to tame it,
 * each one a long shot. Dens move every day; a cleared den stays empty until then.
 */
object Dens {
    const val RANGE_M = 100.0
    const val MIN_THROWS = 5
    const val MAX_THROWS = 10
    /** Den monsters fight with this much more HP than usual. */
    const val HP_MULTIPLIER = 1.7
    /** Den fights pay this much more than a wild fight. */
    const val REWARD_MULTIPLIER = 1.8

    private const val CELL_DEG = 0.005 // ~550 m
    private const val CELL_RADIUS = 3

    private fun epochDay(nowMs: Long, zone: ZoneId) = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toEpochDay()

    private fun seed(x: Long, y: Long, day: Long): Long {
        var h = 1469598103934665603L
        for (p in longArrayOf(x, y, day, 0xde75)) {
            h = (h xor p) * 1099511628211L
            h = h xor (h ushr 31)
        }
        return h
    }

    private fun denName(e: Element): String = when (e) {
        Element.FIRE -> "Cinder Lair"
        Element.WATER -> "Tidepool Grotto"
        Element.EARTH -> "Mossy Burrow"
        Element.FLIGHT -> "Windswept Roost"
        Element.ELECTRIC -> "Static Hollow"
        Element.GAS -> "Fume Cave"
        Element.CORRUPT -> "Shadow Hollow"
        Element.NEUTRAL -> "Beast Den"
    }

    /** Per-throw taming chance: rarer monsters are harder. Over 5-10 throws that's roughly a coin flip. */
    fun throwChance(m: Monster): Double = when (m.species.rarity) {
        Rarity.COMMON -> 0.12
        Rarity.UNCOMMON -> 0.10
        Rarity.RARE -> 0.08
        else -> 0.06
    }

    /** Today's dens around a location (cleared ones excluded). */
    fun near(state: GameState, lat: Double, lon: Double, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): List<Den> {
        val day = epochDay(nowMs, zone)
        val cx = floor(lat / CELL_DEG).toLong()
        val cy = floor(lon / CELL_DEG).toLong()
        val out = mutableListOf<Den>()
        for (dx in -CELL_RADIUS..CELL_RADIUS) for (dy in -CELL_RADIUS..CELL_RADIUS) {
            val x = cx + dx
            val y = cy + dy
            val r = Random(seed(x, y, day))
            if (r.nextDouble() > 0.3) continue
            val id = "den:$day:$x:$y"
            if (id in state.densCleared) continue
            val level = (state.hero.level + r.nextInt(3, 6)).coerceAtLeast(4)
            val species = Bestiary.randomWild(r, level, rareBoost = 1.6)
            // Great stats: nothing below 10, and two perfect.
            val base = Monster.create(species, level, r, shiny = rollShiny(r))
            val sturdy = base.copy(
                ivs = StatBlock(
                    maxOf(10, base.ivs.hp), maxOf(10, base.ivs.attack), maxOf(10, base.ivs.magic),
                    maxOf(10, base.ivs.defense), maxOf(10, base.ivs.speed),
                ),
            )
            out += Den(
                id = id,
                name = denName(species.element),
                lat = (x + 0.15 + r.nextDouble() * 0.7) * CELL_DEG,
                lon = (y + 0.15 + r.nextDouble() * 0.7) * CELL_DEG,
                monster = GameEngine.withPerfectStats(sturdy, 2, r),
            )
        }
        return out
    }

    /** The den's monster is beaten: the den is cleared and the taming throws begin. */
    fun defeated(state: GameState, denId: String, monster: Monster, nowMs: Long, rng: Random, zone: ZoneId = ZoneId.systemDefault()): GameState {
        val day = epochDay(nowMs, zone)
        val throws = rng.nextInt(MIN_THROWS, MAX_THROWS + 1)
        return state.copy(
            densCleared = state.densCleared.filterValues { it >= day - 2 } + (denId to day),
            pendingDenCatch = DenCatch(denId, monster.copy(damage = 0), throws, throws),
        )
    }

    /** One taming throw at a beaten den monster. */
    fun attempt(state: GameState, rng: Random = Random.Default): Update {
        val c = state.pendingDenCatch ?: return Update(state, "Nothing to tame.")
        val m = c.monster
        if (state.storageFull) return Update(state, "Monster storage is full. Release a monster or upgrade storage first.")
        if (rng.nextDouble() < throwChance(m)) {
            val tamed = m.copy(uid = newId(rng), xp = 0)
            var s = state.copy(
                pendingDenCatch = null,
                monsters = state.monsters + tamed,
                party = if (state.party.size < MAX_PARTY_SIZE) state.party + tamed.uid else state.party,
                stats = state.stats.copy(monstersTamed = state.stats.monstersTamed + 1),
            )
            s = GameEngine.addCandy(s, tamed.species, GameEngine.TAME_CANDY)
            val xp = GameEngine.captureXp(tamed.level) * 2
            val gain = GameEngine.addHeroXp(s, xp)
            return Update(gain.state, "Got it! ${m.displayName} from the den was tamed! +$xp XP, +${GameEngine.TAME_CANDY} ${GameEngine.candyName(tamed.species)}.${GameEngine.levelUpText(gain)}")
        }
        val left = c.throwsLeft - 1
        return if (left <= 0) {
            Update(state.copy(pendingDenCatch = null), "${m.displayName} broke free and slipped back into the wild.")
        } else {
            Update(state.copy(pendingDenCatch = c.copy(throwsLeft = left)), "${m.displayName} broke free! $left ${if (left == 1) "throw" else "throws"} left.")
        }
    }

    /** Stop trying: the monster leaves. */
    fun giveUp(state: GameState): Update {
        val c = state.pendingDenCatch ?: return Update(state, "")
        return Update(state.copy(pendingDenCatch = null), "You let ${c.monster.displayName} go.")
    }
}
