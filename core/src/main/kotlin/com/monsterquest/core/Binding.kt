package com.monsterquest.core

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** How well a binding glyph was traced: speed and accuracy together. */
enum class TraceGrade(val label: String, val multiplier: Double) {
    /** Missed most of the glyph or scribbled: the cast barely holds. */
    FIZZLE("Fizzle", 0.3),
    FAINT("Faint", 0.65),
    GOOD("Good", 1.0),
    STRONG("Strong", 1.3),
    PERFECT("Perfect", 1.6),
}

/**
 * How a Binding cast played out, for the shake animation: the crystal shakes
 * [shakes] times, then seals ([success]) or breaks. A tame always shakes 3
 * times (a critical catch once); a near miss shakes twice, a clear miss less.
 */
data class CastRoll(val success: Boolean, val shakes: Int, val critical: Boolean = false)

/** Shakes for a cast that rolled [roll] (0..1) against [chance]. */
fun shakesFor(success: Boolean, critical: Boolean, roll: Double, chance: Double): Int = when {
    critical -> 1
    success -> 3
    roll < chance * 1.5 -> 2
    roll < chance * 2.5 -> 1
    else -> 0
}

/** A point in a glyph's unit square (0..1 on both axes, y down). */
data class GlyphPoint(val x: Float, val y: Float)

/** One glyph to trace: a single continuous stroke, and the time allowed once you start. */
data class Glyph(val name: String, val points: List<GlyphPoint>, val timeLimitMs: Long)

/**
 * The Binding Tome: the hero's capture spell. It costs no Energy. Each cast
 * uses a Binding Crystal; you trace the glyph that appears (its shape follows
 * the monster's type, longer and quicker for rarer monsters), and the trace's
 * speed and accuracy multiply the crystal's taming odds.
 */
object Binding {
    const val SPELL = "Binding"

    // ---- glyph shapes, one family per type (unit square, single stroke) ----

    private fun pts(vararg xy: Float) = xy.toList().chunked(2).map { GlyphPoint(it[0], it[1]) }

    private fun spiral(): List<GlyphPoint> = (0..28).map { i ->
        val t = i / 28.0
        val a = t * Math.PI * 3.2
        val r = 0.08 + 0.38 * t
        GlyphPoint((0.5 + r * cos(a)).toFloat(), (0.5 + r * sin(a)).toFloat())
    }

    private fun crescent(): List<GlyphPoint> {
        val outer = (0..14).map { i -> val a = Math.PI * (0.25 + 1.5 * i / 14); GlyphPoint((0.5 + 0.4 * cos(a)).toFloat(), (0.5 + 0.4 * sin(a)).toFloat()) }
        val inner = (14 downTo 0).map { i -> val a = Math.PI * (0.4 + 1.2 * i / 14); GlyphPoint((0.62 + 0.25 * cos(a)).toFloat(), (0.5 + 0.25 * sin(a)).toFloat()) }
        return outer + inner
    }

    private fun circle(): List<GlyphPoint> = (0..24).map { i ->
        val a = -Math.PI / 2 + 2 * Math.PI * i / 24
        GlyphPoint((0.5 + 0.4 * cos(a)).toFloat(), (0.5 + 0.4 * sin(a)).toFloat())
    }

    private fun base(e: Element): Pair<String, List<GlyphPoint>> = when (e) {
        Element.FIRE -> "Flame" to pts(0.35f, 0.95f, 0.18f, 0.6f, 0.45f, 0.5f, 0.32f, 0.2f, 0.6f, 0.03f, 0.72f, 0.4f, 0.55f, 0.55f, 0.78f, 0.75f, 0.6f, 0.95f)
        Element.ELECTRIC -> "Bolt" to pts(0.65f, 0.02f, 0.28f, 0.52f, 0.58f, 0.5f, 0.3f, 0.98f)
        Element.WATER -> "Wave" to pts(0.02f, 0.5f, 0.18f, 0.28f, 0.34f, 0.5f, 0.5f, 0.72f, 0.66f, 0.5f, 0.82f, 0.28f, 0.98f, 0.5f)
        Element.FLIGHT -> "Wing" to pts(0.02f, 0.3f, 0.25f, 0.72f, 0.5f, 0.38f, 0.75f, 0.72f, 0.98f, 0.3f)
        Element.GAS -> "Spiral" to spiral()
        Element.CORRUPT -> "Crescent" to crescent()
        Element.EARTH -> "Peaks" to pts(0.02f, 0.9f, 0.3f, 0.28f, 0.5f, 0.62f, 0.72f, 0.18f, 0.98f, 0.9f)
        Element.NEUTRAL -> "Ring" to circle()
    }

