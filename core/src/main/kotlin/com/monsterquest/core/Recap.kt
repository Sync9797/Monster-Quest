package com.monsterquest.core

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** What happened on one day: shown in the next morning's recap. */
@Serializable
data class DayTally(
    val heroLevels: Int = 0,
    val monsterLevels: Int = 0,
    val monsterXp: Int = 0,
    val gold: Int = 0,
    /** Items gained (not bought). */
    val items: Map<Item, Int> = emptyMap(),
    /** Weapons and armor gained. */
    val gear: Int = 0,
    val hpRestored: Int = 0,
    val tasks: Int = 0,
) {
    val isEmpty: Boolean
        get() = heroLevels == 0 && monsterLevels == 0 && monsterXp == 0 && gold == 0 && items.isEmpty() && gear == 0 && hpRestored == 0 && tasks == 0

    operator fun plus(o: DayTally) = DayTally(
        heroLevels + o.heroLevels, monsterLevels + o.monsterLevels, monsterXp + o.monsterXp, gold + o.gold,
        (items.keys + o.items.keys).associateWith { (items[it] ?: 0) + (o.items[it] ?: 0) }, gear + o.gear,
        hpRestored + o.hpRestored, tasks + o.tasks,
    )
}

/**
 * The morning recap: every change to the save is compared with the one before
 * it and today's gains are added up (levels, monster XP, loot, HP restored,
 * tasks). The first time the game opens each morning, yesterday's totals are
 * shown with last night's sleep.
 */
object Recap {
    /** The recap waits until this hour, so a late night doesn't count as "the morning". */
    const val MORNING_HOUR = 4
    private const val DAYS_KEPT = 14

    private fun day(ms: Long, zone: ZoneId) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    /** A monster's XP since level 1, so gains across a level-up add up. */
    private fun totalXp(m: Monster): Long = (1 until m.level).sumOf { monsterXpForNext(it).toLong() } + m.xp

    /** What [after] gained over [before]. Buying things isn't loot, so items and gear only count when no gold was spent. */
    fun diff(before: GameState, after: GameState): DayTally {
        val was = before.monsters.associateBy { it.uid }
        var levels = 0
        var xp = 0L
        var hp = (before.heroDamage - after.heroDamage).coerceAtLeast(0)
        for (m in after.monsters) {
            val b = was[m.uid] ?: continue
            levels += (m.level - b.level).coerceAtLeast(0)
            xp += (totalXp(m) - totalXp(b)).coerceAtLeast(0)
            hp += (b.damage - m.damage).coerceAtLeast(0)
        }
        val spent = after.gold < before.gold
        val items = if (spent) emptyMap() else after.inventory.mapNotNull { (item, n) ->
            val gain = n - (before.inventory[item] ?: 0)
            if (gain > 0) item to gain else null
        }.toMap()
        val gear = if (spent) 0 else ((after.gear.size + after.armor.size) - (before.gear.size + before.armor.size)).coerceAtLeast(0)
        return DayTally(
            heroLevels = (after.hero.level - before.hero.level).coerceAtLeast(0),
            monsterLevels = levels,
            monsterXp = xp.toInt(),
            gold = (after.gold - before.gold).coerceAtLeast(0),
            items = items,
            gear = gear,
            hpRestored = hp,
            tasks = (after.stats.tasksCompleted - before.stats.tasksCompleted).coerceAtLeast(0),
        )
    }

    /** Adds what changed between [before] and [after] to today's tally. */
    fun track(before: GameState, after: GameState, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): GameState {
        if (before === after || !before.starterChosen) return after
        val gained = diff(before, after)
        if (gained.isEmpty) return after
        val today = day(nowMs, zone).toEpochDay()
        val kept = after.tallies.filterKeys { it > today - DAYS_KEPT }
        return after.copy(tallies = kept + (today to (kept[today] ?: DayTally()) + gained))
    }

    /** Yesterday's totals. */
    fun yesterday(state: GameState, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): DayTally =
        state.tallies[day(nowMs, zone).toEpochDay() - 1] ?: DayTally()

    /** True on the first open of a new day, from [MORNING_HOUR], when there's something to show. */
    fun due(state: GameState, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val t = Instant.ofEpochMilli(nowMs).atZone(zone)
        if (t.hour < MORNING_HOUR || !state.starterChosen) return false
        val today = t.toLocalDate().toEpochDay()
        if (state.recapShownDay == today) return false
        return !yesterday(state, nowMs, zone).isEmpty || state.sleep[today] != null
    }

    fun markShown(state: GameState, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): GameState =
        state.copy(recapShownDay = day(nowMs, zone).toEpochDay())
}
