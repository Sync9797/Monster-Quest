package com.monsterquest.core

import kotlinx.serialization.Serializable

/** What today's completed quests have used up (reset each day). */
@Serializable
data class QuestLimits(
    val day: Long? = null,
    /** Quests that paid something today. */
    val rewarded: Int = 0,
    /** Quests paid at the Hard rate today. */
    val hard: Int = 0,
    /** Quest names completed today (normalized) and how often. */
    val titles: Map<String, Int> = emptyMap(),
) {
    fun on(day: Long): QuestLimits = if (this.day == day) this else QuestLimits(day = day)
}

/**
 * Keeps quest rewards honest without getting in the way of real to-do lists:
 * - A quest pays only once it has existed for [MIN_AGE_MS] (calendar quests: once they've started).
 * - The first [FULL_PER_DAY] paying quests a day pay in full, the next [HALF_PER_DAY] pay half, then nothing.
 * - Only [HARD_PER_DAY] quests a day pay at the Hard rate; the rest pay as Normal.
 * - The same quest name done again the same day pays half.
 * Unpaid quests still count as done (for the calendar and streaks).
 */
object QuestRewards {
    const val MIN_AGE_MS = 15 * 60_000L
    const val FULL_PER_DAY = 8
    const val HALF_PER_DAY = 4
    const val HARD_PER_DAY = 3

    fun normalize(title: String): String = title.trim().lowercase().replace(Regex("\\s+"), " ")

    /** Why a quest pays nothing yet, or null if it's old enough. */
    fun tooSoon(task: QuestTask, nowMs: Long): Long? {
        val from = if (task.source == TaskSource.CALENDAR) task.startMs else task.createdAtMs?.plus(MIN_AGE_MS)
        return from?.takeIf { nowMs < it }?.let { it - nowMs }
    }

    data class Payout(
        val difficulty: Difficulty,
        /** 1.0, 0.5 or 0.0. */
        val share: Double,
        val xp: Int,
        val gold: Int,
        val token: Boolean,
        /** Why it paid less than usual (for the message). */
        val notes: List<String>,
        val limits: QuestLimits,
    )

    fun payout(state: GameState, task: QuestTask, nowMs: Long): Payout {
        val day = GameEngine.epochDay(nowMs)
        val l = state.questLimits.on(day)
        val title = normalize(task.title)
        val repeats = l.titles[title] ?: 0
        val logged = l.copy(titles = l.titles + (title to repeats + 1))
        tooSoon(task, nowMs)?.let { left ->
            val why = if (task.source == TaskSource.CALENDAR) "it hasn't started yet" else "quests pay out ${MIN_AGE_MS / 60_000} minutes after they're added (${(left + 59_999) / 60_000} min to go)"
            return Payout(task.difficulty, 0.0, 0, 0, false, listOf("No reward: $why."), logged)
        }
        val notes = mutableListOf<String>()
        var share = when {
            l.rewarded < FULL_PER_DAY -> 1.0
            l.rewarded < FULL_PER_DAY + HALF_PER_DAY -> 0.5.also { notes += "Half reward: you've had today's $FULL_PER_DAY full-reward quests." }
            else -> 0.0.also { notes += "No reward: today's quest rewards are used up. Back tomorrow!" }
        }
        if (share > 0 && repeats > 0) {
            share /= 2
            notes += "Half reward: you already did \"${task.title.trim()}\" today."
        }
        var d = task.difficulty
        if (d == Difficulty.HARD && l.hard >= HARD_PER_DAY && share > 0) {
            d = Difficulty.NORMAL
            notes += "Paid as Normal: only $HARD_PER_DAY Hard quests a day pay the Hard rate."
        }
        val paid = share > 0
        return Payout(
            d, share, (d.xp * share).toInt(), (d.gold * share).toInt(), paid, notes,
            logged.copy(rewarded = l.rewarded + if (paid) 1 else 0, hard = l.hard + if (paid && d == Difficulty.HARD) 1 else 0),
        )
    }

    /** Full-reward quests left today, half-reward ones after that, and Hard quests left. */
    data class Status(val fullLeft: Int, val halfLeft: Int, val hardLeft: Int)

    fun status(state: GameState, nowMs: Long): Status {
        val l = state.questLimits.on(GameEngine.epochDay(nowMs))
        return Status(
            (FULL_PER_DAY - l.rewarded).coerceAtLeast(0),
            (FULL_PER_DAY + HALF_PER_DAY - l.rewarded).coerceIn(0, HALF_PER_DAY),
            (HARD_PER_DAY - l.hard).coerceAtLeast(0),
        )
    }
}
