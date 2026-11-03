package com.monsterquest.core

import java.time.Instant
import java.time.ZoneId

/**
 * Torches light your way into dungeons. Walking earns them
 * ([STEPS_PER_TORCH] steps each), a few wait for you every morning, and a
 * good night's sleep adds more.
 */
object Torches {
    const val STEPS_PER_TORCH = 1_000
    const val MORNING = 3
    /** The most torches anyone can hold (level 40 and up). */
    const val MAX = 30
    /** Earned rewards (chests, long focus sessions, a good night) can go this far over the cap. */
    const val REWARD_OVERFLOW = 5

    /** How many torches a hero of [level] can hold: 10, then 5 more every 10 levels, up to [MAX] at 40. */
    fun cap(level: Int): Int = (10 + 5 * ((level.coerceAtLeast(1)) / 10)).coerceAtMost(MAX)
    fun cap(state: GameState): Int = cap(state.hero.level)

    /** Whether walking and the morning gift have stopped adding torches. */
    fun full(state: GameState): Boolean = state.torches >= cap(state)

    /**
     * Adds torches up to the hero's cap (walking, the morning gift). An earned
     * [reward] can go [REWARD_OVERFLOW] over it so a prize is never wasted.
     * Never takes away torches already held above the cap.
     */
    fun add(state: GameState, n: Int, reward: Boolean = false): GameState {
        if (n <= 0) return state
        val limit = cap(state) + if (reward) REWARD_OVERFLOW else 0
        if (state.torches >= limit) return state
        return state.copy(torches = minOf(state.torches + n, limit))
    }

    /** Walking: steps build toward the next torch. Returns the state and how many torches were earned. */
    fun addSteps(state: GameState, steps: Int): Pair<GameState, Int> {
        if (steps <= 0) return state to 0
        val bank = state.torchSteps + steps
        val earned = bank / STEPS_PER_TORCH
        val before = state.torches
        // At the cap, steps stop banking too, so walking doesn't save up a pile for later.
        if (full(state)) return state.copy(torchSteps = 0) to 0
        val s = add(state.copy(torchSteps = bank % STEPS_PER_TORCH), earned)
        return s to (s.torches - before)
    }

    /** The morning's torches, once a day (the first time you open the game). */
    fun morning(state: GameState, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Update? {
        val day = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toEpochDay()
        if (state.torchGiftDay == day) return null
        val s = add(state.copy(torchGiftDay = day), MORNING)
        val got = s.torches - state.torches
        return Update(
            s,
            if (got == 0) "Good morning! Your torches are full (${s.torches}/${cap(s)}): spend some to keep earning."
            else "Good morning! $got torch${if (got == 1) " is" else "es are"} waiting for you (${s.torches}/${cap(s)}).",
        )
    }
}

/** How rested the hero is today, from last night's sleep. */
@kotlinx.serialization.Serializable
enum class Rest(val label: String, val statFactor: Double) {
    /** 8 hours or more: everyone fights a little better today, and a couple of extra torches. */
    WELL_RESTED("Well rested", 1.1),
    NORMAL("Rested", 1.0),
    /** Under 7 hours: a little worse today. */
    TIRED("Tired", 0.9);

    companion object {
        const val WELL_RESTED_MIN = 8 * 60
        const val TIRED_BELOW_MIN = 7 * 60
        const val WELL_RESTED_TORCHES = 2

        fun of(asleepMin: Int): Rest = when {
            asleepMin >= WELL_RESTED_MIN -> WELL_RESTED
            asleepMin < TIRED_BELOW_MIN -> TIRED
            else -> NORMAL
        }

        /** Today's rest (normal when last night isn't known, or on another day). */
        fun today(state: GameState, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Rest {
            val day = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toEpochDay()
            return if (state.restDay == day) state.rest else NORMAL
        }

        /**
         * Sets today's rest from last night's sleep, once a day: well rested
         * brings extra torches, tired a stat penalty for the day.
         */
        fun update(state: GameState, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Update {
            val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
            if (state.restDay == today.toEpochDay()) return Update(state, "")
            val night = SleepLog.lastNight(state, today) ?: return Update(state, "")
            val rest = of(night.asleepMin)
            var s = state.copy(restDay = today.toEpochDay(), rest = rest)
            val text = when (rest) {
                WELL_RESTED -> {
                    s = Torches.add(s, WELL_RESTED_TORCHES, reward = true)
                    "Well rested (${SleepLog.label(night.asleepMin)})! Your hero and monsters fight 10% better today, and +$WELL_RESTED_TORCHES torches."
                }
                TIRED -> "Short night (${SleepLog.label(night.asleepMin)}): your team is tired, 10% weaker today. Aim for 7+ hours."
                NORMAL -> ""
            }
            return Update(s, text)
        }
    }
}
