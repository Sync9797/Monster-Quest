package com.monsterquest.core

/**
 * Store prices rise when you keep buying the same thing, like a shop marking
 * up what's selling fast. It nudges you to walk to heal instead of buying your
 * way out. Each item has its own "demand": the first [FREE_UNITS] cost the
 * usual price, each one after adds [STEP] (up to [MAX_MULTIPLIER]x). Demand
 * cools by one unit every [COOL_MS] and is gone the next day.
 */
object Prices {
    const val FREE_UNITS = 3
    const val STEP = 0.15
    const val MAX_MULTIPLIER = 2.5
    const val COOL_MS = 60 * 60_000L

    /** How much of [item] counts as bought lately (cooled down to [nowMs]). */
    fun demand(state: GameState, item: Item, nowMs: Long): Double {
        if (state.demandDay != GameEngine.epochDay(nowMs)) return 0.0
        val (units, atMs) = state.demand[item] ?: return 0.0
        return (units - (nowMs - atMs).coerceAtLeast(0).toDouble() / COOL_MS).coerceAtLeast(0.0)
    }

    private fun multiplierAt(demand: Double): Double = (1 + STEP * (demand - FREE_UNITS + 1).coerceAtLeast(0.0)).coerceAtMost(MAX_MULTIPLIER)

    /** What the next one costs now, and its markup (1 = the usual price). */
    fun multiplier(state: GameState, item: Item, nowMs: Long): Double = multiplierAt(demand(state, item, nowMs))

    fun unitPrice(state: GameState, item: Item, nowMs: Long): Int = Math.round(item.price * multiplier(state, item, nowMs)).toInt()

    /** What [qty] cost bought together (each one raising the next). */
    fun price(state: GameState, item: Item, qty: Int, nowMs: Long): Int {
        val d = demand(state, item, nowMs)
        return (0 until qty).sumOf { Math.round(item.price * multiplierAt(d + it)).toInt() }
    }

    /** Remembers a purchase. */
    fun record(state: GameState, item: Item, qty: Int, nowMs: Long): GameState {
        val day = GameEngine.epochDay(nowMs)
        val fresh = if (state.demandDay == day) state.demand else emptyMap()
        val now = demand(state, item, nowMs)
        return state.copy(demandDay = day, demand = fresh + (item to ((now + qty) to nowMs)))
    }
}
