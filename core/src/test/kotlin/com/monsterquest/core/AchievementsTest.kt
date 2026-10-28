package com.monsterquest.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AchievementsTest {
    @Test
    fun achievementsTrackProgressAndPayOnce() {
        var s = GameEngine.chooseStarter(GameState(), "emberkit", Random(1)).state
        assertTrue(Achievements.all.map { it.id }.toSet().size == Achievements.all.size)
        assertTrue(Achievements.claimable(s).none { it.id == "tame_1" })
        s = s.copy(stats = s.stats.copy(monstersTamed = 1))
        assertTrue(Achievements.claimable(s).any { it.id == "tame_1" })
        val gold = s.gold
        val u = Achievements.claim(s, "tame_1")
        assertEquals(gold + 100, u.state.gold)
        assertTrue("tame_1" in u.state.achievementsClaimed)
        // Only once.
        assertEquals(u.state.gold, Achievements.claim(u.state, "tame_1").state.gold)
        // Not done yet: nothing.
        assertEquals(u.state, Achievements.claim(u.state, "win_1000").state)
    }

    @Test
    fun streaksAndLongSessionsCount() {
        val base = GameEngine.chooseStarter(GameState(), "emberkit", Random(1)).state
        val days = (100L..106L).associateWith { 1 } + (110L to 1)
        assertEquals(7, Achievements.longestQuestStreak(base.copy(questDays = days)))
        val s = GameEngine.resolveFocus(GameEngine.startFocus(base, 120, 0L, Random(2)).state, true, Random(2)).state
        assertEquals(120, s.stats.longestFocus)
        assertTrue(Achievements.claimable(s).any { it.id == "focus_120" })
    }
}
