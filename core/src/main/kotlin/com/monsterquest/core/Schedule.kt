package com.monsterquest.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class WeekProgress(val total: Int, val done: Int) {
    val percent: Int get() = if (total == 0) 0 else done * 100 / total
}

data class Access(val open: Boolean, val reason: String)

/**
 * Weekly rhythm for boss shrines, so the big fights stay special:
 * - Silver shrines open Tuesday and Thursday if at least [SILVER_PERCENT]% of
 *   this week's quests so far are done (and at least [SILVER_MIN_DONE]).
 * - Gold shrines open Saturday and Sunday if at least [GOLD_PERCENT]% of the
 *   Monday–Friday quests were done (and at least [GOLD_MIN_DONE]).
 */
object Schedule {
    const val SILVER_PERCENT = 50
    const val SILVER_MIN_DONE = 2
    const val GOLD_PERCENT = 70
    const val GOLD_MIN_DONE = 5

    private val silverDays = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)
    private val goldDays = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    private fun local(nowMs: Long, zone: ZoneId): ZonedDateTime = Instant.ofEpochMilli(nowMs).atZone(zone)

    /** Monday 00:00 of the current week. */
    fun weekStart(nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        local(nowMs, zone).truncatedTo(ChronoUnit.DAYS)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .toInstant().toEpochMilli()

    /** Saturday 00:00 of the current week (end of the Monday–Friday window). */
    fun workweekEnd(nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(weekStart(nowMs, zone)).atZone(zone).plusDays(5).toInstant().toEpochMilli()

    /**
     * Quests in a window: those scheduled or added in it, plus any completed in
     * it. "Done" means completed within the window.
     */
    fun progress(state: GameState, fromMs: Long, toMs: Long): WeekProgress {
        fun inWindow(t: Long?) = t != null && t >= fromMs && t < toMs
        val relevant = state.tasks.filter { inWindow(it.startMs ?: it.createdAtMs) || inWindow(it.completedAtMs) }
        return WeekProgress(relevant.size, relevant.count { inWindow(it.completedAtMs) })
    }

    fun weekSoFar(state: GameState, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()) =
        progress(state, weekStart(nowMs, zone), nowMs + 1)

    fun workweek(state: GameState, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()) =
        progress(state, weekStart(nowMs, zone), workweekEnd(nowMs, zone))

    private fun met(p: WeekProgress, percent: Int, minDone: Int) = p.done >= minDone && p.percent >= percent

    fun silver(state: GameState, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Access {
        val p = weekSoFar(state, nowMs, zone)
        val goalMet = met(p, SILVER_PERCENT, SILVER_MIN_DONE)
        return when {
            local(nowMs, zone).dayOfWeek !in silverDays ->
                Access(false, "Silver shrines open on Tuesdays and Thursdays.")
            !goalMet -> Access(
                false,
                "Sealed: complete $SILVER_PERCENT% of this week's quests (at least $SILVER_MIN_DONE). " +
                    "So far ${p.done}/${p.total} (${p.percent}%).",
            )
            else -> Access(true, "Open today: you've completed ${p.percent}% of this week's quests.")
        }
    }

    fun gold(state: GameState, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Access {
        val p = workweek(state, nowMs, zone)
        val goalMet = met(p, GOLD_PERCENT, GOLD_MIN_DONE)
        return when {
            local(nowMs, zone).dayOfWeek !in goldDays ->
                Access(false, "Gold shrines open on Saturday and Sunday for heroes who complete $GOLD_PERCENT% of their Monday–Friday quests.")
            !goalMet -> Access(
                false,
                "Sealed this weekend: needed $GOLD_PERCENT% of Monday–Friday quests (at least $GOLD_MIN_DONE). " +
                    "You completed ${p.done}/${p.total} (${p.percent}%).",
            )
            else -> Access(true, "Open this weekend: you completed ${p.percent}% of your Monday–Friday quests!")
        }
    }

    fun access(state: GameState, shrine: Shrine, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Access = when (shrine.tier) {
        ShrineTier.ITEM -> Access(true, "")
        ShrineTier.SILVER -> silver(state, nowMs, zone)
        ShrineTier.GOLD -> gold(state, nowMs, zone)
    }
}
