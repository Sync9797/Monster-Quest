package com.monsterquest.core

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemindersTest {
    private val zone = ZoneOffset.UTC
    // Monday 5 January 2026.
    private val monday = LocalDate.of(2026, 1, 5)

    private fun at(day: LocalDate, h: Int, m: Int = 0) = day.atTime(h, m).toInstant(zone).toEpochMilli()

    /** "Make bed" at 6:00 every day but Saturday. */
    private fun withBed(): Pair<GameState, String> {
        val s = Reminders.add(GameState(hero = Hero(level = 10)), "Make bed", 6, 0, (1..7).toSet() - 6, Random(1)).state
        return s to s.reminders.single().id
    }

    @Test
    fun schedulesOnChosenDays() {
        val (s, _) = withBed()
        val r = s.reminders.single()
        assertEquals("Every day but Sat", r.daysLabel)
        assertEquals("6:00 AM", r.timeLabel)
        // Friday after 6: next ring is Sunday (Saturday is skipped).
        val friday = monday.plusDays(4)
        assertEquals(at(friday.plusDays(2), 6), Reminders.nextRing(r, at(friday, 7), zone))
        assertNull(Reminders.nextRing(r.copy(enabled = false), at(friday, 7), zone))
        assertTrue(Reminders.isOpen(r, at(monday, 6, 5), zone))
        assertFalse(Reminders.isOpen(r, at(monday, 5, 59), zone))
    }

    @Test
    fun onTimePaysEarlyAndLateDoNot() {
        val (s, id) = withBed()
        val early = Reminders.complete(s, id, at(monday, 5, 30), zone)
        assertEquals(s.gold, early.state.gold)
        assertTrue("no reward" in early.message)
        val onTime = Reminders.complete(s, id, at(monday, 7), zone)
        assertEquals(s.gold + Reminders.GOLD, onTime.state.gold)
        assertTrue(onTime.state.hero.xp > s.hero.xp || onTime.state.hero.level > s.hero.level)
        val late = Reminders.complete(s, id, at(monday, 10), zone)
        assertEquals(s.gold, late.state.gold)
        // Only once a day.
        assertEquals(onTime.state.gold, Reminders.complete(onTime.state, id, at(monday, 7, 30), zone).state.gold)
    }

    @Test
    fun streaksSkipsAndBonuses() {
        var (s, id) = withBed()
        var day = monday
        var done = 0
        // Two weeks of mornings, with Saturdays off and one skipped Wednesday.
        while (done < 7) {
            val r = s.reminders.single()
            if (r.ringsOn(day)) {
                s = if (day == monday.plusDays(2)) Reminders.skip(s, id, at(day, 6, 10), zone).state
                else Reminders.complete(s, id, at(day, 6, 10), zone).state.also { done++ }
            }
            day = day.plusDays(1)
        }
        val r = s.reminders.single()
        assertEquals(7, Reminders.streak(r, at(day.minusDays(1), 12), zone))
        // The 7th day in a row gave a Monster Treat.
        assertEquals(1, s.count(Item.TREAT))
        // Missing a day: no penalty, the streak just starts over.
        val missedNext = day.plusDays(if (r.ringsOn(day)) 1 else 2)
        val after = Reminders.complete(s, id, at(missedNext, 6, 30), zone).state
        assertEquals(1, Reminders.streak(after.reminders.single(), at(missedNext, 12), zone))
        assertEquals(7, after.reminders.single().bestStreak)
    }

    @Test
    fun remindersShareTheDailyQuestAllowance() {
        var (s, id) = withBed()
        s = s.copy(questLimits = QuestLimits(day = monday.toEpochDay(), rewarded = QuestRewards.FULL_PER_DAY + QuestRewards.HALF_PER_DAY))
        val u = Reminders.complete(s, id, at(monday, 6, 30), zone)
        assertEquals(s.gold, u.state.gold)
        // Still counts for the streak.
        assertEquals(1, Reminders.streak(u.state.reminders.single(), at(monday, 12), zone))
    }
}
