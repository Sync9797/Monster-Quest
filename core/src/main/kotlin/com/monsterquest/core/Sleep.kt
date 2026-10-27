package com.monsterquest.core

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * One night's sleep, from Health Connect (Samsung Health, Fitbit, Google Fit...)
 * or logged by hand. Stage minutes are 0 when the source didn't record stages.
 */
@Serializable
data class SleepNight(
    val startMs: Long,
    val endMs: Long,
    val deepMin: Int = 0,
    val lightMin: Int = 0,
    val remMin: Int = 0,
    val awakeMin: Int = 0,
    val manual: Boolean = false,
) {
    /** Time actually asleep (in bed, minus time awake). */
    val asleepMin: Int get() = ((endMs - startMs) / 60_000L).toInt() - awakeMin
    val hasStages: Boolean get() = deepMin + lightMin + remMin > 0
}

/** The sleep log: one night per wake-up day, about two months kept. No rewards; it's just for you. */
object SleepLog {
    /** A good night (8 hours) fills the chart's bar. */
    const val GOAL_MIN = 8 * 60
    private const val DAYS_KEPT = 60

    fun wakeDay(n: SleepNight, zone: ZoneId): Long = Instant.ofEpochMilli(n.endMs).atZone(zone).toLocalDate().toEpochDay()

    /**
     * Saves [night] under the day it ended. A synced night replaces a hand-logged one,
     * and a hand-logged one never overwrites a synced one.
     */
    fun record(state: GameState, night: SleepNight, zone: ZoneId = ZoneId.systemDefault()): GameState {
        if (night.endMs <= night.startMs) return state
        val day = wakeDay(night, zone)
        val existing = state.sleep[day]
        if (existing != null && !existing.manual && night.manual) return state
        // Two synced sessions the same morning (a nap, a split night): keep the longer.
        if (existing != null && !existing.manual && !night.manual && existing.asleepMin > night.asleepMin) return state
        val kept = state.sleep.filterKeys { it > day - DAYS_KEPT }
        return state.copy(sleep = kept + (day to night))
    }

    /** Last night: the sleep that ended today, or null if nothing was logged yet. */
    fun lastNight(state: GameState, today: LocalDate): SleepNight? = state.sleep[today.toEpochDay()]

    /** The last 7 days ending [today], oldest first, with the night (if any) that ended on each. */
    fun week(state: GameState, today: LocalDate): List<Pair<LocalDate, SleepNight?>> =
        (6 downTo 0).map { today.minusDays(it.toLong()) }.map { it to state.sleep[it.toEpochDay()] }

    /** Average sleep over the nights logged this week, in minutes, or null if none. */
    fun weekAverage(state: GameState, today: LocalDate): Int? =
        week(state, today).mapNotNull { it.second?.asleepMin }.takeIf { it.isNotEmpty() }?.average()?.toInt()

    /** "7h 35m". */
    fun label(minutes: Int): String = "${minutes / 60}h ${"%02d".format(minutes % 60)}m"
}
