package com.monsterquest.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlyphFairnessTest {
    @Test
    fun glyphsStayReadableAndScribblingFails() {
        for (sp in Bestiary.all) {
            val g = Binding.glyphFor(sp)
            // Readable: a glyph crosses itself at most once.
            assertTrue(Binding.crossings(g.points) <= 1, "${sp.id} ${g.name} crosses ${Binding.crossings(g.points)}x")
            // Tracing it well is Perfect; scribbling over the whole square fizzles.
            assertEquals(TraceGrade.PERFECT, Binding.grade(g, g.points, g.timeLimitMs / 3), sp.id)
            val rng = Random(sp.id.hashCode())
            val scribble = (0..80).map { GlyphPoint(rng.nextFloat(), rng.nextFloat()) }
            assertEquals(TraceGrade.FIZZLE, Binding.grade(g, scribble, g.timeLimitMs / 3), sp.id)
            val zigzag = (0..40).map { i -> GlyphPoint(if (i % 2 == 0) 0.05f else 0.95f, i / 40f) }
            assertEquals(TraceGrade.FIZZLE, Binding.grade(g, zigzag, g.timeLimitMs / 3), sp.id)
            // Only half of it is at best Good.
            assertTrue(Binding.grade(g, g.points.take(g.points.size / 2), g.timeLimitMs / 3) <= TraceGrade.GOOD, sp.id)
        }
        // A fizzled cast is much weaker than a Faint one.
        assertTrue(TraceGrade.FIZZLE.multiplier < 0.5)
    }
}
