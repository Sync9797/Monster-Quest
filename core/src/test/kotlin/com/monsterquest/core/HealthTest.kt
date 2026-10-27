package com.monsterquest.core

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class HealthTest {
    private val zone = ZoneOffset.UTC
    private val day = LocalDate.of(2026, 1, 5)
    private fun at(h: Int, m: Int = 0, d: LocalDate = day) = d.atTime(h, m).toInstant(zone).toEpochMilli()

    @Test
    fun walkingFillsTheHourlyChart() {
        var s = GameState()
        s = GameEngine.walk(s, 300, at(8, 10), zone).state
        s = GameEngine.walk(s, 200, at(8, 50), zone).state
        s = GameEngine.walk(s, 1_000, at(17, 5), zone).state
        val hours = Health.hourly(s, day)
        assertEquals(24, hours.size)
        assertEquals(500, hours[8])
        assertEquals(1_000, hours[17])
        assertEquals(1_500, hours.sum())
        // Only about a week is kept.
        s = GameEngine.walk(s, 50, at(9, d = day.plusDays(10)), zone).state
        assertEquals(0, Health.hourly(s, day).sum())
        assertEquals("12a", Health.hourLabel(0))
        assertEquals("3p", Health.hourLabel(15))
    }

    @Test
    fun waterTrackerCountsGlassesWithoutRewards() {
        val start = GameState()
        var s = Health.addWater(start, 1, at(9), zone)
        s = Health.addWater(s, 2, at(12), zone)
        assertEquals(3, Health.glasses(s, day.toEpochDay()))
        s = Health.addWater(s, -5, at(13), zone)
        assertEquals(0, Health.glasses(s, day.toEpochDay()))
        s = Health.addWater(s, 99, at(14), zone)
        assertEquals(Health.MAX_GLASSES, Health.glasses(s, day.toEpochDay()))
        // A new day starts empty.
        assertEquals(0, Health.glasses(s, day.plusDays(1).toEpochDay()))
        // No rewards: gold, XP and items untouched.
        assertEquals(start.gold, s.gold)
        assertEquals(start.hero, s.hero)
        assertEquals(start.inventory, s.inventory)
        assertEquals(16, Health.setWaterGoal(s, 40).waterGoal)
        assertEquals("1.5 L", Health.waterAmount(6, imperial = false))
        assertEquals("48 oz", Health.waterAmount(6, imperial = true))
        assertEquals("750 ml", Health.waterAmount(3, imperial = false))
    }
}
