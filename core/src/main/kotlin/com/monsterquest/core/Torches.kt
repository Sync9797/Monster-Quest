package com.monsterquest.core

import java.time.Instant
import java.time.ZoneId

/**
 * Torches light your way into dungeons. Walking earns them
 * ([STEPS_PER_TORCH] steps each), a few wait for you every morning, and a
 * good night's sleep adds more.
 */
object Torches {
    const val STEPS_PER_TORCH = 500
    const val MORNING = 3
    const val MAX = 30

    fun add(state: GameState, n: Int): GameState = state.copy(torches = (state.torches + n).coerceIn(0, MAX))

    /** Walking: steps build toward the next torch. Returns the state and how many torches were earned. */
    fun addSteps(state: GameState, steps: Int): Pair<GameState, Int> {
        if (steps <= 0) return state to 0
        val bank = state.torchSteps + steps
        val earned = bank / STEPS_PER_TORCH
        val before = state.torches
        val s = add(state.copy(torchSteps = bank % STEPS_PER_TORCH), earned)
        return s to (s.torches - before)
    }

    /** The morning's torches, once a day (the first time you open the game). */
    fun morning(state: GameState, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Update? {
        val day = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toEpochDay()
        if (state.torchGiftDay == day) return null
        val s = add(state.copy(torchGiftDay = day), MORNING)
        return Update(s, "Good morning! $MORNING torches are waiting for you (${s.torches} in all).")
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
                    s = Torches.add(s, WELL_RESTED_TORCHES)
                    "Well rested (${SleepLog.label(night.asleepMin)})! Your hero and monsters fight 10% better today, and +$WELL_RESTED_TORCHES torches."
                }
                TIRED -> "Short night (${SleepLog.label(night.asleepMin)}): your team is tired, 10% weaker today. Aim for 7+ hours."
                NORMAL -> ""
            }
            return Update(s, text)
        }
    }
}
