package com.monsterquest.core

import kotlinx.serialization.Serializable
import kotlin.random.Random

const val MAX_LOOT_BOXES = 6
const val MAX_UNLOCKING_BOXES = 2
const val METERS_PER_MILE = 1609.344
/** Rough average; used to turn the phone's step counter into distance. */
const val STEPS_PER_MILE = 2000

/**
 * Box levels and what they hold:
 * 1. gold and potions
 * 2. + weapon parts
 * 3. + spell sheets (pages)
 * 4. + a boss encounter (20% legendary); only from sessions of an hour or more
 */
@Serializable
enum class BoxRarity(
    val label: String,
    val emoji: String,
    /** Distance to walk before the box opens. */
    val miles: Int,
) {
    COMMON("Common", "📦", 1),
    RARE("Rare", "🎁", 3),
    EPIC("Epic", "💎", 5),
    LEGENDARY("Legendary", "👑", 10);

    val level: Int get() = ordinal + 1
    val meters: Double get() = miles * METERS_PER_MILE

    val contents: String
        get() = when (this) {
            COMMON -> "Gold and potions"
            RARE -> "Gold, potions and weapon parts"
            EPIC -> "Gold, potions, weapon parts and spell sheets"
            LEGENDARY -> "Everything from Level 3, plus a boss encounter (20% legendary)"
        }
}

/** Result of opening a box: the loot, and a boss to fight for Level 4 boxes. */
data class BoxOpening(val update: Update, val boss: Monster? = null)

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
        // Level 4 boxes need at least an hour off the phone.
        minutes >= 45 -> mapOf(BoxRarity.COMMON to 40, BoxRarity.RARE to 47, BoxRarity.EPIC to 13)
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

    private val potions = listOf(Item.POTION, Item.POTION, Item.POTION, Item.HI_POTION, Item.ETHER, Item.PHOENIX_DOWN)

    /** Chance that a Level 4 box's boss is a legendary. */
    const val LEGENDARY_BOSS_CHANCE = 0.2

    fun open(state: GameState, id: String, rng: Random = Random.Default): BoxOpening {
        val box = state.lootBoxes.find { it.id == id } ?: return BoxOpening(Update(state, "Box not found."))
        if (!box.ready) {
            return BoxOpening(Update(state, "Keep walking! %.1f of %d miles.".format(box.walkedMeters / METERS_PER_MILE, box.rarity.miles)))
        }
        val level = box.rarity.level
        val gold = when (level) {
            1 -> rng.nextInt(30, 61)
            2 -> rng.nextInt(60, 121)
            3 -> rng.nextInt(120, 201)
            else -> rng.nextInt(200, 301)
        }
        val items = mutableMapOf<Item, Int>()
        repeat(level + 1) {
            val p = if (rng.nextDouble() < 0.08) Item.RESTORE_POTION else potions.random(rng)
            items[p] = (items[p] ?: 0) + 1
        }
        val parts = when (level) {
            1 -> 0
            2 -> rng.nextInt(2, 4)
            3 -> rng.nextInt(3, 5)
            else -> rng.nextInt(4, 6)
        }
        val pages = mutableMapOf<String, Int>()
        val unknown = Spells.all.filter { it.id !in state.hero.knownSpells }
        if (level >= 3 && unknown.isNotEmpty()) {
            repeat(if (level == 3) rng.nextInt(2, 4) else rng.nextInt(3, 5)) {
                val sp = unknown.random(rng)
                pages[sp.id] = (pages[sp.id] ?: 0) + 1
            }
        }
        var s = state.copy(lootBoxes = state.lootBoxes.filterNot { it.id == id }, gold = state.gold + gold)
        val granted = GameEngine.grantLoot(s, GameEngine.Loot(items = items, pages = pages, parts = parts))
        s = granted.state
        val text = "${box.rarity.emoji} Level $level box: +$gold gold, ${granted.message}."
        if (level < 4) return BoxOpening(Update(s, text))

        val boss = boxBoss(state.hero.level, rng)
        val kind = if (boss.species.rarity == Rarity.LEGENDARY) "a LEGENDARY" else "a"
        return BoxOpening(Update(s, "$text Something stirs inside... $kind ${boss.species.name} appears!"), boss)
    }

    /** The boss inside a Level 4 box: 20% a legendary, otherwise a top-stage monster. */
    fun boxBoss(heroLevel: Int, rng: Random): Monster {
        val legendary = rng.nextDouble() < LEGENDARY_BOSS_CHANCE
        val pool = if (legendary) Bestiary.all.filter { it.bossOnly } else Bestiary.bosses.filter { !it.bossOnly }
        val m = Monster.create(pool.random(rng), heroLevel + 4, rng)
        return GameEngine.withPerfectStats(m, 2, rng)
    }

    fun discard(state: GameState, id: String): Update {
        val box = state.lootBoxes.find { it.id == id } ?: return Update(state, "Box not found.")
        return Update(state.copy(lootBoxes = state.lootBoxes.filterNot { it.id == id }), "Discarded a ${box.rarity.label} box.")
    }
}
