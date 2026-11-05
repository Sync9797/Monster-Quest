package com.monsterquest.core

import kotlin.random.Random

/**
 * The weekly bounty board: the same three real-life goals every week (walk,
 * stay phone-free for long stretches, get things done). Clearing all three
 * pays a guaranteed Rare-or-better weapon or armor piece and a Resonant
 * Crystal. Weeks run Sunday to Saturday, like the weekly goals, and progress
 * counts from the week's start ([GameState.weekBaseline]).
 */
object Bounty {
    const val STEPS = 40_000
    const val LONG_FOCUS_MIN = 60
    const val LONG_FOCUS_SESSIONS = 2
    const val QUESTS = 10
    const val GOLD = 600

    data class Line(val text: String, val goal: Int, val progress: Int) {
        val done: Boolean get() = progress >= goal
    }

    fun lines(state: GameState): List<Line> {
        val b = state.weekBaseline
        val n = state.stats
        fun clamp(x: Long, goal: Int) = x.coerceIn(0, goal.toLong()).toInt()
        return listOf(
            Line("Walk ${"%,d".format(STEPS)} steps", STEPS, clamp(n.totalSteps - b.totalSteps, STEPS)),
            Line("$LONG_FOCUS_SESSIONS phone-free sessions of $LONG_FOCUS_MIN+ minutes", LONG_FOCUS_SESSIONS, clamp((n.longFocusWins - b.longFocusWins).toLong(), LONG_FOCUS_SESSIONS)),
            Line("Complete $QUESTS quests", QUESTS, clamp((n.tasksCompleted - b.tasksCompleted).toLong(), QUESTS)),
        )
    }

    fun id(epochDay: Long) = "b:${Goals.weekOf(epochDay)}"

    fun claimed(state: GameState, epochDay: Long) = id(epochDay) in state.goalsClaimed

    fun ready(state: GameState, epochDay: Long) = !claimed(state, epochDay) && lines(state).all { it.done }

    /** Pays the bounty: gold, a Resonant Crystal and a Rare-or-better weapon or armor piece (a quarter of the time Epic). */
    fun claim(state: GameState, epochDay: Long, rng: Random = Random.Default): Update {
        if (claimed(state, epochDay)) return Update(state, "This week's bounty is already paid.")
        val left = lines(state).filter { !it.done }
        if (left.isNotEmpty()) return Update(state, "Still to do: ${left.joinToString { "${it.text} (${"%,d".format(it.progress)}/${"%,d".format(it.goal)})" }}.")
        val minimum = if (rng.nextDouble() < 0.25) GearRarity.EPIC else GearRarity.RARE
        val level = state.hero.level
        var s = state.copy(gold = state.gold + GOLD, goalsClaimed = state.goalsClaimed + id(epochDay))
        s = GameEngine.addItems(s, mapOf(Item.RESONANT_CRYSTAL to 1), overfill = true).first
        val loot = mutableListOf<Reward>(Reward.Gold(GOLD), Reward.Items(Item.RESONANT_CRYSTAL, 1))
        val prize = if (rng.nextBoolean()) {
            val w = Armory.roll(level, rng, luck = 10, minimum = minimum)
            s = s.copy(gear = s.gear + w)
            loot += Reward.WeaponDrop(w)
            "${w.rarity.label} ${w.name}"
        } else {
            val a = Armory.rollArmor(level, rng, luck = 10, minimum = minimum)
            s = s.copy(armor = s.armor + a)
            loot += Reward.ArmorDrop(a)
            "${a.rarity.label} ${a.name}"
        }
        return Update(s, "Bounty paid! +$GOLD gold, a Resonant Crystal and a $prize.", rewards = loot, rewardTitle = "Weekly bounty")
    }
}
