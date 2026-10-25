package com.monsterquest.core

import kotlinx.serialization.Serializable
import kotlin.random.Random

const val MAX_LOOT_BOXES = 6
const val MAX_UNLOCKING_BOXES = 2
const val METERS_PER_MILE = 1609.344
/** Rough average; used to turn the phone's step counter into distance. */
const val STEPS_PER_MILE = 2000

@Serializable
enum class BoxRarity(
    val label: String,
    val emoji: String,
    /** Distance to walk before the box opens. */
    val miles: Int,
    val lootRolls: Int,
    val gold: Int,
    val xp: Int,
) {
    COMMON("Common", "📦", 1, 2, 30, 40),
    RARE("Rare", "🎁", 3, 4, 80, 100),
    EPIC("Epic", "💎", 5, 6, 150, 200),
    LEGENDARY("Legendary", "👑", 10, 8, 300, 400);

    val meters: Double get() = miles * METERS_PER_MILE
}

@Serializable
data class LootBox(
    val id: String,
    val rarity: BoxRarity,
    val earnedMs: Long,
    /** Only unlocking boxes collect walking distance. */
    val unlocking: Boolean = false,
    val walkedMeters: Double = 0.0,
) {
    val ready: Boolean get() = walkedMeters >= rarity.meters
    val progress: Double get() = (walkedMeters / rarity.meters).coerceIn(0.0, 1.0)
}

/** Loot boxes: earned from phone-free sessions, opened by walking. */
object LootBoxes {

    /** Odds (percent) of each box rarity for a session of [minutes]. Longer sessions, better boxes. */
    fun odds(minutes: Int): Map<BoxRarity, Int> = when {
        minutes >= 120 -> mapOf(BoxRarity.RARE to 30, BoxRarity.EPIC to 50, BoxRarity.LEGENDARY to 20)
        minutes >= 90 -> mapOf(BoxRarity.COMMON to 10, BoxRarity.RARE to 40, BoxRarity.EPIC to 38, BoxRarity.LEGENDARY to 12)
        minutes >= 60 -> mapOf(BoxRarity.COMMON to 25, BoxRarity.RARE to 45, BoxRarity.EPIC to 25, BoxRarity.LEGENDARY to 5)
        minutes >= 45 -> mapOf(BoxRarity.COMMON to 40, BoxRarity.RARE to 45, BoxRarity.EPIC to 13, BoxRarity.LEGENDARY to 2)
        minutes >= 30 -> mapOf(BoxRarity.COMMON to 60, BoxRarity.RARE to 35, BoxRarity.EPIC to 5)
        else -> mapOf(BoxRarity.COMMON to 85, BoxRarity.RARE to 15)
    }

    fun roll(minutes: Int, rng: Random): BoxRarity {
        var r = rng.nextInt(100)
        for ((rarity, pct) in odds(minutes)) {
            r -= pct
            if (r < 0) return rarity
        }
        return BoxRarity.COMMON
    }

    fun startUnlocking(state: GameState, id: String): Update {
        val box = state.lootBoxes.find { it.id == id } ?: return Update(state, "Box not found.")
        if (box.unlocking) return Update(state, "Already unlocking.")
        if (state.lootBoxes.count { it.unlocking } >= MAX_UNLOCKING_BOXES) {
            return Update(state, "You can only unlock $MAX_UNLOCKING_BOXES boxes at a time.")
        }
        return Update(
            state.copy(lootBoxes = state.lootBoxes.map { if (it.id == id) it.copy(unlocking = true) else it }),
            "${box.rarity.label} box is unlocking. Walk ${box.rarity.miles} mile(s) to open it!",
        )
    }

    /** Credits walked distance to every unlocking box. */
    fun addWalk(state: GameState, meters: Double): GameState {
        if (meters <= 0 || state.lootBoxes.none { it.unlocking && !it.ready }) return state
        return state.copy(
            lootBoxes = state.lootBoxes.map {
                if (it.unlocking && !it.ready) it.copy(walkedMeters = minOf(it.rarity.meters, it.walkedMeters + meters)) else it
            },
        )
    }

    /**
     * A new reading from the phone's step counter (total steps since boot).
     * Steps since the previous reading count as walking; a lower reading means
     * the phone rebooted, so the new total counts from zero.
     */
    fun onStepCount(state: GameState, totalSteps: Long): GameState {
        val last = state.lastStepCount
        val delta = when {
            last == null -> 0L
            totalSteps >= last -> totalSteps - last
            else -> totalSteps
        }
        val walked = addWalk(state, delta * METERS_PER_MILE / STEPS_PER_MILE)
        return walked.copy(lastStepCount = totalSteps)
    }

    fun open(state: GameState, id: String, rng: Random = Random.Default): Update {
        val box = state.lootBoxes.find { it.id == id } ?: return Update(state, "Box not found.")
        if (!box.ready) return Update(state, "Keep walking! %.1f of %d miles.".format(box.walkedMeters / METERS_PER_MILE, box.rarity.miles))
        val r = box.rarity
        val xp = (r.xp * (1 + state.hero.level / 10.0)).toInt()
        var s = state.copy(lootBoxes = state.lootBoxes.filterNot { it.id == id }, gold = state.gold + r.gold)
        val rolled = GameEngine.rollLoot(s, r.lootRolls, rng)
        val bonusWeapons = if (r == BoxRarity.EPIC || r == BoxRarity.LEGENDARY) listOf(GameEngine.rollWeapon(s.hero.level + 10, rng)) else emptyList()
        val bonusItems = if (r == BoxRarity.LEGENDARY) mapOf(Item.MASTER_ORB to 1) else emptyMap()
        val loot = rolled.copy(
            weapons = rolled.weapons + bonusWeapons,
            items = (rolled.items.keys + bonusItems.keys).associateWith { (rolled.items[it] ?: 0) + (bonusItems[it] ?: 0) },
        )
        val granted = GameEngine.grantLoot(s, loot)
        s = granted.state
        val gain = GameEngine.addHeroXp(s, xp)
        val lvl = if (gain.levelsGained > 0) " LEVEL UP! Now level ${gain.state.hero.level}." else ""
        return Update(gain.state, "${r.emoji} ${r.label} box: +${r.gold} gold, +$xp XP, ${granted.message}.$lvl")
    }

    fun discard(state: GameState, id: String): Update {
        val box = state.lootBoxes.find { it.id == id } ?: return Update(state, "Box not found.")
        return Update(state.copy(lootBoxes = state.lootBoxes.filterNot { it.id == id }), "Discarded a ${box.rarity.label} box.")
    }
}
