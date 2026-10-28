package com.monsterquest.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GoalsTest {
    private fun started() = GameEngine.chooseStarter(GameState(), "emberkit", Random(1)).state

    @Test
    fun goalsAreStableForTheDayAndCountFromItsStart() {
        val day = 20_000L
        assertEquals(Goals.daily(day).map { it.text }, Goals.daily(day).map { it.text })
        assertEquals(Goals.PER_PERIOD, Goals.daily(day).size)
        assertEquals(Goals.weekly(day).map { it.id }, Goals.weekly(day + 1).map { it.id }.takeIf { Goals.weekOf(day) == Goals.weekOf(day + 1) } ?: Goals.weekly(day).map { it.id })
        // The day starts: the stats so far don't count.
        var s = started().copy(stats = Lifetime(battlesWon = 40, totalSteps = 90_000, tasksCompleted = 9))
        s = Goals.rollover(s, s, day)
        assertTrue(Goals.daily(day).all { Goals.progress(s, it) == 0 })
        // Progress made today counts, even in the change that rolled the day over.
        val before = s.copy(goalDay = day - 1)
        val after = before.copy(stats = before.stats.copy(battlesWon = 43, monstersTamed = 2, tasksCompleted = 12, totalSteps = 96_000, shrinesVisited = 3, focusWins = 1, tamedByType = Element.typed.associateWith { 1 }))
        s = Goals.rollover(before, after, day)
        assertTrue(Goals.daily(day).all { Goals.done(s, it) }, Goals.daily(day).map { "${it.text} ${Goals.progress(s, it)}" }.toString())
        // Claim all three: rewards and the set bonus.
        val gold = s.gold
        for (g in Goals.daily(day)) s = Goals.claim(s, g).state
        assertEquals(gold + 3 * 120 + Goals.DAILY_SET_GOLD, s.gold)
        assertTrue(s.count(Item.TREAT) >= 1)
        // Claims can't repeat.
        assertEquals(s.gold, Goals.claim(s, Goals.daily(day).first()).state.gold)
    }
}
