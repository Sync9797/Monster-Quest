package com.monsterquest.core

import kotlin.random.Random

/** A daily or weekly challenge: progress since the period began, and a reward. */
data class Goal(
    /** "d:<day>:<n>" or "w:<week>:<n>": unique per period, so claims don't carry over. */
    val id: String,
    val text: String,
    val goal: Int,
    val gold: Int,
    val item: Item?,
    /** Progress from the stats at the start of the period ([base]) to now ([now]). */
    val progress: (base: Lifetime, now: Lifetime) -> Int,
)

/**
 * Three goals a day and three a week, picked from a pool by the date (the same
 * for the whole day or week). Progress counts from the moment the period
 * began. Finishing all three of a day (or a week) pays a bonus.
 */
object Goals {
    const val PER_PERIOD = 3

    /** Weeks run Monday to Sunday (epoch day 0 was a Thursday). */
    fun weekOf(epochDay: Long): Long = Math.floorDiv(epochDay + 3, 7L)

    private data class Template(
        val text: (Int, Element) -> String,
        val goal: (Int) -> Int,
        val progress: (Lifetime, Lifetime, Element) -> Int,
    )

    private fun steps(b: Lifetime, n: Lifetime) = (n.totalSteps - b.totalSteps).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
    private fun typed(b: Lifetime, n: Lifetime, e: Element) = (n.tamedByType[e] ?: 0) - (b.tamedByType[e] ?: 0)

    private val dailyPool = listOf(
        Template({ g, _ -> "Win $g battles" }, { 3 }, { b, n, _ -> n.battlesWon - b.battlesWon }),
        Template({ g, _ -> "Tame $g monsters" }, { 2 }, { b, n, _ -> n.monstersTamed - b.monstersTamed }),
        Template({ g, e -> "Tame a ${e.label} type" }, { 1 }, { b, n, e -> typed(b, n, e) }),
        Template({ g, _ -> "Complete $g quests" }, { 3 }, { b, n, _ -> n.tasksCompleted - b.tasksCompleted }),
        Template({ g, _ -> "Walk ${"%,d".format(g)} steps" }, { 5_000 }, { b, n, _ -> steps(b, n) }),
        Template({ g, _ -> "Visit $g shrines" }, { 3 }, { b, n, _ -> n.shrinesVisited - b.shrinesVisited }),
        Template({ _, _ -> "Finish a phone-free session" }, { 1 }, { b, n, _ -> n.focusWins - b.focusWins }),
    )

    private val weeklyPool = listOf(
        Template({ g, _ -> "Win $g battles" }, { 20 }, { b, n, _ -> n.battlesWon - b.battlesWon }),
        Template({ g, _ -> "Tame $g monsters" }, { 10 }, { b, n, _ -> n.monstersTamed - b.monstersTamed }),
        Template({ g, e -> "Tame $g ${e.label} types" }, { 3 }, { b, n, e -> typed(b, n, e) }),
        Template({ g, _ -> "Complete $g quests" }, { 15 }, { b, n, _ -> n.tasksCompleted - b.tasksCompleted }),
        Template({ g, _ -> "Walk ${"%,d".format(g)} steps" }, { 35_000 }, { b, n, _ -> steps(b, n) }),
        Template({ g, _ -> "Visit $g shrines" }, { 15 }, { b, n, _ -> n.shrinesVisited - b.shrinesVisited }),
        Template({ g, _ -> "Spend $g minutes phone-free" }, { 180 }, { b, n, _ -> n.focusMinutes - b.focusMinutes }),
        Template({ g, _ -> "Beat $g shrine bosses" }, { 2 }, { b, n, _ -> n.bossesDefeated - b.bossesDefeated }),
    )

    private val dailyItems = listOf(Item.POTION, Item.TAMING_ORB, Item.SWEET_BERRY, Item.ETHER)
    private val weeklyItems = listOf(Item.HI_POTION, Item.GREAT_ORB, Item.TREAT, Item.LURE)

    private fun pick(pool: List<Template>, prefix: String, period: Long, gold: Int, items: List<Item>): List<Goal> {
        val rng = Random(period * 7919 + prefix.hashCode())
        // The featured type changes every period.
        val type = Element.typed[Math.floorMod(period, Element.typed.size.toLong()).toInt()]
        return pool.shuffled(rng).take(PER_PERIOD).mapIndexed { i, t ->
            val g = t.goal(0)
            Goal("$prefix:$period:$i", t.text(g, type), g, gold, items.random(rng)) { b, n -> t.progress(b, n, type) }
        }
    }

    fun daily(epochDay: Long): List<Goal> = pick(dailyPool, "d", epochDay, 120, dailyItems)
    fun weekly(epochDay: Long): List<Goal> = pick(weeklyPool, "w", weekOf(epochDay), 500, weeklyItems)

    /** Finishing all three: a Monster Treat for a day, a Golden Berry and gold for a week. */
    const val DAILY_SET_GOLD = 150
    const val WEEKLY_SET_GOLD = 800

    /**
     * Starts a new day or week of goals when the date has moved on. [before]
     * is the state before the latest change, so progress made in it counts.
     */
    fun rollover(before: GameState, after: GameState, epochDay: Long): GameState {
        var s = after
        if (s.goalDay != epochDay) s = s.copy(goalDay = epochDay, dayBaseline = before.stats)
        val week = weekOf(epochDay)
        if (s.goalWeek != week) s = s.copy(goalWeek = week, weekBaseline = before.stats)
        return s
    }

    fun progress(state: GameState, goal: Goal): Int {
        val base = if (goal.id.startsWith("w:")) state.weekBaseline else state.dayBaseline
        return goal.progress(base, state.stats).coerceIn(0, goal.goal)
    }

    fun done(state: GameState, goal: Goal) = progress(state, goal) >= goal.goal

    fun claim(state: GameState, goal: Goal): Update {
        if (goal.id in state.goalsClaimed) return Update(state, "Already claimed.")
        if (!done(state, goal)) return Update(state, "Not done yet: ${progress(state, goal)}/${goal.goal}.")
        var s = state.copy(gold = state.gold + goal.gold, goalsClaimed = state.goalsClaimed + goal.id)
        var text = "Goal done: ${goal.text}! +${goal.gold} gold"
        goal.item?.let { item ->
            val (withItem, lost) = GameEngine.addItems(s, mapOf(item to 1))
            s = withItem
            text += if (lost > 0) " (bag full: no ${item.label})" else ", ${item.label}"
        }
        // The whole set done: a bonus.
        val weekly = goal.id.startsWith("w:")
        val set = if (weekly) weekly(s.goalDay) else daily(s.goalDay)
        if (set.all { it.id in s.goalsClaimed }) {
            val bonusGold = if (weekly) WEEKLY_SET_GOLD else DAILY_SET_GOLD
            val bonusItem = if (weekly) Item.GOLDEN_BERRY else Item.TREAT
            s = GameEngine.addItems(s.copy(gold = s.gold + bonusGold), mapOf(bonusItem to 1)).first
            text += ". All ${if (weekly) "weekly" else "daily"} goals done: +$bonusGold gold and a ${bonusItem.label}"
        }
        // Old claims can go: only this day's and week's matter.
        val keep = setOf("d:${s.goalDay}:", "w:${weekOf(s.goalDay)}:")
        s = s.copy(goalsClaimed = s.goalsClaimed.filter { id -> keep.any { id.startsWith(it) } }.toSet())
        return Update(s, "$text.")
    }
}
