package com.monsterquest.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The Health section: steps by hour and a simple water tracker. It only
 * helps the player keep track; nothing here pays rewards.
 */
object Health {
    /** A good day's walking (the calendar marks days that reach it). */
    const val STEP_GOAL = 5_000
    /** One glass of water: 8 oz, or 250 ml in metric. */
    const val GLASS_ML = 250
    const val GLASS_OZ = 8
    const val DEFAULT_WATER_GOAL = 8
    val WATER_GOALS = 4..16
    /** No one logs more than this in a day; stops runaway taps. */
    const val MAX_GLASSES = 30
    /** Hourly steps kept for the last week or so. */
    private const val HOURS_KEPT = 8 * 24L
    private const val WATER_DAYS_KEPT = 90

    /** Key for one hour of one local day: epoch day × 24 + hour (works in half-hour time zones too). */
    fun hourKey(day: LocalDate, hour: Int): Long = day.toEpochDay() * 24 + hour

    private fun hourKeyAt(ms: Long, zone: ZoneId): Long {
        val t = Instant.ofEpochMilli(ms).atZone(zone)
        return hourKey(t.toLocalDate(), t.hour)
    }

    /** Adds [steps] to the hour they were walked in. Called from [GameEngine.walk]. */
    fun recordSteps(state: GameState, steps: Int, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): GameState {
        if (steps <= 0) return state
        val key = hourKeyAt(nowMs, zone)
        val kept = state.stepsByHour.filterKeys { it > key - HOURS_KEPT }
        return state.copy(stepsByHour = kept + (key to (kept[key] ?: 0) + steps))
    }

    /** Steps in each hour (0..23) of [day], as the game counted them. */
    fun hourly(state: GameState, day: LocalDate): List<Int> =
        (0 until 24).map { state.stepsByHour[hourKey(day, it)] ?: 0 }

    fun glasses(state: GameState, day: Long): Int = state.water[day] ?: 0

    /** Adds (or with a negative [delta], takes back) glasses of water for today. */
    fun addWater(state: GameState, delta: Int, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): GameState {
        val day = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toEpochDay()
        val n = (glasses(state, day) + delta).coerceIn(0, MAX_GLASSES)
        val kept = state.water.filterKeys { it > day - WATER_DAYS_KEPT }
        return state.copy(water = if (n == 0) kept - day else kept + (day to n))
    }

    fun setWaterGoal(state: GameState, goal: Int): GameState = state.copy(waterGoal = goal.coerceIn(WATER_GOALS.first, WATER_GOALS.last))

    /** "1.5 L" / "40 oz" for [glasses]. */
    fun waterAmount(glasses: Int, imperial: Boolean): String =
        if (imperial) "${glasses * GLASS_OZ} oz"
        else (glasses * GLASS_ML).let { ml -> if (ml < 1000) "$ml ml" else "%.2f".format(java.util.Locale.US, ml / 1000.0).trimEnd('0').trimEnd('.') + " L" }

    /** A step record from a health app: [count] steps between [startMs] and [endMs]. */
    data class StepSpan(val startMs: Long, val endMs: Long, val count: Long)

    /** Records longer than this are too coarse to say when the steps happened. */
    const val COARSE_SPAN_MS = 2 * 60 * 60_000L

    /**
     * Steps in each hour (0..23) of [day], from health-app records, each spread
     * evenly over the time it covers (so a record that starts at midnight
     * doesn't pile everything into 12 AM). Null when most steps come from
     * records too long to place ([COARSE_SPAN_MS]): the chart then uses the
     * game's own counts.
     */
    fun spreadByHour(spans: List<StepSpan>, day: LocalDate, zone: ZoneId): List<Int>? {
        val total = spans.sumOf { it.count }
        if (total <= 0) return List(24) { 0 }
        val coarse = spans.filter { it.endMs - it.startMs > COARSE_SPAN_MS }.sumOf { it.count }
        if (coarse * 2 > total) return null
        val out = DoubleArray(24)
        for (sp in spans) {
            val len = (sp.endMs - sp.startMs).coerceAtLeast(1)
            for (h in 0 until 24) {
                // Hour boundaries in real time (DST-safe).
                val hs = day.atStartOfDay(zone).plusHours(h.toLong()).toInstant().toEpochMilli()
                val he = day.atStartOfDay(zone).plusHours(h + 1L).toInstant().toEpochMilli()
                val overlap = minOf(he, sp.endMs) - maxOf(hs, sp.startMs)
                if (sp.endMs == sp.startMs && sp.startMs in hs until he) out[h] += sp.count.toDouble()
                else if (overlap > 0) out[h] += sp.count * overlap.toDouble() / len
            }
        }
        return out.map { Math.round(it).toInt() }
    }

    /** "12a", "6a", "12p", "3p"... for chart labels. */
    fun hourLabel(hour: Int): String = "${if (hour % 12 == 0) 12 else hour % 12}${if (hour < 12) "a" else "p"}"
}