    /** Fit [p] into the unit square with a margin, keeping its shape. */
    private fun fit(p: List<GlyphPoint>): List<GlyphPoint> {
        val minX = p.minOf { it.x }; val maxX = p.maxOf { it.x }
        val minY = p.minOf { it.y }; val maxY = p.maxOf { it.y }
        val s = 0.84f / maxOf(maxX - minX, maxY - minY, 0.01f)
        val ox = (1 - (maxX - minX) * s) / 2
        val oy = (1 - (maxY - minY) * s) / 2
        return p.map { GlyphPoint(ox + (it.x - minX) * s, oy + (it.y - minY) * s) }
    }

    /** How many times a stroke crosses itself (kept low so glyphs stay readable). */
    fun crossings(p: List<GlyphPoint>): Int {
        fun ccw(a: GlyphPoint, b: GlyphPoint, c: GlyphPoint) = (c.y - a.y) * (b.x - a.x) > (b.y - a.y) * (c.x - a.x)
        fun cross(a: GlyphPoint, b: GlyphPoint, c: GlyphPoint, d: GlyphPoint) = ccw(a, c, d) != ccw(b, c, d) && ccw(a, b, c) != ccw(a, b, d)
        var n = 0
        for (i in 0 until p.size - 1) for (j in i + 2 until p.size - 1) if (cross(p[i], p[i + 1], p[j], p[j + 1])) n++
        return n
    }

    /** The candidate that crosses itself least (the first, on a tie). */
    private fun clearest(candidates: List<List<GlyphPoint>>): List<GlyphPoint> = candidates.minBy { crossings(fit(it)) }

    /** The shape with a short tail off its end: down, right, up or left. */
    private fun tails(shape: List<GlyphPoint>): List<List<GlyphPoint>> {
        val e = shape.last()
        return listOf(0f to 0.35f, 0.35f to 0f, 0f to -0.35f, -0.35f to 0f).map { (dx, dy) -> shape + GlyphPoint(e.x + dx, e.y + dy) }
    }

    /** The shape and a copy of it: mirrored or repeated, beside it or below it. */
    private fun doubles(shape: List<GlyphPoint>): List<List<GlyphPoint>> = listOf(
        shape + shape.reversed().map { GlyphPoint(2.1f - it.x, it.y) },
        shape + shape.reversed().map { GlyphPoint(it.x, 2.1f - it.y) },
        shape + shape.map { GlyphPoint(it.x + 1.1f, it.y) },
        shape + shape.map { GlyphPoint(it.x, it.y + 1.1f) },
    )

