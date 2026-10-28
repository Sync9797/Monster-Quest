package com.monsterquest.core

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HealthSpreadTest {
    private val day = LocalDate.of(2026, 10, 28)
    private val zone = ZoneOffset.UTC
    private fun at(h: Int, m: Int = 0) = day.atTime(h, m).toInstant(zone).toEpochMilli()

    @Test
    fun recordsSpreadOverTheHoursTheyCover() {
        // A 30-minute walk at 5:45 pm splits across 5 and 6 pm.
        val hours = Health.spreadByHour(listOf(Health.StepSpan(at(17, 45), at(18, 15), 2000)), day, zone)!!
        assertEquals(1000, hours[17])
        assertEquals(1000, hours[18])
        assertEquals(0, hours[0])
    }

    @Test
    fun coarseMidnightRecordsDontPileUpAt12am() {
        // One record from midnight to 7 pm holding most of the day's steps: too coarse to place.
        val spans = listOf(Health.StepSpan(at(0), at(19), 9000), Health.StepSpan(at(12), at(12, 30), 500))
        assertEquals(null, Health.spreadByHour(spans, day, zone))
        // Fine-grained records are placed where they happened; the busiest hour is the real one.
        val fine = listOf(Health.StepSpan(at(0, 5), at(0, 10), 50), Health.StepSpan(at(12), at(12, 50), 4000), Health.StepSpan(at(8), at(8, 20), 1200))
        val hours = Health.spreadByHour(fine, day, zone)!!
        assertEquals(12, hours.indices.maxBy { hours[it] })
        assertTrue(hours[0] < hours[12])
    }
}