    fun length(p: List<GlyphPoint>): Float = p.zipWithNext().sumOf { (a, b) -> hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()) }.toFloat()

    /** Crystal tiers, weakest first: better crystals also give a little more time to trace. */
    private val crystals = listOf(Item.TAMING_ORB, Item.GREAT_ORB, Item.MEGA_ORB, Item.ULTRA_ORB, Item.MASTER_ORB, Item.PERFECT_ORB)

    /** Every cast with a Perfect Crystal tames (no trace needed). */
    fun needsTrace(crystal: Item): Boolean = crystal != Item.PERFECT_ORB

    /**
     * The glyph for taming [species] with [crystal]: its main type's shape;
     * uncommon ones add a flourish, rare and stronger ones double back on
     * themselves. Rarer monsters also give less time.
     */
    fun glyphFor(species: Species, crystal: Item = Item.TAMING_ORB): Glyph {
        val (name, shape) = base(species.element)
        val stroke = when (species.rarity) {
            Rarity.COMMON -> shape
            Rarity.UNCOMMON -> clearest(tails(shape))
            // Rare and up: the shape twice (side by side or stacked), whichever way crosses itself least.
            else -> clearest(doubles(shape) + tails(shape).flatMap { tails(it) })
        }
        val points = fit(stroke)
        val rarityPace = when (species.rarity) {
            Rarity.COMMON -> 1.0
            Rarity.UNCOMMON -> 0.92
            Rarity.RARE -> 0.85
            Rarity.EPIC -> 0.78
            else -> 0.72
        }
        val tier = crystals.indexOf(crystal).coerceAtLeast(0)
        val limit = (900 + 750 * length(points)) * rarityPace * (1 + 0.1 * tier)
        return Glyph(name, points, limit.toLong())
    }

    // ---- scoring ----

    private fun resample(p: List<GlyphPoint>, n: Int): List<GlyphPoint> {
        if (p.size < 2) return List(n) { p.firstOrNull() ?: GlyphPoint(0.5f, 0.5f) }
        val total = length(p)
        if (total <= 0f) return List(n) { p.first() }
        val step = total / (n - 1)
        val out = mutableListOf(p.first())
        var acc = 0f
        var i = 1
        var prev = p.first()
        while (i < p.size && out.size < n) {
            val cur = p[i]
            val d = hypot((cur.x - prev.x).toDouble(), (cur.y - prev.y).toDouble()).toFloat()
            if (acc + d >= step && d > 0f) {
                val t = (step - acc) / d
                val q = GlyphPoint(prev.x + t * (cur.x - prev.x), prev.y + t * (cur.y - prev.y))
                out += q
                prev = q
                acc = 0f
            } else {
                acc += d
                prev = cur
                i++
            }
        }
        while (out.size < n) out += p.last()
        return out
    }

    private fun nearest(q: GlyphPoint, set: List<GlyphPoint>): Float =
        set.minOf { hypot((it.x - q.x).toDouble(), (it.y - q.y).toDouble()) }.toFloat()

    /** How close (unit square) a trace must pass to count as on the line. */
    private const val ON_LINE = 0.1f

    /** How much of the glyph (0..1) the trace passed over. */
    fun coverage(glyph: Glyph, trace: List<GlyphPoint>): Double {
        if (trace.size < 2) return 0.0
        val g = resample(glyph.points, 64)
        val t = resample(trace, 96)
        return g.count { nearest(it, t) < ON_LINE } / g.size.toDouble()
    }

    /** Share of the trace (0..1) that wandered off the glyph. */
    fun offPath(glyph: Glyph, trace: List<GlyphPoint>): Double {
        if (trace.size < 2) return 1.0
        val g = resample(glyph.points, 96)
        val t = resample(trace, 64)
        return t.count { nearest(it, g) >= ON_LINE } / t.size.toDouble()
    }

    /**
     * Accuracy 0..1: covering the glyph, staying on it, and not drawing much
     * more than it (so scribbling over everything scores badly).
     */
    fun accuracy(glyph: Glyph, trace: List<GlyphPoint>): Double {
        if (trace.size < 2) return 0.0
        val cov = coverage(glyph, trace)
        val stay = 1 - offPath(glyph, trace)
        // Up to 40% longer than the glyph is fine (wobbles); beyond that it's scribbling.
        val extra = (length(trace) / length(glyph.points).coerceAtLeast(0.01f) - 1.4).coerceAtLeast(0.0)
        val tidy = (1 - extra).coerceIn(0.0, 1.0)
        return cov * cov * stay * stay * tidy
    }

    /** Speed 0..1: full marks finishing within half the time, nothing at the limit. */
    fun speed(glyph: Glyph, durationMs: Long): Double {
        val t = durationMs.toDouble() / glyph.timeLimitMs
        return ((1 - t) / 0.5).coerceIn(0.0, 1.0)
    }

    /** The grade for tracing [glyph] as [trace] (unit-square points) in [durationMs]. */
    fun grade(glyph: Glyph, trace: List<GlyphPoint>, durationMs: Long): TraceGrade {
        // Speed only counts for the part of the glyph you actually traced.
        val acc = accuracy(glyph, trace)
        val score = 0.75 * acc + 0.25 * speed(glyph, durationMs) * acc
        return when {
            score >= 0.85 -> TraceGrade.PERFECT
            score >= 0.6 -> TraceGrade.STRONG
            score >= 0.4 -> TraceGrade.GOOD
            score >= 0.2 -> TraceGrade.FAINT
            else -> TraceGrade.FIZZLE
        }
    }
}
